import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, waitFor, customRender } from '../../../../test/test-utils';
import { StaffRegistration } from '../StaffRegistration';
import { staffApi } from '../services/staff';

vi.mock('../../../../hooks/usePermission', () => ({
  usePermission: () => true,
}));

vi.mock('../services/staff', () => ({
  staffApi: {
    create: vi.fn(),
    findAll: vi.fn(),
    findById: vi.fn(),
    generatePixQrCode: vi.fn(),
  },
}));

vi.mock('../../../../hooks/useAlert', () => ({
  useAlert: () => ({
    error: vi.fn(),
    success: vi.fn(),
    alert: vi.fn(),
    confirm: vi.fn().mockResolvedValue(true),
  }),
}));

const mockStaffList = [
  {
    id: 1,
    userId: 10,
    name: 'Maria',
    email: 'maria@example.com',
    roleName: 'FUNCIONARIA',
    active: true,
    fullName: 'Maria Silva',
    displayName: 'Maria Silva',
    cpfMasked: '***.***.777-35',
    birthDate: '1990-01-01',
    phone: '81999999999',
    zipCode: '50000-000',
    street: 'Rua A',
    streetNumber: '10',
    district: 'Boa Vista',
    city: 'Recife',
    stateUf: 'PE',
    hasPixKey: false,
    createdAt: '2026-01-01T10:00:00',
  },
];

const renderPage = () =>
  customRender(<StaffRegistration />, {
    user: { email: 'admin@salao.com', role: 'ADMIN', userId: 1, permissions: [] },
    isAuthenticated: true,
  });

describe('StaffRegistration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(staffApi.findAll).mockResolvedValue({
      content: mockStaffList,
      totalPages: 1,
      totalElements: mockStaffList.length,
      size: 20,
      number: 0,
    } as any);
  });

  it('renders the staff list', async () => {
    await act(async () => {
      renderPage();
    });

    expect(screen.getByText('Cadastro de Equipe')).toBeInTheDocument();
    expect(await screen.findByText('Maria Silva')).toBeInTheDocument();
  });

  it('opens the modal on step 1 (role selection) when clicking "Novo Cadastro"', async () => {
    await act(async () => {
      renderPage();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Cadastro/i }));

    expect(screen.getByText('Novo cadastro de equipe — escolha o papel')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Colaboradora/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Gerente de Atendimento/ })).toBeInTheDocument();
  });

  it('advances to step 2 with the full form after choosing FUNCIONARIA, showing remuneration fields', async () => {
    await act(async () => {
      renderPage();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Cadastro/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Colaboradora/ }));

    expect(screen.getByText('Novo cadastro — Colaboradora')).toBeInTheDocument();
    expect(screen.getByText('Remuneração')).toBeInTheDocument();
  });

  it('advances to step 2 after choosing GERENTE_DE_ATENDIMENTO, showing only Salário Fixo', async () => {
    await act(async () => {
      renderPage();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Cadastro/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Gerente de Atendimento/ }));

    expect(screen.getByText('Novo cadastro — Gerente de Atendimento')).toBeInTheDocument();
    expect(screen.getByText('Remuneração')).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Comissionado' })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Fixo + comissão' })).not.toBeInTheDocument();
    // Gerente não é FUNCIONARIA, então o campo de Bio (específico de quem atende cliente) some.
    expect(screen.queryByLabelText('Bio (opcional)')).not.toBeInTheDocument();
  });

  it('lets the user go back to step 1 to change the role', async () => {
    await act(async () => {
      renderPage();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Cadastro/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Colaboradora/ }));
    fireEvent.click(screen.getByRole('button', { name: /Trocar papel/i }));

    expect(screen.getByText('Novo cadastro de equipe — escolha o papel')).toBeInTheDocument();
  });

  it('submits a valid FUNCIONARIA form and calls staffApi.create with the expected payload', async () => {
    vi.mocked(staffApi.create).mockResolvedValue(mockStaffList[0] as any);

    await act(async () => {
      renderPage();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Cadastro/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Colaboradora/ }));

    fireEvent.change(screen.getByLabelText('Nome de exibição'), { target: { value: 'Maria' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'maria@example.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha@123' } });
    fireEvent.change(screen.getByLabelText('Confirmar senha'), { target: { value: 'Senha@123' } });
    fireEvent.change(screen.getByLabelText('Nome completo'), { target: { value: 'Maria Silva' } });
    fireEvent.change(screen.getByLabelText('CPF'), { target: { value: '111.444.777-35' } });
    fireEvent.change(screen.getByLabelText('Data de nascimento'), { target: { value: '1990-01-01' } });
    fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '(81) 99999-9999' } });
    fireEvent.change(screen.getByLabelText('CEP'), { target: { value: '50000-000' } });
    fireEvent.change(screen.getByLabelText('Logradouro'), { target: { value: 'Rua A' } });
    fireEvent.change(screen.getByLabelText('Número'), { target: { value: '10' } });
    fireEvent.change(screen.getByLabelText('Bairro'), { target: { value: 'Boa Vista' } });
    fireEvent.change(screen.getByLabelText('Cidade'), { target: { value: 'Recife' } });
    fireEvent.change(screen.getByLabelText('UF'), { target: { value: 'PE' } });
    fireEvent.change(screen.getByLabelText('Tipo de remuneração'), { target: { value: 'SALARIO_FIXO' } });
    fireEvent.change(screen.getByLabelText('Valor do salário fixo (R$)'), { target: { value: '2000' } });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Criar Cadastro/i }));
    });

    await waitFor(() => expect(staffApi.create).toHaveBeenCalled());

    const payload = vi.mocked(staffApi.create).mock.calls[0][0];
    expect(payload).toMatchObject({
      name: 'Maria',
      email: 'maria@example.com',
      roleName: 'FUNCIONARIA',
      fullName: 'Maria Silva',
      cpf: '111.444.777-35',
      remunerationType: 'SALARIO_FIXO',
      remunerationValue: 2000,
    });
    // Nenhuma chave PIX foi informada: os campos de PIX devem ir nulos, não omitidos ou vazios.
    expect(payload.pixKeyType).toBeNull();
    expect(payload.pixKey).toBeNull();
  });

  it('submits a valid GERENTE_DE_ATENDIMENTO form with Salário Fixo and no commission', async () => {
    vi.mocked(staffApi.create).mockResolvedValue(mockStaffList[0] as any);

    await act(async () => {
      renderPage();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Cadastro/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Gerente de Atendimento/ }));

    fireEvent.change(screen.getByLabelText('Nome de exibição'), { target: { value: 'Ana' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ana@example.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha@123' } });
    fireEvent.change(screen.getByLabelText('Confirmar senha'), { target: { value: 'Senha@123' } });
    fireEvent.change(screen.getByLabelText('Nome completo'), { target: { value: 'Ana Souza' } });
    fireEvent.change(screen.getByLabelText('CPF'), { target: { value: '111.444.777-35' } });
    fireEvent.change(screen.getByLabelText('Data de nascimento'), { target: { value: '1988-01-01' } });
    fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '(81) 99999-9999' } });
    fireEvent.change(screen.getByLabelText('CEP'), { target: { value: '50000-000' } });
    fireEvent.change(screen.getByLabelText('Logradouro'), { target: { value: 'Rua A' } });
    fireEvent.change(screen.getByLabelText('Número'), { target: { value: '10' } });
    fireEvent.change(screen.getByLabelText('Bairro'), { target: { value: 'Boa Vista' } });
    fireEvent.change(screen.getByLabelText('Cidade'), { target: { value: 'Recife' } });
    fireEvent.change(screen.getByLabelText('UF'), { target: { value: 'PE' } });
    fireEvent.change(screen.getByLabelText('Tipo de remuneração'), { target: { value: 'SALARIO_FIXO' } });
    fireEvent.change(screen.getByLabelText('Valor do salário fixo (R$)'), { target: { value: '3000' } });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Criar Cadastro/i }));
    });

    await waitFor(() => expect(staffApi.create).toHaveBeenCalled());

    const payload = vi.mocked(staffApi.create).mock.calls[0][0];
    expect(payload).toMatchObject({
      roleName: 'GERENTE_DE_ATENDIMENTO',
      remunerationType: 'SALARIO_FIXO',
      remunerationValue: 3000,
    });
  });

  it('does not submit when the CPF is invalid', async () => {
    await act(async () => {
      renderPage();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Cadastro/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Gerente de Atendimento/ }));

    fireEvent.change(screen.getByLabelText('CPF'), { target: { value: '111.111.111-11' } });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Criar Cadastro/i }));
    });

    expect(staffApi.create).not.toHaveBeenCalled();
    expect(await screen.findByText('CPF inválido')).toBeInTheDocument();
  });
});
