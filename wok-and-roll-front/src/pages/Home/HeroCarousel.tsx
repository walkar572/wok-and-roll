import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { Link } from 'react-router-dom';
import { Icon } from '../../components/common/Icon';
import { useI18n } from '../../i18n';

export interface HeroSlide {
  key: string;
  image: string;
  /** The dish name in the language of the interface. */
  name: string;
  /** The price in the local currency, when the menu is loaded. */
  price?: string;
}

// The same as the progress animation of the active dot (hero-progress, figma-layout.css).
const INTERVAL_MS = 5000;

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/**
 * Photos of dishes from the menu, one after another (WAI-ARIA carousel pattern). It moves on by
 * itself every 5 s; it stops while the mouse or the keyboard focus is on it, with the pause button
 * (WCAG 2.2.2) and when the system asks for reduced motion. Arrows, the arrow keys and a swipe
 * change the dish.
 */
export function HeroCarousel({ slides }: { slides: HeroSlide[] }) {
  const { t } = useI18n();
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(() => !prefersReducedMotion());
  const [held, setHeld] = useState(false);
  const swipeStart = useRef<number | null>(null);
  const count = slides.length;
  const current = slides[Math.min(index, count - 1)];
  const running = playing && !held && count > 1;

  // A new country can have fewer dishes: stay inside the list.
  useEffect(() => {
    if (index >= count) setIndex(0);
  }, [count, index]);

  useEffect(() => {
    if (!running) return undefined;
    const timer = window.setTimeout(() => setIndex((value) => (value + 1) % count), INTERVAL_MS);
    return () => window.clearTimeout(timer);
  }, [running, index, count]);

  if (!current) return null;

  const show = (next: number) => setIndex((next + count) % count);

  function onKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      show(index - 1);
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      show(index + 1);
    }
  }

  function onPointerDown(event: PointerEvent<HTMLElement>) {
    if (event.pointerType === 'touch') swipeStart.current = event.clientX;
  }

  function onPointerUp(event: PointerEvent<HTMLElement>) {
    if (swipeStart.current === null) return;
    const distance = event.clientX - swipeStart.current;
    swipeStart.current = null;
    if (Math.abs(distance) > 40) show(distance < 0 ? index + 1 : index - 1);
  }

  return (
    <section
      className="hero-carousel"
      aria-roledescription="carousel"
      aria-label={t('home.carousel.label')}
      onMouseEnter={() => setHeld(true)}
      onMouseLeave={() => setHeld(false)}
      onFocus={() => setHeld(true)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setHeld(false);
      }}
      onKeyDown={onKeyDown}
      onPointerDown={onPointerDown}
      onPointerUp={onPointerUp}
      onPointerCancel={() => {
        swipeStart.current = null;
      }}
    >
      <div className="hero-carousel__slides">
        {slides.map((slide, position) => (
          <div
            key={slide.key}
            className={`hero-carousel__slide${position === index ? ' is-active' : ''}`}
            role="group"
            aria-roledescription="slide"
            aria-label={t('home.carousel.slide', { current: position + 1, total: count })}
            aria-hidden={position !== index}
          >
            <img
              src={slide.image}
              alt={slide.name}
              width="512"
              height="512"
              loading={position === 0 ? 'eager' : 'lazy'}
              draggable={false}
            />
          </div>
        ))}
      </div>

      <Link className="hero-carousel__caption" to="/menu">
        <span className="hero-carousel__name">{current.name}</span>
        {current.price && <span className="hero-carousel__price">{current.price}</span>}
      </Link>

      {count > 1 && (
        <div className="hero-carousel__controls">
          <button
            className="hero-carousel__button"
            type="button"
            aria-label={t('home.carousel.prev')}
            onClick={() => show(index - 1)}
          >
            <Icon name="left" />
          </button>
          <div className="hero-carousel__dots" aria-hidden="true">
            {slides.map((slide, position) => (
              <span
                key={slide.key}
                className={`hero-carousel__dot${position === index ? ' is-active' : ''}${
                  position === index && running ? ' is-running' : ''
                }`}
              />
            ))}
          </div>
          <button
            className="hero-carousel__button"
            type="button"
            aria-label={t('home.carousel.next')}
            onClick={() => show(index + 1)}
          >
            <Icon name="right" />
          </button>
          <button
            className="hero-carousel__button hero-carousel__button--toggle"
            type="button"
            aria-label={playing ? t('home.carousel.pause') : t('home.carousel.play')}
            onClick={() => setPlaying((value) => !value)}
          >
            <Icon name={playing ? 'pause' : 'play'} />
          </button>
        </div>
      )}

      <p className="sr-only" aria-live={running ? 'off' : 'polite'} aria-atomic="true">
        {t('home.carousel.status', { current: index + 1, total: count, name: current.name })}
      </p>
    </section>
  );
}
