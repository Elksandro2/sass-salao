import { useState } from 'react';
import { Users as TeamIcon, ShieldCheck } from 'lucide-react';
import { StaffRegistration } from '../staff/StaffRegistration';
import { Employees } from '../employees/Employees';
import { Users } from '../users/Users';

type TeamTab = 'staff' | 'accounts';

const TABS: { key: TeamTab; label: string; icon: typeof TeamIcon }[] = [
  { key: 'staff', label: 'Funcionárias(os) & Gerentes', icon: TeamIcon },
  { key: 'accounts', label: 'Contas administrativas', icon: ShieldCheck },
];

/**
 * Ponto único de entrada pra gestão de equipe. Substitui as antigas 3 abas separadas
 * (Equipe/Users, Funcionários(as)/Employees, Cadastro de Equipe/StaffRegistration), que
 * cobriam capacidades distintas do backend e não podiam simplesmente sumir:
 * - Cadastro completo (StaffRegistration) continua sendo o único jeito de criar
 *   FUNCIONARIA/GERENTE — é o formulário mais validado, com CPF/endereço/PIX/remuneração.
 * - Employees (embutido) continua sendo o único jeito de editar remuneração depois que a
 *   pessoa já foi cadastrada — o endpoint /v1/staff não tem update.
 * - Users continua sendo o único jeito de criar/editar uma conta Administrador.
 */
export const Team = () => {
  const [tab, setTab] = useState<TeamTab>('staff');

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-2xl font-bold text-[#3b3036] dark:text-white">Equipe</h1>
        <p className="text-xs text-[#3b3036]/60 dark:text-gray-400 mt-1">
          Cadastro e gestão de todas as pessoas que trabalham no salão.
        </p>
      </div>

      <div className="flex gap-2 border-b border-[#eae1e1] dark:border-[#1e293b]">
        {TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key)}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 -mb-px transition-colors cursor-pointer ${
              tab === key
                ? 'border-[#be8a83] text-[#be8a83]'
                : 'border-transparent text-[#3b3036]/60 dark:text-gray-400 hover:text-[#3b3036] dark:hover:text-white'
            }`}
          >
            <Icon size={16} />
            {label}
          </button>
        ))}
      </div>

      {tab === 'staff' ? (
        <div className="space-y-8">
          <StaffRegistration />
          <div className="border-t border-[#eae1e1] dark:border-[#1e293b] pt-6">
            <Employees embedded />
          </div>
        </div>
      ) : (
        <Users />
      )}
    </div>
  );
};
