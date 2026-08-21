import { z } from 'zod';

export const salonServiceFormSchema = z.object({
  name: z.string().min(1, 'Nome é obrigatório').min(3, 'Mín. 3 caracteres').max(150, 'Máximo de 150 caracteres'),
  description: z.string().max(2000, 'Máximo de 2000 caracteres'),
  price: z.number().optional(),
  active: z.boolean(),
  commissionPercent: z
    .number()
    .min(0, 'A comissão não pode ser negativa')
    .max(100, 'A comissão não pode exceder 100%')
    .optional(),
});

export type SalonServiceFormValues = z.infer<typeof salonServiceFormSchema>;
