import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, customRender } from '../../../../test/test-utils';
import type { UserContextData } from '../../../../types/auth';
import { AdminProfile } from '../AdminProfile';
import { profileApi } from '../../../profile/services/profile';
import { employeesApi } from '../../employees/services/employees';
import { employeeMercadoPagoApi } from '../../employees/services/mercadoPago';

vi.mock('../../../profile/services/profile', () => ({
  profileApi: { getProfileById: vi.fn(), updateProfile: vi.fn() },
}));

vi.mock('../../employees/services/employees', () => ({
  employeesApi: { getMyActing: vi.fn(), setMyActing: vi.fn() },
}));

vi.mock('../../employees/services/mercadoPago', () => ({
  employeeMercadoPagoApi: { statusMe: vi.fn(), connectMe: vi.fn(), disconnectMe: vi.fn() },
}));

vi.mock('../../../../hooks/useFeatureFlag', () => ({
  useFeatureFlag: () => ({ enabled: false }),
}));

const mockShowError = vi.fn();
const mockShowSuccess = vi.fn();
vi.mock('../../../../hooks/useAlert', () => ({
  useAlert: () => ({ error: mockShowError, success: mockShowSuccess }),
}));

const adminUser: UserContextData = {
  email: 'admin@salao.com',
  role: 'ADMIN',
  userId: 1,
  permissions: [],
};

const renderFor = (user: UserContextData) =>
  customRender(<AdminProfile />, { user, isAuthenticated: true });

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(profileApi.getProfileById).mockResolvedValue({
    id: 1,
    name: 'Cristiane',
    email: 'admin@salao.com',
    phone: '',
    cpf: null,
  } as never);
  vi.mocked(employeeMercadoPagoApi.statusMe).mockRejectedValue(new Error('no employee'));
});

describe('AdminProfile — atuar como profissional', () => {
  it('does not render the card for a non-admin user', async () => {
    vi.mocked(employeesApi.getMyActing).mockResolvedValue({
      hasProfile: false,
      acting: false,
      remunerationType: null,
      remunerationValue: null,
    });
    renderFor({ ...adminUser, role: 'FUNCIONARIA' });
    await screen.findByText('Meu Perfil');
    expect(screen.queryByText('Atuar como profissional')).not.toBeInTheDocument();
    expect(employeesApi.getMyActing).not.toHaveBeenCalled();
  });

  it('shows "Ativar atuação" when the admin is not acting yet and toggles it on', async () => {
    vi.mocked(employeesApi.getMyActing).mockResolvedValue({
      hasProfile: true,
      acting: false,
      remunerationType: null,
      remunerationValue: null,
    });
    vi.mocked(employeesApi.setMyActing).mockResolvedValue({
      hasProfile: true,
      acting: true,
      remunerationType: 'COMISSIONADO',
      remunerationValue: null,
    });

    renderFor(adminUser);

    const btn = await screen.findByRole('button', { name: /Ativar atuação/i });
    fireEvent.click(btn);

    await waitFor(() => expect(employeesApi.setMyActing).toHaveBeenCalledWith(true));
    expect(mockShowSuccess).toHaveBeenCalled();
  });

  it('shows the current remuneration and a "Desativar" action when already acting', async () => {
    vi.mocked(employeesApi.getMyActing).mockResolvedValue({
      hasProfile: true,
      acting: true,
      remunerationType: 'DIARIA_E_COMISSIONADO',
      remunerationValue: 100,
    });

    renderFor(adminUser);

    expect(await screen.findByText(/Remuneração atual: Diarista \+ comissão/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Desativar atuação/i })).toBeInTheDocument();
  });
});
