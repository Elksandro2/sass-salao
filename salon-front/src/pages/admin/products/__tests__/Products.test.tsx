import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, act, customRender, waitFor } from '../../../../test/test-utils';
import { Products } from '../Products';
import { productsApi } from '../services/products';

vi.mock('../services/products', () => ({
  productsApi: {
    findAll: vi.fn(),
    delete: vi.fn(),
    reactivate: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
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

const mockProducts = [
  { id: 1, name: 'Shampoo', price: 25.5, active: true },
  { id: 2, name: 'Condicionador', price: 28.0, active: false },
];

const mockPage = (content: typeof mockProducts) => ({
  content,
  totalPages: 1,
  totalElements: content.length,
  size: 10,
  number: 0,
});

describe('Products Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(productsApi.findAll).mockResolvedValue(mockPage(mockProducts));
  });

  it('renders products and handles filter changes', async () => {
    await act(async () => {
      customRender(<Products mode="sale" />);
    });

    expect(screen.getByText('Gerenciar Produtos (Venda)')).toBeInTheDocument();
    expect(screen.getByText('Shampoo')).toBeInTheDocument();
    expect(screen.getByText('Condicionador')).toBeInTheDocument();

    const select = screen.getByRole('combobox');
    await act(async () => {
      fireEvent.change(select, { target: { value: 'true' } });
    });
    expect(productsApi.findAll).toHaveBeenCalledWith(
      expect.objectContaining({ active: true }),
      0,
      10
    );

    await act(async () => {
      fireEvent.change(select, { target: { value: 'false' } });
    });
    expect(productsApi.findAll).toHaveBeenCalledWith(
      expect.objectContaining({ active: false }),
      0,
      10
    );
  });

  it('triggers reactivate flow when RotateCcw button is clicked and confirmed', async () => {
    vi.mocked(productsApi.reactivate).mockResolvedValue({
      id: 2,
      name: 'Condicionador',
      price: 28.0,
      active: true,
    });

    await act(async () => {
      customRender(<Products mode="sale" />);
    });

    const reactivateButtons = screen.getAllByTitle('Reativar Produto');
    expect(reactivateButtons).toHaveLength(1);

    await act(async () => {
      fireEvent.click(reactivateButtons[0]);
    });

    expect(screen.getByText('Reativar Produto')).toBeInTheDocument();
    expect(
      screen.getByText('Tem certeza que deseja reativar este produto? Ele aparecerá novamente nas listagens públicas.')
    ).toBeInTheDocument();

    const confirmButton = screen.getAllByRole('button', { name: 'Reativar' })[1];
    await act(async () => {
      fireEvent.click(confirmButton);
    });

    expect(productsApi.reactivate).toHaveBeenCalledWith(2);
    await waitFor(() => {
      expect(productsApi.findAll).toHaveBeenCalledTimes(2); // Initial + refresh
    });
  });

  it('triggers delete flow when delete button is clicked and confirmed', async () => {
    vi.mocked(productsApi.delete).mockResolvedValue(undefined);

    await act(async () => {
      customRender(<Products mode="sale" />);
    });

    const deleteButtons = screen.getAllByTitle('Excluir Produto');
    expect(deleteButtons).toHaveLength(1);

    await act(async () => {
      fireEvent.click(deleteButtons[0]);
    });

    expect(screen.getByText('Excluir Produto')).toBeInTheDocument();
    expect(
      screen.getByText('Tem certeza que deseja excluir este produto? Esta ação não pode ser desfeita.')
    ).toBeInTheDocument();

    const confirmButton = screen.getByRole('button', { name: 'Excluir' });
    await act(async () => {
      fireEvent.click(confirmButton);
    });

    expect(productsApi.delete).toHaveBeenCalledWith(1);
    await waitFor(() => {
      expect(productsApi.findAll).toHaveBeenCalledTimes(2);
    });
  });

  describe('mode="use" (Produtos de uso interno)', () => {
    it('renders the "Uso" heading, filters by usedInServiceRecipe and hides the sale price field by default', async () => {
      await act(async () => {
        customRender(<Products mode="use" />);
      });

      expect(screen.getByText('Gerenciar Produtos (Uso)')).toBeInTheDocument();
      expect(productsApi.findAll).toHaveBeenCalledWith(
        expect.objectContaining({ usedInServiceRecipe: true }),
        0,
        10
      );

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /Novo Produto de Uso/i }));
      });

      expect(screen.queryByLabelText(/Preço de Venda/i)).not.toBeInTheDocument();
      expect(screen.getByText('Também disponível para venda')).toBeInTheDocument();
    });

    it('reveals the sale price field when "Também disponível para venda" is checked', async () => {
      await act(async () => {
        customRender(<Products mode="use" />);
      });

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /Novo Produto de Uso/i }));
      });

      const alsoForSale = screen.getByRole('checkbox', { name: 'Também disponível para venda' });
      await act(async () => {
        fireEvent.click(alsoForSale);
      });

      expect(screen.getByText(/Preço de Venda \(R\$\)/i)).toBeInTheDocument();
    });
  });

  describe('mode="sale" (Produtos de venda)', () => {
    it('filters by availableForSale and offers "também usado na receita de serviço"', async () => {
      await act(async () => {
        customRender(<Products mode="sale" />);
      });

      expect(productsApi.findAll).toHaveBeenCalledWith(
        expect.objectContaining({ availableForSale: true }),
        0,
        10
      );

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /Novo Produto de Venda/i }));
      });

      expect(screen.getByText(/Preço de Venda \(R\$\)/i)).toBeInTheDocument();
      expect(screen.getByText('Também usado na receita de serviço (uso interno)')).toBeInTheDocument();
    });
  });
});
