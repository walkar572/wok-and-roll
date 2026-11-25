import { useEffect, useState } from 'react';

export interface DishImageProps {
  src: string | null;
  alt: string;
  className: string;
  placeholderClassName: string;
  width?: number;
  height?: number;
  loading?: 'lazy' | 'eager';
}

/**
 * The photo of a dish. A dish without a photo, or a photo that cannot be loaded (for example a
 * file that is not uploaded yet), shows the same calm placeholder instead of a broken image.
 */
export function DishImage({
  src,
  alt,
  className,
  placeholderClassName,
  width,
  height,
  loading = 'lazy',
}: DishImageProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => setFailed(false), [src]);

  if (!src || failed) {
    return (
      <span className={`${className} ${placeholderClassName}`} aria-hidden="true">
        ✳
      </span>
    );
  }
  return (
    <img
      className={className}
      src={src}
      alt={alt}
      width={width}
      height={height}
      loading={loading}
      onError={() => setFailed(true)}
    />
  );
}
