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

// Unidades da mesma grandeza — ML/L são volume, G/KG são massa, UNIDADE só combina com ela
// mesma. Espelha ProductUnit.factorTo no backend: um valor só é convertível pro outro dentro
// do mesmo grupo (ml pra g não faz sentido).
const UNIT_FAMILIES: ProductUnitValue[][] = [
  ['ML', 'L'],
  ['G', 'KG'],
  ['UNIDADE'],
];

/**
 * Unidades que podem ser usadas numa receita para um produto cadastrado em `productUnit` — a
 * própria unidade do produto sempre entra primeiro. Sem unidade cadastrada no produto, devolve
 * a lista inteira (não há grandeza pra restringir contra).
 */
export function compatibleUnits(productUnit: string | null | undefined): ProductUnitValue[] {
  if (!productUnit) return PRODUCT_UNITS.map((u) => u.value);
  const family = UNIT_FAMILIES.find((f) => f.includes(productUnit as ProductUnitValue));
  return family ?? [productUnit as ProductUnitValue];
}
