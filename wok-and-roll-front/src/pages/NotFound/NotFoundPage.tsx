import { PagePlaceholder } from '../../components/layout/PagePlaceholder';

export function NotFoundPage() {
  return (
    <PagePlaceholder
      eyebrow="404 · Wrong turn"
      title="We lost that page."
      description="Looks like this page took a detour."
      symbol="?"
      panelTitle="Nothing to see here"
      panelDescription="The page may have moved, or it may never have existed."
      actionLabel="Go home"
      actionTo="/"
    />
  );
}
