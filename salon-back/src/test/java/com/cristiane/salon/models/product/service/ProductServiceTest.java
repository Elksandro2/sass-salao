package com.cristiane.salon.models.product.service;

import com.cristiane.salon.exception.BusinessException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.appointment.repository.AppointmentProductItemRepository;
import com.cristiane.salon.models.product.dto.ProductFilter;
import com.cristiane.salon.models.product.dto.ProductRequest;
import com.cristiane.salon.models.product.dto.ProductResponse;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.repository.ProductRepository;
import com.cristiane.salon.models.service.repository.SalonServiceProductUsageRepository;
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

    @Mock
    private AppointmentProductItemRepository appointmentProductItemRepository;

    @Mock
    private SalonServiceProductUsageRepository serviceProductUsageRepository;

    @Test
    void findAll_shouldReturnPageFromRepository() {
        // Arrange
        Product p1 = new Product(1L, "P1", new BigDecimal("10.0"), true, null, null, null, null, null, null);
        Product p2 = new Product(2L, "P2", new BigDecimal("20.0"), false, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(Arrays.asList(p1, p2));
        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act
        Page<ProductResponse> result = productService.findAll(new ProductFilter(null, null, null, null), pageable);

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
    void permanentlyDelete_whenNeverUsed_shouldRemoveFromDatabase() {
        Long id = 1L;
        Product product = new Product(id, "P", new BigDecimal("10.0"), true, null, null, null, null, null, null);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(appointmentProductItemRepository.existsByProductId(id)).thenReturn(false);
        when(serviceProductUsageRepository.existsByProductId(id)).thenReturn(false);

        productService.permanentlyDelete(id);

        verify(productRepository).delete(product);
    }

    @Test
    void permanentlyDelete_whenNotFound_shouldThrowResourceNotFoundException() {
        Long id = 1L;
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.permanentlyDelete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void permanentlyDelete_whenAlreadySoldInAppointment_shouldThrowBusinessException() {
        Long id = 1L;
        Product product = new Product(id, "P", new BigDecimal("10.0"), true, null, null, null, null, null, null);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(appointmentProductItemRepository.existsByProductId(id)).thenReturn(true);

        assertThatThrownBy(() -> productService.permanentlyDelete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já vendido");
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void permanentlyDelete_whenUsedInServiceRecipe_shouldThrowBusinessException() {
        Long id = 1L;
        Product product = new Product(id, "P", new BigDecimal("10.0"), true, null, null, null, null, null, null);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(appointmentProductItemRepository.existsByProductId(id)).thenReturn(false);
        when(serviceProductUsageRepository.existsByProductId(id)).thenReturn(true);

        assertThatThrownBy(() -> productService.permanentlyDelete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("receita");
        verify(productRepository, never()).delete(any(Product.class));
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

    // --- Produtos "de uso" (sem preço de venda) --------------------------------------------

    @Test
    void create_whenAvailableForSaleAndNoPrice_shouldThrowBadRequestException() {
        ProductRequest request = new ProductRequest(
                "Óleo de coco", null, null, null, null, null, null, true, false);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(com.cristiane.salon.exception.BadRequestException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_whenUseOnlyProductWithoutPrice_shouldSucceed() {
        ProductRequest request = new ProductRequest(
                "Óleo de coco", null, null, null, new BigDecimal("40.00"), new BigDecimal("1000"),
                com.cristiane.salon.models.product.entity.ProductUnit.ML, false, true);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse result = productService.create(request);

        assertThat(result.price()).isNull();
        assertThat(result.availableForSale()).isFalse();
        assertThat(result.usedInServiceRecipe()).isTrue();
    }

    @Test
    void create_whenAvailableForSaleDefaultsTrueAndNoPrice_shouldThrowBadRequestException() {
        // availableForSale omitido (null) -> ProductService assume true por padrão na criação
        ProductRequest request = new ProductRequest(
                "Shampoo", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(com.cristiane.salon.exception.BadRequestException.class);
    }

    @Test
    void update_whenTogglingToAvailableForSaleWithoutAnyPrice_shouldThrowBadRequestException() {
        Long id = 1L;
        Product existing = new Product(id, "Óleo", null, true, null, null, null, null, false, true);
        when(productRepository.findById(id)).thenReturn(Optional.of(existing));

        ProductRequest request = new ProductRequest(
                null, null, null, null, null, null, null, true, null);

        assertThatThrownBy(() -> productService.update(id, request))
                .isInstanceOf(com.cristiane.salon.exception.BadRequestException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_whenKeepingExistingPriceAndTogglingAvailableForSale_shouldSucceed() {
        Long id = 1L;
        Product existing = new Product(id, "Óleo", new BigDecimal("30.00"), true, null, null, null, null, false, true);
        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest(
                null, null, null, null, null, null, null, true, null);

        ProductResponse result = productService.update(id, request);

        assertThat(result.availableForSale()).isTrue();
        assertThat(result.price()).isEqualByComparingTo("30.00");
    }
}
