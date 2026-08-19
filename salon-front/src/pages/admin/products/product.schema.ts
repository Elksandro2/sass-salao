import { z } from 'zod';

export const productFormSchema = z.object({
  name: z.string().min(1, 'Nome é obrigatório').min(3, 'Mín. 3 caracteres').max(150, 'Máximo de 150 caracteres'),
  stock: z
    .string()
    .min(1, 'Estoque é obrigatório')
    .refine((v) => Number(v) >= 0, 'Não pode ser negativo'),
  price: z
    .string()
    .min(1, 'Preço é obrigatório')
    .refine((v) => Number(v) >= 0, 'Não pode ser negativo'),
  active: z.boolean(),
  brand: z.string().max(100, 'Máximo de 100 caracteres').optional(),
  costPrice: z
    .string()
    .optional()
    .refine((v) => !v || Number(v) >= 0, 'Não pode ser negativo'),
  capacity: z
    .string()
    .optional()
    .refine((v) => !v || Number(v) > 0, 'Deve ser maior que zero'),
  unit: z.enum(['', 'ML', 'L', 'G', 'KG', 'UNIDADE']).optional(),
});

export type ProductFormValues = z.infer<typeof productFormSchema>;
