import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender } from '../../../test/test-utils';
import { MyAppointments } from '../MyAppointments';
import { appointmentsApi } from '../services/appointments';

vi.mock('../services/appointments', () => ({
  appointmentsApi: {
    getMyAppointments: vi.fn(),
    cancel: vi.fn(),
    generatePix: vi.fn(),
    findById: vi.fn(),
  },
}));


vi.mock('../../admin/users/services/users', () => ({
  usersApi: {
    getMyCpfInfo: vi.fn().mockResolvedValue({
      hasSavedCpf: true,
      cpfMasked: '***.***.123-45',
    }),
    updateMyCpf: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock('../../../hooks/useAlert', () => ({
  useAlert: () => ({
    error: vi.fn(),
    success: vi.fn(),
    alert: vi.fn(),
    confirm: vi.fn(),
  }),
}));

const mockUseFeatureFlag = vi.fn(() => ({ enabled: true, isLoading: false }));
vi.mock('../../../hooks/useFeatureFlag', () => ({
  useFeatureFlag: () => mockUseFeatureFlag(),
}));

function buildServiceItem(serviceId: number, serviceName: string) {
  return {
    serviceId,
    serviceName,
    catalogPrice: null,
    catalogDurationMin: null,
    customPrice: null,
    customDurationMin: null,
    customServiceNotes: null,
    effectivePrice: null,
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
    services: [buildServiceItem(1, 'Corte de Cabelo')],
    totalPrice: null,
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
    clientId: 5,
    clientName: 'Elksandro',
    employeeId: 10,
    employeeName: 'Mariana',
    services: [buildServiceItem(2, 'Manicure')],
    totalPrice: null,
    totalDurationMin: null,
    scheduledAt: '2026-06-26T10:00:00Z',
    status: 'CONFIRMED',
    paymentStatus: 'PENDING',
    pixQrCode: 'pix-copia-e-cola-code-mock-2',
    clientHasSavedCpf: true,
    clientCpfMasked: '***.***.123-45',
  },
  {
    id: 3,
    clientId: 5,
    clientName: 'Elksandro',
    employeeId: 10,
    employeeName: 'Mariana',
    services: [buildServiceItem(1, 'Escova')],
    totalPrice: null,
    totalDurationMin: null,
    scheduledAt: '2026-06-27T16:00:00Z',
    status: 'PENDING',
    paymentStatus: null,
    pixQrCode: null,
    clientHasSavedCpf: true,
    clientCpfMasked: '***.***.123-45',
  },
];

const renderMyAppointments = () => {
  return customRender(<MyAppointments />, {
    user: {
      email: 'client@salao.com',
      role: 'CLIENTE',
      userId: 5,
      permissions: [],
      cpf: '12345678909',
    },
    isAuthenticated: true,
  });
};

describe('MyAppointments Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseFeatureFlag.mockReturnValue({ enabled: true, isLoading: false });
    vi.mocked(appointmentsApi.getMyAppointments).mockResolvedValue(mockAppointments);
    vi.mocked(appointmentsApi.findById).mockResolvedValue({ id: 1, paymentStatus: 'PENDING' } as any);
    vi.mocked(appointmentsApi.generatePix).mockResolvedValue({
      id: 1,
      clientId: 5,
      clientName: 'Elksandro',
      employeeId: 10,
      employeeName: 'Mariana',
      services: [buildServiceItem(1, 'Corte de Cabelo')],
      totalPrice: null,
      totalDurationMin: null,
      scheduledAt: '2026-06-25T14:00:00Z',
      status: 'CONFIRMED',
      paymentStatus: 'PENDING',
      pixQrCode: 'pix-generated-qr-code-1',
    } as any);
  });

  it('renders loading indicator then lists appointments with proper badges', async () => {
    await act(async () => {
      renderMyAppointments();
    });

    expect(screen.getByText('Minha Agenda')).toBeInTheDocument();
    expect(screen.getByText('Corte de Cabelo')).toBeInTheDocument();
    expect(screen.getByText('Manicure')).toBeInTheDocument();
    expect(screen.getByText('Escova')).toBeInTheDocument();

    // Confirm status badges are present
    expect(screen.getAllByText('Confirmado')).toHaveLength(2);
    expect(screen.getByText('Pendente')).toBeInTheDocument();

    // Confirm payment badges
    expect(screen.getByText('Pagamento Pendente')).toBeInTheDocument();
    expect(screen.getByText('PIX gerado (Aguardando)')).toBeInTheDocument();
  });

  it('triggers generatePix when clicking Pagar com PIX on unpaid confirmed appointment without pixQrCode', async () => {
    await act(async () => {
      renderMyAppointments();
    });

    const payBtns = screen.getAllByRole('button', { name: 'Pagar com PIX' });
    const payBtn = payBtns[1]; // Corte de Cabelo is scheduled at 2026-06-25, so it is sorted after Escova (2026-06-27)
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
    
    // Check if the PIX Payment Modal opens with the generated code
    expect(screen.getByText('Pagamento via PIX')).toBeInTheDocument();
    expect(screen.getAllByText('Corte de Cabelo')).toHaveLength(2);
  });

  it('hides Pagar com PIX when the ENABLE_MERCADO_PAGO feature flag is disabled', async () => {
    mockUseFeatureFlag.mockReturnValue({ enabled: false, isLoading: false });

    await act(async () => {
      renderMyAppointments();
    });

    expect(screen.queryByRole('button', { name: 'Pagar com PIX' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ver QR Code PIX' })).not.toBeInTheDocument();
  });

  it('opens modal immediately without generating new PIX if pixQrCode is already present', async () => {
    await act(async () => {
      renderMyAppointments();
    });

    const viewPixBtn = screen.getByRole('button', { name: 'Ver QR Code PIX' });
    expect(viewPixBtn).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(viewPixBtn);
    });

    expect(appointmentsApi.generatePix).not.toHaveBeenCalled();
    expect(screen.getByText('Pagamento via PIX')).toBeInTheDocument();
    expect(screen.getAllByText('Manicure')).toHaveLength(2);
  });

  it('opens cancellation confirmation modal and cancels appointment when confirmed', async () => {
    vi.mocked(appointmentsApi.cancel).mockResolvedValue({} as any);

    await act(async () => {
      renderMyAppointments();
    });

    const cancelButtons = screen.getAllByRole('button', { name: 'Cancelar Agendamento' });
    // Click cancellation button for first appointment (ID 3 due to descending date sort)
    fireEvent.click(cancelButtons[0]);

    expect(screen.getByText('Cancelar Horário')).toBeInTheDocument();
    
    const confirmBtn = screen.getByRole('button', { name: 'Confirmar' });
    await act(async () => {
      fireEvent.click(confirmBtn);
    });

    expect(appointmentsApi.cancel).toHaveBeenCalledWith(3);
  });
});
