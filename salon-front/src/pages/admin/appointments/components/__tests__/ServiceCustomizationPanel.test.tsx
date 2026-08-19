import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ServiceCustomizationPanel } from '../ServiceCustomizationPanel';

const emptyValues = { price: '', notes: '' };

describe('ServiceCustomizationPanel', () => {
  it('starts collapsed, hiding the input fields', () => {
    render(
      <ServiceCustomizationPanel defaultPrice={100} values={emptyValues} onChange={vi.fn()} />
    );

    expect(screen.getByText('Personalizar para este cliente')).toBeInTheDocument();
    expect(screen.queryByLabelText(/Preço/)).not.toBeInTheDocument();
  });

  it('expands to show fields pre-filled with the catalog defaults as placeholders', () => {
    render(
      <ServiceCustomizationPanel defaultPrice={100} values={emptyValues} onChange={vi.fn()} />
    );

    fireEvent.click(screen.getByText('Personalizar para este cliente'));

    expect(screen.getByPlaceholderText('100.00')).toBeInTheDocument();
    expect(screen.getByText('Padrão: R$ 100.00')).toBeInTheDocument();
  });

  it('calls onChange with the updated price when the price input changes', () => {
    const handleChange = vi.fn();
    render(
      <ServiceCustomizationPanel defaultPrice={100} values={emptyValues} onChange={handleChange} />
    );

    fireEvent.click(screen.getByText('Personalizar para este cliente'));
    fireEvent.change(screen.getByPlaceholderText('100.00'), { target: { value: '200' } });

    expect(handleChange).toHaveBeenCalledWith({ ...emptyValues, price: '200' });
  });

  it('calls onChange with the updated notes when the notes textarea changes', () => {
    const handleChange = vi.fn();
    render(
      <ServiceCustomizationPanel defaultPrice={100} values={emptyValues} onChange={handleChange} />
    );

    fireEvent.click(screen.getByText('Personalizar para este cliente'));
    fireEvent.change(screen.getByPlaceholderText('Ex.: cabelo mais longo, precisa de mais tempo'), {
      target: { value: 'Cabelo longo' },
    });

    expect(handleChange).toHaveBeenCalledWith({ ...emptyValues, notes: 'Cabelo longo' });
  });

  it('shows "não definido" placeholder when there is no catalog default', () => {
    render(
      <ServiceCustomizationPanel defaultPrice={null} values={emptyValues} onChange={vi.fn()} />
    );

    fireEvent.click(screen.getByText('Personalizar para este cliente'));

    expect(screen.getByText('Padrão: não definido')).toBeInTheDocument();
  });
});
