import { Router } from 'express';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import * as authService from './auth.service';
import { loginSchema, refreshSchema, registerSchema, verifyIdentitySchema, resetPasswordSchema } from './auth.schemas';

export const authRouter = Router();

authRouter.post(
  '/register',
  asyncHandler(async (req, res) => {
    const body = registerSchema.parse(req.body);
    const result = await authService.register(body);
    res.status(201).json(result);
  }),
);

authRouter.post(
  '/login',
  asyncHandler(async (req, res) => {
    const { email, password } = loginSchema.parse(req.body);
    const result = await authService.login(email, password);
    res.json(result);
  }),
);

authRouter.post(
  '/refresh',
  asyncHandler(async (req, res) => {
    const { refreshToken } = refreshSchema.parse(req.body);
    const tokens = await authService.refresh(refreshToken);
    res.json({ tokens });
  }),
);

authRouter.post(
  '/logout',
  asyncHandler(async (req, res) => {
    const { refreshToken } = refreshSchema.parse(req.body);
    await authService.logout(refreshToken);
    res.status(204).end();
  }),
);

// Forgot password - step 1: verify identity (email + date of birth).
authRouter.post(
  '/forgot-password/verify',
  asyncHandler(async (req, res) => {
    const { email, dob } = verifyIdentitySchema.parse(req.body);
    const verified = await authService.verifyIdentity(email, dob);
    res.json({ verified });
  }),
);

// Forgot password - step 2: set a new password (re-verifies email + DOB server-side).
authRouter.post(
  '/reset-password',
  asyncHandler(async (req, res) => {
    const { email, dob, newPassword } = resetPasswordSchema.parse(req.body);
    await authService.resetPassword(email, dob, newPassword);
    res.status(204).end();
  }),
);

authRouter.get(
  '/me',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const user = await authService.getMe(req.user!.id);
    res.json({ user });
  }),
);
