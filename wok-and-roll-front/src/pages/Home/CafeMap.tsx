import maplibregl, { type ExpressionSpecification, type Map as MapLibreMap } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useEffect, useRef } from 'react';
import { iconPath } from '../../components/common/Icon';
import type { Locale } from '../../i18n/locales';

/** A café with a place on the map. */
export interface MapCafe {
  id: number;
  /** In the language of the interface. */
  name: string;
  latitude: number;
  longitude: number;
}

export interface CafeMapLabels {
  map: string;
  zoomIn: string;
  zoomOut: string;
  attribution: string;
  ctrlScroll: string;
  cmdScroll: string;
  twoFingers: string;
}

interface CafeMapProps {
  cafes: MapCafe[];
  activeId?: number;
  locale: Locale;
  labels: CafeMapLabels;
  onSelect: (id: number) => void;
  /** No WebGL, or the map style could not be loaded: the list stays, the map is replaced. */
  onFail: () => void;
}

// OpenFreeMap: free vector tiles of OpenStreetMap, no key and no limits (openfreemap.org).
const STYLE_URL = 'https://tiles.openfreemap.org/styles/positron';
// The canvas colour of the site under the map, a little darker, so the map sits in the page.
const LAND_COLOUR = '#f1ede4';
const MAX_ZOOM = 15;

// The place names in the language of the site when the map has them, else the local name.
function localName(locale: Locale): ExpressionSpecification {
  return ['coalesce', ['get', `name:${locale}`], ['get', 'name:latin'], ['get', 'name']];
}

function markerElement(cafe: MapCafe): HTMLButtonElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'cafe-marker';
  button.setAttribute('aria-label', cafe.name);
  button.setAttribute('aria-pressed', 'false');
  button.innerHTML =
    '<span class="cafe-marker__pin"><svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
    `<path d="${iconPath('bowl')}" fill="none" stroke="currentColor" stroke-width="1.9" ` +
    'stroke-linecap="round" stroke-linejoin="round"/></svg></span>' +
    '<span class="cafe-marker__label" aria-hidden="true"></span>';
  // The name is text, never HTML: it comes from the API.
  const label = button.querySelector('.cafe-marker__label');
  if (label) label.textContent = cafe.name;
  return button;
}

function fitAll(map: MapLibreMap, cafes: MapCafe[], animate: boolean) {
  const bounds = new maplibregl.LngLatBounds();
  cafes.forEach(({ latitude, longitude }) => bounds.extend([longitude, latitude]));
  // More room at the top: a marker stands 50 px above its point.
  map.fitBounds(bounds, {
    padding: { top: 96, right: 64, bottom: 48, left: 64 },
    maxZoom: 14,
    animate,
  });
}

/**
 * The cafés of the country on an OpenStreetMap map (MapLibre GL). Each café is a button: it
 * selects the café in the list next to the map. The list is the accessible way to the same data,
 * so the map is a bonus, never the only way.
 */
export default function CafeMap({
  cafes,
  activeId,
  locale,
  labels,
  onSelect,
  onFail,
}: CafeMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markersRef = useRef(new Map<number, HTMLButtonElement>());
  const onSelectRef = useRef(onSelect);
  const onFailRef = useRef(onFail);
  const activeRef = useRef(activeId);

  useEffect(() => {
    onSelectRef.current = onSelect;
    onFailRef.current = onFail;
  });

  // A new map for every country and language: the place names and the control texts are set
  // when it is made.
  const cafesKey = cafes.map((cafe) => `${cafe.id}:${cafe.latitude},${cafe.longitude}`).join('|');
  useEffect(() => {
    const container = containerRef.current;
    if (!container || cafes.length === 0) return undefined;

    let map: MapLibreMap;
    try {
      map = new maplibregl.Map({
        container,
        style: STYLE_URL,
        center: [cafes[0].longitude, cafes[0].latitude],
        zoom: 12,
        maxZoom: MAX_ZOOM,
        // Page scroll is not stolen: Ctrl + wheel or two fingers move the map.
        cooperativeGestures: true,
        dragRotate: false,
        pitchWithRotate: false,
        touchPitch: false,
        attributionControl: { compact: true },
        locale: {
          'Map.Title': labels.map,
          'NavigationControl.ZoomIn': labels.zoomIn,
          'NavigationControl.ZoomOut': labels.zoomOut,
          'AttributionControl.ToggleAttribution': labels.attribution,
          'CooperativeGesturesHandler.WindowsHelpText': labels.ctrlScroll,
          'CooperativeGesturesHandler.MacHelpText': labels.cmdScroll,
          'CooperativeGesturesHandler.MobileHelpText': labels.twoFingers,
        },
      });
    } catch {
      // No WebGL in this browser.
      onFailRef.current();
      return undefined;
    }
    mapRef.current = map;
    map.touchZoomRotate.disableRotation();
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
    fitAll(map, cafes, false);

    let loaded = false;
    map.on('load', () => {
      loaded = true;
      if (map.getLayer('background')) {
        map.setPaintProperty('background', 'background-color', LAND_COLOUR);
      }
      for (const layer of map.getStyle().layers) {
        if (layer.type !== 'symbol') continue;
        const field = map.getLayoutProperty(layer.id, 'text-field') as unknown;
        if (field && JSON.stringify(field).includes('"name')) {
          map.setLayoutProperty(layer.id, 'text-field', localName(locale));
        }
      }
    });
    // Only the style matters: a missing tile later is not a reason to drop the map.
    map.on('error', (event: { tile?: unknown }) => {
      if (!loaded && !event.tile) onFailRef.current();
    });

    const markers = markersRef.current;
    const added = cafes.map((cafe) => {
      const element = markerElement(cafe);
      element.addEventListener('click', () => onSelectRef.current(cafe.id));
      markers.set(cafe.id, element);
      return new maplibregl.Marker({ element, anchor: 'bottom' })
        .setLngLat([cafe.longitude, cafe.latitude])
        .addTo(map);
    });
    markActive(markers, activeRef.current);

    return () => {
      added.forEach((marker) => marker.remove());
      markers.clear();
      map.remove();
      mapRef.current = null;
    };
    // cafesKey stands for the cafés; a new array with the same cafés keeps the map.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cafesKey, locale, labels]);

  // The chosen café: its marker is marked and the map moves to it (no animation when the system
  // asks for reduced motion: MapLibre then jumps).
  useEffect(() => {
    activeRef.current = activeId;
    markActive(markersRef.current, activeId);
    const map = mapRef.current;
    const cafe = cafes.find(({ id }) => id === activeId);
    if (!map || !cafe) return;
    map.easeTo({
      center: [cafe.longitude, cafe.latitude],
      zoom: Math.max(map.getZoom(), 14),
      duration: 600,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId]);

  return <div ref={containerRef} className="cafe-map__canvas" />;
}

function markActive(markers: Map<number, HTMLButtonElement>, activeId: number | undefined) {
  markers.forEach((element, id) => {
    const active = id === activeId;
    element.classList.toggle('is-active', active);
    element.setAttribute('aria-pressed', String(active));
    // The chosen marker and its name are above the other one.
    element.style.zIndex = active ? '2' : '1';
  });
}
