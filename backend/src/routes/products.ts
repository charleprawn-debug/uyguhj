import { Router } from 'express';
import crypto from 'crypto';
import { requireAuth } from '../middleware/auth';
import { supabaseAdmin } from '../utils/supabase';
import { ezProxyService } from '../services/ezProxy';
import { encryptionService } from '../utils/encryption';

const router = Router();
const money = (value: string | number) => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) throw new Error('Invalid provider price');
  return Math.round(parsed * 100) / 100;
};

async function providerOptions(protocol = 'socks5') {
  const [plans, locations] = await Promise.all([
    ezProxyService.plans(),
    ezProxyService.locations({ protocol }),
  ]);
  const available = locations.filter((location) => location.status === undefined || location.status === 'available');
  if (!available.length) throw new Error('لا توجد مواقع متاحة لهذا البروتوكول حاليًا.');
  return {
    protocol,
    plans: plans.map((plan) => ({ id: plan.id, name: plan.name, duration_hours: plan.duration_hours, price: money(plan.price), currency: 'USD' })),
    locations: available.map((location) => ({
      id: location.id,
      country: location.country,
      state: location.state,
      isp: location.isp,
      protocols: location.protocols,
      status: location.status ?? 'available',
    })),
  };
}

router.get('/', async (req, res) => {
  const q = supabaseAdmin.from('proxy_products').select('id,name,description,protocol,country_code,country_name,city,ip_type,price_daily,price_weekly,price_monthly,device_limit,stock_available,is_featured,category,tags').eq('is_active', true);
  if (req.query.country) q.eq('country_code', String(req.query.country).toUpperCase());
  if (req.query.protocol) q.eq('protocol', String(req.query.protocol));
  if (req.query.featured === 'true') q.eq('is_featured', true);
  const { data, error } = await q.order('is_featured', { ascending: false }).order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'PRODUCTS_UNAVAILABLE', message: 'Could not load products' } });
  return res.json({ data: data ?? [] });
});

// Provider-driven marketplace catalog. This endpoint intentionally does not read proxy_products.
router.get('/purchase-options', requireAuth, async (req, res) => {
  const protocol = String(req.query.protocol ?? 'socks5').toLowerCase();
  if (!['http', 'socks5'].includes(protocol)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'البروتوكول غير صالح.' } });
  try {
    return res.json({ data: await providerOptions(protocol) });
  } catch (providerError) {
    return res.status(502).json({ error: { code: 'PROVIDER_UNAVAILABLE', message: providerError instanceof Error ? providerError.message : 'تعذر تحميل خيارات الشراء.' } });
  }
});

router.get('/:id/purchase-options', requireAuth, async (req, res) => {
  const { data: product, error } = await supabaseAdmin.from('proxy_products').select('id,name,protocol,country_code,country_name,city,is_active').eq('id', req.params.id).eq('is_active', true).maybeSingle();
  if (error) return res.status(500).json({ error: { code: 'PRODUCT_UNAVAILABLE', message: 'Could not load product' } });
  if (!product) return res.status(404).json({ error: { code: 'NOT_FOUND', message: 'Product not found' } });
  try {
    const protocol = product.protocol?.toLowerCase() === 'http' ? 'http' : 'socks5';
    const options = await providerOptions(protocol);
    const locations = (await ezProxyService.locations({ countryCode: product.country_code ?? undefined, protocol })).filter((location) => location.status === undefined || location.status === 'available');
    return res.json({ data: { product, ...options, locations: locations.map((location) => ({ id: location.id, country: location.country, state: location.state, isp: location.isp, protocols: location.protocols })) } });
  } catch (providerError) {
    return res.status(502).json({ error: { code: 'PROVIDER_UNAVAILABLE', message: providerError instanceof Error ? providerError.message : 'تعذر تحميل خيارات الشراء.' } });
  }
});

// Provider-direct checkout. The selected location contains the country, state and ISP.
router.post('/purchase-proxy', requireAuth, async (req, res) => {
  const { planId, locationId, protocol, username, password, clientRequestId } = req.body as Record<string, unknown>;
  const parsedPlanId = Number(planId);
  const parsedLocationId = Number(locationId);
  const selectedProtocol = String(protocol ?? 'socks5').toLowerCase();
  const requestId = typeof clientRequestId === 'string' && clientRequestId.length > 0 ? clientRequestId : crypto.randomUUID();
  if (!Number.isInteger(parsedPlanId) || !Number.isInteger(parsedLocationId) || !['http', 'socks5'].includes(selectedProtocol)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'خيار الشراء غير صالح.' } });
  if (typeof username !== 'string' || !/^[A-Za-z0-9._-]{3,64}$/.test(username)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'اسم المستخدم يجب أن يكون بين 3 و64 محرفًا.' } });
  if (typeof password !== 'string' || password.length < 6 || password.length > 128) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'كلمة المرور يجب أن تكون بين 6 و128 محرفًا.' } });

  const { data: existing } = await supabaseAdmin.from('marketplace_proxy_orders').select('id,status,amount,provider_proxy_id').eq('client_request_id', requestId).eq('user_id', req.user!.id).maybeSingle();
  if (existing?.status === 'completed') {
    const { data: saved } = await supabaseAdmin.from('user_proxy_credentials').select('*').eq('order_id', existing.id).single();
    return res.status(200).json({ data: saved ? formatSavedProxy(saved, encryptionService.decrypt(saved.password_encrypted)) : existing });
  }

  let planPrice = 0;
  try {
    const [plans, locations] = await Promise.all([ezProxyService.plans(), ezProxyService.locations({ protocol: selectedProtocol })]);
    const selectedPlan = plans.find((plan) => plan.id === parsedPlanId);
    const selectedLocation = locations.find((location) => location.id === parsedLocationId && (location.status === undefined || location.status === 'available'));
    if (!selectedPlan || !selectedLocation) return res.status(409).json({ error: { code: 'OPTION_UNAVAILABLE', message: 'الخطة أو الموقع لم يعد متاحًا. حدّث الخيارات وحاول مجددًا.' } });
    planPrice = money(selectedPlan.price);
  } catch (providerError) {
    return res.status(502).json({ error: { code: 'PROVIDER_UNAVAILABLE', message: providerError instanceof Error ? providerError.message : 'تعذر التحقق من السعر.' } });
  }

  const orderId = existing?.id ?? crypto.randomUUID();
  if (!existing) {
    const { error: insertError } = await supabaseAdmin.from('marketplace_proxy_orders').insert({ id: orderId, user_id: req.user!.id, product_id: null, client_request_id: requestId, status: 'pending', protocol: selectedProtocol, plan_id: parsedPlanId, location_id: parsedLocationId, amount: planPrice, metadata: { source: 'provider_catalog' } });
    if (insertError) return res.status(409).json({ error: { code: 'CLIENT_REQUEST_ID_CONFLICT', message: 'هذا الطلب قيد المعالجة أو تم استخدامه سابقًا.' } });
  }

  const { data: reserved, error: reserveError } = await supabaseAdmin.rpc('reserve_proxy_purchase', { p_user_id: req.user!.id, p_amount: planPrice, p_reference_id: orderId, p_description: 'Provider proxy purchase' });
  const reserve = Array.isArray(reserved) ? reserved[0] : reserved;
  if (reserveError || !reserve?.success) {
    await supabaseAdmin.from('marketplace_proxy_orders').update({ status: 'failed', error_message: reserve?.message ?? reserveError?.message ?? 'Insufficient wallet balance' }).eq('id', orderId);
    return res.status(409).json({ error: { code: 'INSUFFICIENT_BALANCE', message: 'رصيد المحفظة غير كافٍ لإتمام الشراء.', data: { balance: reserve?.new_balance ?? null, required: planPrice } } });
  }

  try {
    const result = await ezProxyService.createOrder({ clientRequestId: requestId, locationId: parsedLocationId, planId: parsedPlanId, protocol: selectedProtocol as 'http' | 'socks5', username, password });
    const proxy = result.proxy;
    const { error: saveError } = await supabaseAdmin.from('user_proxy_credentials').insert({ user_id: req.user!.id, order_id: orderId, provider_proxy_id: String(proxy.id), product_id: null, protocol: proxy.protocol, host: proxy.host.trim(), port: proxy.port, username: proxy.username ?? username, password_encrypted: encryptionService.encrypt(proxy.password ?? password), proxy_url: proxy.url?.trim() ?? null, expires_at: proxy.expires_at ?? null, location: proxy.location ?? {} });
    if (saveError) throw saveError;
    await supabaseAdmin.from('marketplace_proxy_orders').update({ status: 'completed', provider_order_id: String(result.order.id), provider_proxy_id: String(proxy.id), completed_at: new Date().toISOString(), updated_at: new Date().toISOString() }).eq('id', orderId);
    return res.status(201).json({ data: { order_id: orderId, price: planPrice, balance_after: reserve.new_balance, proxy: formatProxy(proxy, proxy.password ?? password) } });
  } catch (providerError) {
    await supabaseAdmin.rpc('refund_proxy_purchase', { p_user_id: req.user!.id, p_amount: planPrice, p_reference_id: orderId, p_description: 'Refund failed proxy purchase' });
    await supabaseAdmin.from('marketplace_proxy_orders').update({ status: 'refunded', error_message: providerError instanceof Error ? providerError.message : 'Provider purchase failed' }).eq('id', orderId);
    return res.status(502).json({ error: { code: 'PROXY_PURCHASE_FAILED', message: providerError instanceof Error ? providerError.message : 'تعذر إنشاء البروكسي وتمت إعادة الرصيد.' } });
  }
});

// Legacy product checkout remains available for existing clients.
router.post('/:id/purchase-proxy', requireAuth, async (req, res) => {
  return res.status(410).json({ error: { code: 'LEGACY_CHECKOUT_DISABLED', message: 'استخدم كتالوج الشراء الجديد.' } });
});

router.get('/:id', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('proxy_products').select('*').eq('id', req.params.id).eq('is_active', true).maybeSingle();
  if (error) return res.status(500).json({ error: { code: 'PRODUCT_UNAVAILABLE', message: 'Could not load product' } });
  if (!data) return res.status(404).json({ error: { code: 'NOT_FOUND', message: 'Product not found' } });
  return res.json({ data });
});

function formatProxy(proxy: { id: number; status: string; protocol: string; host: string; port: number; username?: string; password?: string; url?: string; expires_at?: string }, password: string) {
  return { provider_id: String(proxy.id), status: proxy.status, protocol: proxy.protocol, host: proxy.host.trim(), port: proxy.port, username: proxy.username, password, url: proxy.url?.trim(), expires_at: proxy.expires_at };
}
function formatSavedProxy(row: Record<string, unknown>, password: string) {
  return { order_id: row.order_id, status: row.status, protocol: row.protocol, host: row.host, port: row.port, username: row.username, password, url: row.proxy_url, expires_at: row.expires_at };
}

export default router;
