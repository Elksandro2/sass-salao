import { z } from 'zod';

export const salonServiceFormSchema = z.object({
  name: z.string().min(1, 'Nome é obrigatório').min(3, 'Mín. 3 caracteres').max(150, 'Máximo de 150 caracteres'),
  description: z.string().max(2000, 'Máximo de 2000 caracteres'),
  price: z.number().optional(),
  active: z.boolean(),
});

export type SalonServiceFormValues = z.infer<typeof salonServiceFormSchema>;
