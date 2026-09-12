import { z } from 'zod';

export const productFormSchema = z
  .object({
    name: z.string().min(1, 'Nome é obrigatório').min(3, 'Mín. 3 caracteres').max(150, 'Máximo de 150 caracteres'),
    // Só é obrigatório quando availableForSale estiver marcado (produto só de uso interno não
    // tem preço de venda) — ver o superRefine abaixo.
    price: z
      .string()
      .optional()
      .refine((v) => !v || Number(v) >= 0, 'Não pode ser negativo'),
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
    availableForSale: z.boolean(),
    usedInServiceRecipe: z.boolean(),
  })
  .superRefine((data, ctx) => {
    if (data.availableForSale && !data.price) {
      ctx.addIssue({
        code: 'custom',
        message: 'O preço de venda é obrigatório para produtos disponíveis para venda',
        path: ['price'],
      });
    }
  });

export type ProductFormValues = z.infer<typeof productFormSchema>;
