import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender, waitFor } from '../../../../test/test-utils';
import { MarkPaidModal, type PeriodItem } from '../components/PayrollModals';
import { payrollApi, type SalaryMonthStatus } from '../services/payroll';

vi.mock('../services/payroll', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/payroll')>();
  return { ...actual, payrollApi: { previewPayment: vi.fn() } };
});

const items: PeriodItem[] = [
  { key: 'a', date: '2026-10-05', label: 'Comissão · Ana (Corte)', amount: 20 },
  { key: 'b', date: '2026-10-20', label: 'Comissão · Bia (Escova)', amount: 30 },
  { key: 'c', date: '2026-10-22', label: 'Desconto · falta', amount: 50, negative: true },
];

const base = {
  employeeId: 7,
  employeeName: 'Ella',
  filterFrom: '2026-10-01',
  filterTo: '2026-10-31',
  items,
  isSaving: false,
  onClose: vi.fn(),
};

const octoberPending: SalaryMonthStatus = {
  month: '2026-10-01',
  amount: 1500,
  paid: false,
  paidAt: null,
};
const octoberPaid: SalaryMonthStatus = {
  month: '2026-10-01',
  amount: 1500,
  paid: true,
  paidAt: '2026-10-31',
};

describe('MarkPaidModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(payrollApi.previewPayment).mockResolvedValue({
      calculatedAmount: 750,
      conflict: null,
      breakdown: null,
    });
  });

  it('shortens the days to the unpaid ones, colors items and includes the pending monthly salary by default', async () => {
    const onSubmit = vi.fn();
    await act(async () => {
      customRender(
        <MarkPaidModal
          {...base}
          paidRanges={[{ start: '2026-10-01', end: '2026-10-15', amount: 750 }]}
          unpaidRanges={[{ start: '2026-10-16', end: '2026-10-31', amount: null }]}
          salaryMonths={[octoberPending]}
          onSubmit={onSubmit}
        />
      );
    });

    expect(screen.getByText('Dias 01/10/2026 a 15/10/2026 já pagos')).toBeInTheDocument();
    expect(screen.getByTestId('mark-paid-period').textContent).toBe('16/10/2026 a 31/10/2026');
    expect(screen.getByLabelText('Salário de outubro/2026')).toBeChecked();
    expect(payrollApi.previewPayment).toHaveBeenCalledWith(7, {
      periodStart: '2026-10-16',
      periodEnd: '2026-10-31',
      salaryMonths: ['2026-10-01'],
    });
    expect(screen.getByText(/Ana \(Corte\)/).closest('li')).toHaveAttribute('data-status', 'paid');
    expect(screen.getByText(/Bia \(Escova\)/).closest('li')).toHaveAttribute(
      'data-status',
      'included'
    );

    await waitFor(() => expect(screen.getByText('Registrar pagamento')).not.toBeDisabled());
    await act(async () => {
      fireEvent.click(screen.getByText('Registrar pagamento'));
    });
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        periodStart: '2026-10-16',
        periodEnd: '2026-10-31',
        salaryMonths: ['2026-10-01'],
      })
    );
  });

  it('unchecking the salary leaves it for another payment of the month', async () => {
    const onSubmit = vi.fn();
    await act(async () => {
      customRender(
        <MarkPaidModal
          {...base}
          paidRanges={[]}
          unpaidRanges={[{ start: '2026-10-01', end: '2026-10-31', amount: null }]}
          salaryMonths={[octoberPending]}
          onSubmit={onSubmit}
        />
      );
    });

    await act(async () => {
      fireEvent.click(screen.getByLabelText('Salário de outubro/2026'));
    });
    expect(
      screen.getByText('O salário desmarcado fica pendente pra outro pagamento.')
    ).toBeInTheDocument();
    expect(payrollApi.previewPayment).toHaveBeenLastCalledWith(7, {
      periodStart: '2026-10-01',
      periodEnd: '2026-10-31',
      salaryMonths: [],
    });

    await waitFor(() => expect(screen.getByText('Registrar pagamento')).not.toBeDisabled());
    await act(async () => {
      fireEvent.click(screen.getByText('Registrar pagamento'));
    });
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ salaryMonths: [] }));
  });

  it('salary already paid this month is shown as paid and is not offered again', async () => {
    await act(async () => {
      customRender(
        <MarkPaidModal
          {...base}
          paidRanges={[]}
          unpaidRanges={[{ start: '2026-10-01', end: '2026-10-31', amount: null }]}
          salaryMonths={[octoberPaid]}
          onSubmit={vi.fn()}
        />
      );
    });

    expect(
      screen.getByText(/Salário de outubro\/2026 já pago em 31\/10\/2026/)
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Salário de outubro/2026')).not.toBeInTheDocument();
    expect(payrollApi.previewPayment).toHaveBeenCalledWith(7, {
      periodStart: '2026-10-01',
      periodEnd: '2026-10-31',
      salaryMonths: [],
    });
  });

  it('allows a salary-only payment when there are no pending days, and lists each month of the filter', async () => {
    const onSubmit = vi.fn();
    await act(async () => {
      customRender(
        <MarkPaidModal
          {...base}
          filterTo="2026-11-30"
          paidRanges={[]}
          unpaidRanges={[]}
          salaryMonths={[
            octoberPending,
            { month: '2026-11-01', amount: 1500, paid: false, paidAt: null },
          ]}
          onSubmit={onSubmit}
        />
      );
    });

    expect(
      screen.getByText('Nenhum dia pendente com valor a pagar — só salário.')
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Salário de outubro/2026')).toBeChecked();
    expect(screen.getByLabelText('Salário de novembro/2026')).toBeChecked();

    await act(async () => {
      fireEvent.click(screen.getByLabelText('Salário de novembro/2026'));
    });
    await waitFor(() => expect(screen.getByText('Registrar pagamento')).not.toBeDisabled());
    await act(async () => {
      fireEvent.click(screen.getByText('Registrar pagamento'));
    });
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ periodStart: null, periodEnd: null, salaryMonths: ['2026-10-01'] })
    );
  });

  it('nothing selected (no pending days, salary unchecked) cannot be saved', async () => {
    await act(async () => {
      customRender(
        <MarkPaidModal
          {...base}
          paidRanges={[]}
          unpaidRanges={[]}
          salaryMonths={[octoberPending]}
          onSubmit={vi.fn()}
        />
      );
    });

    await act(async () => {
      fireEvent.click(screen.getByLabelText('Salário de outubro/2026'));
    });
    expect(screen.getByText('Nada selecionado pra pagar.')).toBeInTheDocument();
    expect(screen.getByText('Registrar pagamento')).toBeDisabled();
  });

  it('lets the user pick one of the separated unpaid ranges', async () => {
    await act(async () => {
      customRender(
        <MarkPaidModal
          {...base}
          paidRanges={[{ start: '2026-10-10', end: '2026-10-15', amount: 300 }]}
          unpaidRanges={[
            { start: '2026-10-01', end: '2026-10-09', amount: null },
            { start: '2026-10-16', end: '2026-10-31', amount: null },
          ]}
          salaryMonths={[]}
          onSubmit={vi.fn()}
        />
      );
    });

    expect(screen.getByTestId('mark-paid-period').textContent).toBe('01/10/2026 a 09/10/2026');
    await act(async () => {
      fireEvent.click(screen.getByLabelText('16/10/2026 a 31/10/2026'));
    });
    expect(screen.getByTestId('mark-paid-period').textContent).toBe('16/10/2026 a 31/10/2026');
  });

  it('does not allow saving when the whole filtered period, salary included, is already paid', async () => {
    await act(async () => {
      customRender(
        <MarkPaidModal
          {...base}
          paidRanges={[{ start: '2026-10-01', end: '2026-10-31', amount: 1500 }]}
          unpaidRanges={[]}
          salaryMonths={[octoberPaid]}
          onSubmit={vi.fn()}
        />
      );
    });

    expect(screen.getByText('Todo o período filtrado já está pago.')).toBeInTheDocument();
    expect(screen.getByText('Registrar pagamento')).toBeDisabled();
    expect(payrollApi.previewPayment).not.toHaveBeenCalled();
  });
});
