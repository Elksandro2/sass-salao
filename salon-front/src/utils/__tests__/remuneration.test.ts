import { describe, it, expect } from 'vitest';
import {
  REMUNERATION_TYPES,
  remunerationLabel,
  remunerationIsDaily,
  remunerationHasFixedSalary,
  remunerationNeedsValue,
  remunerationPaysServiceCommission,
  remunerationValueFieldLabel,
} from '../remuneration';

describe('remuneration helpers', () => {
  it('lists the five types', () => {
    expect(REMUNERATION_TYPES).toEqual([
      'SALARIO_FIXO',
      'COMISSIONADO',
      'FIXO_E_COMISSIONADO',
      'DIARISTA',
      'DIARIA_E_COMISSIONADO',
    ]);
  });

  it('labels each type and falls back gracefully', () => {
    expect(remunerationLabel('DIARISTA')).toBe('Diarista');
    expect(remunerationLabel('DIARIA_E_COMISSIONADO')).toBe('Diarista + comissão');
    expect(remunerationLabel(null)).toBe('Não definido');
    expect(remunerationLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });

  it('classifies daily types', () => {
    expect(remunerationIsDaily('DIARISTA')).toBe(true);
    expect(remunerationIsDaily('DIARIA_E_COMISSIONADO')).toBe(true);
    expect(remunerationIsDaily('SALARIO_FIXO')).toBe(false);
  });

  it('classifies fixed-salary types', () => {
    expect(remunerationHasFixedSalary('SALARIO_FIXO')).toBe(true);
    expect(remunerationHasFixedSalary('FIXO_E_COMISSIONADO')).toBe(true);
    expect(remunerationHasFixedSalary('DIARISTA')).toBe(false);
  });

  it('needsValue for fixed salary and daily, not for pure commission', () => {
    expect(remunerationNeedsValue('SALARIO_FIXO')).toBe(true);
    expect(remunerationNeedsValue('DIARISTA')).toBe(true);
    expect(remunerationNeedsValue('DIARIA_E_COMISSIONADO')).toBe(true);
    expect(remunerationNeedsValue('COMISSIONADO')).toBe(false);
  });

  it('pays service commission for the three commission variants', () => {
    expect(remunerationPaysServiceCommission('COMISSIONADO')).toBe(true);
    expect(remunerationPaysServiceCommission('FIXO_E_COMISSIONADO')).toBe(true);
    expect(remunerationPaysServiceCommission('DIARIA_E_COMISSIONADO')).toBe(true);
    expect(remunerationPaysServiceCommission('DIARISTA')).toBe(false);
    expect(remunerationPaysServiceCommission('SALARIO_FIXO')).toBe(false);
  });

  it('names the value field per type', () => {
    expect(remunerationValueFieldLabel('DIARISTA')).toBe('Valor da diária (R$)');
    expect(remunerationValueFieldLabel('SALARIO_FIXO')).toBe('Valor do salário fixo (R$)');
  });
});
