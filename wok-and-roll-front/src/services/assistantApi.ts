// Panko, the AI chat (API contract §3.21). The answer streams as server-sent events over a POST,
// so it is read with fetch and a stream reader, not with EventSource (which can only GET).
import type { Locale } from '../i18n/locales';
import { apiFetch, parseProduct, requestJson, type CountryCode, type Product } from './api';
import { bool, record, str } from './parse';

/** The same limits as the server: a question, the history, an earlier answer. */
export const ASSISTANT_LIMITS = {
  questionLength: 500,
  messages: 20,
  answerLength: 2000,
} as const;

export interface AssistantStatus {
  /** false when the server has no API key: Panko then helps without the AI. */
  enabled: boolean;
}

export interface ChatTurn {
  role: 'user' | 'assistant';
  text: string;
}

export interface ChatRequest {
  country: CountryCode;
  /** The chosen city; null in "All cities". */
  city: string | null;
  language: Locale;
  /** The conversation, the guest's question last. */
  messages: ChatTurn[];
}

export interface ChatAnswer {
  /** The dishes Panko recommends, as menu cards: on the menu, in stock, at most 4. */
  dishes: Product[];
  /** true when the model declined to answer. */
  refused: boolean;
}

/** An error event in the middle of the stream: ASSISTANT_BUSY or ASSISTANT_ERROR. */
export class AssistantStreamError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'AssistantStreamError';
    this.code = code;
  }
}

export async function loadAssistantStatus(): Promise<AssistantStatus> {
  const body = record(await requestJson('/api/v1/assistant'), 'Assistant API');
  return { enabled: bool(body, 'enabled', 'Assistant API') };
}

/**
 * The last {@link ASSISTANT_LIMITS.messages} messages that the server takes: they start with a
 * question of the guest, and an earlier answer is cut to its length limit.
 */
export function historyForRequest(messages: ChatTurn[]): ChatTurn[] {
  let recent = messages.slice(-ASSISTANT_LIMITS.messages);
  while (recent.length > 0 && recent[0].role !== 'user') recent = recent.slice(1);
  return recent.map((message) =>
    message.role === 'assistant' && message.text.length > ASSISTANT_LIMITS.answerLength
      ? { ...message, text: message.text.slice(0, ASSISTANT_LIMITS.answerLength) }
      : message,
  );
}

interface SseEvent {
  name: string;
  data: string;
}

/** Splits the text of the stream into events; the rest waits for the next piece. */
function takeEvents(buffer: string): { events: SseEvent[]; rest: string } {
  const events: SseEvent[] = [];
  const blocks = buffer.replace(/\r\n?/g, '\n').split('\n\n');
  const rest = blocks.pop() ?? '';
  for (const block of blocks) {
    let name = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) name = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (data.length > 0) events.push({ name, data: data.join('\n') });
  }
  return { events, rest };
}

/**
 * Asks Panko. Every piece of the answer goes to {@code onText} as it comes; the dish cards come at
 * the end. Errors before the stream (400, 429 with retryAfterSeconds, 503) are ApiRequestError,
 * an error inside it is AssistantStreamError, and an abort (the guest pressed stop) is an
 * AbortError.
 */
export async function askPanko(
  request: ChatRequest,
  onText: (text: string) => void,
  signal?: AbortSignal,
): Promise<ChatAnswer> {
  const response = await apiFetch('/api/v1/assistant/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream, application/json',
    },
    body: JSON.stringify(request),
    signal,
  });
  if (!response.body) throw new Error('Assistant API returned no stream.');

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    buffer += done ? decoder.decode() + '\n\n' : decoder.decode(value, { stream: true });
    const { events, rest } = takeEvents(buffer);
    buffer = rest;
    for (const event of events) {
      const data = record(JSON.parse(event.data) as unknown, 'Assistant API', event.name);
      if (event.name === 'delta') {
        onText(str(data, 'text', 'Assistant API'));
      } else if (event.name === 'done') {
        await reader.cancel().catch(() => undefined);
        const dishes = Array.isArray(data.dishes) ? data.dishes.map(parseProduct) : [];
        return { dishes, refused: bool(data, 'refused', 'Assistant API') };
      } else if (event.name === 'error') {
        await reader.cancel().catch(() => undefined);
        throw new AssistantStreamError(
          typeof data.code === 'string' ? data.code : 'ASSISTANT_ERROR',
          typeof data.message === 'string' ? data.message : 'Panko cannot answer right now',
        );
      }
    }
    if (done) break;
  }
  // The stream ended without "done": the connection broke in the middle.
  throw new AssistantStreamError('ASSISTANT_ERROR', 'The answer was cut off');
}
