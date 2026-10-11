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
      deleteWorkDay: vi.fn(),
      createAdjustment: vi.fn(),
      updateAdjustment: vi.fn(),
      deleteAdjustment: vi.fn(),
      createPayment: vi.fn(),
      updatePayment: vi.fn(),
      deletePayment: vi.fn(),
    },
  };
});

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
      paidUntil: null,
      hasPaymentGaps: false,
    },
  ],
  totalSalonCost: 265,
  totalNetPayable: 265,
  totalPaidInPeriod: 0,
  totalOutstanding: 265,
  payrollPaidUntil: null,
  behindEmployees: ['Ella'],
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
    { cashFlowId: 3, date: firstDay, description: 'Venda de Produtos: 1x Óleo', amount: 50, commission: 5 },
  ],
  workDays: [
    { id: 1, employeeId: 7, date: firstDay, amount: 120, justification: null, defaultRate: 120 },
    { id: 2, employeeId: 7, date: secondDay, amount: 120, justification: null, defaultRate: 120 },
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
  });

  it('shows the summary cards and who has never been paid', async () => {
    await act(async () => {
      customRender(<Payroll />);
    });

    expect(payrollApi.getSummary).toHaveBeenCalledWith(from, to);
    expect(screen.getByText('Ella')).toBeInTheDocument();
    expect(screen.getByText('Sem pagamentos')).toBeInTheDocument();
    expect(screen.getByText('Nunca pagas: Ella')).toBeInTheDocument();
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
    expect(screen.getByText(/Marcar 1 dia\(s\) com atendimento/)).toBeInTheDocument();
  });

  it('marks an unmarked day with the default rate when the day is clicked', async () => {
    vi.mocked(payrollApi.createWorkDay).mockResolvedValue({
      id: 3, employeeId: 7, date: thirdDay, amount: 120, justification: null, defaultRate: 120,
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

  it('opens the payment modal prefilled with the outstanding value from "Marcar como pago"', async () => {
    await act(async () => {
      customRender(<Payroll />);
    });

    await act(async () => {
      fireEvent.click(screen.getByText('Marcar como pago'));
    });

    await waitFor(() => expect(screen.getByText('Marcar período como pago', { selector: 'h3' })).toBeInTheDocument());
    expect((screen.getByLabelText('Valor pago') as HTMLInputElement).value).toContain('265,00');
  });
});
