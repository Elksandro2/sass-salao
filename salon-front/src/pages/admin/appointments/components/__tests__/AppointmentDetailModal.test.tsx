import { describe, it, expect, vi } from 'vitest';
import { screen, fireEvent, customRender as render } from '../../../../../test/test-utils';
import { AppointmentDetailModal } from '../AppointmentDetailModal';
import type { AppointmentResponse } from '../../../../appointments/services/appointments';

vi.mock('../../../../../hooks/useAlert', () => ({
  useAlert: () => ({
    error: vi.fn(),
    success: vi.fn(),
    alert: vi.fn(),
    confirm: vi.fn().mockResolvedValue(true),
  }),
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
      catalogDurationMin: 60,
      customPrice: null,
      customDurationMin: null,
      customServiceNotes: null,
      effectivePrice: 150,
      effectiveDurationMin: 60,
    },
  ],
  totalPrice: 150,
  totalDurationMin: 60,
  scheduledAt: '2026-08-01T10:00:00',
  status: 'CONFIRMED',
};

describe('AppointmentDetailModal', () => {
  it('renders nothing when there is no appointment', () => {
    const { container } = render(<AppointmentDetailModal appointment={null} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the effective price/duration without a catalog comparison when there is no customization', () => {
    render(<AppointmentDetailModal appointment={baseAppointment} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.getByText('Maria')).toBeInTheDocument();
    expect(screen.getByText('Coloração')).toBeInTheDocument();
    expect(screen.getByText('R$ 150.00')).toBeInTheDocument();
    expect(screen.getByText('60 min')).toBeInTheDocument();
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
          catalogDurationMin: 60,
          customPrice: 200,
          customDurationMin: 90,
          customServiceNotes: 'Cabelo mais longo',
          effectivePrice: 200,
          effectiveDurationMin: 90,
        },
      ],
      totalPrice: 200,
      totalDurationMin: 90,
    };

    render(<AppointmentDetailModal appointment={customized} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.getByText('Catálogo: R$ 150.00')).toBeInTheDocument();
    expect(screen.getByText('Catálogo: 60 min')).toBeInTheDocument();
    expect(screen.getByText('R$ 200.00')).toBeInTheDocument();
    expect(screen.getByText('90 min')).toBeInTheDocument();
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
          catalogDurationMin: 30,
          customPrice: null,
          customDurationMin: null,
          customServiceNotes: null,
          effectivePrice: 50,
          effectiveDurationMin: 30,
        },
      ],
      totalPrice: 200,
      totalDurationMin: 90,
    };

    render(<AppointmentDetailModal appointment={multi} onClose={vi.fn()} />, { user: adminUser, isAuthenticated: true });

    expect(screen.getByText('Total')).toBeInTheDocument();
    expect(screen.getByText('R$ 200.00')).toBeInTheDocument();
    expect(screen.getByText('90 min')).toBeInTheDocument();
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
});
