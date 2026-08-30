/**
 * Unidade de medida da embalagem/capacidade do produto — espelha o enum ProductUnit do backend.
 * Centraliza rótulo longo (para <option>) e símbolo curto (para addon ao lado de um campo).
 */
export type ProductUnitValue = 'ML' | 'L' | 'G' | 'KG' | 'UNIDADE';

interface ProductUnitInfo {
  value: ProductUnitValue;
  /** Texto do <option> no seletor de unidade. */
  label: string;
  /** Símbolo curto exibido colado ao campo de quantidade (ex.: "ml"). */
  symbol: string;
}

export const PRODUCT_UNITS: ProductUnitInfo[] = [
  { value: 'ML', label: 'Mililitros (ml)', symbol: 'ml' },
  { value: 'L', label: 'Litros (L)', symbol: 'L' },
  { value: 'G', label: 'Gramas (g)', symbol: 'g' },
  { value: 'KG', label: 'Quilos (kg)', symbol: 'kg' },
  { value: 'UNIDADE', label: 'Unidade', symbol: 'un' },
];

const bySymbol = new Map(PRODUCT_UNITS.map((u) => [u.value, u.symbol]));
const byLabel = new Map(PRODUCT_UNITS.map((u) => [u.value, u.label]));

/** "ML" -> "ml". Aceita string solta vinda da API; devolve minúsculo como fallback. */
export function productUnitSymbol(unit: string | null | undefined): string {
  if (!unit) return '';
  return bySymbol.get(unit as ProductUnitValue) ?? unit.toLowerCase();
}

/** "ML" -> "Mililitros (ml)". Fallback: a própria string. */
export function productUnitLabel(unit: string | null | undefined): string {
  if (!unit) return '';
  return byLabel.get(unit as ProductUnitValue) ?? unit;
}
