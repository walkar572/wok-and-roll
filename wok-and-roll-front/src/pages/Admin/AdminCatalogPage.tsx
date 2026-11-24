import { useCallback, useEffect, useState } from 'react';
import { Button, ErrorMessage, Loader, Toast } from '../../components/common';
import { useCountry } from '../../context/CountryContext';
import { categoryName, countryName, productName, useI18n } from '../../i18n';
import {
  loadAdminProducts,
  savePrice,
  updateProductVisibility,
  type AdminProduct,
} from '../../services/adminApi';
import { ApiRequestError, formatPrice, guestErrorMessage } from '../../services/api';

interface Draft {
  price: string;
  inStock: boolean;
}

const PRICE = /^\d{1,10}(\.\d{1,2})?$/;

function draftOf(product: AdminProduct): Draft {
  return {
    price: product.price === null ? '' : product.price.toFixed(2),
    inStock: product.inStock ?? true,
  };
}

/** Menu admin (#185, API contract §3.15): price, stock and visibility per country. */
export function AdminCatalogPage() {
  const { country: currentCountry, countries } = useCountry();
  const { t } = useI18n();
  const [country, setCountry] = useState(currentCountry);
  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [drafts, setDrafts] = useState<Record<number, Draft>>({});
  const [rowErrors, setRowErrors] = useState<Record<number, string>>({});
  const [savingId, setSavingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [toast, setToast] = useState<{ variant: 'success' | 'error' | 'warning'; text: string }>();
  const currency = countries.find(({ code }) => code === country)?.currency ?? '';

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const result = await loadAdminProducts(country);
      setProducts(result);
      setDrafts(Object.fromEntries(result.map((product) => [product.id, draftOf(product)])));
      setRowErrors({});
    } catch (requestError) {
      setError(guestErrorMessage(requestError, t('admin.loadError')));
    } finally {
      setLoading(false);
    }
  }, [country, t]);

  useEffect(() => {
    void load();
  }, [load]);

  function replace(product: AdminProduct) {
    setProducts((current) => current.map((item) => (item.id === product.id ? product : item)));
    setDrafts((current) => ({ ...current, [product.id]: draftOf(product) }));
  }

  function conflict(requestError: unknown): boolean {
    if (requestError instanceof ApiRequestError && requestError.code === 'VERSION_CONFLICT') {
      setToast({
        variant: 'warning',
        text: t('admin.conflict'),
      });
      void load();
      return true;
    }
    return false;
  }

  async function saveRow(product: AdminProduct) {
    const draft = drafts[product.id];
    const priceText = draft.price.trim();
    if (!PRICE.test(priceText)) {
      setRowErrors((current) => ({
        ...current,
        [product.id]: t('admin.priceFormat'),
      }));
      return;
    }
    setRowErrors((current) => ({ ...current, [product.id]: '' }));
    setSavingId(product.id);
    try {
      const saved = await savePrice(product, Number(priceText), draft.inStock);
      replace({
        ...product,
        price: saved.price,
        inStock: saved.inStock,
        priceVersion: saved.version,
      });
      setToast({
        variant: 'success',
        text: t('admin.saved', {
          name: productName(product.name),
          price: formatPrice(saved.price, saved.currency),
          stock: saved.inStock ? t('admin.inStockLower') : t('admin.outOfStockLower'),
        }),
      });
    } catch (requestError) {
      if (!conflict(requestError)) {
        const message =
          requestError instanceof ApiRequestError && requestError.fields.price
            ? requestError.fields.price
            : guestErrorMessage(requestError, t('admin.saveError'));
        setRowErrors((current) => ({ ...current, [product.id]: message }));
      }
    } finally {
      setSavingId(null);
    }
  }

  async function toggleVisible(product: AdminProduct) {
    setSavingId(product.id);
    try {
      const saved = await updateProductVisibility(product, !product.visible);
      replace({
        ...saved,
        price: product.price,
        inStock: product.inStock,
        priceVersion: product.priceVersion,
      });
      setToast({
        variant: 'success',
        text: t(saved.visible ? 'admin.shownNow' : 'admin.hiddenNow', {
          name: productName(product.name),
        }),
      });
    } catch (requestError) {
      if (!conflict(requestError)) {
        setToast({
          variant: 'error',
          text: guestErrorMessage(requestError, t('admin.visibilityError')),
        });
      }
    } finally {
      setSavingId(null);
    }
  }

  return (
    <section className="admin-page" aria-labelledby="admin-title">
      <p className="eyebrow">{t('admin.eyebrow')}</p>
      <h1 id="admin-title">{t('admin.title')}</h1>
      <p className="page-description">{t('admin.description')}</p>

      <div className="report-filters admin-filters">
        <div className="input">
          <label className="input__label" htmlFor="admin-country">
            {t('admin.country')}
          </label>
          <select
            id="admin-country"
            className="input__control"
            value={country}
            onChange={(event) => setCountry(event.target.value)}
          >
            {countries.map((item) => (
              <option key={item.code} value={item.code}>
                {countryName(item.code, item.name)} ({item.currency})
              </option>
            ))}
          </select>
        </div>
      </div>

      {toast && (
        <Toast
          className="report-toast"
          variant={toast.variant}
          onDismiss={() => setToast(undefined)}
        >
          {toast.text}
        </Toast>
      )}
      {loading && <Loader label={t('admin.loading')} />}
      {error && (
        <div className="page-error">
          <ErrorMessage title={t('admin.notLoaded')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={() => void load()}>
            {t('common.retry')}
          </Button>
        </div>
      )}

      {!loading && !error && (
        <div
          className="table-scroll"
          role="region"
          aria-labelledby="admin-table-caption"
          tabIndex={0}
        >
          <table className="data-table admin-table">
            <caption id="admin-table-caption">
              {t('admin.caption', { country: countryName(country) })}
            </caption>
            <thead>
              <tr>
                <th scope="col">{t('admin.dish')}</th>
                <th scope="col">{t('admin.onMenu')}</th>
                <th scope="col">{t('admin.price', { currency })}</th>
                <th scope="col">{t('admin.inStock')}</th>
                <th scope="col">
                  <span className="sr-only">{t('common.save')}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {products.map((product) => {
                const draft = drafts[product.id] ?? draftOf(product);
                const rowError = rowErrors[product.id];
                const priceId = `price-${product.id}`;
                const busy = savingId === product.id;
                return (
                  <tr key={product.id} className={product.visible ? undefined : 'is-hidden'}>
                    <th scope="row">
                      <span className="admin-table__name">{productName(product.name)}</span>
                      <span className="admin-table__meta">
                        {categoryName(product.categoryName)}
                        {product.price === null ? ` · ${t('admin.notSold')}` : ''}
                      </span>
                    </th>
                    <td>
                      <label className="switch">
                        <input
                          type="checkbox"
                          role="switch"
                          checked={product.visible}
                          disabled={busy}
                          onChange={() => void toggleVisible(product)}
                        />
                        <span>{product.visible ? t('admin.shown') : t('admin.hidden')}</span>
                        <span className="sr-only">
                          {' '}
                          — {t('admin.visibleFor', { name: productName(product.name) })}
                        </span>
                      </label>
                    </td>
                    <td>
                      <label className="sr-only" htmlFor={priceId}>
                        {t('admin.priceFor', { name: productName(product.name), currency })}
                      </label>
                      <input
                        id={priceId}
                        className={`input__control admin-table__price${rowError ? ' input__control--error' : ''}`}
                        inputMode="decimal"
                        value={draft.price}
                        placeholder="0.00"
                        aria-invalid={Boolean(rowError) || undefined}
                        aria-describedby={rowError ? `${priceId}-error` : undefined}
                        onChange={(event) =>
                          setDrafts((current) => ({
                            ...current,
                            [product.id]: { ...draft, price: event.target.value },
                          }))
                        }
                      />
                      {rowError && (
                        <span
                          id={`${priceId}-error`}
                          className="input__message input__message--error"
                        >
                          {rowError}
                        </span>
                      )}
                    </td>
                    <td>
                      <label className="checkbox">
                        <input
                          type="checkbox"
                          checked={draft.inStock}
                          onChange={(event) =>
                            setDrafts((current) => ({
                              ...current,
                              [product.id]: { ...draft, inStock: event.target.checked },
                            }))
                          }
                        />
                        <span>{draft.inStock ? t('common.yes') : t('common.no')}</span>
                        <span className="sr-only">
                          {' '}
                          — {t('admin.inStockFor', { name: productName(product.name) })}
                        </span>
                      </label>
                    </td>
                    <td>
                      <Button
                        size="small"
                        variant="outline"
                        loading={busy}
                        disabled={busy}
                        onClick={() => void saveRow(product)}
                      >
                        {t('common.save')}
                        <span className="sr-only"> {productName(product.name)}</span>
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
