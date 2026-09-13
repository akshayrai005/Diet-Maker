import { prisma } from '../../lib/prisma';
import { decryptJson } from '../../lib/crypto';
import type { SensitiveData } from '../profile/profile.schemas';
import { getAdherence } from './adherence.service';
import { suggestSupplements, type SuggestedSupplement } from './supplementSuggest';

/** Real supplement suggestions for a user - diet/allergen/condition-gated, protein-gap-aware. */
export async function getSupplementSuggestions(userId: string): Promise<SuggestedSupplement[]> {
  const profile = await prisma.profile.findUnique({ where: { userId } });
  const sensitive = profile?.sensitiveEnc ? decryptJson<SensitiveData>(profile.sensitiveEnc) : null;
  const adherence = await getAdherence(userId);

  return suggestSupplements({
    dietType: profile?.dietType ?? 'nonveg',
    conditions: sensitive?.conditions ?? [],
    allergies: sensitive?.allergies ?? [],
    proteinAdherencePct: adherence.protein.pct,
  });
}
