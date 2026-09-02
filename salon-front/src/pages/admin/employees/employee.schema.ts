import { z } from 'zod';
import { remunerationNeedsValue, remunerationIsDaily } from '../../../utils/remuneration';

const remunerationTypeSchema = z.enum([
  '',
  'SALARIO_FIXO',
  'COMISSIONADO',
  'FIXO_E_COMISSIONADO',
  'DIARISTA',
  'DIARIA_E_COMISSIONADO',
]);

export const employeeFormSchema = z
  .object({
    userId: z.string().min(1, 'ID do usuário é obrigatório'),
    remunerationType: remunerationTypeSchema.optional(),
    remunerationValue: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    const { remunerationType, remunerationValue } = data;

    // COMISSIONADO não tem valor próprio — a comissão vem do serviço/produto realizado.
    // SALARIO_FIXO/FIXO_E_COMISSIONADO usam remunerationValue como salário base;
    // DIARISTA/DIARIA_E_COMISSIONADO usam como valor da diária.
    const needsSalary = remunerationNeedsValue(remunerationType);

    if (needsSalary) {
      if (!remunerationValue) {
        ctx.addIssue({
          code: 'custom',
          message: remunerationIsDaily(remunerationType)
            ? 'O valor da diária é obrigatório'
            : 'O valor do salário é obrigatório',
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
