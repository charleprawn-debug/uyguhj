import { Router } from 'express';
import { requireAuth } from '../middleware/auth';
import { supabaseAdmin } from '../utils/supabase';

const router = Router();
router.use(requireAuth);

router.get('/', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('users').select('id,email,full_name,avatar_url,country_code,language,role,status,is_email_verified,created_at').eq('id', req.user!.id).single();
  if (error) return res.status(500).json({ error: { code: 'PROFILE_UNAVAILABLE', message: 'Could not load profile' } });
  return res.json({ data });
});

router.get('/subscriptions', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('subscriptions').select('*,proxy_products(id,name,description,protocol,country_code,country_name,price_monthly,feature_flags),subscription_plans(id,name,description,price,currency,duration_days,feature_flags)').eq('user_id', req.user!.id).order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'SUBSCRIPTIONS_UNAVAILABLE', message: 'Could not load subscriptions' } });
  return res.json({ data: data ?? [] });
});

router.get('/subscription-plans', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('subscription_plans').select('id,name,description,price,currency,duration_days,is_active,is_featured,feature_flags').eq('is_active', true).order('is_featured', { ascending: false }).order('sort_order', { ascending: true });
  if (error) return res.status(500).json({ error: { code: 'SUBSCRIPTION_PLANS_UNAVAILABLE', message: 'Could not load subscription plans' } });
  return res.json({ data: data ?? [] });
});

router.get('/wallet', async (req, res) => {
  const { data, error } = await supabaseAdmin.rpc('ensure_wallet', { p_user_id: req.user!.id });
  if (error) return res.status(500).json({ error: { code: 'WALLET_UNAVAILABLE', message: 'Could not load wallet' } });
  return res.json({ data });
});

router.get('/wallet/transactions', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('wallet_transactions').select('id,amount,balance_after,transaction_type,status,description,reference_id,created_at').eq('user_id', req.user!.id).order('created_at', { ascending: false }).limit(100);
  if (error) return res.status(500).json({ error: { code: 'WALLET_HISTORY_UNAVAILABLE', message: 'Could not load wallet history' } });
  return res.json({ data: data ?? [] });
});

router.post('/wallet/top-up', async (_req, res) => res.status(409).json({ error: { code: 'TOP_UP_NOT_AVAILABLE', message: 'Wallet top-up methods are not available yet' } }));

router.get('/access', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('subscriptions').select('status,expires_at,proxy_products(feature_flags),subscription_plans(feature_flags)').eq('user_id', req.user!.id).eq('status', 'active').gt('expires_at', new Date().toISOString());
  if (error) return res.status(500).json({ error: { code: 'ACCESS_UNAVAILABLE', message: 'Could not load feature access' } });
  const access = { advanced: false, sim: false, vpn: false, mock_location: false };
  for (const row of data ?? []) for (const flags of [row.proxy_products, row.subscription_plans]) for (const [key, value] of Object.entries((flags as { feature_flags?: Record<string, boolean> } | null)?.feature_flags ?? {})) if (key in access && value === true) access[key as keyof typeof access] = true;
  const { data: grants } = await supabaseAdmin.from('user_feature_grants').select('feature_key,enabled,expires_at').eq('user_id', req.user!.id).eq('enabled', true);
  for (const grant of grants ?? []) if ((!grant.expires_at || new Date(grant.expires_at) > new Date()) && grant.feature_key in access) access[grant.feature_key as keyof typeof access] = true;
  return res.json({ data: access });
});

router.post('/subscription-plans/:id/activate', async (req, res) => {
  const { data, error } = await supabaseAdmin.rpc('purchase_subscription_plan', { p_user_id: req.user!.id, p_plan_id: req.params.id });
  if (error) return res.status(500).json({ error: { code: 'PLAN_PURCHASE_FAILED', message: error.message } });
  const result = Array.isArray(data) ? data[0] : data;
  if (!result?.success) return res.status(409).json({ error: { code: 'PLAN_PURCHASE_REJECTED', message: result?.message ?? 'Could not activate plan' }, data: result });
  return res.status(201).json({ data: result });
});

export default router;
