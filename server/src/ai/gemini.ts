import { env } from '../lib/env';
import { buildSystemPrompt, type AiChatContext, type AiProvider } from './provider';

const MODEL = env.GEMINI_MODEL || 'gemini-2.0-flash';
const TIMEOUT_MS = 12_000;

/**
 * Google Gemini chat provider. Returns `null` on any problem - missing key, timeout,
 * network error, non-2xx response, or an unparsable body - so the caller can fall back
 * to the deterministic rules engine. Never throws.
 */
export const geminiProvider: AiProvider = {
  async chatReply(message: string, ctx: AiChatContext): Promise<string | null> {
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
          system_instruction: { parts: [{ text: buildSystemPrompt(ctx) }] },
          contents: [
            ...(ctx.history ?? []).map((m) => ({
              role: m.role === 'assistant' ? 'model' : 'user',
              parts: [{ text: m.content }],
            })),
            { role: 'user', parts: [{ text: message }] },
          ],
          generationConfig: { temperature: 0.3, maxOutputTokens: 400 },
        }),
        signal: controller.signal,
      });

      if (!res.ok) return null;

      const json = (await res.json()) as {
        candidates?: { content?: { parts?: { text?: string }[] } }[];
      };
      const text = json.candidates?.[0]?.content?.parts?.[0]?.text;
      return typeof text === 'string' && text.trim() ? text.trim() : null;
    } catch {
      return null;
    } finally {
      clearTimeout(timer);
    }
  },
};
