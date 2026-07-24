/** Account-deletion grace period. During this window a soft-deleted account can be restored
 * simply by logging back in; after it, the account is treated as gone. PURE. */
export const DELETE_GRACE_DAYS = 7;

/** True if a soft-delete timestamp is still inside the grace window (restorable). */
export function withinGrace(deletedAt: Date | null | undefined, now: Date, graceDays = DELETE_GRACE_DAYS): boolean {
  if (!deletedAt) return true; // not deleted at all
  return now.getTime() - deletedAt.getTime() < graceDays * 86_400_000;
}
