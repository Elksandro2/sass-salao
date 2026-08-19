import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender, waitFor } from '../../../../test/test-utils';
import { AdminServices } from '../AdminServices';
import { salonServicesApi } from '../../../services/services/services';

vi.mock('../../../services/services/services', () => ({
  salonServicesApi: {
    findAll: vi.fn(),
    delete: vi.fn(),
    reactivate: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock('../../../../hooks/usePermission', () => ({
  usePermission: () => true,
}));

vi.mock('../../../../hooks/useAlert', () => ({
  useAlert: () => ({
    error: vi.fn(),
    success: vi.fn(),
    alert: vi.fn(),
    confirm: vi.fn(),
  }),
}));

const mockServices = [
  { id: 1, name: 'Corte de Cabelo', description: 'Corte tesoura', price: 50.0, active: true },
  { id: 2, name: 'Pintura', description: 'Tintura premium', price: 100.0, active: false },
];

const mockPage = (content: typeof mockServices) => ({
  content,
  totalPages: 1,
  totalElements: content.length,
  size: 10,
  number: 0,
});

describe('AdminServices Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(salonServicesApi.findAll).mockResolvedValue(mockPage(mockServices));
  });

  it('renders services and handles filter changes', async () => {
    await act(async () => {
      customRender(<AdminServices />);
    });

    expect(screen.getByText('Gerenciar Serviços')).toBeInTheDocument();
    expect(screen.getByText('Corte de Cabelo')).toBeInTheDocument();
    expect(screen.getByText('Pintura')).toBeInTheDocument();

    const select = screen.getByRole('combobox');
    await act(async () => {
      fireEvent.change(select, { target: { value: 'true' } });
    });
    expect(salonServicesApi.findAll).toHaveBeenCalledWith(
      expect.objectContaining({ active: true }),
      0,
      10
    );

    await act(async () => {
      fireEvent.change(select, { target: { value: 'false' } });
    });
    expect(salonServicesApi.findAll).toHaveBeenCalledWith(
      expect.objectContaining({ active: false }),
      0,
      10
    );
  });

  it('triggers reactivate flow when RotateCcw button is clicked and confirmed', async () => {
    vi.mocked(salonServicesApi.reactivate).mockResolvedValue({
      id: 2,
      name: 'Pintura',
      description: 'Tintura premium',
      price: 100.0,
      active: true,
    });

    await act(async () => {
      customRender(<AdminServices />);
    });

    const reactivateButtons = screen.getAllByTitle('Reativar Serviço');
    expect(reactivateButtons).toHaveLength(1);

    await act(async () => {
      fireEvent.click(reactivateButtons[0]);
    });

    expect(screen.getByText('Reativar Serviço')).toBeInTheDocument();
    expect(
      screen.getByText('Tem certeza que deseja reativar este serviço? Ele aparecerá novamente nas listagens públicas.')
    ).toBeInTheDocument();

    const confirmButton = screen.getAllByRole('button', { name: 'Reativar' })[1];
    await act(async () => {
      fireEvent.click(confirmButton);
    });

    expect(salonServicesApi.reactivate).toHaveBeenCalledWith(2);
    await waitFor(() => {
      expect(salonServicesApi.findAll).toHaveBeenCalledTimes(2);
    });
  });

  it('triggers delete flow when delete button is clicked and confirmed', async () => {
    vi.mocked(salonServicesApi.delete).mockResolvedValue(undefined);

    await act(async () => {
      customRender(<AdminServices />);
    });

    const deleteButtons = screen.getAllByTitle('Excluir Serviço');
    expect(deleteButtons).toHaveLength(1);

    await act(async () => {
      fireEvent.click(deleteButtons[0]);
    });

    expect(screen.getByText('Excluir Serviço')).toBeInTheDocument();
    expect(
      screen.getByText('Tem certeza que deseja excluir este serviço? Esta ação não pode ser desfeita.')
    ).toBeInTheDocument();

    const confirmButton = screen.getByRole('button', { name: 'Excluir' });
    await act(async () => {
      fireEvent.click(confirmButton);
    });

    expect(salonServicesApi.delete).toHaveBeenCalledWith(1);
    await waitFor(() => {
      expect(salonServicesApi.findAll).toHaveBeenCalledTimes(2);
    });
  });
});
