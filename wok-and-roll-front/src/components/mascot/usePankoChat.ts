import { useCallback, useEffect, useRef, useState } from 'react';
import { useCountry } from '../../context/CountryContext';
import { useI18n } from '../../i18n';
import { ApiRequestError, type Product } from '../../services/api';
import {
  askPanko,
  AssistantStreamError,
  historyForRequest,
  loadAssistantStatus,
  type ChatTurn,
} from '../../services/assistantApi';

export interface PankoMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  /** The dish cards under an answer. */
  dishes?: Product[];
  /**
   * streaming: the answer is coming; stopped: the guest stopped it; error: it failed (the text of
   * the error is in `error`, a retry asks again).
   */
  state?: 'streaming' | 'done' | 'stopped' | 'error';
  error?: string;
}

/** loading: asking the server; on: the AI answers; off: no API key (or no server). */
export type PankoMode = 'loading' | 'on' | 'off';

const STORE_KEY = 'wr.panko.chat';

interface Saved {
  country: string;
  messages: PankoMessage[];
}

// One status request per page load, shared by every chat that opens.
let statusRequest: Promise<boolean | null> | undefined;

/** Whether the AI is on; null when the server did not answer (it may still be starting). */
function aiEnabled(): Promise<boolean | null> {
  statusRequest ??= loadAssistantStatus()
    .then((status) => status.enabled)
    .catch(() => {
      statusRequest = undefined;
      return null;
    });
  return statusRequest;
}

/** How often the open chat asks again while the server does not answer. */
const RECHECK_MS = 5000;

function readSaved(country: string): PankoMessage[] {
  try {
    const saved = JSON.parse(window.sessionStorage.getItem(STORE_KEY) ?? 'null') as Saved | null;
    if (!saved || saved.country !== country || !Array.isArray(saved.messages)) return [];
    // An answer that was still coming when the page closed is kept as stopped.
    return saved.messages.map((message) =>
      message.state === 'streaming' ? { ...message, state: 'stopped' } : message,
    );
  } catch {
    return [];
  }
}

function save(country: string, messages: PankoMessage[]) {
  try {
    if (messages.length === 0) window.sessionStorage.removeItem(STORE_KEY);
    else window.sessionStorage.setItem(STORE_KEY, JSON.stringify({ country, messages }));
  } catch {
    // Private mode: the chat lasts for this page only.
  }
}

let nextId = 0;
function newId(): string {
  nextId += 1;
  return `m${Date.now().toString(36)}${nextId}`;
}

/**
 * The conversation with Panko: kept in this tab only (sessionStorage) and sent whole with every
 * question, since the server keeps nothing. A new country starts a new chat, as its menu and
 * prices are different.
 */
export function usePankoChat(active: boolean) {
  const { country, city, allCities } = useCountry();
  const { locale, t } = useI18n();
  const [mode, setMode] = useState<PankoMode>('loading');
  // The server did not answer the status: Panko is off for now, but asks again.
  const [unreachable, setUnreachable] = useState(false);
  const [checks, setChecks] = useState(0);
  const lastCheck = useRef(0);
  const [messages, setMessages] = useState<PankoMessage[]>(() => readSaved(country));
  const [chatCountry, setChatCountry] = useState(country);
  const abort = useRef<AbortController>();
  const streaming = messages.some((message) => message.state === 'streaming');

  // The status is asked when the chat opens for the first time. While the server does not answer
  // (after a restart the site is up before the server), the open chat asks again every few
  // seconds, so Panko comes back without a reload of the page.
  useEffect(() => {
    if (!active || (mode !== 'loading' && !unreachable)) return undefined;
    let current = true;
    const timer = window.setTimeout(
      () => {
        lastCheck.current = Date.now();
        void aiEnabled().then((enabled) => {
          if (!current) return;
          setUnreachable(enabled === null);
          setMode(enabled ? 'on' : 'off');
          setChecks((count) => count + 1);
        });
      },
      Math.max(0, lastCheck.current + RECHECK_MS - Date.now()),
    );
    return () => {
      current = false;
      window.clearTimeout(timer);
    };
  }, [active, mode, unreachable, checks]);

  useEffect(() => {
    if (chatCountry === country) return;
    // another country: another menu, so a new chat
    abort.current?.abort();
    setChatCountry(country);
    setMessages(readSaved(country));
  }, [chatCountry, country]);

  useEffect(() => {
    if (!streaming) save(chatCountry, messages);
  }, [chatCountry, messages, streaming]);

  useEffect(() => () => abort.current?.abort(), []);

  const update = useCallback(
    (id: string, change: (message: PankoMessage) => PankoMessage) =>
      setMessages((list) => list.map((message) => (message.id === id ? change(message) : message))),
    [],
  );

  const errorText = useCallback(
    (error: unknown): string => {
      if (error instanceof ApiRequestError && error.code === 'TOO_MANY_MESSAGES') {
        const minutes = Math.max(1, Math.ceil((error.retryAfterSeconds ?? 60) / 60));
        return t('panko.errors.tooMany', { minutes });
      }
      if (error instanceof AssistantStreamError && error.code === 'ASSISTANT_BUSY') {
        return t('panko.errors.busy');
      }
      return t('panko.errors.failed');
    },
    [t],
  );

  /** Asks the question at the end of `history` and streams the answer into a new message. */
  const ask = useCallback(
    (history: PankoMessage[]) => {
      const answerId = newId();
      setMessages([...history, { id: answerId, role: 'assistant', text: '', state: 'streaming' }]);
      const turns: ChatTurn[] = historyForRequest(
        history
          .filter((message) => message.role === 'user' || message.text.trim())
          .map(({ role, text }) => ({ role, text })),
      );
      const controller = new AbortController();
      abort.current = controller;

      askPanko(
        { country, city: allCities ? null : (city ?? null), language: locale, messages: turns },
        (text) => update(answerId, (message) => ({ ...message, text: message.text + text })),
        controller.signal,
      )
        .then((answer) =>
          update(answerId, (message) => {
            if (message.text.trim()) return { ...message, dishes: answer.dishes, state: 'done' };
            // No words at all: a declined question gets his own kind "only Wok & Roll", anything
            // else is an answer that did not come.
            return answer.refused
              ? { ...message, text: t('panko.refused'), state: 'done' }
              : { ...message, state: 'error', error: t('panko.errors.failed') };
          }),
        )
        .catch((error: unknown) => {
          if (controller.signal.aborted) {
            update(answerId, (message) => ({ ...message, state: 'stopped' }));
            return;
          }
          if (error instanceof ApiRequestError && error.code === 'ASSISTANT_UNAVAILABLE') {
            // switched off since the status was asked: no AI, and the question stays
            setUnreachable(false);
            setMode('off');
            setMessages((list) => list.filter((message) => message.id !== answerId));
            return;
          }
          update(answerId, (message) => ({ ...message, state: 'error', error: errorText(error) }));
        })
        .finally(() => {
          if (abort.current === controller) abort.current = undefined;
        });
    },
    [allCities, city, country, errorText, locale, t, update],
  );

  const send = useCallback(
    (text: string) => {
      const question = text.trim();
      if (!question || streaming || mode !== 'on') return;
      ask([...messages, { id: newId(), role: 'user', text: question }]);
    },
    [ask, messages, mode, streaming],
  );

  /** Asks the last question again, after an error. */
  const retry = useCallback(() => {
    if (streaming) return;
    const last = messages[messages.length - 1];
    ask(last?.role === 'assistant' ? messages.slice(0, -1) : messages);
  }, [ask, messages, streaming]);

  const stop = useCallback(() => abort.current?.abort(), []);

  const reset = useCallback(() => {
    abort.current?.abort();
    setMessages([]);
  }, []);

  return { mode, messages, streaming, send, retry, stop, reset };
}
