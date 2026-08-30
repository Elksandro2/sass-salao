import { UserCog, Users as UsersIcon, CheckCircle } from 'lucide-react';
import type { StaffRoleName } from '../services/staff';

interface RoleSelectorProps {
  value: StaffRoleName | null;
  onChange: (role: StaffRoleName) => void;
}

const ROLES: { value: StaffRoleName; label: string; description: string; icon: typeof UsersIcon }[] = [
  {
    value: 'FUNCIONARIA',
    label: 'Colaboradora',
    description: 'Atende clientes. Precisa de dados de remuneração/comissão.',
    icon: UsersIcon,
  },
  {
    value: 'GERENTE_DE_ATENDIMENTO',
    label: 'Gerente de Atendimento',
    description: 'Gerencia a operação do salão. Recebe salário fixo, sem comissão.',
    icon: UserCog,
  },
];

export const RoleSelector = ({ value, onChange }: RoleSelectorProps) => {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
      {ROLES.map((role) => {
        const Icon = role.icon;
        const selected = value === role.value;
        return (
          <button
            type="button"
            key={role.value}
            onClick={() => onChange(role.value)}
            className={`relative text-left p-5 rounded-2xl border-2 transition-all duration-200 cursor-pointer ${
              selected
                ? 'border-[#be8a83] bg-[#be8a83]/5 shadow-md shadow-[#be8a83]/10'
                : 'border-gray-100 bg-white hover:border-[#be8a83]/50'
            }`}
          >
            <Icon size={28} className="text-[#be8a83] mb-2" />
            <h5 className="font-bold text-[#3b3036] mb-1">{role.label}</h5>
            <p className="text-xs text-[#3b3036]/60 leading-relaxed">{role.description}</p>
            {selected && <CheckCircle size={20} className="absolute top-3 right-3 text-[#be8a83]" />}
          </button>
        );
      })}
    </div>
  );
};
