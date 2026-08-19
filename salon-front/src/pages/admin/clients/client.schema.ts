import { z } from 'zod';

export const clientFormSchema = z.object({
  _isEdit: z.boolean().optional(),
  name: z.string().min(1, 'Nome é obrigatório').min(3, 'Mínimo 3 caracteres').max(150, 'Máximo de 150 caracteres'),
  /** Opcional — funcionalidade de e-mail desligada por feature flag nesta versão. */
  email: z
    .string()
    .max(150, 'Máximo de 150 caracteres')
    .optional()
    .refine(
      (v) => !v || /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(v),
      'Formato de e-mail inválido'
    ),
  phone: z
    .string()
    .max(20, 'Máximo de 20 caracteres')
    .optional()
    .refine(
      (v) => !v || /^\(?\d{2}\)?\s?\d{4,5}-?\d{4}$/.test(v),
      'Formato inválido. Use (XX) XXXXX-XXXX'
    ),
  cpf: z
    .string()
    .optional()
    .refine(
      (v) => !v || /^\d{11}$/.test(v),
      'O CPF deve conter exatamente 11 dígitos numéricos'
    ),
  active: z.boolean().optional(),
  roleId: z.number().optional(),
});

export type ClientFormValues = z.infer<typeof clientFormSchema>;
