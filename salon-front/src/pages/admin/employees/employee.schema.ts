import { z } from 'zod';

const remunerationTypeSchema = z.enum(['', 'SALARIO_FIXO', 'COMISSIONADO', 'FIXO_E_COMISSIONADO']);
const commissionScopeSchema = z.enum(['', 'INDIVIDUAL', 'GLOBAL']);

export const employeeFormSchema = z
  .object({
    userId: z.string().min(1, 'ID do usuário é obrigatório'),
    remunerationType: remunerationTypeSchema.optional(),
    commissionScope: commissionScopeSchema.optional(),
    remunerationValue: z.string().optional(),
    commissionValue: z.string().optional(),
    productCommissionValue: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    const { remunerationType, commissionScope, remunerationValue, commissionValue, productCommissionValue } = data;

    const isCommissionBased =
      remunerationType === 'COMISSIONADO' || remunerationType === 'FIXO_E_COMISSIONADO';
    const needsRemunerationValue =
      remunerationType === 'SALARIO_FIXO' ||
      remunerationType === 'COMISSIONADO' ||
      remunerationType === 'FIXO_E_COMISSIONADO';

    if (isCommissionBased && !commissionScope) {
      ctx.addIssue({
        code: 'custom',
        message: 'O escopo da comissão é obrigatório',
        path: ['commissionScope'],
      });
    }

    if (needsRemunerationValue) {
      if (!remunerationValue) {
        ctx.addIssue({
          code: 'custom',
          message:
            remunerationType === 'COMISSIONADO'
              ? 'A porcentagem da comissão é obrigatória'
              : 'O salário fixo é obrigatório',
          path: ['remunerationValue'],
        });
      } else {
        const num = Number(remunerationValue);
        if (num < 0) {
          ctx.addIssue({
            code: 'custom',
            message: 'O valor não pode ser negativo',
            path: ['remunerationValue'],
          });
        } else if (remunerationType === 'COMISSIONADO' && num > 100) {
          ctx.addIssue({
            code: 'custom',
            message: 'A comissão não pode passar de 100%',
            path: ['remunerationValue'],
          });
        }
      }
    }

    if (remunerationType === 'FIXO_E_COMISSIONADO') {
      if (!commissionValue) {
        ctx.addIssue({
          code: 'custom',
          message: 'A porcentagem da comissão é obrigatória',
          path: ['commissionValue'],
        });
      } else {
        const num = Number(commissionValue);
        if (num < 0) {
          ctx.addIssue({
            code: 'custom',
            message: 'O valor não pode ser negativo',
            path: ['commissionValue'],
          });
        } else if (num > 100) {
          ctx.addIssue({
            code: 'custom',
            message: 'A comissão não pode passar de 100%',
            path: ['commissionValue'],
          });
        }
      }
    }

    // Comissão sobre produtos é opcional (nem toda comissionada vende produto), mas quando
    // preenchida segue as mesmas regras de faixa 0-100%.
    if (isCommissionBased && productCommissionValue) {
      const num = Number(productCommissionValue);
      if (num < 0) {
        ctx.addIssue({
          code: 'custom',
          message: 'O valor não pode ser negativo',
          path: ['productCommissionValue'],
        });
      } else if (num > 100) {
        ctx.addIssue({
          code: 'custom',
          message: 'A comissão não pode passar de 100%',
          path: ['productCommissionValue'],
        });
      }
    }
  });

export type EmployeeFormValues = z.infer<typeof employeeFormSchema>;
