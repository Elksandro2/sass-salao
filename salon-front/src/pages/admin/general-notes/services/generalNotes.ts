import api from '../../../../services/api';

export interface GeneralNoteData {
  id: number;
  content: string;
  authorName: string;
  done: boolean;
  createdAt: string;
  updatedAt?: string | null;
}

export const generalNotesApi = {
  findAll: async () => {
    const { data } = await api.get<GeneralNoteData[]>('/general-notes');
    return data;
  },

  create: async (content: string) => {
    const { data } = await api.post<GeneralNoteData>('/general-notes', { content });
    return data;
  },

  updateContent: async (id: number, content: string) => {
    const { data } = await api.patch<GeneralNoteData>(`/general-notes/${id}`, { content });
    return data;
  },

  toggleDone: async (id: number) => {
    const { data } = await api.patch<GeneralNoteData>(`/general-notes/${id}/done`);
    return data;
  },

  delete: async (id: number) => {
    await api.delete(`/general-notes/${id}`);
  },
};
