import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender, waitFor } from '../../../../test/test-utils';
import { PaymentsList } from '../components/PaymentsList';
import { payrollApi, type PayrollPayment } from '../services/payroll';

vi.mock('../services/payroll', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/payroll')>();
  return {
    ...actual,
    payrollApi: {
      listPayments: vi.fn(),
      previewPayment: vi.fn(),
      updatePayment: vi.fn(),
      deletePayment: vi.fn(),
      getEmployeeDetail: vi.fn(),
    },
  };
});
vi.mock('../../employees/services/employees', () => ({
  employeesApi: { findAll: vi.fn().mockResolvedValue({ content: [{ id: 7, name: 'Ella' }] }) },
}));
const alertMock = { error: vi.fn(), success: vi.fn(), alert: vi.fn(), confirm: vi.fn() };
vi.mock('../../../../hooks/useAlert', () => ({ useAlert: () => alertMock }));

const payment: PayrollPayment = {
  id: 5,
  employeeId: 7,
  employeeName: 'Ella',
  periodStart: '2026-09-01',
  periodEnd: '2026-09-15',
  calculatedAmount: 600,
  amountPaid: 600,
  paidAt: '2026-09-16',
  paymentMethod: 'PIX',
  notes: null,
  recalculatedAmount: 650,
  changedSincePayment: true,
};

describe('PaymentsList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(payrollApi.listPayments).mockResolvedValue([payment]);
  });

  it('lists payments with the change-after-payment warning', async () => {
    await act(async () => {
      customRender(<PaymentsList />);
    });

    expect(screen.getByText('Ella', { selector: 'td' })).toBeInTheDocument();
    expect(screen.getByText(/mudou depois: hoje R\$\s?650,00/)).toBeInTheDocument();
  });

  it('blocks saving when the new dates hit days already paid in another payment', async () => {
    vi.mocked(payrollApi.previewPayment).mockResolvedValue({
      calculatedAmount: 900,
      conflict: 'Parte deste período já foi paga: 16/09/2026 a 30/09/2026 (pago em 01/10/2026).',
      breakdown: null,
    });
    await act(async () => {
      customRender(<PaymentsList />);
    });
    await act(async () => {
      fireEvent.click(screen.getByLabelText('Editar pagamento'));
    });

    await waitFor(() =>
      expect(screen.getByText(/Parte deste período já foi paga/)).toBeInTheDocument()
    );
    expect(screen.getByText('Salvar')).toBeDisabled();
    expect(payrollApi.updatePayment).not.toHaveBeenCalled();
  });
});
