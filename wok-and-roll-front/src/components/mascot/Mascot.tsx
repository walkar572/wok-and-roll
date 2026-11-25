import { useEffect, useId, useRef, useState } from 'react';
import { useI18n } from '../../i18n';
import { Icon } from '../common/Icon';
import { PankoArt } from './PankoArt';
import { PankoChat } from './PankoChat';

const HIDDEN_KEY = 'wr.mascot.hidden';
const GREETED_KEY = 'wr.mascot.greeted';
/** As long as the mascot-out animation in mascot.css. */
const LEAVE_MS = 280;

function readStore(store: 'local' | 'session', key: string): string | null {
  try {
    return (store === 'local' ? window.localStorage : window.sessionStorage).getItem(key);
  } catch {
    return null;
  }
}

function writeStore(store: 'local' | 'session', key: string, value: string | null) {
  try {
    const storage = store === 'local' ? window.localStorage : window.sessionStorage;
    if (value === null) storage.removeItem(key);
    else storage.setItem(key, value);
  } catch {
    // Private mode: the choice lasts for this page only.
  }
}

function prefersReducedMotion(): boolean {
  return (
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/**
 * Panko in the bottom right corner of the shop (not in the admin panel), next to a round arrow
 * button that tucks him away into the corner and brings him back; the choice is remembered. He
 * breathes, wags his tail, blinks and tosses his wok; once a visit he says hello. A click on him
 * makes him wave and toss the wok, and opens the chat with him beside him (api-contract.md 3.21).
 */
export function Mascot() {
  const { t } = useI18n();
  const id = useId();
  const chatId = useId();
  const [visible, setVisible] = useState(() => readStore('local', HIDDEN_KEY) !== '1');
  const [leaving, setLeaving] = useState(false);
  const [bubble, setBubble] = useState<'hello' | null>(null);
  const [chatOpen, setChatOpen] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const [greeting, setGreeting] = useState(false);
  const greetTimer = useRef<number>();
  const leaveTimer = useRef<number>();
  const helloTimer = useRef<number>();

  // The first page of a visit: "Hi! I'm Panko" for a few seconds. The visit counts as greeted
  // only when the bubble shows (an effect that runs twice in development must not skip it).
  useEffect(() => {
    if (!visible || readStore('session', GREETED_KEY) === '1') return undefined;
    const show = window.setTimeout(() => {
      writeStore('session', GREETED_KEY, '1');
      // a click before it (his message) wins over the hello
      setBubble((current) => current ?? 'hello');
    }, 1600);
    const hide = window.setTimeout(
      () => setBubble((current) => (current === 'hello' ? null : current)),
      8000,
    );
    return () => {
      window.clearTimeout(show);
      window.clearTimeout(hide);
    };
  }, [visible]);

  useEffect(
    () => () => {
      window.clearTimeout(greetTimer.current);
      window.clearTimeout(leaveTimer.current);
      window.clearTimeout(helloTimer.current);
    },
    [],
  );

  function toggleChat() {
    // he waves when the chat opens; the hello is not needed any more
    writeStore('session', GREETED_KEY, '1');
    setBubble(null);
    if (!chatOpen) {
      setGreeting(true);
      window.clearTimeout(greetTimer.current);
      greetTimer.current = window.setTimeout(() => setGreeting(false), 1400);
    }
    setChatOpen(!chatOpen);
  }

  function closeChat() {
    setChatOpen(false);
    buttonRef.current?.focus();
  }

  function toggle() {
    window.clearTimeout(helloTimer.current);
    if (visible && !leaving) {
      // into the corner: the leave animation first, then he is gone
      writeStore('local', HIDDEN_KEY, '1');
      setBubble(null);
      setChatOpen(false);
      if (prefersReducedMotion()) {
        setVisible(false);
        return;
      }
      setLeaving(true);
      leaveTimer.current = window.setTimeout(() => {
        setVisible(false);
        setLeaving(false);
      }, LEAVE_MS);
      return;
    }
    // back from the corner, with a hello
    window.clearTimeout(leaveTimer.current);
    writeStore('local', HIDDEN_KEY, null);
    setLeaving(false);
    setVisible(true);
    setBubble('hello');
    helloTimer.current = window.setTimeout(
      () => setBubble((current) => (current === 'hello' ? null : current)),
      4000,
    );
  }

  const shown = visible && !leaving;

  return (
    <div className={`mascot-dock${chatOpen && shown ? ' is-chatting' : ''}`}>
      {chatOpen && shown && <PankoChat id={chatId} onClose={closeChat} />}
      {visible && (
        <div
          id={id}
          className={`mascot${greeting ? ' is-greeting' : ''}${leaving ? ' is-leaving' : ''}`}
        >
          {bubble && !chatOpen && (
            // Decoration: the button says the same (its name), so it is not read twice.
            <div className="mascot__bubble" aria-hidden="true">
              <strong>{t('mascot.hello')}</strong>
              <p>{t('mascot.intro')}</p>
            </div>
          )}
          <button
            ref={buttonRef}
            type="button"
            className="mascot__button"
            aria-label={t('mascot.label')}
            aria-expanded={chatOpen}
            aria-controls={chatOpen ? chatId : undefined}
            onClick={toggleChat}
            onKeyDown={(event) => {
              if (event.key === 'Escape' && chatOpen) closeChat();
            }}
          >
            <PankoArt />
          </button>
        </div>
      )}
      <button
        type="button"
        className="mascot-toggle"
        aria-label={shown ? t('mascot.hide') : t('mascot.show')}
        aria-expanded={shown}
        aria-controls={visible ? id : undefined}
        onClick={toggle}
      >
        {/* towards the corner to tuck him away, out of it to bring him back */}
        <Icon name={shown ? 'right' : 'left'} />
      </button>
    </div>
  );
}
