import { env } from '../lib/env';
import { buildSystemPrompt, type AiChatContext, type AiProvider } from './provider';

const GROQ_URL = 'https://api.groq.com/openai/v1/chat/completions';
const MODEL = 'llama-3.3-70b-versatile';
const TIMEOUT_MS = 12_000;

/**
 * Groq (OpenAI-compatible) chat provider. Returns `null` on any problem - missing key,
 * timeout, network error, non-2xx response, or an unparsable body - so the caller can
 * fall back to the deterministic rules engine. Never throws.
 */
export const groqProvider: AiProvider = {
  async chatReply(message: string, ctx: AiChatContext): Promise<string | null> {
    const key = env.GROQ_API_KEY;
    if (!key) return null;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const res = await fetch(GROQ_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${key}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model: MODEL,
          messages: [
            { role: 'system', content: buildSystemPrompt(ctx) },
            ...(ctx.history ?? []).map((m) => ({ role: m.role, content: m.content })),
            { role: 'user', content: message },
          ],
          temperature: 0.3,
          max_tokens: 400,
        }),
        signal: controller.signal,
      });

      if (!res.ok) return null;

      const json = (await res.json()) as {
        choices?: { message?: { content?: string } }[];
      };
      const text = json.choices?.[0]?.message?.content;
      return typeof text === 'string' && text.trim() ? text.trim() : null;
    } catch {
      return null;
    } finally {
      clearTimeout(timer);
    }
  },
};
