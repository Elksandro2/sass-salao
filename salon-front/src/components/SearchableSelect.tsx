import { useState, useEffect } from 'react';

export interface SearchableSelectOption {
  id: number;
  label: string;
}

/**
 * Um único campo de busca + seleção — digita pra filtrar, clica numa opção da lista pra
 * escolher. Substitui o padrão antigo de "input de busca + <select> nativo embaixo dele", que
 * parecia dois controles pra uma coisa só — útil sempre que a lista de opções é grande.
 */
export const SearchableSelect = ({
  value,
  onSelect,
  options,
  placeholder,
  noResultsLabel = 'Nenhum resultado encontrado',
  disabled = false,
  className = 'input-premium',
}: {
  value: string;
  onSelect: (id: string) => void;
  options: SearchableSelectOption[];
  placeholder: string;
  noResultsLabel?: string;
  disabled?: boolean;
  className?: string;
}) => {
  const [query, setQuery] = useState(
    () => options.find((o) => String(o.id) === value)?.label ?? ''
  );
  const [showDropdown, setShowDropdown] = useState(false);

  // Um campo travado (ex.: funcionária só pode agendar pra si mesma) recebe o valor de fora
  // depois do primeiro render — sincroniza só nesse caso, pra não brigar com o que a pessoa
  // está digitando quando o campo é editável.
  useEffect(() => {
    if (disabled) {
      setQuery(options.find((o) => String(o.id) === value)?.label ?? '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [disabled, value]);

  const matches = options.filter((o) => o.label.toLowerCase().includes(query.trim().toLowerCase()));

  return (
    <div className="relative">
      <input
        type="text"
        value={query}
        disabled={disabled}
        onChange={(e) => {
          setQuery(e.target.value);
          setShowDropdown(true);
          if (value) onSelect(''); // digitar de novo desfaz a seleção anterior
        }}
        onFocus={() => setShowDropdown(true)}
        onBlur={() => setTimeout(() => setShowDropdown(false), 150)}
        placeholder={placeholder}
        autoComplete="off"
        className={`${className} ${disabled ? 'opacity-60 cursor-not-allowed' : ''}`}
      />
      {showDropdown && !disabled && (
        <ul className="absolute z-10 mt-1 w-full max-h-48 overflow-y-auto bg-white border border-[#eae1e1] rounded-xl shadow-lg">
          {matches.length === 0 ? (
            <li className="px-3 py-2 text-sm text-gray-400">{noResultsLabel}</li>
          ) : (
            matches.map((o) => (
              <li key={o.id}>
                <button
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => {
                    onSelect(String(o.id));
                    setQuery(o.label);
                    setShowDropdown(false);
                  }}
                  className="w-full text-left px-3 py-2 text-sm hover:bg-[#fcf9f9] cursor-pointer"
                >
                  {o.label}
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
};
