import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender, waitFor } from '../../../../test/test-utils';
import { EmployeeFinancialHistorySection } from '../EmployeeFinancialHistorySection';
import { reportsApi } from '../services/reports';

vi.mock('../services/reports', () => ({
  reportsApi: {
    getEmployeeFinancialHistory: vi.fn(),
  },
}));

vi.mock('../../../../hooks/useAlert', () => ({
  useAlert: () => ({
    error: vi.fn(),
    success: vi.fn(),
  }),
}));

const mockEmployees = [
  { employeeId: 1, employeeName: 'Mariana', doneAppointmentsCount: 5, doneAppointmentsValue: 500, doneProductsValue: 0, calculatedPayout: 200 },
  { employeeId: 2, employeeName: 'Joana', doneAppointmentsCount: 3, doneAppointmentsValue: 300, doneProductsValue: 0, calculatedPayout: 100 },
];

const mockHistory = [
  {
    id: 10,
    scheduledAt: '2026-06-01T10:00:00',
    preferredDate: null,
    serviceName: 'Corte',
    price: 85.0,
    status: 'DONE',
    paymentStatus: 'PAID',
  },
];

describe('EmployeeFinancialHistorySection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(reportsApi.getEmployeeFinancialHistory).mockResolvedValue({
      content: mockHistory,
      totalPages: 1,
      totalElements: 1,
      size: 10,
      number: 0,
    });
  });

  it('shows a prompt and does not call the API before an employee is selected', () => {
    customRender(<EmployeeFinancialHistorySection employees={mockEmployees} />);

    expect(
      screen.getByText('Selecione um(a) profissional acima para ver o histórico.')
    ).toBeInTheDocument();
    expect(reportsApi.getEmployeeFinancialHistory).not.toHaveBeenCalled();
  });

  it('loads and displays the financial history after selecting an employee', async () => {
    customRender(<EmployeeFinancialHistorySection employees={mockEmployees} />);

    const select = screen.getByRole('combobox');
    await act(async () => {
      fireEvent.change(select, { target: { value: '1' } });
    });

    await waitFor(() => {
      expect(reportsApi.getEmployeeFinancialHistory).toHaveBeenCalledWith(1, undefined, undefined, 0, 10);
    });

    expect(screen.getByText('Corte')).toBeInTheDocument();
    expect(screen.getByText('R$ 85,00')).toBeInTheDocument();
    expect(screen.getByText('Concluído')).toBeInTheDocument();
    expect(screen.getByText('Pago')).toBeInTheDocument();
  });

  it('passes the date range filters through to the API call', async () => {
    customRender(
      <EmployeeFinancialHistorySection employees={mockEmployees} dateFrom="2026-06-01" dateTo="2026-06-30" />
    );

    const select = screen.getByRole('combobox');
    await act(async () => {
      fireEvent.change(select, { target: { value: '2' } });
    });

    await waitFor(() => {
      expect(reportsApi.getEmployeeFinancialHistory).toHaveBeenCalledWith(
        2,
        '2026-06-01',
        '2026-06-30',
        0,
        10
      );
    });
  });
});
