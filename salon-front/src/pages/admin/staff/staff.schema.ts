import { z } from 'zod';
import { isValidCpf } from '../../../utils/cpfValidator';

const roleNameSchema = z.enum(['FUNCIONARIA', 'GERENTE_DE_ATENDIMENTO']);
const genderSchema = z.enum(['', 'FEMININO', 'MASCULINO', 'NAO_BINARIO', 'OUTRO', 'PREFIRO_NAO_INFORMAR']);
const pixKeyTypeSchema = z.enum(['', 'CPF', 'CNPJ', 'EMAIL', 'TELEFONE', 'ALEATORIA']);
const remunerationTypeSchema = z.enum(['', 'SALARIO_FIXO', 'COMISSIONADO', 'FIXO_E_COMISSIONADO']);
const commissionScopeSchema = z.enum(['', 'INDIVIDUAL', 'GLOBAL']);

const BRAZILIAN_STATES = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG',
  'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
] as const;

const phoneRegex = /^\(?\d{2}\)?\s?\d{4,5}-?\d{4}$/;
const zipCodeRegex = /^\d{5}-?\d{3}$/;

const PIX_KEY_VALIDATORS: Record<string, { test: (v: string) => boolean; message: string }> = {
  CPF: { test: isValidCpf, message: 'CPF inválido' },
  CNPJ: { test: (v) => /^\d{14}$/.test(v), message: 'O CNPJ deve ter 14 dígitos' },
  EMAIL: {
    test: (v) => /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(v),
    message: 'E-mail inválido',
  },
  TELEFONE: {
    test: (v) => /^\+55\d{10,11}$/.test(v),
    message: 'Use o formato +55DDDNÚMERO (ex.: +5581999998888)',
  },
  ALEATORIA: {
    test: (v) => /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(v),
    message: 'A chave aleatória deve ser um UUID válido',
  },
};

export const staffFormSchema = z
  .object({
    // Acesso
    roleName: roleNameSchema,
    name: z.string().min(1, 'O nome de exibição é obrigatório').min(3, 'Mínimo 3 caracteres').max(150, 'Máximo de 150 caracteres'),
    email: z.string().min(1, 'O email é obrigatório').email('Formato de e-mail inválido').max(150, 'Máximo de 150 caracteres'),
    password: z
      .string()
      .min(1, 'A senha é obrigatória')
      .min(8, 'A senha deve ter no mínimo 8 caracteres')
      .regex(/[a-z]/, 'A senha deve conter ao menos uma letra minúscula')
      .regex(/[A-Z]/, 'A senha deve conter ao menos uma letra maiúscula')
      .regex(/\d/, 'A senha deve conter ao menos um número'),
    confirmPassword: z.string().min(1, 'A confirmação de senha é obrigatória'),

    // Dados pessoais
    fullName: z.string().min(1, 'O nome completo é obrigatório').min(3, 'Mínimo 3 caracteres').max(150, 'Máximo de 150 caracteres'),
    socialName: z.string().max(150, 'Máximo de 150 caracteres').optional(),
    cpf: z
      .string()
      .min(1, 'O CPF é obrigatório')
      .refine((v) => isValidCpf(v), 'CPF inválido'),
    birthDate: z
      .string()
      .min(1, 'A data de nascimento é obrigatória')
      .refine((v) => new Date(v) < new Date(), 'A data de nascimento deve estar no passado'),
    gender: genderSchema.optional(),

    // Contato
    phone: z.string().min(1, 'O telefone é obrigatório').regex(phoneRegex, 'Telefone inválido'),
    emergencyContactName: z.string().max(150, 'Máximo de 150 caracteres').optional(),
    emergencyContactPhone: z
      .string()
      .optional()
      .refine((v) => !v || phoneRegex.test(v), 'Telefone inválido'),

    // Endereço
    zipCode: z.string().min(1, 'O CEP é obrigatório').regex(zipCodeRegex, 'CEP inválido'),
    street: z.string().min(1, 'O logradouro é obrigatório').max(200, 'Máximo de 200 caracteres'),
    streetNumber: z.string().min(1, 'O número é obrigatório').max(20, 'Máximo de 20 caracteres'),
    complement: z.string().max(100, 'Máximo de 100 caracteres').optional(),
    district: z.string().min(1, 'O bairro é obrigatório').max(100, 'Máximo de 100 caracteres'),
    city: z.string().min(1, 'A cidade é obrigatória').max(100, 'Máximo de 100 caracteres'),
    stateUf: z.enum(['', ...BRAZILIAN_STATES]),

    // PIX (opcional)
    pixKeyType: pixKeyTypeSchema.optional(),
    pixKey: z.string().max(150, 'Máximo de 150 caracteres').optional(),

    // Metadados
    hiredAt: z.string().optional(),
    notes: z.string().max(2000, 'Máximo de 2000 caracteres').optional(),

    // Remuneração (obrigatório só para FUNCIONARIA)
    remunerationType: remunerationTypeSchema.optional(),
    commissionScope: commissionScopeSchema.optional(),
    remunerationValue: z.string().optional(),
    commissionValue: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.confirmPassword !== data.password) {
      ctx.addIssue({ code: 'custom', message: 'As senhas não coincidem', path: ['confirmPassword'] });
    }

    if (!data.stateUf) {
      ctx.addIssue({ code: 'custom', message: 'O estado (UF) é obrigatório', path: ['stateUf'] });
    }

    // --- PIX: tipo e chave são um par — ou os dois, ou nenhum ---
    const hasPixType = !!data.pixKeyType;
    const hasPixKey = !!data.pixKey;

    if (hasPixType !== hasPixKey) {
      ctx.addIssue({
        code: 'custom',
        message: hasPixType ? 'Informe a chave PIX' : 'Selecione o tipo da chave PIX',
        path: [hasPixType ? 'pixKey' : 'pixKeyType'],
      });
    } else if (hasPixType && hasPixKey) {
      const validator = PIX_KEY_VALIDATORS[data.pixKeyType!];
      if (validator && !validator.test(data.pixKey!)) {
        ctx.addIssue({ code: 'custom', message: validator.message, path: ['pixKey'] });
      }
    }

    // --- Remuneração: obrigatória pros dois papéis; gerente só pode ter Salário Fixo, sem
    // comissão (não presta serviço, então não há base pra calcular comissão sobre nada) ---
    if (data.roleName === 'GERENTE_DE_ATENDIMENTO') {
      if (data.remunerationType && data.remunerationType !== 'SALARIO_FIXO') {
        ctx.addIssue({
          code: 'custom',
          message: 'Gerente de atendimento só pode ter remuneração do tipo Salário Fixo',
          path: ['remunerationType'],
        });
      }
      if (data.commissionScope || data.commissionValue) {
        ctx.addIssue({
          code: 'custom',
          message: 'Dados de comissão não se aplicam ao papel de gerente de atendimento',
          path: ['remunerationType'],
        });
      }
    }

    if (data.roleName === 'FUNCIONARIA' || data.roleName === 'GERENTE_DE_ATENDIMENTO') {
      if (!data.remunerationType) {
        ctx.addIssue({
          code: 'custom',
          message: 'O tipo de remuneração é obrigatório',
          path: ['remunerationType'],
        });
        return;
      }

      const isCommissioned =
        data.remunerationType === 'COMISSIONADO' || data.remunerationType === 'FIXO_E_COMISSIONADO';

      if (isCommissioned && !data.commissionScope) {
        ctx.addIssue({
          code: 'custom',
          message: 'O escopo da comissão é obrigatório',
          path: ['commissionScope'],
        });
      }

      if (!data.remunerationValue) {
        ctx.addIssue({
          code: 'custom',
          message: 'O valor de remuneração é obrigatório',
          path: ['remunerationValue'],
        });
      } else {
        const num = Number(data.remunerationValue);
        if (num < 0) {
          ctx.addIssue({ code: 'custom', message: 'Não pode ser negativo', path: ['remunerationValue'] });
        } else if (data.remunerationType === 'COMISSIONADO' && num > 100) {
          ctx.addIssue({
            code: 'custom',
            message: 'A porcentagem não pode exceder 100%',
            path: ['remunerationValue'],
          });
        }
      }

      if (data.remunerationType === 'FIXO_E_COMISSIONADO') {
        if (!data.commissionValue) {
          ctx.addIssue({
            code: 'custom',
            message: 'A porcentagem de comissão é obrigatória',
            path: ['commissionValue'],
          });
        } else if (Number(data.commissionValue) > 100) {
          ctx.addIssue({
            code: 'custom',
            message: 'A porcentagem não pode exceder 100%',
            path: ['commissionValue'],
          });
        }
      }
    }
  });

export type StaffFormValues = z.infer<typeof staffFormSchema>;
export { BRAZILIAN_STATES };
