import { Router } from 'express';
import { requireAuth } from '../middleware/auth';
import { supabaseAdmin } from '../utils/supabase';
const router = Router();
router.use(requireAuth);
router.get('/', async (req, res) => { const { data, error } = await supabaseAdmin.from('users').select('id,email,full_name,avatar_url,country_code,language,role,status,is_email_verified,created_at').eq('id', req.user!.id).single(); if (error) return res.status(500).json({ error: { code: 'PROFILE_UNAVAILABLE', message: 'Could not load profile' } }); return res.json({ data }); });
router.get('/subscriptions', async (req, res) => { const { data, error } = await supabaseAdmin.from('subscriptions').select('*,proxy_products(name,protocol,country_code)').eq('user_id', req.user!.id).order('created_at', { ascending: false }); if (error) return res.status(500).json({ error: { code: 'SUBSCRIPTIONS_UNAVAILABLE', message: 'Could not load subscriptions' } }); return res.json({ data: data ?? [] }); });
export default router;
