import { z } from 'zod';

const remunerationTypeSchema = z.enum(['', 'SALARIO_FIXO', 'COMISSIONADO', 'FIXO_E_COMISSIONADO']);

export const employeeFormSchema = z
  .object({
    userId: z.string().min(1, 'ID do usuário é obrigatório'),
    remunerationType: remunerationTypeSchema.optional(),
    remunerationValue: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    const { remunerationType, remunerationValue } = data;

    // COMISSIONADO não tem mais % própria — a comissão vem do serviço/produto realizado, não
    // é cadastrada aqui. Só SALARIO_FIXO/FIXO_E_COMISSIONADO usam remunerationValue (salário base).
    const needsSalary =
      remunerationType === 'SALARIO_FIXO' || remunerationType === 'FIXO_E_COMISSIONADO';

    if (needsSalary) {
      if (!remunerationValue) {
        ctx.addIssue({
          code: 'custom',
          message: 'O valor do salário é obrigatório',
          path: ['remunerationValue'],
        });
      } else if (Number(remunerationValue) < 0) {
        ctx.addIssue({
          code: 'custom',
          message: 'O valor não pode ser negativo',
          path: ['remunerationValue'],
        });
      }
    }
  });

export type EmployeeFormValues = z.infer<typeof employeeFormSchema>;
