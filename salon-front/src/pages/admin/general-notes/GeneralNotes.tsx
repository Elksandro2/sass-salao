import { useEffect, useState } from 'react';
import { Plus, Trash2, Check, RotateCcw, Pencil } from 'lucide-react';
import { generalNotesApi } from './services/generalNotes';
import type { GeneralNoteData } from './services/generalNotes';
import { PermissionGate } from '../../../components/permissions/PermissionGate';
import { ConfirmDialog } from '../../../components/modal/ConfirmDialog';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';

const inputCls = 'input-premium';

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export const GeneralNotes = () => {
  const [notes, setNotes] = useState<GeneralNoteData[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [newContent, setNewContent] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingContent, setEditingContent] = useState('');
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);
  const { error: showError } = useAlert();

  const load = async () => {
    setIsLoading(true);
    try {
      const data = await generalNotesApi.findAll();
      setNotes(data);
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao carregar anotações'));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleCreate = async () => {
    if (!newContent.trim()) return;
    setIsSaving(true);
    try {
      const created = await generalNotesApi.create(newContent.trim());
      setNotes((prev) => [created, ...prev]);
      setNewContent('');
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao criar anotação'));
    } finally {
      setIsSaving(false);
    }
  };

  const startEdit = (note: GeneralNoteData) => {
    setEditingId(note.id);
    setEditingContent(note.content);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditingContent('');
  };

  const saveEdit = async (id: number) => {
    if (!editingContent.trim()) return;
    try {
      const updated = await generalNotesApi.updateContent(id, editingContent.trim());
      setNotes((prev) => prev.map((n) => (n.id === id ? updated : n)));
      cancelEdit();
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao editar anotação'));
    }
  };

  const handleToggleDone = async (id: number) => {
    try {
      const updated = await generalNotesApi.toggleDone(id);
      setNotes((prev) => prev.map((n) => (n.id === id ? updated : n)));
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao atualizar anotação'));
    }
  };

  const handleDelete = async () => {
    if (deleteTargetId == null) return;
    try {
      await generalNotesApi.delete(deleteTargetId);
      setNotes((prev) => prev.filter((n) => n.id !== deleteTargetId));
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao apagar anotação'));
    } finally {
      setDeleteTargetId(null);
    }
  };

  const pending = notes.filter((n) => !n.done);
  const done = notes.filter((n) => n.done);

  const renderNote = (note: GeneralNoteData) => (
    <div
      key={note.id}
      className={`p-4 rounded-2xl border ${
        note.done
          ? 'border-[#eae1e1] dark:border-[#1e293b] bg-[#fcf9f9]/50 dark:bg-[#161c2a]/40'
          : 'border-[#eae1e1] dark:border-[#1e293b] bg-white dark:bg-[#161c2a]'
      }`}
    >
      {editingId === note.id ? (
        <div className="space-y-2">
          <textarea
            rows={3}
            maxLength={4000}
            className={`${inputCls} resize-none`}
            value={editingContent}
            onChange={(e) => setEditingContent(e.target.value)}
            autoFocus
          />
          <div className="flex justify-end gap-2">
            <button
              onClick={cancelEdit}
              className="px-3 py-1.5 text-xs font-semibold text-[#3b3036] dark:text-gray-300 border border-[#eae1e1] dark:border-[#1e293b] rounded-lg hover:bg-white dark:hover:bg-[#0b0f17] transition-all cursor-pointer"
            >
              Cancelar
            </button>
            <button
              onClick={() => saveEdit(note.id)}
              className="px-3 py-1.5 text-xs font-semibold text-white bg-[#be8a83] hover:bg-[#a6726b] rounded-lg transition-all cursor-pointer"
            >
              Salvar
            </button>
          </div>
        </div>
      ) : (
        <>
          <p
            className={`text-sm whitespace-pre-wrap ${
              note.done
                ? 'text-[#3b3036]/50 dark:text-gray-500 line-through'
                : 'text-[#3b3036] dark:text-gray-200'
            }`}
          >
            {note.content}
          </p>
          <div className="flex items-center justify-between mt-3 pt-2 border-t border-[#eae1e1]/50 dark:border-gray-800">
            <span className="text-3xs text-gray-400">
              {note.authorName} · {formatDate(note.createdAt)}
            </span>
            <div className="flex gap-1.5">
              <PermissionGate method="PATCH" endpoint={`/v1/general-notes/${note.id}/done`}>
                <button
                  onClick={() => handleToggleDone(note.id)}
                  title={note.done ? 'Reabrir' : 'Concluir'}
                  className="p-1.5 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-950/20 border border-emerald-200 dark:border-emerald-800 rounded-lg transition-all cursor-pointer"
                >
                  {note.done ? <RotateCcw size={13} /> : <Check size={13} />}
                </button>
              </PermissionGate>
              <PermissionGate method="PATCH" endpoint={`/v1/general-notes/${note.id}`}>
                <button
                  onClick={() => startEdit(note)}
                  title="Editar"
                  className="p-1.5 text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-950/20 border border-indigo-200 dark:border-indigo-800 rounded-lg transition-all cursor-pointer"
                >
                  <Pencil size={13} />
                </button>
              </PermissionGate>
              <PermissionGate method="DELETE" endpoint={`/v1/general-notes/${note.id}`}>
                <button
                  onClick={() => setDeleteTargetId(note.id)}
                  title="Apagar"
                  className="p-1.5 text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/20 border border-rose-200 dark:border-rose-800 rounded-lg transition-all cursor-pointer"
                >
                  <Trash2 size={13} />
                </button>
              </PermissionGate>
            </div>
          </div>
        </>
      )}
    </div>
  );

  return (
    <div className="space-y-6">
      <div>
        <h2 className="font-heading text-2xl font-bold text-[#3b3036] dark:text-white">
          Anotações Gerais
        </h2>
        <p className="text-xs text-[#3b3036]/60 dark:text-gray-400 mt-1">
          Lembretes livres da equipe — não ligados a um cliente ou agendamento específico.
        </p>
      </div>

      <PermissionGate method="POST" endpoint="/v1/general-notes">
        <div className="flex gap-2">
          <input
            type="text"
            maxLength={4000}
            className={inputCls}
            placeholder="Ex.: comprar mais toalha, combinar horário de almoço..."
            value={newContent}
            onChange={(e) => setNewContent(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleCreate();
            }}
          />
          <button
            onClick={handleCreate}
            disabled={isSaving || !newContent.trim()}
            className="btn-premium shrink-0 disabled:opacity-50"
          >
            <Plus size={18} /> Adicionar
          </button>
        </div>
      </PermissionGate>

      {isLoading ? (
        <div className="flex justify-center py-10">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-[#be8a83]"></div>
        </div>
      ) : notes.length === 0 ? (
        <div className="text-center py-10 px-4 text-sm text-[#7a7074]/80 bg-white/50 dark:bg-[#161c2a]/40 border border-[#eae1e1] dark:border-[#1e293b] rounded-2xl">
          Nenhuma anotação ainda.
        </div>
      ) : (
        <div className="space-y-6">
          {pending.length > 0 && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-[#7a7074] dark:text-gray-400 uppercase tracking-wider">
                Pendentes ({pending.length})
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">{pending.map(renderNote)}</div>
            </div>
          )}
          {done.length > 0 && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-[#7a7074] dark:text-gray-400 uppercase tracking-wider">
                Concluídas ({done.length})
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">{done.map(renderNote)}</div>
            </div>
          )}
        </div>
      )}

      <ConfirmDialog
        show={deleteTargetId != null}
        onHide={() => setDeleteTargetId(null)}
        onConfirm={handleDelete}
        title="Apagar Anotação"
        message="Tem certeza que deseja apagar esta anotação? Essa ação não pode ser desfeita."
        confirmLabel="Apagar"
        variant="danger"
      />
    </div>
  );
};
