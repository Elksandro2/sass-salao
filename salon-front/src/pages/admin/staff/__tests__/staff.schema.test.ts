import { describe, it, expect } from 'vitest';
import { staffFormSchema } from '../staff.schema';

const validBase = {
  roleName: 'FUNCIONARIA' as const,
  name: 'Maria',
  email: 'maria@example.com',
  password: 'Senha@123',
  confirmPassword: 'Senha@123',
  fullName: 'Maria Silva',
  cpf: '111.444.777-35',
  birthDate: '1990-01-01',
  phone: '(81) 99999-9999',
  zipCode: '50000-000',
  street: 'Rua A',
  streetNumber: '10',
  district: 'Boa Vista',
  city: 'Recife',
  stateUf: 'PE' as const,
  remunerationType: 'SALARIO_FIXO' as const,
  remunerationValue: '2000',
};

describe('staffFormSchema', () => {
  it('accepts a valid FUNCIONARIA submission', () => {
    const result = staffFormSchema.safeParse(validBase);
    expect(result.success).toBe(true);
  });

  it('accepts a valid GERENTE_DE_ATENDIMENTO submission with Salário Fixo', () => {
    const result = staffFormSchema.safeParse({ ...validBase, roleName: 'GERENTE_DE_ATENDIMENTO' });
    expect(result.success).toBe(true);
  });

  it('rejects GERENTE_DE_ATENDIMENTO without remuneration', () => {
    const { remunerationType: _remunerationType, remunerationValue: _remunerationValue, ...rest } = validBase;
    const result = staffFormSchema.safeParse({ ...rest, roleName: 'GERENTE_DE_ATENDIMENTO' });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.includes('remunerationType'))).toBe(true);
    }
  });

  it('rejects an invalid CPF', () => {
    const result = staffFormSchema.safeParse({ ...validBase, cpf: '111.444.777-36' });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.includes('cpf'))).toBe(true);
    }
  });

  it('rejects mismatched password confirmation', () => {
    const result = staffFormSchema.safeParse({ ...validBase, confirmPassword: 'Outra@123' });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.includes('confirmPassword'))).toBe(true);
    }
  });

  it('rejects a weak password missing an uppercase letter', () => {
    const result = staffFormSchema.safeParse({
      ...validBase,
      password: 'senha123',
      confirmPassword: 'senha123',
    });
    expect(result.success).toBe(false);
  });

  it('rejects FUNCIONARIA without remunerationType', () => {
    const { remunerationType: _remunerationType, ...rest } = validBase;
    const result = staffFormSchema.safeParse(rest);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.includes('remunerationType'))).toBe(true);
    }
  });

  it('rejects COMISSIONADO without commissionScope', () => {
    const result = staffFormSchema.safeParse({
      ...validBase,
      remunerationType: 'COMISSIONADO',
      remunerationValue: '10',
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.includes('commissionScope'))).toBe(true);
    }
  });

  it('rejects commission percentage over 100', () => {
    const result = staffFormSchema.safeParse({
      ...validBase,
      remunerationType: 'COMISSIONADO',
      commissionScope: 'GLOBAL',
      remunerationValue: '150',
    });
    expect(result.success).toBe(false);
  });

  it('rejects GERENTE_DE_ATENDIMENTO with COMISSIONADO (não presta serviço, sem comissão)', () => {
    const result = staffFormSchema.safeParse({
      ...validBase,
      roleName: 'GERENTE_DE_ATENDIMENTO',
      remunerationType: 'COMISSIONADO',
      commissionScope: 'GLOBAL',
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.includes('remunerationType'))).toBe(true);
    }
  });

  it('rejects GERENTE_DE_ATENDIMENTO with commissionScope set', () => {
    const result = staffFormSchema.safeParse({
      ...validBase,
      roleName: 'GERENTE_DE_ATENDIMENTO',
      commissionScope: 'GLOBAL',
    });
    expect(result.success).toBe(false);
  });

  it('rejects a missing UF', () => {
    const result = staffFormSchema.safeParse({ ...validBase, stateUf: '' });
    expect(result.success).toBe(false);
  });

  it('rejects zip code in the wrong format', () => {
    const result = staffFormSchema.safeParse({ ...validBase, zipCode: 'abc' });
    expect(result.success).toBe(false);
  });

  it('rejects a birth date in the future', () => {
    const future = new Date();
    future.setFullYear(future.getFullYear() + 1);
    const result = staffFormSchema.safeParse({
      ...validBase,
      birthDate: future.toISOString().slice(0, 10),
    });
    expect(result.success).toBe(false);
  });

  it('accepts when pix type and key are both absent', () => {
    const result = staffFormSchema.safeParse(validBase);
    expect(result.success).toBe(true);
  });

  it('rejects when pix type is set but key is missing', () => {
    const result = staffFormSchema.safeParse({ ...validBase, pixKeyType: 'EMAIL' });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.includes('pixKey'))).toBe(true);
    }
  });

  it('rejects when pix key is set but type is missing', () => {
    const result = staffFormSchema.safeParse({ ...validBase, pixKey: 'maria@example.com' });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.includes('pixKeyType'))).toBe(true);
    }
  });

  it('accepts a valid EMAIL pix key', () => {
    const result = staffFormSchema.safeParse({
      ...validBase,
      pixKeyType: 'EMAIL',
      pixKey: 'maria@example.com',
    });
    expect(result.success).toBe(true);
  });

  it('rejects an EMAIL pix key with invalid format', () => {
    const result = staffFormSchema.safeParse({
      ...validBase,
      pixKeyType: 'EMAIL',
      pixKey: 'not-an-email',
    });
    expect(result.success).toBe(false);
  });

  it('accepts a valid random (UUID) pix key', () => {
    const result = staffFormSchema.safeParse({
      ...validBase,
      pixKeyType: 'ALEATORIA',
      pixKey: '550e8400-e29b-41d4-a716-446655440000',
    });
    expect(result.success).toBe(true);
  });

  it('rejects a CPF pix key that fails check digits', () => {
    const result = staffFormSchema.safeParse({
      ...validBase,
      pixKeyType: 'CPF',
      pixKey: '11111111111',
    });
    expect(result.success).toBe(false);
  });
});
