import { describe, it, expect, vi } from 'vitest';
import { screen, fireEvent, act, customRender as render } from '../../../../../test/test-utils';
import { AppointmentDetailModal } from '../AppointmentDetailModal';
import type { AppointmentResponse } from '../../../../appointments/services/appointments';
import { appointmentsApi } from '../../../../appointments/services/appointments';
import { employeesApi } from '../../../employees/services/employees';

vi.mock('../../../../../hooks/useAlert', () => ({
  useAlert: () => ({
    error: vi.fn(),
    success: vi.fn(),
    alert: vi.fn(),
    confirm: vi.fn().mockResolvedValue(true),
  }),
}));

vi.mock('../../../../appointments/services/appointments', async () => {
  const actual = await vi.importActual<typeof import('../../../../appointments/services/appointments')>(
    '../../../../appointments/services/appointments'
  );
  return {
    ...actual,
    appointmentsApi: {
      ...actual.appointmentsApi,
      updateInternalNotes: vi.fn().mockResolvedValue({}),
      updateDetails: vi.fn().mockResolvedValue({}),
    },
  };
});

vi.mock('../../../employees/services/employees', () => ({
  employeesApi: {
    findAllForBooking: vi.fn().mockResolvedValue([{ id: 1, userId: 1, name: 'Ana', active: true }]),
  },
}));

const adminUser = { email: 'admin@salao.com', role: 'ADMIN', userId: 1, permissions: [] };

const baseAppointment: AppointmentResponse = {
  id: 1,
  clientId: 1,
  clientName: 'Maria',
  employeeId: 1,
  employeeName: 'Ana',
  services: [
    {
      serviceId: 1,
      serviceName: 'Coloração',
      catalogPrice: 150,
      customPrice: null,
      customServiceNotes: null,
      effectivePrice: 150,
    },
  ],
  totalPrice: 150,
  scheduledAt: '2026-08-01T10:00:00',
  status: 'CONFIRMED',
};

describe('AppointmentDetailModal', () => {
  it('renders nothing when there is no appointment', () => {
    const { container } = render(<AppointmentDetailModal appointment={null} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the effective price without a catalog comparison when there is no customization', () => {
    render(<AppointmentDetailModal appointment={baseAppointment} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.getByText('Maria')).toBeInTheDocument();
    expect(screen.getByText('Coloração')).toBeInTheDocument();
    expect(screen.getByText('R$ 150.00')).toBeInTheDocument();
    expect(screen.queryByText(/Catálogo:/)).not.toBeInTheDocument();
  });

  it('shows both the catalog value and the custom effective value when customized', () => {
    const customized: AppointmentResponse = {
      ...baseAppointment,
      services: [
        {
          serviceId: 1,
          serviceName: 'Coloração',
          catalogPrice: 150,
          customPrice: 200,
          customServiceNotes: 'Cabelo mais longo',
          effectivePrice: 200,
        },
      ],
      totalPrice: 200,
    };

    render(<AppointmentDetailModal appointment={customized} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.getByText('Catálogo: R$ 150.00')).toBeInTheDocument();
    expect(screen.getByText('R$ 200.00')).toBeInTheDocument();
    expect(screen.getByText('Cabelo mais longo')).toBeInTheDocument();
  });

  it('shows a total row when there is more than one service', () => {
    const multi: AppointmentResponse = {
      ...baseAppointment,
      services: [
        ...baseAppointment.services,
        {
          serviceId: 2,
          serviceName: 'Corte',
          catalogPrice: 50,
          customPrice: null,
          customServiceNotes: null,
          effectivePrice: 50,
        },
      ],
      totalPrice: 200,
    };

    render(<AppointmentDetailModal appointment={multi} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.getByText('Total')).toBeInTheDocument();
    expect(screen.getByText('R$ 200.00')).toBeInTheDocument();
  });

  it('mostra a data e hora do agendamento sem deslocar o fuso', () => {
    // O modal não exibia data nenhuma — justamente o dado que se quer conferir ao abrir os
    // detalhes. E é regressão do bug de fuso: 10h agendado tem que aparecer como 10h.
    render(<AppointmentDetailModal appointment={baseAppointment} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.getByText('Data e hora')).toBeInTheDocument();
    expect(screen.getByText(/^01\/08\/2026,? 10:00$/)).toBeInTheDocument();
    expect(screen.queryByText(/07:00/)).not.toBeInTheDocument();
  });

  it('mostra "A combinar" e a preferência do cliente enquanto o horário não foi definido', () => {
    const semHorario: AppointmentResponse = {
      ...baseAppointment,
      scheduledAt: null,
      preferredDate: '2026-08-03',
      status: 'REQUESTED',
    } as AppointmentResponse;

    render(<AppointmentDetailModal appointment={semHorario} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.getByText('A combinar')).toBeInTheDocument();
    expect(screen.getByText('Preferência do cliente')).toBeInTheDocument();
    // Data pura não pode perder um dia na conversão (seria 02/08 se lida como UTC).
    expect(screen.getByText('03/08/2026')).toBeInTheDocument();
  });

  it('calls onClose when the close button is clicked', () => {
    const handleClose = vi.fn();
    render(<AppointmentDetailModal appointment={baseAppointment} onClose={handleClose} />, { user: adminUser, isAuthenticated: true });

    fireEvent.click(screen.getByText('Fechar'));

    expect(handleClose).toHaveBeenCalled();
  });

  it('shows "Ver lucro deste atendimento" for an active appointment', () => {
    render(<AppointmentDetailModal appointment={baseAppointment} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.getByText('Ver lucro deste atendimento')).toBeInTheDocument();
  });

  it('hides "Ver lucro deste atendimento" for a cancelled appointment — it never generates real revenue', () => {
    const cancelled = { ...baseAppointment, status: 'CANCELLED' } as AppointmentResponse;
    render(<AppointmentDetailModal appointment={cancelled} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.queryByText('Ver lucro deste atendimento')).not.toBeInTheDocument();
  });

  it('hides "Ver lucro deste atendimento" for a declined request', () => {
    const declined = { ...baseAppointment, status: 'DECLINED' } as AppointmentResponse;
    render(<AppointmentDetailModal appointment={declined} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.queryByText('Ver lucro deste atendimento')).not.toBeInTheDocument();
  });

  describe('modo de edição', () => {
    it('abre em modo leitura — não expõe campos editáveis até clicar em "Editar"', async () => {
      await act(async () => {
        render(<AppointmentDetailModal appointment={baseAppointment} onClose={vi.fn()} />, {
          user: adminUser,
          isAuthenticated: true,
        });
      });

      expect(screen.getByText('Ana')).toBeInTheDocument();
      expect(screen.queryByRole('combobox', { name: 'Profissional' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Editar/i })).toBeInTheDocument();
    });

    it('clicar em "Editar" revela profissional e data/período, e salva via updateDetails', async () => {
      await act(async () => {
        render(<AppointmentDetailModal appointment={baseAppointment} onClose={vi.fn()} />, {
          user: adminUser,
          isAuthenticated: true,
        });
      });

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /Editar/i }));
      });

      expect(employeesApi.findAllForBooking).toHaveBeenCalled();
      const employeeSelect = screen.getByRole('combobox', { name: 'Profissional' });
      expect(employeeSelect).toHaveValue('1');

      const dateInput = screen.getByLabelText('Data');
      fireEvent.change(dateInput, { target: { value: '2026-09-25' } });
      fireEvent.click(screen.getByRole('button', { name: 'Manhã' }));

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Salvar dados do agendamento' }));
      });

      expect(appointmentsApi.updateDetails).toHaveBeenCalledWith(1, {
        employeeId: 1,
        scheduledDate: '2026-09-25',
        scheduledPeriod: 'MORNING',
      });
    });

    it('"Cancelar edição" some com os campos editáveis de novo', async () => {
      await act(async () => {
        render(<AppointmentDetailModal appointment={baseAppointment} onClose={vi.fn()} />, {
          user: adminUser,
          isAuthenticated: true,
        });
      });

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /Editar/i }));
      });
      expect(screen.getByRole('combobox', { name: 'Profissional' })).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /Cancelar edição/i }));
      expect(screen.queryByRole('combobox', { name: 'Profissional' })).not.toBeInTheDocument();
    });
  });
});
