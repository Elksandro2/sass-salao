package com.cristiane.salon.models.product.service;

import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.product.dto.ProductFilter;
import com.cristiane.salon.models.product.dto.ProductRequest;
import com.cristiane.salon.models.product.dto.ProductResponse;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Test
    void findAll_shouldReturnPageFromRepository() {
        // Arrange
        Product p1 = new Product(1L, "P1", new BigDecimal("10.0"), true, null, null, null, null, null, null);
        Product p2 = new Product(2L, "P2", new BigDecimal("20.0"), false, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(Arrays.asList(p1, p2));
        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act
        Page<ProductResponse> result = productService.findAll(new ProductFilter(null, null), pageable);

        // Assert
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).name()).isEqualTo("P1");
        assertThat(result.getContent().get(1).name()).isEqualTo("P2");
        verify(productRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findById_shouldReturnProduct_whenProductExists() {
        // Arrange
        Long id = 1L;
        Product p = new Product(id, "P", new BigDecimal("10.0"), true, null, null, null, null, null, null);
        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        // Act
        ProductResponse result = productService.findById(id);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void findById_shouldThrowResourceNotFoundException_whenProductDoesNotExist() {
        // Arrange
        Long id = 1L;
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto não encontrado");
    }

    @Test
    void create_shouldSaveProductWithDefaultValues_whenRequestFieldsAreNull() {
        // Arrange
        ProductRequest request = new ProductRequest("New Product", new BigDecimal("15.0"), null, null, null, null, null, null, null);
        Product saved = new Product(1L, "New Product", new BigDecimal("15.0"), true, null, null, null, null, null, null);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        // Act
        ProductResponse result = productService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.active()).isTrue();

        verify(productRepository).save(argThat(product ->
                product.getName().equals("New Product") &&
                product.getPrice().equals(new BigDecimal("15.0")) &&
                product.getActive()
        ));
    }

    @Test
    void create_shouldSaveProductWithSpecifiedValues_whenRequestFieldsAreProvided() {
        // Arrange
        ProductRequest request = new ProductRequest("Custom Product", new BigDecimal("15.0"), false, null, null, null, null, null, null);
        Product saved = new Product(1L, "Custom Product", new BigDecimal("15.0"), false, null, null, null, null, null, null);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        // Act
        ProductResponse result = productService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.active()).isFalse();
    }

    @Test
    void update_shouldThrowResourceNotFoundException_whenProductDoesNotExist() {
        // Arrange
        Long id = 1L;
        ProductRequest request = new ProductRequest("Updated", new BigDecimal("20.0"), true, null, null, null, null, null, null);
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_shouldOnlyUpdateNonNullFields() {
        // Arrange
        Long id = 1L;
        Product product = new Product(id, "Old Name", new BigDecimal("10.0"), true, null, null, null, null, null, null);
        ProductRequest request = new ProductRequest("New Name", new BigDecimal("12.0"), null, null, null, null, null, null, null);

        Product saved = new Product(id, "New Name", new BigDecimal("12.0"), true, null, null, null, null, null, null);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        // Act
        ProductResponse result = productService.update(id, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.price()).isEqualTo(new BigDecimal("12.0"));
        assertThat(result.active()).isTrue();

        verify(productRepository).save(argThat(p ->
                p.getName().equals("New Name") &&
                p.getPrice().equals(new BigDecimal("12.0")) &&
                p.getActive()
        ));
    }

    @Test
    void delete_shouldMarkProductAsInactive() {
        // Arrange
        Long id = 1L;
        Product product = new Product(id, "P", new BigDecimal("10.0"), true, null, null, null, null, null, null);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // Act
        productService.delete(id);

        // Assert
        assertThat(product.getActive()).isFalse();
        verify(productRepository).save(product);
    }

    @Test
    void delete_shouldThrowResourceNotFoundException_whenProductDoesNotExist() {
        // Arrange
        Long id = 1L;
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reactivate_shouldMarkProductAsActive() {
        // Arrange
        Long id = 1L;
        Product product = new Product(id, "P", new BigDecimal("10.0"), false, null, null, null, null, null, null);
        Product saved = new Product(id, "P", new BigDecimal("10.0"), true, null, null, null, null, null, null);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(saved);

        // Act
        ProductResponse result = productService.reactivate(id);

        // Assert
        assertThat(result.active()).isTrue();
        assertThat(product.getActive()).isTrue();
        verify(productRepository).save(product);
    }

    @Test
    void reactivate_shouldThrowResourceNotFoundException_whenProductDoesNotExist() {
        // Arrange
        Long id = 1L;
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.reactivate(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
