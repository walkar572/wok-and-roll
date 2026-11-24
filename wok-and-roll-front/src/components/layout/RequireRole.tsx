import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n';
import type { Role } from '../../services/authToken';
import { PagePlaceholder } from './PagePlaceholder';

/**
 * Route guard (#186). A guest goes to Log in and comes back after it; a logged-in user without
 * the role sees "No access" (API contract §4: 403). The server checks the role again on every
 * request, so this only keeps people away from screens they cannot use.
 */
export function RequireRole({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { auth } = useAuth();
  const { t } = useI18n();
  const location = useLocation();

  if (!auth) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  if (roles && !roles.includes(auth.role)) {
    return (
      <PagePlaceholder
        eyebrow={t('noAccess.eyebrow')}
        title={t('noAccess.title')}
        description={t('noAccess.description')}
        symbol="!"
        panelTitle={t('noAccess.panelTitle')}
        panelDescription={t('noAccess.panelText')}
        actionLabel={t('noAccess.action')}
        actionTo="/menu"
      />
    );
  }

  return <>{children}</>;
}
