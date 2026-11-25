import { useEffect, useId, useRef, type ReactNode } from 'react';
import { useI18n } from '../../i18n';
import { Button } from './Button';

export interface ModalProps {
  open: boolean;
  title: string;
  children: ReactNode;
  onClose: () => void;
  closeLabel?: string;
  footer?: ReactNode;
}

export function Modal({ open, title, children, onClose, closeLabel, footer }: ModalProps) {
  const { t } = useI18n();
  const close = closeLabel ?? t('common.close');
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      className="modal"
      ref={dialogRef}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="modal__content">
        <header className="modal__header">
          <h2 className="modal__title" id={titleId}>
            {title}
          </h2>
          <button className="modal__dismiss" type="button" aria-label={close} onClick={onClose}>
            ×
          </button>
        </header>
        <div className="modal__body">{children}</div>
        {footer === undefined ? (
          <footer className="modal__footer">
            <Button variant="secondary" onClick={onClose}>
              {close}
            </Button>
          </footer>
        ) : footer ? (
          <footer className="modal__footer">{footer}</footer>
        ) : null}
      </div>
    </dialog>
  );
}
