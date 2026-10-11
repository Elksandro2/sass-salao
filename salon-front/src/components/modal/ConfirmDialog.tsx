import { X } from 'lucide-react';
import { createPortal } from 'react-dom';

interface ConfirmDialogProps {
  show: boolean;
  onHide: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'primary' | 'danger' | 'warning';
  isProcessing?: boolean;
}

export const ConfirmDialog = ({
  show,
  onHide,
  onConfirm,
  title,
  message,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  variant = 'danger',
  isProcessing = false,
}: ConfirmDialogProps) => {
  if (!show) return null;

  const getConfirmButtonClasses = () => {
    switch (variant) {
      case 'danger':
        return 'bg-red-500 hover:bg-red-600 text-white shadow-md shadow-red-500/15';
      case 'warning':
        return 'bg-amber-500 hover:bg-amber-600 text-white shadow-md shadow-amber-500/15';
      case 'primary':
      default:
        return 'bg-[#be8a83] hover:bg-[#a1706a] text-white shadow-md shadow-[#be8a83]/15';
    }
  };

  // Portal pro <body>: o modal não pode depender de onde é renderizado — um ancestral com
  // transform/filter prende o position:fixed e o modal sai do centro da tela.
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-x-hidden overflow-y-auto outline-none focus:outline-none">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-[#261f23]/40 backdrop-blur-md transition-opacity duration-300"
        onClick={onHide}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-md mx-auto my-6 z-50 px-4">
        <div className="relative flex flex-col w-full bg-white border border-[#eae1e1] rounded-2xl shadow-xl outline-none focus:outline-none animate-scale-up">
          {/* Header */}
          <div className="flex items-center justify-between p-5 border-b border-solid border-[#eae1e1] rounded-t-2xl">
            <h3 className="text-lg font-semibold font-heading text-[#3b3036]">{title}</h3>
            <button
              type="button"
              onClick={onHide}
              className="p-1 ml-auto bg-transparent border-0 text-[#7a7074] hover:text-[#be8a83] float-right text-3xl leading-none font-semibold outline-none focus:outline-none transition-colors"
            >
              <X size={20} />
            </button>
          </div>

          {/* Body */}
          <div className="relative p-6 flex-auto">
            <p className="text-sm text-[#7a7074]">{message}</p>
          </div>

          {/* Footer */}
          <div className="flex items-center justify-end gap-3 p-5 border-t border-solid border-[#eae1e1] rounded-b-2xl bg-[#fcf9f9]">
            <button
              type="button"
              onClick={onHide}
              disabled={isProcessing}
              className="px-4 py-2 border border-[#eae1e1] rounded-xl text-sm font-semibold text-[#3b3036] hover:bg-white hover:border-[#be8a83]/50 transition-all duration-200 disabled:opacity-50"
            >
              {cancelLabel}
            </button>
            <button
              type="button"
              onClick={onConfirm}
              disabled={isProcessing}
              className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all duration-200 disabled:opacity-50 ${getConfirmButtonClasses()}`}
            >
              {isProcessing ? 'Processando...' : confirmLabel}
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
};
