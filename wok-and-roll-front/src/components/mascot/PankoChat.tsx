import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { useCart } from '../../context/CartContext';
import { useCountry } from '../../context/CountryContext';
import { productName, useI18n } from '../../i18n';
import { ProductDetailModal } from '../../pages/Menu/ProductDetailModal';
import { formatPrice, type Product } from '../../services/api';
import { ASSISTANT_LIMITS } from '../../services/assistantApi';
import { DishImage } from '../common/DishImage';
import { Icon } from '../common/Icon';
import { PankoArt } from './PankoArt';
import { usePankoChat, type PankoMessage } from './usePankoChat';

const SUGGESTIONS = ['popular', 'spicy', 'veggie', 'sets', 'delivery', 'hours'] as const;

/** Shows the counter of characters from this length on. */
const COUNTER_FROM = 400;

interface PankoChatProps {
  id: string;
  onClose: () => void;
}

function DishCards({
  dishes,
  canOrder,
  onAdd,
}: {
  dishes: Product[];
  canOrder: boolean;
  onAdd: (id: number) => void;
}) {
  const { t } = useI18n();
  return (
    <>
      <ul className="panko-dishes" aria-label={t('panko.dishesLabel')}>
        {dishes.map((dish) => {
          const name = productName(dish.name);
          return (
            <li key={dish.id} className="panko-dish">
              <DishImage
                src={dish.imageUrl}
                alt=""
                className="panko-dish__image"
                placeholderClassName="panko-dish__image--empty"
                width={52}
                height={52}
              />
              <span className="panko-dish__text">
                <strong>{name}</strong>
                {canOrder && <span>{formatPrice(dish.price, dish.currency)}</span>}
              </span>
              {canOrder && (
                <button
                  type="button"
                  className="panko-dish__add"
                  aria-label={t('panko.addDish', { name })}
                  onClick={() => onAdd(dish.id)}
                >
                  <Icon name="plus" />
                </button>
              )}
            </li>
          );
        })}
      </ul>
      {!canOrder && <p className="panko-dishes__hint">{t('panko.chooseCity')}</p>}
    </>
  );
}

/**
 * The chat with Panko, next to him (a full screen sheet on a phone). The answer streams in as it
 * is written; the dishes he recommends come as cards that open the dish, as on the menu. Without
 * the AI (no API key on the server) he shows the way to the menu and the booking instead.
 */
export function PankoChat({ id, onClose }: PankoChatProps) {
  const { t } = useI18n();
  const { country, allCities } = useCountry();
  const { addItem } = useCart();
  const { mode, messages, streaming, send, retry, stop, reset } = usePankoChat(true);
  const [draft, setDraft] = useState('');
  const [dishId, setDishId] = useState<number | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const titleId = useId();
  const noteId = useId();
  const logRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const stuckToEnd = useRef(true);
  const announced = useRef(messages[messages.length - 1]?.id);

  // Into the chat: the field on a computer; the title on a phone, so no keyboard jumps up.
  useEffect(() => {
    const coarse =
      typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches;
    (coarse ? titleRef.current : inputRef.current)?.focus();
  }, []);

  // Without the AI the field goes away, and the focus with it: it comes back to the title, so the
  // keyboard stays in the chat (Escape closes it, Tab reaches the links).
  useEffect(() => {
    if (mode !== 'off') return;
    const active = document.activeElement;
    if (!active || active === document.body) titleRef.current?.focus();
  }, [mode]);

  // Follow the answer as it grows, unless the guest scrolled up to read.
  useEffect(() => {
    const log = logRef.current;
    if (log && stuckToEnd.current) log.scrollTop = log.scrollHeight;
  }, [messages, mode]);

  // A screen reader hears a finished answer once, not every piece of it.
  useEffect(() => {
    const last = messages[messages.length - 1];
    if (!last || last.role !== 'assistant' || last.id === announced.current) return;
    if (last.state === 'done' || last.state === 'error') {
      announced.current = last.id;
      setAnnouncement(
        last.state === 'error' ? (last.error ?? '') : t('panko.answered', { text: last.text }),
      );
    }
  }, [messages, t]);

  function submit(event?: FormEvent) {
    event?.preventDefault();
    // While the status is still coming, the question waits in the field.
    if (!draft.trim() || streaming || mode !== 'on') return;
    stuckToEnd.current = true;
    send(draft);
    setDraft('');
  }

  function onInputKey(event: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter sends, Shift+Enter is a new line; not while an input method is composing (Chinese).
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  }

  function onPanelKey(event: KeyboardEvent<HTMLElement>) {
    // Escape inside the dish dialog closes only the dialog.
    if (event.key !== 'Escape' || (event.target as Element).closest('dialog')) return;
    event.stopPropagation();
    onClose();
  }

  function ask(text: string) {
    stuckToEnd.current = true;
    send(text);
  }

  const last = messages[messages.length - 1];

  function renderMessage(message: PankoMessage) {
    if (message.role === 'user') {
      return (
        <div key={message.id} className="panko-msg panko-msg--guest">
          <span className="sr-only">{t('panko.you')}: </span>
          {message.text}
        </div>
      );
    }
    const typing = message.state === 'streaming' && !message.text;
    return (
      <div key={message.id} className="panko-msg panko-msg--panko">
        <span className="sr-only">{t('panko.title')}: </span>
        {typing ? (
          <span className="panko-typing">
            <span aria-hidden="true" />
            <span aria-hidden="true" />
            <span aria-hidden="true" />
            <span className="sr-only">{t('panko.typing')}</span>
          </span>
        ) : (
          message.text && <p className="panko-msg__text">{message.text}</p>
        )}
        {message.dishes && message.dishes.length > 0 && (
          <DishCards dishes={message.dishes} canOrder={!allCities} onAdd={setDishId} />
        )}
        {message.state === 'stopped' && <p className="panko-msg__meta">{t('panko.stopped')}</p>}
        {message.state === 'error' && (
          <p className="panko-msg__error">
            {message.error}
            {message.id === last?.id && (
              <button type="button" className="panko-msg__retry" onClick={retry}>
                {t('panko.retry')}
              </button>
            )}
          </p>
        )}
      </div>
    );
  }

  return (
    <section
      id={id}
      className="panko-chat"
      role="dialog"
      aria-modal="false"
      aria-labelledby={titleId}
      aria-describedby={noteId}
      onKeyDown={onPanelKey}
    >
      <header className="panko-chat__head">
        <span className="panko-chat__avatar">
          <PankoArt face />
        </span>
        <div className="panko-chat__heading">
          <h2 id={titleId} ref={titleRef} tabIndex={-1}>
            {t('panko.title')}
          </h2>
          <p>
            <span className={`panko-chat__dot panko-chat__dot--${mode}`} aria-hidden="true" />
            {mode === 'off' ? t('panko.offlineSubtitle') : t('panko.subtitle')}
          </p>
        </div>
        {messages.length > 0 && (
          <button type="button" className="panko-chat__reset" onClick={reset}>
            {t('panko.reset')}
          </button>
        )}
        <button
          type="button"
          className="panko-chat__close"
          aria-label={t('panko.close')}
          onClick={onClose}
        >
          <Icon name="close" />
        </button>
      </header>

      <div
        ref={logRef}
        className="panko-chat__log"
        role="log"
        aria-live="off"
        aria-labelledby={titleId}
        onScroll={(event) => {
          const log = event.currentTarget;
          stuckToEnd.current = log.scrollHeight - log.scrollTop - log.clientHeight < 48;
        }}
      >
        <div className="panko-msg panko-msg--panko">
          <span className="sr-only">{t('panko.title')}: </span>
          <p className="panko-msg__text">{t('panko.greeting')}</p>
        </div>

        {mode === 'off' && (
          <div className="panko-msg panko-msg--panko">
            <p className="panko-msg__text">{t('panko.offline')}</p>
            <div className="panko-chat__links">
              <Link to="/menu" onClick={onClose}>
                {t('panko.offlineMenu')}
              </Link>
              <Link to="/booking" onClick={onClose}>
                {t('panko.offlineBooking')}
              </Link>
            </div>
          </div>
        )}

        {mode !== 'off' && messages.map(renderMessage)}

        {mode === 'on' && messages.length === 0 && (
          <ul className="panko-chips" aria-label={t('panko.suggestionsLabel')}>
            {SUGGESTIONS.map((key) => (
              <li key={key}>
                <button type="button" onClick={() => ask(t(`panko.suggestions.${key}`))}>
                  {t(`panko.suggestions.${key}`)}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <p className="sr-only" role="status">
        {announcement}
      </p>

      {mode !== 'off' && (
        <form className="panko-chat__form" onSubmit={submit}>
          <label className="sr-only" htmlFor={`${id}-input`}>
            {t('panko.placeholder')}
          </label>
          <textarea
            id={`${id}-input`}
            ref={inputRef}
            className="panko-chat__input"
            rows={2}
            maxLength={ASSISTANT_LIMITS.questionLength}
            placeholder={t('panko.placeholder')}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={onInputKey}
          />
          {draft.length >= COUNTER_FROM && (
            <span className="panko-chat__counter" aria-live="polite">
              {t('panko.counter', {
                count: draft.length,
                max: ASSISTANT_LIMITS.questionLength,
              })}
            </span>
          )}
          {streaming ? (
            <button
              type="button"
              className="panko-chat__send is-stop"
              aria-label={t('panko.stop')}
              onClick={stop}
            >
              <Icon name="stop" />
            </button>
          ) : (
            <button
              type="submit"
              className="panko-chat__send"
              aria-label={t('panko.send')}
              disabled={mode !== 'on' || !draft.trim()}
            >
              <Icon name="arrow" />
            </button>
          )}
        </form>
      )}

      <p id={noteId} className="panko-chat__note">
        {t('panko.note')}
      </p>

      <ProductDetailModal
        productId={dishId}
        country={country}
        onAdd={addItem}
        onClose={() => setDishId(null)}
      />
    </section>
  );
}
