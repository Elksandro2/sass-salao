package com.cristiane.salon.models.product.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.BusinessException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.appointment.repository.AppointmentProductItemRepository;
import com.cristiane.salon.models.product.dto.ProductFilter;
import com.cristiane.salon.models.product.dto.ProductRequest;
import com.cristiane.salon.models.product.dto.ProductResponse;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.repository.ProductRepository;
import com.cristiane.salon.models.product.specification.ProductSpecifications;
import com.cristiane.salon.models.service.repository.SalonServiceProductUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final AppointmentProductItemRepository appointmentProductItemRepository;
    private final SalonServiceProductUsageRepository serviceProductUsageRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(ProductFilter filter, Pageable pageable) {
        return productRepository.findAll(ProductSpecifications.filter(filter), pageable)
                .map(ProductResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        return ProductResponse.fromEntity(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        boolean availableForSale = request.availableForSale() != null ? request.availableForSale() : true;
        validateSalePrice(availableForSale, request.price());

        product.setName(request.name());
        product.setPrice(request.price());
        product.setActive(request.active() != null ? request.active() : true);
        product.setBrand(blankToNull(request.brand()));
        product.setCostPrice(request.costPrice());
        product.setCapacity(request.capacity());
        product.setUnit(request.unit());
        product.setAvailableForSale(availableForSale);
        product.setUsedInServiceRecipe(request.usedInServiceRecipe() != null ? request.usedInServiceRecipe() : true);

        return ProductResponse.fromEntity(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        Boolean existingAvailableForSale = product.getAvailableForSale();
        boolean availableForSale = request.availableForSale() != null
                ? request.availableForSale()
                : existingAvailableForSale == null || existingAvailableForSale;
        BigDecimal effectivePrice = request.price() != null ? request.price() : product.getPrice();
        validateSalePrice(availableForSale, effectivePrice);

        if (request.name() != null) product.setName(request.name());
        if (request.price() != null) product.setPrice(request.price());
        if (request.active() != null) product.setActive(request.active());
        product.setAvailableForSale(availableForSale);
        if (request.usedInServiceRecipe() != null) product.setUsedInServiceRecipe(request.usedInServiceRecipe());
        product.setBrand(blankToNull(request.brand()));
        product.setCostPrice(request.costPrice());
        product.setCapacity(request.capacity());
        product.setUnit(request.unit());

        return ProductResponse.fromEntity(productRepository.save(product));
    }

    /** Preço de venda só é obrigatório quando o produto aparece pra venda — uso interno fica sem. */
    private void validateSalePrice(boolean availableForSale, BigDecimal price) {
        if (availableForSale && price == null) {
            throw new BadRequestException(
                    "O preço de venda é obrigatório para produtos disponíveis para venda");
        }
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        product.setActive(false);
        productRepository.save(product);
    }

    /**
     * Exclusão definitiva (some do banco de vez) — diferente de {@link #delete}, que só
     * desativa e mantém o cadastro (reversível via {@link #reactivate}). Só permitida quando o
     * produto nunca foi vendido em nenhum atendimento nem consta na receita de nenhum serviço:
     * apagar de vez um produto com histórico corromperia esses registros.
     */
    @Transactional
    public void permanentlyDelete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        if (appointmentProductItemRepository.existsByProductId(id)) {
            throw new BusinessException(
                    "Não é possível excluir um produto já vendido em algum atendimento — desative-o em vez de excluir.");
        }
        if (serviceProductUsageRepository.existsByProductId(id)) {
            throw new BusinessException(
                    "Não é possível excluir um produto usado na receita de algum serviço — remova-o da receita primeiro.");
        }

        productRepository.delete(product);
    }

    @Transactional
    public ProductResponse reactivate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        product.setActive(true);
        return ProductResponse.fromEntity(productRepository.save(product));
    }
}

