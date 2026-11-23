import { useEffect, useId, useRef, useState } from 'react';

export interface BarDatum {
  key: string;
  /** Under the bar, short: "UZB", "Jan". */
  label: string;
  /** Full name for the tooltip and the accessible name: "Uzbekistan", "January 2026". */
  name: string;
  value: number;
  /** The formatted value: "120 orders", "UZS 52,340.00". */
  valueText: string;
}

export interface BarChartProps {
  data: BarDatum[];
  /** What one bar measures, for the accessible description: "Orders per country". */
  description: string;
  height?: number;
  /** Axis numbers; the exact values are in the tooltip and the table. */
  formatAxis?: (value: number) => string;
}

const PADDING = { top: 24, right: 8, bottom: 28, left: 52 };
const MAX_BAR = 40;
const RADIUS = 4;
const GRID_LINES = 4;

/** A round axis maximum just above the highest bar, so the bars use most of the height. */
export function niceMax(max: number): number {
  if (max <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(max));
  for (const step of [1, 1.2, 1.6, 2, 2.4, 3, 4, 5, 6, 8, 10]) {
    if (step * magnitude >= max) return step * magnitude;
  }
  return 10 * magnitude;
}

export function compactNumber(value: number): string {
  return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(
    value,
  );
}

/** A column with 4px rounded top corners, anchored flat on the baseline. */
function columnPath(x: number, y: number, width: number, height: number): string {
  if (height <= 0) return '';
  const r = Math.min(RADIUS, width / 2, height);
  const bottom = y + height;
  return [
    `M${x},${bottom}`,
    `V${y + r}`,
    `Q${x},${y} ${x + r},${y}`,
    `H${x + width - r}`,
    `Q${x + width},${y} ${x + width},${y + r}`,
    `V${bottom}`,
    'Z',
  ].join(' ');
}

/**
 * A single-series column chart in SVG (#180, #181). One hue, no legend box (the heading names the
 * series). Every bar has a tooltip on hover and on keyboard focus; the same numbers are in the
 * table next to the chart, so the tooltip never gates a value. Only the highest bar gets a
 * direct label.
 */
export function BarChart({
  data,
  description,
  height = 260,
  formatAxis = compactNumber,
}: BarChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);
  const [active, setActive] = useState<number | null>(null);
  const descriptionId = useId();

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return undefined;
    const update = () => setWidth(Math.max(280, Math.round(element.clientWidth)));
    update();
    if (typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(update);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const max = Math.max(0, ...data.map((d) => d.value));
  const scaleMax = niceMax(max);
  const plotWidth = width - PADDING.left - PADDING.right;
  const plotHeight = height - PADDING.top - PADDING.bottom;
  const slot = plotWidth / Math.max(data.length, 1);
  // A 2px surface gap is the minimum between neighbours; bars stay thin.
  const barWidth = Math.max(6, Math.min(MAX_BAR, slot * 0.6, slot - 2));
  const highest = max > 0 ? data.findIndex((d) => d.value === max) : -1;
  const showEveryLabel = slot >= 28;

  const x = (i: number) => PADDING.left + slot * i + (slot - barWidth) / 2;
  const y = (value: number) => PADDING.top + plotHeight - (plotHeight * value) / scaleMax;

  const activeDatum = active === null ? null : data[active];

  return (
    <div className="bar-chart" ref={containerRef}>
      <p id={descriptionId} className="sr-only">
        {description}. {data.length} bars. Use Tab to move between bars; the table below has every
        value.
      </p>
      <svg
        width={width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        role="group"
        aria-describedby={descriptionId}
        onPointerLeave={() => setActive(null)}
      >
        {Array.from({ length: GRID_LINES + 1 }, (_, i) => {
          const value = (scaleMax * i) / GRID_LINES;
          const lineY = y(value);
          return (
            <g key={i} aria-hidden="true">
              <line
                className={i === 0 ? 'bar-chart__baseline' : 'bar-chart__grid'}
                x1={PADDING.left}
                x2={width - PADDING.right}
                y1={lineY}
                y2={lineY}
              />
              <text className="bar-chart__axis" x={PADDING.left - 8} y={lineY + 4} textAnchor="end">
                {formatAxis(value)}
              </text>
            </g>
          );
        })}

        {data.map((datum, i) => {
          const barHeight = plotHeight - (y(datum.value) - PADDING.top);
          const isActive = active === i;
          return (
            <g
              key={datum.key}
              className={`bar-chart__bar${isActive ? ' is-active' : ''}`}
              tabIndex={0}
              role="img"
              aria-label={`${datum.name}: ${datum.valueText}`}
              onPointerEnter={() => setActive(i)}
              onFocus={() => setActive(i)}
              onBlur={() => setActive((current) => (current === i ? null : current))}
            >
              {/* The hit area is the whole slot, much bigger than the painted bar. */}
              <rect
                className="bar-chart__hit"
                x={PADDING.left + slot * i}
                y={PADDING.top}
                width={slot}
                height={plotHeight}
              />
              <path
                className="bar-chart__fill"
                d={columnPath(x(i), y(datum.value), barWidth, barHeight)}
              />
              {(showEveryLabel || i % 2 === 0) && (
                <text
                  className="bar-chart__label"
                  x={x(i) + barWidth / 2}
                  y={height - PADDING.bottom + 18}
                  textAnchor="middle"
                  aria-hidden="true"
                >
                  {datum.label}
                </text>
              )}
              {i === highest && !isActive && (
                <text
                  className="bar-chart__value"
                  x={x(i) + barWidth / 2}
                  y={y(datum.value) - 8}
                  textAnchor="middle"
                  aria-hidden="true"
                >
                  {formatAxis(datum.value)}
                </text>
              )}
            </g>
          );
        })}
      </svg>
      {activeDatum && active !== null && (
        <div
          className="bar-chart__tooltip"
          aria-hidden="true"
          style={{
            left: Math.min(Math.max(x(active) + barWidth / 2, 70), width - 70),
            top: Math.max(y(activeDatum.value) - 12, 0),
          }}
        >
          <strong>{activeDatum.valueText}</strong>
          <span>{activeDatum.name}</span>
        </div>
      )}
    </div>
  );
}
