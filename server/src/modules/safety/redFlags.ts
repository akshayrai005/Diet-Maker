/**
 * Conservative red-flag safety net. For a small, well-defined set of serious symptom patterns a
 * user might type, this returns a clear "seek urgent care" escalation. It is NOT triage or
 * diagnosis: it ONLY ever escalates to "get medical help now" - it never reassures, never rules
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
// everyday words - every entry here is unambiguously emergency-associated.
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

/**
 * Disordered-eating language. NOT a diagnosis and not an emergency: it only triggers a supportive reply that
 * declines to coach the behaviour and points to a professional. A single mention is enough - better a gentle
 * check-in than coaching someone through starving or purging.
 */
const DISORDERED_EATING = [
  'starve myself', 'starving myself', 'stop eating completely', 'stop eating altogether', 'not eat for days', "haven't eaten in days", 'make myself throw up', 'make myself puke',
  'purge', 'purging', 'laxatives to lose', 'laxative to lose', 'diuretics to lose', 'eat under 800', 'eat 500 calories', '500 calories a day',
  '600 calories a day', '800 calories a day', 'hate my body', 'feel guilty after eating', 'binge and', 'anorexia', 'bulimia',
];
export const DISORDERED_EATING_MESSAGE =
  'I can’t coach that safely - eating very little, purging, or using laxatives/diuretics to lose weight can seriously harm your health, and it isn’t something to push through alone. Please talk to a doctor, a registered dietitian or a counsellor who works with eating concerns (in India, your GP or a psychiatrist/psychologist can refer you). If you feel unwell, faint, or have chest pain, get medical help now. I’m glad to help you build a sustainable, well-fed plan whenever you want.';
export function checkDisorderedEating(text: string): boolean {
  const hay = text.toLowerCase().replace(/\s+/g, ' ');
  return DISORDERED_EATING.some((t) => hay.includes(t));
}

export interface RedFlagResult {
  urgent: boolean;
  matched: { id: string; label: string }[];
  message: string;
}

const EMERGENCY_MESSAGE =
  'This may be a medical emergency. Please contact your local emergency number or get to the nearest emergency department now. If you can, ask someone nearby to help. Kaizen canâ€™t assess this - itâ€™s important to get real medical help right away.';

const SELF_HARM_MESSAGE =
  'It sounds like youâ€™re going through something incredibly hard, and you deserve support right now. Please reach out to a local crisis line or emergency services, or talk to someone you trust. You donâ€™t have to face this alone - please contact a mental-health professional or crisis service today.';

/**
 * Scan free text for red-flag patterns. Returns urgent=true with an escalation message if any match.
 * Never returns "you're fine" - a non-match simply means no red flag was detected here.
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
