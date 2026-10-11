import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender, waitFor } from '../../../../test/test-utils';
import { MarkPaidModal, type PeriodItem } from '../components/PayrollModals';
import { payrollApi } from '../services/payroll';

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

describe('MarkPaidModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(payrollApi.previewPayment).mockResolvedValue({
      calculatedAmount: 750,
      conflict: null,
      breakdown: null,
    });
  });

  it('shortens the period to the unpaid days, colors paid/pending items and submits the unpaid range', async () => {
    const onSubmit = vi.fn();
    await act(async () => {
      customRender(
        <MarkPaidModal
          {...base}
          paidRanges={[{ start: '2026-10-01', end: '2026-10-15', amount: 750 }]}
          unpaidRanges={[{ start: '2026-10-16', end: '2026-10-31', amount: null }]}
          onSubmit={onSubmit}
        />
      );
    });

    expect(screen.getByText('Dias 01/10/2026 a 15/10/2026 já pagos')).toBeInTheDocument();
    expect(screen.getByTestId('mark-paid-period').textContent).toBe('16/10/2026 a 31/10/2026');
    expect(payrollApi.previewPayment).toHaveBeenCalledWith(7, '2026-10-16', '2026-10-31');
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
      expect.objectContaining({ periodStart: '2026-10-16', periodEnd: '2026-10-31' })
    );
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
          onSubmit={vi.fn()}
        />
      );
    });

    expect(screen.getByTestId('mark-paid-period').textContent).toBe('01/10/2026 a 09/10/2026');
    await act(async () => {
      fireEvent.click(screen.getByLabelText('16/10/2026 a 31/10/2026'));
    });
    expect(screen.getByTestId('mark-paid-period').textContent).toBe('16/10/2026 a 31/10/2026');
    expect(payrollApi.previewPayment).toHaveBeenLastCalledWith(7, '2026-10-16', '2026-10-31');
  });

  it('does not allow saving when the whole filtered period is already paid', async () => {
    await act(async () => {
      customRender(
        <MarkPaidModal
          {...base}
          paidRanges={[{ start: '2026-10-01', end: '2026-10-31', amount: 1500 }]}
          unpaidRanges={[]}
          onSubmit={vi.fn()}
        />
      );
    });

    expect(screen.getByText('Todo o período filtrado já está pago.')).toBeInTheDocument();
    expect(screen.getByText('Registrar pagamento')).toBeDisabled();
    expect(payrollApi.previewPayment).not.toHaveBeenCalled();
  });
});
