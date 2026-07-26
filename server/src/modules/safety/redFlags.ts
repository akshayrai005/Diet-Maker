/**
 * Conservative red-flag safety net. For a small, well-defined set of serious symptom patterns a
 * user might type, this returns a clear "seek urgent care" escalation. It is NOT triage or
 * diagnosis: it ONLY ever escalates to "get medical help now" — it never reassures, never rules
 * anything out, and never names a specific condition as confirmed. Pure & unit-tested.
 */

export interface RedFlag {
  id: string;
  /** What the user might describe. */
  label: string;
  /** Lowercase keyword/phrase triggers (word-boundary matched). */
  triggers: string[];
}

// Deliberately short, standard, high-specificity. Better to miss a vague phrasing than to fire on
// everyday words — every entry here is unambiguously emergency-associated.
export const RED_FLAGS: RedFlag[] = [
  {
    id: 'cardiac',
    label: 'Possible heart attack',
    triggers: ['chest pain', 'chest tightness', 'crushing chest', 'pain in my left arm', 'pain radiating to arm', 'pressure in my chest'],
  },
  {
    id: 'stroke',
    label: 'Possible stroke',
    triggers: ['face drooping', 'slurred speech', 'sudden numbness', 'weakness on one side', 'sudden confusion', "can't speak", 'facial droop'],
  },
  {
    id: 'breathing',
    label: 'Severe breathing difficulty',
    triggers: ['can\'t breathe', 'cannot breathe', 'struggling to breathe', 'severe shortness of breath', 'choking'],
  },
  {
    id: 'severe_hypoglycemia',
    label: 'Severe low blood sugar',
    triggers: ['passing out', 'about to faint', 'confused and shaky', 'seizure', 'unconscious'],
  },
  {
    id: 'severe_hyperglycemia',
    label: 'Diabetic emergency (very high sugar)',
    triggers: ['fruity breath', 'vomiting and very thirsty', 'blood sugar over 400', 'ketoacidosis'],
  },
  {
    id: 'anaphylaxis',
    label: 'Severe allergic reaction',
    triggers: ['throat closing', 'swelling of the throat', 'anaphylaxis', 'lips swelling and breathing'],
  },
  {
    id: 'suicidal',
    label: 'Thoughts of self-harm',
    triggers: ['kill myself', 'want to die', 'end my life', 'suicidal', 'hurt myself', 'no reason to live'],
  },
];

export interface RedFlagResult {
  urgent: boolean;
  matched: { id: string; label: string }[];
  message: string;
}

const EMERGENCY_MESSAGE =
  'This may be a medical emergency. Please contact your local emergency number or get to the nearest emergency department now. If you can, ask someone nearby to help. Kaizen can’t assess this — it’s important to get real medical help right away.';

const SELF_HARM_MESSAGE =
  'It sounds like you’re going through something incredibly hard, and you deserve support right now. Please reach out to a local crisis line or emergency services, or talk to someone you trust. You don’t have to face this alone — please contact a mental-health professional or crisis service today.';

/**
 * Scan free text for red-flag patterns. Returns urgent=true with an escalation message if any match.
 * Never returns "you're fine" — a non-match simply means no red flag was detected here.
 */
export function checkRedFlags(text: string): RedFlagResult {
  const hay = ` ${text.toLowerCase().replace(/\s+/g, ' ')} `;
  const matched = RED_FLAGS.filter((f) => f.triggers.some((t) => hay.includes(` ${t} `) || hay.includes(t))).map((f) => ({
    id: f.id,
    label: f.label,
  }));
  if (matched.length === 0) {
    return { urgent: false, matched: [], message: '' };
  }
  const hasSelfHarm = matched.some((m) => m.id === 'suicidal');
  return {
    urgent: true,
    matched,
    message: hasSelfHarm ? SELF_HARM_MESSAGE : EMERGENCY_MESSAGE,
  };
}
