import { Router } from 'express';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { deleteAccount, exportUserData } from './account.service';

export const accountRouter = Router();

/** GDPR-style "download my data". */
accountRouter.get(
  '/me/export',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const data = await exportUserData(req.user!.id);
    res.setHeader('Content-Disposition', 'attachment; filename="kaizen-my-data.json"');
    res.json(data);
  }),
);

/** Permanently deletes the account and all its data immediately. No grace window or restore. */
accountRouter.delete(
  '/me',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    await deleteAccount(req.user!.id);
    res.status(204).end();
  }),
);
