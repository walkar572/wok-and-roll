import { PagePlaceholder } from '../../components/layout/PagePlaceholder';
import { useI18n } from '../../i18n';

export function NotFoundPage() {
  const { t } = useI18n();
  return (
    <PagePlaceholder
      eyebrow={t('notFound.eyebrow')}
      title={t('notFound.title')}
      description={t('notFound.description')}
      symbol="?"
      panelTitle={t('notFound.panelTitle')}
      panelDescription={t('notFound.panelText')}
      actionLabel={t('notFound.action')}
      actionTo="/"
    />
  );
}
