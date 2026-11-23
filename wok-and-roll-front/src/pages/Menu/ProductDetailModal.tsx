import { useEffect, useState } from 'react';
import { Button, ErrorMessage, Loader, Modal } from '../../components/common';
import {
  ApiRequestError,
  formatPrice,
  guestErrorMessage,
  loadProductDetails,
  type CountryCode,
  type ProductDetail,
} from '../../services/api';

interface ProductDetailModalProps {
  productId: number | null;
  country: CountryCode;
  onAdd: (product: ProductDetail, modifierIds: number[], quantity: number) => void;
  onClose: () => void;
}

function toMinorUnits(amount: number): number {
  return Math.round(amount * 100);
}

export function ProductDetailModal({
  productId,
  country,
  onAdd,
  onClose,
}: ProductDetailModalProps) {
  const [product, setProduct] = useState<ProductDetail>();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [retryCount, setRetryCount] = useState(0);
  const [selectedSizeId, setSelectedSizeId] = useState<number>();
  const [selectedExtraIds, setSelectedExtraIds] = useState<number[]>([]);
  const [quantity, setQuantity] = useState(1);
  const [addError, setAddError] = useState<string>();

  useEffect(() => {
    if (productId === null) {
      setProduct(undefined);
      setError(undefined);
      return;
    }

    let active = true;
    setIsLoading(true);
    setError(undefined);
    setProduct(undefined);

    loadProductDetails(productId, country)
      .then((result) => {
        if (!active) return;
        setProduct(result);
        setSelectedSizeId(result.modifiers.find(({ type }) => type === 'SIZE')?.id);
        setSelectedExtraIds([]);
        setQuantity(1);
        setAddError(undefined);
      })
      .catch((requestError: unknown) => {
        if (!active) return;
        setError(
          requestError instanceof ApiRequestError && requestError.status === 404
            ? `This dish is not available in ${country}.`
            : guestErrorMessage(requestError, 'We could not load this dish. Please try again.'),
        );
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [productId, country, retryCount]);

  const sizes = product?.modifiers.filter(({ type }) => type === 'SIZE') ?? [];
  const extras = product?.modifiers.filter(({ type }) => type === 'EXTRA') ?? [];
  const modifierIds = [
    ...(selectedSizeId === undefined ? [] : [selectedSizeId]),
    ...selectedExtraIds,
  ];
  const modifierTotal =
    product?.modifiers
      .filter(({ id }) => modifierIds.includes(id))
      .reduce((total, modifier) => total + toMinorUnits(modifier.priceDelta), 0) ?? 0;
  const addTotal = product ? (toMinorUnits(product.price) + modifierTotal) * quantity : 0;

  function addProduct() {
    if (!product || !product.inStock || (sizes.length > 0 && selectedSizeId === undefined)) return;
    try {
      onAdd(product, modifierIds, quantity);
      onClose();
    } catch (addRequestError: unknown) {
      setAddError(
        addRequestError instanceof Error ? addRequestError.message : 'Could not add this dish.',
      );
    }
  }

  return (
    <Modal
      open={productId !== null}
      title={product?.name ?? 'Dish details'}
      onClose={onClose}
      footer={
        product ? (
          <div className="product-modal__footer">
            <label className="product-modal__quantity">
              <span>Quantity</span>
              <input
                aria-label="Quantity"
                type="number"
                min="1"
                max="99"
                step="1"
                value={quantity}
                onChange={(event) => {
                  const nextQuantity = Number(event.target.value);
                  if (Number.isInteger(nextQuantity) && nextQuantity >= 1 && nextQuantity <= 99) {
                    setQuantity(nextQuantity);
                  }
                }}
              />
            </label>
            <Button
              onClick={addProduct}
              disabled={!product.inStock || (sizes.length > 0 && selectedSizeId === undefined)}
            >
              Add · {formatPrice(addTotal / 100, product.currency)}
            </Button>
          </div>
        ) : undefined
      }
    >
      {isLoading && <Loader label="Loading dish details" />}
      {error && (
        <div className="product-modal__error">
          <ErrorMessage title="Could not load this dish">{error}</ErrorMessage>
          <button
            className="button button--outline"
            type="button"
            onClick={() => setRetryCount((count) => count + 1)}
          >
            Try again
          </button>
        </div>
      )}
      {product && (
        <div className="product-modal">
          {product.imageUrl && (
            <img className="product-modal__image" src={product.imageUrl} alt={product.name} />
          )}
          {product.description && <p>{product.description}</p>}
          <p className="product-modal__price">{formatPrice(product.price, product.currency)}</p>
          {!product.inStock && (
            <ErrorMessage variant="warning">
              This dish is currently out of stock in {country}.
            </ErrorMessage>
          )}
          {addError && <ErrorMessage title="Could not add this dish">{addError}</ErrorMessage>}
          {sizes.length > 0 && (
            <fieldset className="product-modal__options">
              <legend>Choose a size</legend>
              {sizes.map((modifier) => (
                <label key={modifier.id}>
                  <input
                    type="radio"
                    name={`product-size-${product.id}`}
                    value={modifier.id}
                    checked={selectedSizeId === modifier.id}
                    onChange={() => setSelectedSizeId(modifier.id)}
                  />
                  <span>{modifier.name}</span>
                  <span>+{formatPrice(modifier.priceDelta, product.currency)}</span>
                </label>
              ))}
            </fieldset>
          )}
          {extras.length > 0 && (
            <fieldset className="product-modal__options">
              <legend>Extras</legend>
              {extras.map((modifier) => {
                const checked = selectedExtraIds.includes(modifier.id);
                return (
                  <label key={modifier.id}>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => {
                        setSelectedExtraIds((selected) =>
                          checked
                            ? selected.filter((id) => id !== modifier.id)
                            : [...selected, modifier.id],
                        );
                      }}
                    />
                    <span>{modifier.name}</span>
                    <span>+{formatPrice(modifier.priceDelta, product.currency)}</span>
                  </label>
                );
              })}
            </fieldset>
          )}
        </div>
      )}
    </Modal>
  );
}
