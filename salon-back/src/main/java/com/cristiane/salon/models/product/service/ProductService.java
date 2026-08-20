package com.cristiane.salon.models.product.service;

import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.product.dto.ProductFilter;
import com.cristiane.salon.models.product.dto.ProductRequest;
import com.cristiane.salon.models.product.dto.ProductResponse;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.repository.ProductRepository;
import com.cristiane.salon.models.product.specification.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

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
        product.setName(request.name());
        product.setPrice(request.price());
        product.setActive(request.active() != null ? request.active() : true);
        product.setBrand(blankToNull(request.brand()));
        product.setCostPrice(request.costPrice());
        product.setCapacity(request.capacity());
        product.setUnit(request.unit());

        return ProductResponse.fromEntity(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        if (request.name() != null) product.setName(request.name());
        if (request.price() != null) product.setPrice(request.price());
        if (request.active() != null) product.setActive(request.active());
        product.setBrand(blankToNull(request.brand()));
        product.setCostPrice(request.costPrice());
        product.setCapacity(request.capacity());
        product.setUnit(request.unit());

        return ProductResponse.fromEntity(productRepository.save(product));
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

    @Transactional
    public ProductResponse reactivate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        product.setActive(true);
        return ProductResponse.fromEntity(productRepository.save(product));
    }
}

