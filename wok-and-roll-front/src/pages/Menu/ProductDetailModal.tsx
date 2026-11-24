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
import { countryName, modifierName, productDescription, productName, useI18n } from '../../i18n';
import { DishImage } from '../../components/common/DishImage';

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
  const { t } = useI18n();

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
            ? t('product.notAvailable', { country: countryName(country) })
            : guestErrorMessage(requestError, t('product.loadError')),
        );
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [productId, country, retryCount, t]);

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
        addRequestError instanceof Error ? addRequestError.message : t('product.couldNotAdd'),
      );
    }
  }

  return (
    <Modal
      open={productId !== null}
      title={product ? productName(product.name) : t('product.dishDetails')}
      onClose={onClose}
      footer={
        product ? (
          <div className="product-modal__footer">
            <label className="product-modal__quantity">
              <span>{t('product.quantity')}</span>
              <input
                aria-label={t('product.quantity')}
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
              {t('product.add', { price: formatPrice(addTotal / 100, product.currency) })}
            </Button>
          </div>
        ) : undefined
      }
    >
      {isLoading && <Loader label={t('product.loading')} />}
      {error && (
        <div className="product-modal__error">
          <ErrorMessage title={t('product.couldNotLoad')}>{error}</ErrorMessage>
          <button
            className="button button--outline"
            type="button"
            onClick={() => setRetryCount((count) => count + 1)}
          >
            {t('common.tryAgain')}
          </button>
        </div>
      )}
      {product && (
        <div className="product-modal">
          {product.imageUrl && (
            <DishImage
              src={product.imageUrl}
              alt={productName(product.name)}
              className="product-modal__image"
              placeholderClassName="product-modal__image--placeholder"
              loading="eager"
            />
          )}
          {productDescription(product.name, product.description) && (
            <p>{productDescription(product.name, product.description)}</p>
          )}
          <p className="product-modal__price">{formatPrice(product.price, product.currency)}</p>
          {!product.inStock && (
            <ErrorMessage variant="warning">
              {t('product.outOfStockIn', { country: countryName(country) })}
            </ErrorMessage>
          )}
          {addError && <ErrorMessage title={t('product.couldNotAdd')}>{addError}</ErrorMessage>}
          {sizes.length > 0 && (
            <fieldset className="product-modal__options">
              <legend>{t('product.chooseSize')}</legend>
              {sizes.map((modifier) => (
                <label key={modifier.id}>
                  <input
                    type="radio"
                    name={`product-size-${product.id}`}
                    value={modifier.id}
                    checked={selectedSizeId === modifier.id}
                    onChange={() => setSelectedSizeId(modifier.id)}
                  />
                  <span>{modifierName(modifier.name)}</span>
                  <span>+{formatPrice(modifier.priceDelta, product.currency)}</span>
                </label>
              ))}
            </fieldset>
          )}
          {extras.length > 0 && (
            <fieldset className="product-modal__options">
              <legend>{t('product.extras')}</legend>
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
                    <span>{modifierName(modifier.name)}</span>
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
