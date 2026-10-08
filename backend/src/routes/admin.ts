import { Router } from 'express';
import { requireAuth, requireRole } from '../middleware/auth';
import { supabaseAdmin } from '../utils/supabase';

const router = Router();
router.use(requireAuth, requireRole('admin'));

router.get('/overview', async (_req, res) => {
  const tables = ['users', 'proxy_products', 'subscriptions', 'orders', 'connection_sessions'] as const;
  const counts = await Promise.all(tables.map(async (table) => {
    const result = await supabaseAdmin.from(table).select('id', { count: 'exact', head: true });
    return [table, result.error ? null : result.count ?? 0] as const;
  }));
  const { count: activeSubscriptions } = await supabaseAdmin.from('subscriptions').select('id', { count: 'exact', head: true }).eq('status', 'active');
  const { data: recentUsers } = await supabaseAdmin.from('users').select('id,email,full_name,role,status,created_at,last_login_at').order('created_at', { ascending: false }).limit(10);
  const { data: recentPlans } = await supabaseAdmin.from('proxy_products').select('id,name,protocol,country_name,price_monthly,is_active,stock_available,created_at').order('created_at', { ascending: false }).limit(10);
  return res.json({ data: { counts: Object.fromEntries(counts), activeSubscriptions: activeSubscriptions ?? 0, recentUsers: recentUsers ?? [], recentPlans: recentPlans ?? [] } });
});

router.get('/users', async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit ?? 100), 1), 250);
  const { data, error } = await supabaseAdmin.from('users').select('id,email,full_name,role,status,is_email_verified,created_at,last_login_at,login_count').order('created_at', { ascending: false }).limit(limit);
  if (error) return res.status(500).json({ error: { code: 'USERS_UNAVAILABLE', message: 'Could not load users' } });
  return res.json({ data: data ?? [] });
});

router.get('/plans', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('proxy_products').select('id,name,description,protocol,country_code,country_name,city,price_daily,price_weekly,price_monthly,price_quarterly,price_yearly,device_limit,is_active,is_featured,stock_available,category,tags,created_at,updated_at').order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'PLANS_UNAVAILABLE', message: 'Could not load plans' } });
  return res.json({ data: data ?? [] });
});

router.post('/plans', async (req, res) => {
  const body = req.body ?? {};
  if (typeof body.name !== 'string' || !body.name.trim() || typeof body.protocol !== 'string') {
    return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Plan name and protocol are required' } });
  }
  const payload = {
    name: body.name.trim(), description: typeof body.description === 'string' ? body.description.trim() : null,
    protocol: body.protocol, country_code: body.countryCode || null, country_name: body.countryName || null,
    city: body.city || null, price_daily: Number(body.priceDaily || 0), price_weekly: Number(body.priceWeekly || 0),
    price_monthly: Number(body.priceMonthly || 0), price_quarterly: Number(body.priceQuarterly || 0), price_yearly: Number(body.priceYearly || 0),
    device_limit: Math.max(1, Number(body.deviceLimit || 1)), stock_available: Math.max(0, Number(body.stockAvailable || 0)),
    category: body.category || 'basic', is_active: body.isActive !== false, is_featured: body.isFeatured === true,
  };
  const { data, error } = await supabaseAdmin.from('proxy_products').insert(payload).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'PLAN_CREATE_FAILED', message: error.message } });
  return res.status(201).json({ data });
});

router.patch('/plans/:id', async (req, res) => {
  const allowed = ['name', 'description', 'price_daily', 'price_weekly', 'price_monthly', 'price_quarterly', 'price_yearly', 'device_limit', 'stock_available', 'is_active', 'is_featured', 'category'];
  const patch = Object.fromEntries(Object.entries(req.body ?? {}).filter(([key]) => allowed.includes(key)));
  if (!Object.keys(patch).length) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'No editable fields supplied' } });
  const { data, error } = await supabaseAdmin.from('proxy_products').update({ ...patch, updated_at: new Date().toISOString() }).eq('id', req.params.id).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'PLAN_UPDATE_FAILED', message: error.message } });
  return res.json({ data });
});

export default router;
