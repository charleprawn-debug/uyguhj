import config from '../config';

type Json = Record<string, unknown>;
type RequestOptions = { method?: string; headers?: Record<string, string>; body?: string; signal?: AbortSignal };
export type CheapService = { id: string; label: string; plans?: { id: string; label: string }[] };
export type CheapProxy = { id: string; status: string; protocol: string; host: string; port: number; username: string; password: string; expiresAt?: string; location?: Json };

const asObject = (value: unknown): Json => value && typeof value === 'object' ? value as Json : {};
const asArray = (value: unknown) => Array.isArray(value) ? value : [];
const providerErrorMessage = (value: unknown): string => {
  if (typeof value === 'string') return value;
  if (Array.isArray(value)) return value.map(providerErrorMessage).filter(Boolean).join('; ');
  if (value && typeof value === 'object') {
    const item = value as Record<string, unknown>;
    const messages = [item.message, item.detail, item.code, item.parameters].map(providerErrorMessage).filter(Boolean);
    return messages.join(' ');
  }
  return '';
};

class ProxyCheapService {
  private get baseUrl() { return config.proxyCheap.baseUrl.replace(/\/$/, ''); }
  private async request<T>(path: string, init: RequestOptions = {}): Promise<T> {
    if (!config.proxyCheap.apiKey || !config.proxyCheap.apiSecret) throw new Error('Proxy-Cheap is not configured');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), config.proxyCheap.timeoutMs);
    try {
      const response = await fetch(`${this.baseUrl}${path}`, { ...init, signal: controller.signal, headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-Api-Key': config.proxyCheap.apiKey, 'X-Api-Secret': config.proxyCheap.apiSecret, ...(init.headers ?? {}) } });
      const raw = await response.text();
      let body: unknown = {};
      try { body = raw ? JSON.parse(raw) as unknown : {}; } catch { body = { errors: raw }; }
      const errors = asObject(body).errors;
      if (!response.ok || errors) throw new Error(providerErrorMessage(errors) || `Proxy-Cheap request failed (${response.status})`);
      return body as T;
    } finally { clearTimeout(timer); }
  }
  async services(): Promise<CheapService[]> {
    const body = await this.request<Json>('/order');
    return asArray(body.services).map((item) => { const x = asObject(item); return { id: String(x.id ?? ''), label: String(x.label ?? x.id ?? ''), plans: asArray(x.plans).map((p) => { const y = asObject(p); return { id: String(y.id ?? ''), label: String(y.label ?? y.id ?? '') }; }) }; }).filter((x) => x.id);
  }
  async setup(serviceId: string, planId?: string): Promise<Json> {
    const body = await this.request<Json>(`/order/${encodeURIComponent(serviceId)}`, { method: 'POST', body: JSON.stringify({ planId: planId ?? '' }) });
    return body;
  }
  async price(serviceId: string, payload: Json): Promise<Json> {
    return this.request<Json>(`/order/${encodeURIComponent(serviceId)}/price`, { method: 'POST', body: JSON.stringify(payload) });
  }
  async execute(serviceId: string, payload: Json): Promise<Json> {
    return this.request<Json>(`/order/${encodeURIComponent(serviceId)}/execute`, { method: 'POST', body: JSON.stringify(payload) });
  }
  async proxyAction(proxyId: string, action: string, payload: Json = {}): Promise<Json> {
    const paths: Record<string, { method: string; path: string }> = { 'enable-autoextend': { method: 'POST', path: 'auto-extend/enable' }, 'disable-autoextend': { method: 'POST', path: 'auto-extend/disable' }, 'extend-period': { method: 'POST', path: 'extend-period' }, 'buy-bandwidth': { method: 'POST', path: 'buy-bandwidth' }, 'whitelist-ip': { method: 'POST', path: 'whitelist-ip' }, 'change-protocol': { method: 'POST', path: 'change-protocol' }, 'change-authentication': { method: 'POST', path: 'change-authentication-type' }, rotate: { method: 'POST', path: 'rotate-ip' } };
    const target = paths[action]; if (!target) throw new Error('Proxy-Cheap action is not supported');
    return this.request<Json>(`/proxies/${encodeURIComponent(proxyId)}/${target.path}`, { method: target.method, body: JSON.stringify(payload) });
  }
  async proxies(orderId: string): Promise<CheapProxy[]> {
    const body = await this.request<unknown>(`/orders/${encodeURIComponent(orderId)}/proxies`);
    return asArray(body).map((item) => {
      const x = asObject(item); const auth = asObject(x.authentication); const connection = asObject(x.connection);
      const protocol = String(x.proxyType ?? 'HTTP').toLowerCase();
      const port = Number(connection.socks5Port ?? connection.httpsPort ?? connection.httpPort);
      return { id: String(x.id ?? ''), status: String(x.status ?? ''), protocol, host: String(connection.connectIp ?? connection.publicIp ?? ''), port, username: String(auth.username ?? ''), password: String(auth.password ?? ''), expiresAt: typeof x.expiresAt === 'string' ? x.expiresAt : undefined, location: asObject(x.metadata) };
    }).filter((x) => x.id && x.host && Number.isInteger(x.port) && x.port > 0);
  }
  async waitForProxy(orderId: string): Promise<CheapProxy> {
    for (let attempt = 0; attempt < 12; attempt += 1) {
      const proxies = await this.proxies(orderId);
      const proxy = proxies.find((x) => ['ACTIVE', 'active'].includes(x.status)) ?? proxies[0];
      if (proxy) return proxy;
      await new Promise((resolve) => setTimeout(resolve, 2500));
    }
    throw new Error('Proxy-Cheap order is still provisioning');
  }
}
export const proxyCheapService = new ProxyCheapService();
export default proxyCheapService;
