import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender, waitFor } from '../../../../test/test-utils';
import { AdminServices } from '../AdminServices';
import { salonServicesApi } from '../../../services/services/services';
import { productsApi } from '../../products/services/products';

vi.mock('../../../services/services/services', () => ({
  salonServicesApi: {
    findAll: vi.fn(),
    delete: vi.fn(),
    reactivate: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock('../../products/services/products', () => ({
  productsApi: {
    findAll: vi.fn(),
  },
}));

vi.mock('../../../../hooks/usePermission', () => ({
  usePermission: () => true,
}));

vi.mock('../../../../hooks/useAlert', () => ({
  useAlert: () => ({
    error: vi.fn(),
    success: vi.fn(),
    alert: vi.fn(),
    confirm: vi.fn(),
  }),
}));

const mockServices = [
  { id: 1, name: 'Corte de Cabelo', description: 'Corte tesoura', price: 50.0, active: true },
  { id: 2, name: 'Pintura', description: 'Tintura premium', price: 100.0, active: false },
];

const mockPage = (content: typeof mockServices) => ({
  content,
  totalPages: 1,
  totalElements: content.length,
  size: 10,
  number: 0,
});

const mockProducts = [
  { id: 10, name: 'Tintura', price: 50, costPrice: 40, capacity: 1, unit: 'L', unitCost: 40, usedInServiceRecipe: true },
  { id: 11, name: 'Toalha', price: null, usedInServiceRecipe: true }, // sem unidade cadastrada
];

describe('AdminServices Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(salonServicesApi.findAll).mockResolvedValue(mockPage(mockServices));
    vi.mocked(productsApi.findAll).mockResolvedValue({
      content: mockProducts,
      totalPages: 1,
      totalElements: mockProducts.length,
      size: 1000,
      number: 0,
    } as never);
  });

  it('renders services and handles filter changes', async () => {
    await act(async () => {
      customRender(<AdminServices />);
    });

    expect(screen.getByText('Gerenciar Serviços')).toBeInTheDocument();
    expect(screen.getByText('Corte de Cabelo')).toBeInTheDocument();
    expect(screen.getByText('Pintura')).toBeInTheDocument();

    const select = screen.getByRole('combobox');
    await act(async () => {
      fireEvent.change(select, { target: { value: 'true' } });
    });
    expect(salonServicesApi.findAll).toHaveBeenCalledWith(
      expect.objectContaining({ active: true }),
      0,
      10
    );

    await act(async () => {
      fireEvent.change(select, { target: { value: 'false' } });
    });
    expect(salonServicesApi.findAll).toHaveBeenCalledWith(
      expect.objectContaining({ active: false }),
      0,
      10
    );
  });

  it('triggers reactivate flow when RotateCcw button is clicked and confirmed', async () => {
    vi.mocked(salonServicesApi.reactivate).mockResolvedValue({
      id: 2,
      name: 'Pintura',
      description: 'Tintura premium',
      price: 100.0,
      active: true,
    });

    await act(async () => {
      customRender(<AdminServices />);
    });

    const reactivateButtons = screen.getAllByTitle('Reativar Serviço');
    expect(reactivateButtons).toHaveLength(1);

    await act(async () => {
      fireEvent.click(reactivateButtons[0]);
    });

    expect(screen.getByText('Reativar Serviço')).toBeInTheDocument();
    expect(
      screen.getByText('Tem certeza que deseja reativar este serviço? Ele aparecerá novamente nas listagens públicas.')
    ).toBeInTheDocument();

    const confirmButton = screen.getAllByRole('button', { name: 'Reativar' })[1];
    await act(async () => {
      fireEvent.click(confirmButton);
    });

    expect(salonServicesApi.reactivate).toHaveBeenCalledWith(2);
    await waitFor(() => {
      expect(salonServicesApi.findAll).toHaveBeenCalledTimes(2);
    });
  });

  it('triggers delete flow when delete button is clicked and confirmed', async () => {
    vi.mocked(salonServicesApi.delete).mockResolvedValue(undefined);

    await act(async () => {
      customRender(<AdminServices />);
    });

    const deleteButtons = screen.getAllByTitle('Excluir Serviço');
    expect(deleteButtons).toHaveLength(1);

    await act(async () => {
      fireEvent.click(deleteButtons[0]);
    });

    expect(screen.getByText('Excluir Serviço')).toBeInTheDocument();
    expect(
      screen.getByText('Tem certeza que deseja excluir este serviço? Esta ação não pode ser desfeita.')
    ).toBeInTheDocument();

    const confirmButton = screen.getByRole('button', { name: 'Excluir' });
    await act(async () => {
      fireEvent.click(confirmButton);
    });

    expect(salonServicesApi.delete).toHaveBeenCalledWith(1);
    await waitFor(() => {
      expect(salonServicesApi.findAll).toHaveBeenCalledTimes(2);
    });
  });

  describe('Receita (opcional)', () => {
    const openFormAndAddRow = async () => {
      let rendered!: ReturnType<typeof customRender>;
      await act(async () => {
        rendered = customRender(<AdminServices />);
      });
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /Novo Serviço/i }));
      });
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /Adicionar produto/i }));
      });
      return rendered;
    };

    it('finds the product through a single search+select field instead of a plain dropdown', async () => {
      await openFormAndAddRow();

      const productInput = screen.getByPlaceholderText('Buscar e selecionar o produto...');
      fireEvent.change(productInput, { target: { value: 'Tintura' } });
      fireEvent.click(screen.getByRole('button', { name: 'Tintura' }));

      expect(productInput).toHaveValue('Tintura');
    });

    it('defaults the recipe unit to the product\'s own unit, editable to a compatible one', async () => {
      await openFormAndAddRow();

      const productInput = screen.getByPlaceholderText('Buscar e selecionar o produto...');
      fireEvent.change(productInput, { target: { value: 'Tintura' } });
      fireEvent.click(screen.getByRole('button', { name: 'Tintura' }));

      // Tintura está cadastrada em Litro — a receita já sugere "L" por padrão...
      const unitSelect = screen.getByRole('combobox', { name: 'Unidade da receita' }) as HTMLSelectElement;
      const options = Array.from(unitSelect.options).map((o) => o.value);
      // ...mas ML também é oferecido (mesma grandeza: volume), G/KG não (grandeza diferente).
      expect(options).toEqual(expect.arrayContaining(['L', 'ML']));
      expect(options).not.toEqual(expect.arrayContaining(['G', 'KG']));
    });

    it('warns when the product has no registered unit and the recipe unit was not chosen either', async () => {
      await openFormAndAddRow();

      const productInput = screen.getByPlaceholderText('Buscar e selecionar o produto...');
      fireEvent.change(productInput, { target: { value: 'Toalha' } });
      fireEvent.click(screen.getByRole('button', { name: 'Toalha' }));

      expect(
        screen.getByText(/Defina a unidade \(ml\/g\/L…\) no cadastro deste produto/i)
      ).toBeInTheDocument();
    });

    it('submits the recipe with the chosen product, quantity and unit', async () => {
      vi.mocked(salonServicesApi.create).mockResolvedValue({} as never);
      const { container } = await openFormAndAddRow();

      const nameInput = container.querySelector('input[name="name"]') as HTMLInputElement;
      fireEvent.change(nameInput, { target: { value: 'Coloração' } });

      const productInput = screen.getByPlaceholderText('Buscar e selecionar o produto...');
      fireEvent.change(productInput, { target: { value: 'Tintura' } });
      fireEvent.click(screen.getByRole('button', { name: 'Tintura' }));

      fireEvent.change(screen.getByPlaceholderText('Quantidade consumida'), { target: { value: '30' } });

      const unitSelect = screen.getByRole('combobox', { name: 'Unidade da receita' });
      fireEvent.change(unitSelect, { target: { value: 'ML' } }); // troca de L (padrão) pra ml

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
      });

      expect(salonServicesApi.create).toHaveBeenCalledWith(
        expect.objectContaining({
          productUsages: [{ productId: 10, quantityUsed: 30, unit: 'ML' }],
        })
      );
    });
  });
});
