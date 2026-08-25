import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender } from '../../../../test/test-utils';
import { AdminAppointments } from '../AdminAppointments';
import { appointmentsApi } from '../../../appointments/services/appointments';
import { salonServicesApi } from '../../../services/services/services';
import { employeesApi } from '../../employees/services/employees';
import { clientsApi } from '../../clients/services/clients';

vi.mock('../../../../hooks/usePermission', () => ({
  usePermission: () => true,
}));

vi.mock('../../../appointments/services/appointments', () => ({
  appointmentsApi: {
    findAll: vi.fn(),
    create: vi.fn(),
    confirm: vi.fn(),
    decline: vi.fn(),
    cancel: vi.fn(),
    updateStatus: vi.fn(),
    updatePaymentStatus: vi.fn(),
    generatePix: vi.fn(),
    findById: vi.fn(),
  },
}));

vi.mock('../../../services/services/services', () => ({
  salonServicesApi: {
    findAll: vi.fn(),
  },
}));

vi.mock('../../products/services/products', () => ({
  productsApi: {
    findAll: vi.fn().mockResolvedValue({ content: [], totalPages: 1, totalElements: 0 }),
  },
}));

vi.mock('../../employees/services/employees', () => ({
  employeesApi: {
    findAllForBooking: vi.fn(),
  },
}));

vi.mock('../../users/services/users', () => ({
  usersApi: {
    findAll: vi.fn(),
    getMyCpfInfo: vi.fn().mockResolvedValue({
      hasSavedCpf: true,
      cpfMasked: '***.***.123-45',
    }),
    updateMyCpf: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock('../../clients/services/clients', () => ({
  clientsApi: {
    findAll: vi.fn(),
    findById: vi.fn(),
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

const mockUseFeatureFlag = vi.fn(() => ({ enabled: true, isLoading: false }));
vi.mock('../../../../hooks/useFeatureFlag', () => ({
  useFeatureFlag: () => mockUseFeatureFlag(),
}));

function buildServiceItem(serviceId: number, serviceName: string, price: number) {
  return {
    serviceId,
    serviceName,
    catalogPrice: price,
    catalogDurationMin: null,
    customPrice: null,
    customDurationMin: null,
    customServiceNotes: null,
    effectivePrice: price,
    effectiveDurationMin: null,
  };
}

const mockAppointments = [
  {
    id: 1,
    clientId: 5,
    clientName: 'Elksandro',
    employeeId: 10,
    employeeName: 'Mariana',
    services: [buildServiceItem(100, 'Corte de Cabelo', 85.0)],
    totalPrice: 85.0,
    totalDurationMin: null,
    scheduledAt: '2026-06-25T14:00:00Z',
    status: 'CONFIRMED',
    paymentStatus: 'PENDING',
    pixQrCode: null,
    clientHasSavedCpf: true,
    clientCpfMasked: '***.***.123-45',
  },
  {
    id: 2,
    clientId: 6,
    clientName: 'Joao',
    employeeId: 10,
    employeeName: 'Mariana',
    services: [buildServiceItem(101, 'Manicure', 40.0)],
    totalPrice: 40.0,
    totalDurationMin: null,
    scheduledAt: null,
    preferredDate: '2026-06-26',
    status: 'REQUESTED',
    paymentStatus: null,
    pixQrCode: null,
    clientHasSavedCpf: true,
    clientCpfMasked: '***.***.123-45',
  },
];

const mockServices = [
  { id: 100, name: 'Corte de Cabelo', price: 85.0, active: true, description: '' },
  { id: 101, name: 'Manicure', price: 40.0, active: true, description: '' },
];

const mockEmployees = [
  { id: 10, userId: 100, name: 'Mariana', active: true },
];

const mockUsers = [
  { id: 5, name: 'Elksandro', role: 'CLIENTE', email: 'elksandro@salao.com', phone: '999999999', active: true, createdAt: '2026-06-16T15:00:00Z' },
  { id: 6, name: 'Joao', role: 'CLIENTE', email: 'joao@salao.com', phone: '999999999', active: true, createdAt: '2026-06-16T15:00:00Z' },
];

const renderAdminAppointments = () => {
  return customRender(<AdminAppointments />, {
    user: {
      email: 'admin@salao.com',
      role: 'ADMIN',
      userId: 1,
      permissions: [],
      cpf: '12345678909',
    },
    isAuthenticated: true,
  });
};

describe('AdminAppointments Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseFeatureFlag.mockReturnValue({ enabled: true, isLoading: false });
    vi.mocked(appointmentsApi.findAll).mockResolvedValue({
      content: mockAppointments,
      totalPages: 1,
      totalElements: mockAppointments.length,
      size: 1000,
      number: 0,
    } as any);
    vi.mocked(salonServicesApi.findAll).mockResolvedValue({
      content: mockServices,
      totalPages: 1,
      totalElements: mockServices.length,
      size: 1000,
      number: 0,
    } as any);
    vi.mocked(employeesApi.findAllForBooking).mockResolvedValue(mockEmployees);
    vi.mocked(clientsApi.findAll).mockResolvedValue({
      content: mockUsers.filter((u) => u.role === 'CLIENTE'),
      totalPages: 1,
      totalElements: mockUsers.filter((u) => u.role === 'CLIENTE').length,
      size: 10,
      number: 0,
    });
    vi.mocked(appointmentsApi.findById).mockResolvedValue({ id: 1, paymentStatus: 'PENDING' } as any);
    
    vi.mocked(appointmentsApi.generatePix).mockResolvedValue({
      id: 1,
      clientId: 5,
      clientName: 'Elksandro',
      employeeId: 10,
      employeeName: 'Mariana',
      services: [buildServiceItem(100, 'Corte de Cabelo', 85.0)],
      totalPrice: 85.0,
      totalDurationMin: null,
      scheduledAt: '2026-06-25T14:00:00Z',
      status: 'CONFIRMED',
      paymentStatus: 'PENDING',
      pixQrCode: 'pix-generated-code-admin-1',
    } as any);
  });

  it('renders the appointments table and lists all entries', async () => {
    await act(async () => {
      renderAdminAppointments();
    });

    expect(screen.getByText('Agendamentos (Admin)')).toBeInTheDocument();
    expect(screen.getByText('Elksandro')).toBeInTheDocument();
    expect(screen.getByText('Joao')).toBeInTheDocument();
    expect(screen.getByText('Corte de Cabelo')).toBeInTheDocument();
    expect(screen.getByText('Manicure')).toBeInTheDocument();
  });

  it('triggers updateStatus when the appointment status select is changed', async () => {
    await act(async () => {
      renderAdminAppointments();
    });

    const selects = screen.getAllByRole('combobox');

    // Index 0-1 are the filter bar's Status/Profissional selects.
    // Row 0 is Joao (REQUESTED): renders paymentStatus select (index 2, value PENDING)
    // Row 1 is Elksandro (CONFIRMED): renders status select (index 3) and paymentStatus select (index 4)
    const appStatusSelect = selects[3];
    expect(appStatusSelect).toHaveValue('CONFIRMED');

    await act(async () => {
      fireEvent.change(appStatusSelect, { target: { value: 'DONE' } });
    });

    expect(appointmentsApi.updateStatus).toHaveBeenCalledWith(1, 'DONE');
  });

  it('triggers updatePaymentStatus with the chosen method when marking as paid manually', async () => {
    await act(async () => {
      renderAdminAppointments();
    });

    const selects = screen.getAllByRole('combobox');

    // Row 1 is Elksandro (CONFIRMED): paymentStatus select is index 4 (see comment above)
    const paymentStatusSelect = selects[4];
    expect(paymentStatusSelect).toHaveValue('PENDING');

    await act(async () => {
      fireEvent.change(paymentStatusSelect, { target: { value: 'MANUAL' } });
    });

    // Selecting "Pago Manualmente" opens the payment method chooser instead of saving right away
    expect(appointmentsApi.updatePaymentStatus).not.toHaveBeenCalled();

    const dinheiroBtn = screen.getByRole('button', { name: /Dinheiro/i });
    await act(async () => {
      fireEvent.click(dinheiroBtn);
    });

    expect(appointmentsApi.updatePaymentStatus).toHaveBeenCalledWith(1, 'MANUAL', 'DINHEIRO');
  });

  it('triggers generatePix when clicking Pagar com PIX', async () => {
    await act(async () => {
      renderAdminAppointments();
    });

    const payBtns = screen.getAllByRole('button', { name: 'Pagar com PIX' });
    const payBtn = payBtns[1]; // Elksandro (CONFIRMED) is sorted after Joao (REQUESTED)
    expect(payBtn).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(payBtn);
    });

    // Wait for the modal CPF/Identification step to load
    const generateBtn = await screen.findByRole('button', { name: 'Gerar PIX' });
    expect(generateBtn).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(generateBtn);
    });

    expect(appointmentsApi.generatePix).toHaveBeenCalledWith(1, { useSavedCpf: true });
    
    // Check if the modal opens
    expect(screen.getByText('Pagamento via PIX')).toBeInTheDocument();
    expect(screen.getAllByText('Corte de Cabelo')).toHaveLength(2); // One in table, one in modal
  });

  it('hides Pagar com PIX when the MERCADO_PAGO_ATIVO feature flag is disabled', async () => {
    mockUseFeatureFlag.mockReturnValue({ enabled: false, isLoading: false });

    await act(async () => {
      renderAdminAppointments();
    });

    expect(screen.queryByRole('button', { name: 'Pagar com PIX' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ver PIX' })).not.toBeInTheDocument();
  });

  it('opens confirmation modal and cancels appointment when cancel button is clicked', async () => {
    vi.mocked(appointmentsApi.cancel).mockResolvedValue({} as any);

    await act(async () => {
      renderAdminAppointments();
    });

    const cancelButtons = screen.getAllByRole('button', { name: 'Cancelar' });
    expect(cancelButtons.length).toBeGreaterThan(0);

    // cancelButtons[0] belongs to Row 0 (Joao, ID 2)
    fireEvent.click(cancelButtons[0]);

    // Check confirm modal
    expect(screen.getByText('Cancelar Agendamento')).toBeInTheDocument();
    
    const confirmBtn = screen.getByRole('button', { name: 'Confirmar' });
    await act(async () => {
      fireEvent.click(confirmBtn);
    });

    expect(appointmentsApi.cancel).toHaveBeenCalledWith(2);
  });

  it('allows defining date and time to confirm a requested appointment', async () => {
    vi.mocked(appointmentsApi.confirm).mockResolvedValue({} as any);

    await act(async () => {
      renderAdminAppointments();
    });

    const defineTimeBtn = screen.getByRole('button', { name: 'Definir horário' });
    expect(defineTimeBtn).toBeInTheDocument();

    fireEvent.click(defineTimeBtn);

    // Confirm Modal for Date/Time should open
    expect(screen.getByText('Confirmar horário')).toBeInTheDocument();

    const dateTimeInput = screen.getByLabelText('Data e hora');
    fireEvent.change(dateTimeInput, { target: { value: '2026-06-26T10:00' } });

    const submitBtn = screen.getByRole('button', { name: 'Confirmar solicitação' });
    await act(async () => {
      fireEvent.click(submitBtn);
    });

    expect(appointmentsApi.confirm).toHaveBeenCalledWith(2, '2026-06-26T10:00:00');
  });

  it('creates an appointment with multiple selected services', async () => {
    vi.mocked(appointmentsApi.create).mockResolvedValue({} as any);

    await act(async () => {
      renderAdminAppointments();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Agendamento/i }));

    const clientSelect = screen.getByText('Selecione o cliente').closest('select')!;
    fireEvent.change(clientSelect, { target: { value: '5' } });

    fireEvent.click(screen.getByLabelText(/Corte de Cabelo/i));
    fireEvent.click(screen.getByLabelText(/Manicure/i));

    const employeeSelect = screen.getByText('Selecione a profissional').closest('select')!;
    fireEvent.change(employeeSelect, { target: { value: '10' } });

    const dateTimeInput = screen.getByLabelText(/Data e hora/i);
    fireEvent.change(dateTimeInput, { target: { value: '2026-07-01T09:00' } });

    const submitBtn = screen.getByRole('button', { name: 'Criar Agendamento' });
    await act(async () => {
      fireEvent.click(submitBtn);
    });

    expect(appointmentsApi.create).toHaveBeenCalledWith(
      expect.objectContaining({
        clientId: 5,
        employeeId: 10,
        scheduledAt: '2026-07-01T09:00:00',
        services: expect.arrayContaining([
          expect.objectContaining({ serviceId: 100 }),
          expect.objectContaining({ serviceId: 101 }),
        ]),
      })
    );
    const call = vi.mocked(appointmentsApi.create).mock.calls[0][0];
    expect(call.services).toHaveLength(2);
  });

  it('shows a search box to filter services when there are more than 6, and filters the list', async () => {
    const manyServices = Array.from({ length: 7 }, (_, i) => ({
      id: 200 + i,
      name: `Serviço ${i}`,
      price: 10 + i,
      active: true,
      description: '',
    }));
    vi.mocked(salonServicesApi.findAll).mockResolvedValue({
      content: manyServices,
      totalPages: 1,
      totalElements: manyServices.length,
      size: 1000,
      number: 0,
    } as any);

    await act(async () => {
      renderAdminAppointments();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Agendamento/i }));

    const searchInput = screen.getByPlaceholderText('Buscar serviço por nome...');
    expect(searchInput).toBeInTheDocument();
    expect(screen.getAllByRole('checkbox')).toHaveLength(7);

    fireEvent.change(searchInput, { target: { value: 'Serviço 3' } });

    const checkboxes = screen.getAllByRole('checkbox');
    expect(checkboxes).toHaveLength(1);
    expect(screen.getByLabelText(/Serviço 3/i)).toBeInTheDocument();
  });

  it('shows the search box even with 6 or fewer services', async () => {
    await act(async () => {
      renderAdminAppointments();
    });

    fireEvent.click(screen.getByRole('button', { name: /Novo Agendamento/i }));

    expect(screen.getByPlaceholderText('Buscar serviço por nome...')).toBeInTheDocument();
  });
});
