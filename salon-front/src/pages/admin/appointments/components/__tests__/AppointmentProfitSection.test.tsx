import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender, waitFor } from '../../../../../test/test-utils';
import { AppointmentProfitSection } from '../AppointmentProfitSection';
import { reportsApi, type AppointmentProfitResponse } from '../../../reports/services/reports';

vi.mock('../../../reports/services/reports', () => ({
  reportsApi: { getAppointmentProfit: vi.fn(), updateAppointmentProfit: vi.fn() },
}));
vi.mock('../../../../../hooks/usePermission', () => ({ usePermission: () => true }));
const alertMock = { error: vi.fn(), success: vi.fn(), alert: vi.fn(), confirm: vi.fn() };
vi.mock('../../../../../hooks/useAlert', () => ({ useAlert: () => alertMock }));

const autoProfit: AppointmentProfitResponse = {
  appointmentId: 9,
  grossRevenue: 200,
  serviceRecipeCost: 10,
  productsSoldCost: 0,
  serviceCommissionCost: 20,
  productCommissionCost: 0,
  netProfit: 170,
  positive: true,
  autoServiceRecipeCost: 10,
  autoProductsSoldCost: 0,
  autoServiceCommissionCost: 20,
  autoProductCommissionCost: 0,
  serviceRecipeCostManual: false,
  productsSoldCostManual: false,
  serviceCommissionCostManual: false,
  productCommissionCostManual: false,
  overrideNote: null,
};

describe('AppointmentProfitSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(reportsApi.getAppointmentProfit).mockResolvedValue(autoProfit);
  });

  it('lets the admin type a manual commission only for this appointment, keeping the rest automatic', async () => {
    vi.mocked(reportsApi.updateAppointmentProfit).mockResolvedValue({
      ...autoProfit,
      serviceCommissionCost: 50,
      serviceCommissionCostManual: true,
      netProfit: 140,
      overrideNote: 'combinado',
    });
    await act(async () => {
      customRender(<AppointmentProfitSection appointmentId={9} />);
    });
    await act(async () => {
      fireEvent.click(screen.getByText('Ver lucro deste atendimento'));
    });
    await waitFor(() => expect(screen.getByText('Editar valores')).toBeInTheDocument());

    await act(async () => {
      fireEvent.click(screen.getByText('Editar valores'));
    });
    const commissionInput = screen.getByLabelText('Comissão sobre serviços');
    await act(async () => {
      fireEvent.change(commissionInput, { target: { value: '5000' } }); // máscara: 5000 -> R$ 50,00
    });
    fireEvent.change(screen.getByLabelText('Justificativa (opcional)'), { target: { value: 'combinado' } });
    await act(async () => {
      fireEvent.click(screen.getByText('Salvar valores'));
    });

    expect(reportsApi.updateAppointmentProfit).toHaveBeenCalledWith(9, {
      serviceRecipeCost: null,
      productsSoldCost: null,
      serviceCommissionCost: 50,
      productCommissionCost: null,
      note: 'combinado',
    });
    await waitFor(() => expect(screen.getByText('manual')).toBeInTheDocument());
  });
});
