import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender } from '../../../../../test/test-utils';
import { SaleEditModal } from '../SaleEditModal';
import { cashFlowApi, type CashFlowRecord } from '../../services/cashflow';

vi.mock('../../services/cashflow', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/cashflow')>();
  return { ...actual, cashFlowApi: { updateSale: vi.fn() } };
});
vi.mock('../../../products/services/products', () => ({
  productsApi: {
    findAll: vi.fn().mockResolvedValue({ content: [{ id: 1, name: 'Shampoo', price: 50, active: true }] }),
  },
}));
vi.mock('../../../employees/services/employees', () => ({
  employeesApi: { findAll: vi.fn().mockResolvedValue({ content: [{ id: 5, name: 'Maria' }] }) },
}));
const alertMock = { error: vi.fn(), success: vi.fn(), alert: vi.fn(), confirm: vi.fn() };
vi.mock('../../../../../hooks/useAlert', () => ({ useAlert: () => alertMock }));

const sale: CashFlowRecord = {
  id: 70,
  type: 'INCOME',
  amount: 100,
  description: 'Venda de Produtos: 2x Shampoo',
  date: '2026-10-05',
  appointmentId: null,
  employeeId: 5,
  employeeName: 'Maria',
  commissionAmount: 10,
  commissionPercent: 10,
  commissionManual: false,
  items: [{ productId: 1, productName: 'Shampoo', quantity: 2, unitPrice: 50 }],
};

describe('SaleEditModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(cashFlowApi.updateSale).mockResolvedValue(sale);
  });

  it('edits the quantity and saves items, seller and automatic commission', async () => {
    const onSaved = vi.fn();
    await act(async () => {
      customRender(<SaleEditModal sale={sale} onClose={vi.fn()} onSaved={onSaved} />);
    });

    expect(screen.getByText('Comissão da vendedora')).toBeInTheDocument();
    await act(async () => {
      fireEvent.change(screen.getByLabelText('Qtd.'), { target: { value: '3' } });
    });
    expect(screen.getByText('R$ 150,00', { selector: 'span' })).toBeInTheDocument(); // total

    await act(async () => {
      fireEvent.click(screen.getByText('Salvar venda'));
    });

    expect(cashFlowApi.updateSale).toHaveBeenCalledWith(70, {
      date: '2026-10-05',
      employeeId: 5,
      items: [{ productId: 1, quantity: 3, unitPrice: 50 }],
      amount: null,
      description: null,
      commissionAmount: null,
    });
    expect(onSaved).toHaveBeenCalled();
  });
});
