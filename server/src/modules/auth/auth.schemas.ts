import { z } from 'zod';

export const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8, 'Password must be at least 8 characters').max(200),
  firstName: z.string().min(1).max(80),
  lastName: z.string().min(1).max(80),
  /** Digits only (with optional leading +), 7-15 chars per E.164 - loosely validated, not SMS-verified. */
  phone: z.string().regex(/^\+?[0-9]{7,15}$/, 'Enter a valid phone number').optional(),
});

export const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

export const refreshSchema = z.object({
  refreshToken: z.string().min(1),
});

export const verifyIdentitySchema = z.object({
  email: z.string().email(),
  dob: z.string().date(),
});

export const resetPasswordSchema = z.object({
  email: z.string().email(),
  dob: z.string().date(),
  newPassword: z.string().min(8, 'Password must be at least 8 characters').max(200),
});

export type RegisterBody = z.infer<typeof registerSchema>;
export type LoginBody = z.infer<typeof loginSchema>;
