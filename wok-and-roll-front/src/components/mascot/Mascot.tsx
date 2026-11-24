import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n';
import { Icon } from '../common/Icon';
import { PankoArt } from './PankoArt';

const HIDDEN_KEY = 'wr.mascot.hidden';
const GREETED_KEY = 'wr.mascot.greeted';

function readSession(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeSession(key: string, value: string) {
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    // Private mode: the choice lasts for this page only.
  }
}

/**
 * Panko in the bottom right corner of the shop (not in the admin panel). He breathes, wags his
 * tail, blinks and tosses his wok; once a visit he says hello. A click makes him wave, toss the
 * wok and say what he will do: the chat with him comes later, in this same corner. The cross hides
 * him until the next visit.
 */
export function Mascot() {
  const { t } = useI18n();
  const [hidden, setHidden] = useState(() => readSession(HIDDEN_KEY) === '1');
  const [bubble, setBubble] = useState<'hello' | 'intro' | null>(null);
  const [greeting, setGreeting] = useState(false);
  const greetTimer = useRef<number>();

  // The first page of a visit: "Hi! I'm Panko" for a few seconds. The visit counts as greeted
  // only when the bubble shows (an effect that runs twice in development must not skip it).
  useEffect(() => {
    if (hidden || readSession(GREETED_KEY) === '1') return undefined;
    const show = window.setTimeout(() => {
      writeSession(GREETED_KEY, '1');
      setBubble('hello');
    }, 1600);
    const hide = window.setTimeout(
      () => setBubble((current) => (current === 'hello' ? null : current)),
      8000,
    );
    return () => {
      window.clearTimeout(show);
      window.clearTimeout(hide);
    };
  }, [hidden]);

  useEffect(() => () => window.clearTimeout(greetTimer.current), []);

  if (hidden) return null;

  function greet() {
    setBubble((current) => (current === 'intro' ? null : 'intro'));
    setGreeting(true);
    window.clearTimeout(greetTimer.current);
    greetTimer.current = window.setTimeout(() => setGreeting(false), 1400);
  }

  function hide() {
    writeSession(HIDDEN_KEY, '1');
    setHidden(true);
  }

  return (
    <div className={`mascot${greeting ? ' is-greeting' : ''}`}>
      {bubble && (
        // The same words are read once from the status line below.
        <div className="mascot__bubble" aria-hidden="true">
          <strong>{t('mascot.hello')}</strong>
          {bubble === 'intro' && <p>{t('mascot.intro')}</p>}
        </div>
      )}
      <p className="sr-only" role="status">
        {bubble === 'intro' ? `${t('mascot.hello')}. ${t('mascot.intro')}` : ''}
      </p>
      <div className="mascot__figure">
        <button
          type="button"
          className="mascot__button"
          aria-label={t('mascot.label')}
          aria-expanded={bubble === 'intro'}
          onClick={greet}
        >
          <PankoArt />
        </button>
        <button type="button" className="mascot__hide" aria-label={t('mascot.hide')} onClick={hide}>
          <Icon name="close" />
        </button>
      </div>
    </div>
  );
}
