import { env } from '../lib/env';

const MODEL = env.GEMINI_MODEL || 'gemini-2.0-flash';
const TIMEOUT_MS = 20_000;

/**
 * Sends an image + prompt to Gemini and returns parsed JSON, or null on any problem
 * (missing key, timeout, non-2xx, unparsable). Never throws. The image is NOT persisted -
 * it is forwarded to Gemini for this single request only.
 */
export async function geminiVisionJson(
  imageBase64: string,
  mimeType: string,
  prompt: string,
): Promise<Record<string, unknown> | null> {
  const key = env.GEMINI_API_KEY;
  if (!key) return null;

  const url = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${key}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [
          {
            role: 'user',
            parts: [
              { text: prompt },
              { inline_data: { mime_type: mimeType, data: imageBase64 } },
            ],
          },
        ],
        generationConfig: { temperature: 0.2, maxOutputTokens: 500, responseMimeType: 'application/json' },
      }),
      signal: controller.signal,
    });
    if (!res.ok) return null;
    const json = (await res.json()) as {
      candidates?: { content?: { parts?: { text?: string }[] } }[];
    };
    const text = json.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!text) return null;
    return parseJsonLoose(text);
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/** Parse JSON that may be wrapped in ```json fences or have surrounding prose. */
function parseJsonLoose(text: string): Record<string, unknown> | null {
  const cleaned = text.replace(/```json/gi, '').replace(/```/g, '').trim();
  try {
    return JSON.parse(cleaned) as Record<string, unknown>;
  } catch {
    const start = cleaned.indexOf('{');
    const end = cleaned.lastIndexOf('}');
    if (start >= 0 && end > start) {
      try {
        return JSON.parse(cleaned.slice(start, end + 1)) as Record<string, unknown>;
      } catch {
        return null;
      }
    }
    return null;
  }
}

export const visionAvailable = (): boolean => Boolean(env.GEMINI_API_KEY);

/** Raw Gemini test call that surfaces the HTTP status + error body (no key leaked). */
export async function geminiDiagnostic(): Promise<{ ok: boolean; status?: number; error?: string; model: string }> {
  const key = env.GEMINI_API_KEY;
  if (!key) return { ok: false, error: 'no key', model: MODEL };
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${key}`;
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: 'Say hi' }] }] }),
    });
    if (res.ok) return { ok: true, status: 200, model: MODEL };
    const body = await res.text();
    return { ok: false, status: res.status, error: body.slice(0, 400), model: MODEL };
  } catch (e) {
    return { ok: false, error: String((e as Error)?.message ?? e).slice(0, 300), model: MODEL };
  }
}

/** Groq (OpenAI-compatible) text call returning parsed JSON, or null. Never throws. */
export async function groqTextJson(prompt: string): Promise<Record<string, unknown> | null> {
  const key = env.GROQ_API_KEY;
  if (!key) return null;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15_000);
  try {
    const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: 'llama-3.3-70b-versatile',
        messages: [{ role: 'user', content: prompt }],
        temperature: 0.4,
        max_tokens: 800,
        response_format: { type: 'json_object' },
      }),
      signal: controller.signal,
    });
    if (!res.ok) return null;
    const json = (await res.json()) as { choices?: { message?: { content?: string } }[] };
    const text = json.choices?.[0]?.message?.content;
    return text ? parseJsonLoose(text) : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/** Text JSON via whichever LLM is configured (Gemini first, else Groq). */
export async function llmTextJson(prompt: string): Promise<Record<string, unknown> | null> {
  return (await geminiTextJson(prompt)) ?? (await groqTextJson(prompt));
}

/** True if any text LLM (Gemini or Groq) is configured. */
export const llmAvailable = (): boolean => Boolean(env.GEMINI_API_KEY || env.GROQ_API_KEY);

/** Text-only Gemini call that returns parsed JSON, or null. Never throws. */
export async function geminiTextJson(prompt: string): Promise<Record<string, unknown> | null> {
  const key = env.GEMINI_API_KEY;
  if (!key) return null;
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${key}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.4, maxOutputTokens: 700, responseMimeType: 'application/json' },
      }),
      signal: controller.signal,
    });
    if (!res.ok) return null;
    const json = (await res.json()) as { candidates?: { content?: { parts?: { text?: string }[] } }[] };
    const text = json.candidates?.[0]?.content?.parts?.[0]?.text;
    return text ? parseJsonLoose(text) : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}
