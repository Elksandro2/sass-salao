import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Edit, Trash2, Plus, RotateCcw, Eye, EyeOff } from 'lucide-react';
import { DataTable } from '../../../components/table/DataTable';
import type { FilterField } from '../../../components/table/DataTable';
import { ModalForm } from '../../../components/modal/ModalForm';
import { ConfirmDialog } from '../../../components/modal/ConfirmDialog';
import { PermissionGate } from '../../../components/permissions/PermissionGate';
import { usersApi } from './services/users';
import type { UserData, UserCreateRequest, UserFilter } from './services/users';
import { userFormSchema } from './user-form.schema';
import type { UserFormValues } from './user-form.schema';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';

const labelCls = 'label-premium';

export const Users = () => {
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const [showForm, setShowForm] = useState(false);
  const [editingUser, setEditingUser] = useState<UserData | null>(null);

  const [showConfirm, setShowConfirm] = useState(false);
  const [targetUserId, setTargetUserId] = useState<number | null>(null);
  const [confirmAction, setConfirmAction] = useState<'delete' | 'restore'>('delete');

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<UserFormValues>({ resolver: zodResolver(userFormSchema) });
  const { error: showError } = useAlert();

  const handleOpenForm = (user?: UserData) => {
    reset();
    if (user) {
      setEditingUser(user);
      setValue('_isEdit', true);
      setValue('name', user.name);
      setValue('email', user.email ?? '');
      setValue('phone', user.phone);
      setValue('active', user.active);
      setValue('roleId', getRoleIdByName(user.role));
    } else {
      setEditingUser(null);
      setValue('_isEdit', false);
      setValue('active', true);
      // Criação de FUNCIONARIA/GERENTE agora é feita pelo cadastro completo (aba Equipe →
      // Funcionárias(os) & Gerentes) — esta tela só cria conta administrativa daqui em diante.
      setValue('roleId', 1);
    }
    setShowForm(true);
  };

  const getRoleIdByName = (roleName: string) => {
    switch (roleName) {
      case 'ADMIN':
        return 1;
      case 'GERENTE_DE_ATENDIMENTO':
        return 2;
      case 'FUNCIONARIA':
        return 3;
      case 'SYSADMIN':
        return 5;
      default:
        return 3;
    }
  };

  const onSubmit = async (data: UserFormValues) => {
    try {
      const payload: any = { ...data };
      delete payload._isEdit;
      delete payload.confirmPassword;
      if (editingUser?.id && !payload.password) {
        delete payload.password;
      }

      if (editingUser?.id) {
        await usersApi.update(editingUser.id, payload);
      } else {
        await usersApi.create(payload as unknown as UserCreateRequest);
      }
      setShowForm(false);
      setRefreshTrigger((prev) => prev + 1);
    } catch (error: any) {
      if (error.response?.status === 400 && error.response.data?.errors) {
        const fieldErrors = error.response.data.errors;
        Object.keys(fieldErrors).forEach((field) => {
          setError(field as any, { type: 'server', message: fieldErrors[field] });
        });
      } else if (error.response?.status === 409) {
        const msg = error.response.data?.message || 'Email já cadastrado.';
        setError('email', { type: 'server', message: msg });
      } else {
        const msg = getApiErrorMessage(
          error,
          'Erro ao salvar usuário. Verifique os dados e tente novamente.'
        );
        await showError(msg);
      }
    }
  };

  const handleConfirmAction = async () => {
    if (!targetUserId) return;
    try {
      if (confirmAction === 'delete') {
        await usersApi.delete(targetUserId);
      } else {
        await usersApi.restore(targetUserId);
      }
      setShowConfirm(false);
      setRefreshTrigger((prev) => prev + 1);
    } catch (error) {
      const fallbackMsg =
        confirmAction === 'delete' ? 'Erro ao desativar usuário.' : 'Erro ao reativar usuário.';
      const msg = getApiErrorMessage(error, fallbackMsg);
      await showError(msg);
    }
  };

  const getStatusBadge = (active: boolean) => {
    const className = active
      ? 'bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-400 dark:border-emerald-500/20'
      : 'bg-rose-50 text-rose-700 border border-rose-200 dark:bg-rose-500/10 dark:text-rose-400 dark:border-rose-500/20';
    return (
      <span
        className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold whitespace-nowrap ${className}`}
      >
        {active ? 'Ativo' : 'Inativo'}
      </span>
    );
  };

  const columns = [
    { key: 'name', label: 'Nome' },
    { key: 'email', label: 'Email' },
    {
      key: 'role',
      label: 'Cargo',
      render: (item: UserData) => {
        switch (item.role) {
          case 'ADMIN':
            return 'Administrador(a)';
          case 'GERENTE_DE_ATENDIMENTO':
            return 'Gerente';
          case 'FUNCIONARIA':
            return 'Funcionário(a)';
          case 'SYSADMIN':
            return 'Sysadmin';
          case 'CLIENTE':
            return 'Cliente';
          default:
            return item.role.replace(/_/g, ' ');
        }
      },
    },
    { key: 'phone', label: 'Telefone', render: (item: UserData) => item.phone || 'Não informado' },
    {
      key: 'active',
      label: 'Status',
      render: (item: UserData) => getStatusBadge(item.active),
    },
    {
      key: 'actions',
      label: 'Ações',
      render: (item: UserData) => (
        <div className="flex gap-2">
          <PermissionGate method="PATCH" endpoint={`/v1/users/${item.id}`}>
            <button
              onClick={() => handleOpenForm(item)}
              className="p-1.5 text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-950/20 border border-indigo-200 dark:border-indigo-800 rounded-lg transition-all cursor-pointer"
              title="Editar Usuário"
            >
              <Edit size={15} />
            </button>
          </PermissionGate>
          {item.active ? (
            <PermissionGate method="DELETE" endpoint={`/v1/users/${item.id}`}>
              <button
                onClick={() => {
                  setTargetUserId(item.id);
                  setConfirmAction('delete');
                  setShowConfirm(true);
                }}
                className="p-1.5 text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/20 border border-rose-200 dark:border-rose-800 rounded-lg transition-all cursor-pointer flex items-center gap-1 text-xs font-semibold"
                title="Desativar Usuário"
              >
                <Trash2 size={15} />
                <span>Desativar</span>
              </button>
            </PermissionGate>
          ) : (
            <PermissionGate method="PATCH" endpoint={`/v1/users/${item.id}/restore`}>
              <button
                onClick={() => {
                  setTargetUserId(item.id);
                  setConfirmAction('restore');
                  setShowConfirm(true);
                }}
                className="p-1.5 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-950/20 border border-emerald-200 dark:border-emerald-800 rounded-lg transition-all cursor-pointer flex items-center gap-1 text-xs font-semibold"
                title="Reativar Usuário"
              >
                <RotateCcw size={15} />
                <span>Reativar</span>
              </button>
            </PermissionGate>
          )}
        </div>
      ),
    },
  ];

  const filtersConfig: FilterField[] = [
    { key: 'name', label: 'Nome', type: 'text' },
    { key: 'email', label: 'Email', type: 'text' },
    { key: 'phone', label: 'Telefone', type: 'text' },
    { key: 'active', label: 'Status', type: 'boolean' },
  ];

  const initialFilters: UserFilter = {
    name: '',
    email: '',
    phone: '',
    active: undefined,
  };

  const fetchUsersData = async (filter: UserFilter, page: number, size: number) => {
    return usersApi.findAll(filter, page, size);
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="font-heading text-2xl font-bold text-[#3b3036] dark:text-white">
            Contas Administrativas
          </h2>
          <p className="text-xs text-[#3b3036]/60 dark:text-gray-400 mt-1">
            Login, senha e status de acesso de qualquer conta da equipe.
          </p>
        </div>
        <PermissionGate method="POST" endpoint="/v1/users">
          <button onClick={() => handleOpenForm()} className="btn-premium font-semibold">
            <Plus size={18} /> Nova Conta Administrativa
          </button>
        </PermissionGate>
      </div>

      <DataTable
        columns={columns}
        fetchData={fetchUsersData}
        filtersConfig={filtersConfig}
        keyExtractor={(item) => item.id}
        refreshTrigger={refreshTrigger}
        initialFilters={initialFilters}
      />

      <ModalForm
        show={showForm}
        onHide={() => setShowForm(false)}
        title={editingUser ? 'Editar Conta da Equipe' : 'Nova Conta da Equipe'}
        onSubmit={handleSubmit(onSubmit)}
        isSubmitting={isSubmitting}
      >
        <div className="space-y-4">
          <div>
            <label className={labelCls}>Nome Completo *</label>
            <input
              type="text"
              maxLength={150}
              className={`input-premium ${errors.name ? 'border-rose-300 focus:border-rose-500' : ''}`}
              {...register('name')}
            />
            {errors.name && (
              <span className="text-xs text-rose-500 font-semibold">{errors.name.message}</span>
            )}
          </div>
          <div>
            <label className={labelCls}>Email *</label>
            <input
              type="email"
              maxLength={150}
              className={`input-premium ${errors.email ? 'border-rose-300 focus:border-rose-500' : ''}`}
              {...register('email')}
            />
            {errors.email && (
              <span className="text-xs text-rose-500 font-semibold">{errors.email.message}</span>
            )}
          </div>
          <div>
            <label className={labelCls}>Cargo (Papel) *</label>
            <select
              className={`input-premium ${errors.roleId ? 'border-rose-300 focus:border-rose-500' : ''}`}
              {...register('roleId', { setValueAs: (v) => Number(v) })}
            >
              <option value="1">Administrador(a)</option>
              {/* Funcionária/Gerente só se editava aqui por herança do cadastro simples antigo —
                  a criação foi centralizada em Equipe → Funcionárias(os) & Gerentes. Mantemos as
                  opções na edição para não travar contas que já existiam com esses papéis. */}
              {editingUser && (
                <>
                  <option value="2">Gerente</option>
                  <option value="3">Funcionário(a)</option>
                </>
              )}
            </select>
            {!editingUser && (
              <p className="text-xs text-gray-400 mt-1">
                Para cadastrar funcionária(o) ou gerente, use Equipe → Funcionárias(os) & Gerentes —
                o cadastro completo com dados pessoais, endereço e remuneração.
              </p>
            )}
            {errors.roleId && (
              <span className="text-xs text-rose-500 font-semibold">{errors.roleId.message}</span>
            )}
          </div>
          <div>
            <label className={labelCls}>Telefone</label>
            <input
              type="text"
              maxLength={20}
              className={`input-premium ${errors.phone ? 'border-rose-300 focus:border-rose-500' : ''}`}
              {...register('phone')}
            />
            {errors.phone && (
              <span className="text-xs text-rose-500 font-semibold">{errors.phone.message}</span>
            )}
          </div>
          <div>
            <label className={labelCls}>
              {editingUser?.id ? 'Nova Senha (opcional)' : 'Senha *'}
            </label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                className={`input-premium pr-10 ${errors.password ? 'border-rose-300 focus:border-rose-500' : ''}`}
                placeholder={editingUser?.id ? 'Deixe em branco para manter' : 'Mínimo 8 caracteres com 1 número'}
                {...register('password')}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 focus:outline-none cursor-pointer flex items-center"
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {errors.password && (
              <span className="text-xs text-rose-500 font-semibold">
                {errors.password.message}
              </span>
            )}
          </div>
          <div>
            <label className={labelCls}>
              {editingUser?.id ? 'Confirmar Nova Senha (opcional)' : 'Confirmar Senha *'}
            </label>
            <div className="relative">
              <input
                type={showConfirmPassword ? 'text' : 'password'}
                className={`input-premium pr-10 ${errors.confirmPassword ? 'border-rose-300 focus:border-rose-500' : ''}`}
                placeholder="Confirme a senha"
                {...register('confirmPassword')}
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 focus:outline-none cursor-pointer flex items-center"
              >
                {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {errors.confirmPassword && (
              <span className="text-xs text-rose-500 font-semibold">
                {errors.confirmPassword.message}
              </span>
            )}
          </div>
          <div className="flex items-center gap-3">
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" className="sr-only peer" {...register('active')} />
              <div className="w-10 h-5 bg-gray-200 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-[#be8a83]"></div>
            </label>
            <span className="text-sm font-semibold text-[#3b3036] dark:text-gray-200">
              Conta Ativa
            </span>
          </div>
        </div>
      </ModalForm>

      <ConfirmDialog
        show={showConfirm}
        onHide={() => setShowConfirm(false)}
        onConfirm={handleConfirmAction}
        title={confirmAction === 'delete' ? 'Desativar Conta' : 'Reativar Conta'}
        message={
          confirmAction === 'delete'
            ? 'Tem certeza que deseja desativar esta conta da equipe? O acesso será bloqueado.'
            : 'Tem certeza que deseja reativar esta conta da equipe? O acesso será restaurado.'
        }
        confirmLabel={confirmAction === 'delete' ? 'Desativar' : 'Reativar'}
        variant={confirmAction === 'delete' ? 'danger' : 'primary'}
      />
    </div>
  );
};
