import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender, waitFor } from '../../../../test/test-utils';
import { Payroll } from '../Payroll';
import { payrollApi, type PayrollEmployeeDetail, type PayrollSummary } from '../services/payroll';
import { currentMonthRange } from '../payrollUtils';

vi.mock('../services/payroll', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/payroll')>();
  return {
    ...actual,
    payrollApi: {
      getSummary: vi.fn(),
      getEmployeeDetail: vi.fn(),
      createWorkDay: vi.fn(),
      createWorkDaysBulk: vi.fn(),
      updateWorkDay: vi.fn(),
      unmarkWorkDay: vi.fn(),
      createAdjustment: vi.fn(),
      updateAdjustment: vi.fn(),
      deleteAdjustment: vi.fn(),
      createPayment: vi.fn(),
      previewPayment: vi.fn(),
      listPayments: vi.fn(),
      updatePayment: vi.fn(),
      deletePayment: vi.fn(),
    },
  };
});

vi.mock('../../../appointments/services/appointments', () => ({
  appointmentsApi: { findById: vi.fn().mockResolvedValue({ id: 11, clientName: 'Cliente Teste' }) },
}));
// O modal real de agendamento já tem testes próprios; aqui só importa que a folha o abre.
vi.mock('../../appointments/components/AppointmentDetailModal', () => ({
  AppointmentDetailModal: ({ appointment }: { appointment: { id: number } | null }) =>
    appointment ? <div>modal-agendamento-{appointment.id}</div> : null,
}));

// Objeto estável: as telas usam as funções do alerta como dependência de useCallback.
const alertMock = { error: vi.fn(), success: vi.fn(), alert: vi.fn(), confirm: vi.fn() };
vi.mock('../../../../hooks/useAlert', () => ({ useAlert: () => alertMock }));

const { from, to } = currentMonthRange();

const breakdown = {
  fixedSalary: 0,
  workDaysCount: 2,
  dailyTotal: 240,
  serviceCommission: 20,
  appointmentProductCommission: 0,
  saleProductCommission: 5,
  totalCommission: 25,
  additions: 0,
  discounts: 0,
  advances: 0,
  salonCost: 265,
  netPayable: 265,
};

const summary: PayrollSummary = {
  periodStart: from,
  periodEnd: to,
  items: [
    {
      employeeId: 7,
      employeeName: 'Ella',
      remunerationType: 'DIARIA_E_COMISSIONADO',
      remunerationValue: 120,
      active: true,
      doneAppointmentsCount: 1,
      breakdown,
      periodStatus: 'EM_ABERTO',
      coveredDays: 0,
      totalDays: 30,
      paidInPeriod: 0,
      outstanding: 265,
      lastPayment: null,
      paidRanges: [],
      unpaidRanges: [{ start: from, end: to, amount: null }],
      pendingDifference: 0,
      payableAmount: 265,
      salaryMonths: [],
      hasPaymentGaps: false,
    },
  ],
  totalSalonCost: 265,
  totalNetPayable: 265,
  totalPaidInPeriod: 0,
  totalOutstanding: 265,
};

const firstDay = from;
const secondDay = `${from.slice(0, 8)}02`;
const thirdDay = `${from.slice(0, 8)}03`;

const detail: PayrollEmployeeDetail = {
  summary: summary.items[0],
  periodStart: from,
  periodEnd: to,
  appointments: [
    {
      appointmentId: 11,
      date: thirdDay,
      clientName: 'Cliente Teste',
      serviceNames: 'Escova',
      servicesValue: 200,
      productsValue: 0,
      serviceCommission: 20,
      productCommission: 0,
      commissionManual: false,
      paymentStatus: 'PENDING',
    },
  ],
  productSales: [
    {
      cashFlowId: 3,
      date: firstDay,
      description: 'Venda de Produtos: 1x Óleo',
      amount: 50,
      commission: 5,
    },
  ],
  workDays: [
    {
      id: 1,
      employeeId: 7,
      date: firstDay,
      amount: 120,
      justification: null,
      defaultRate: 120,
      automatic: false,
    },
    {
      id: 2,
      employeeId: 7,
      date: secondDay,
      amount: 120,
      justification: null,
      defaultRate: 120,
      automatic: false,
    },
  ],
  unmarkedAppointmentDates: [thirdDay],
  adjustments: [],
  payments: [],
};

describe('Payroll page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(payrollApi.getSummary).mockResolvedValue(summary);
    vi.mocked(payrollApi.getEmployeeDetail).mockResolvedValue(detail);
    vi.mocked(payrollApi.previewPayment).mockResolvedValue({
      calculatedAmount: 265,
      conflict: null,
      breakdown: null,
    });
  });

  it('shows the summary and the last payment column', async () => {
    await act(async () => {
      customRender(<Payroll />);
    });

    expect(payrollApi.getSummary).toHaveBeenCalledWith(from, to);
    expect(screen.getByText('Ella')).toBeInTheDocument();
    expect(screen.getByText('Último pagamento')).toBeInTheDocument();
    expect(screen.getByText('nenhum pagamento')).toBeInTheDocument();
    expect(screen.getByText('Em aberto')).toBeInTheDocument();
  });

  it('expands an employee to show appointments, sales and the work-days calendar', async () => {
    await act(async () => {
      customRender(<Payroll />);
    });

    await act(async () => {
      fireEvent.click(screen.getByText('Ella'));
    });

    await waitFor(() => expect(screen.getByText('Cliente Teste')).toBeInTheDocument());
    expect(payrollApi.getEmployeeDetail).toHaveBeenCalledWith(7, from, to);
    expect(screen.getByText('Venda de Produtos: 1x Óleo')).toBeInTheDocument();
    expect(screen.getByText('Comissão sobre vendas avulsas')).toBeInTheDocument();
    expect(screen.getByText(/Remarcar 1 dia\(s\) desmarcado/)).toBeInTheDocument();
  });

  it('marks again a day with appointment that had been unmarked when it is clicked', async () => {
    vi.mocked(payrollApi.createWorkDay).mockResolvedValue({
      id: 3,
      employeeId: 7,
      date: thirdDay,
      amount: 120,
      justification: null,
      defaultRate: 120,
      automatic: false,
    });
    await act(async () => {
      customRender(<Payroll />);
    });
    await act(async () => {
      fireEvent.click(screen.getByText('Ella'));
    });
    await waitFor(() => expect(screen.getByText('Cliente Teste')).toBeInTheDocument());

    const dayButton = screen
      .getAllByRole('button')
      .find((b) => b.textContent === '3' || b.textContent?.startsWith('3'))!;
    await act(async () => {
      fireEvent.click(dayButton);
    });

    expect(payrollApi.createWorkDay).toHaveBeenCalledWith({ employeeId: 7, date: thirdDay });
  });

  it('lets the admin unmark a day that counted automatically because of an appointment', async () => {
    vi.mocked(payrollApi.getEmployeeDetail).mockResolvedValue({
      ...detail,
      workDays: [
        ...detail.workDays,
        {
          id: null,
          employeeId: 7,
          date: thirdDay,
          amount: 120,
          justification: null,
          defaultRate: 120,
          automatic: true,
        },
      ],
      unmarkedAppointmentDates: [],
    });
    await act(async () => {
      customRender(<Payroll />);
    });
    await act(async () => {
      fireEvent.click(screen.getByText('Ella'));
    });
    await waitFor(() => expect(screen.getByText('Cliente Teste')).toBeInTheDocument());

    const dayButton = screen.getAllByRole('button').find((b) => b.textContent === '3')!;
    await act(async () => {
      fireEvent.click(dayButton);
    });
    await act(async () => {
      fireEvent.click(screen.getByText('Desmarcar dia'));
    });

    expect(payrollApi.unmarkWorkDay).toHaveBeenCalledWith(7, thirdDay);
  });

  it('opens the same appointment detail/edit modal when an appointment row is clicked', async () => {
    await act(async () => {
      customRender(<Payroll />);
    });
    await act(async () => {
      fireEvent.click(screen.getByText('Ella'));
    });
    await waitFor(() => expect(screen.getByText('Cliente Teste')).toBeInTheDocument());

    await act(async () => {
      fireEvent.click(screen.getByText('Cliente Teste'));
    });

    await waitFor(() => expect(screen.getByText('modal-agendamento-11')).toBeInTheDocument());
  });

  it('opens "Marcar como pago" locked to the filter, with the calculated value', async () => {
    await act(async () => {
      customRender(<Payroll />);
    });

    await act(async () => {
      fireEvent.click(screen.getByText('Marcar como pago'));
    });

    await waitFor(() =>
      expect(screen.getByText('Marcar como pago', { selector: 'h3' })).toBeInTheDocument()
    );
    await waitFor(() =>
      expect(screen.getByTestId('payment-preview-amount').textContent).toContain('265,00')
    );
    // nada pago ainda: o período é exatamente o do filtro, sem campo de data editável
    expect(screen.getByTestId('mark-paid-period').textContent).toContain(
      new Date(`${from}T12:00:00`).toLocaleDateString('pt-BR')
    );
    expect(document.body.querySelector('#pay-start')).toBeNull(); // sem campo de data no modal
    expect(screen.queryByLabelText('Valor pago')).not.toBeInTheDocument();
  });
});
