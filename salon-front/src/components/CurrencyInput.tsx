import { forwardRef, useEffect, useState, type ChangeEvent } from 'react';

/**
 * Campo de valor em R$ com máscara em tempo real — digita e os dígitos entram da direita pra
 * esquerda, como caixa eletrônico ("1234" vira "R$ 12,34"). Evita a ambiguidade de "." vs ","
 * de um <input type="number"> comum, que também deixa digitar "1e5" ou mais de duas casas
 * decimais sem avisar nada.
 *
 * Contrato: value/onValueChange sempre usam string decimal com ponto (ex.: "1234.56"), igual
 * todo o resto do sistema já espera (Number(value), zod, requests pro backend) — só a exibição
 * é mascarada, o valor "de fora" não muda de formato.
 */
interface CurrencyInputProps {
  value: string;
  onValueChange: (value: string) => void;
  className?: string;
  placeholder?: string;
  disabled?: boolean;
  id?: string;
  name?: string;
  onBlur?: () => void;
}

function digitsToDecimalString(digits: string): string {
  if (!digits) return '';
  const num = Number(digits) / 100;
  return num.toFixed(2);
}

function decimalStringToDigits(value: string): string {
  if (!value) return '';
  const num = Number(value);
  if (!Number.isFinite(num)) return '';
  const cents = Math.round(Math.abs(num) * 100);
  return cents ? String(cents) : '';
}

function formatDisplay(digits: string): string {
  if (!digits) return '';
  const decimal = digitsToDecimalString(digits);
  const [intPart, decPart] = decimal.split('.');
  const withThousands = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `R$ ${withThousands},${decPart}`;
}

export const CurrencyInput = forwardRef<HTMLInputElement, CurrencyInputProps>(
  ({ value, onValueChange, className, placeholder, disabled, id, name, onBlur }, ref) => {
    const [digits, setDigits] = useState(() => decimalStringToDigits(value));

    // Sincroniza se o valor "de fora" mudar (ex.: form.reset(), edição de outro registro).
    useEffect(() => {
      setDigits(decimalStringToDigits(value));
    }, [value]);

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
      const rawDigits = e.target.value.replace(/\D/g, '').replace(/^0+(?=\d)/, '');
      setDigits(rawDigits);
      onValueChange(rawDigits ? digitsToDecimalString(rawDigits) : '');
    };

    return (
      <input
        ref={ref}
        id={id}
        name={name}
        type="text"
        inputMode="decimal"
        autoComplete="off"
        value={formatDisplay(digits)}
        onChange={handleChange}
        onBlur={onBlur}
        placeholder={placeholder ?? 'R$ 0,00'}
        disabled={disabled}
        className={className}
      />
    );
  }
);
CurrencyInput.displayName = 'CurrencyInput';
