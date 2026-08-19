package com.cristiane.salon.models.service.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.service.dto.SalonServiceFilter;
import com.cristiane.salon.models.service.dto.SalonServiceRequest;
import com.cristiane.salon.models.service.dto.SalonServiceResponse;
import com.cristiane.salon.models.service.dto.ServiceProductUsageRequest;
import com.cristiane.salon.models.service.entity.SalonService;
import com.cristiane.salon.models.service.repository.SalonServiceRepository;
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
class SalonServiceManagerTest {

    @InjectMocks
    private SalonServiceManager salonServiceManager;

    @Mock
    private SalonServiceRepository salonServiceRepository;

    @Mock
    private com.cristiane.salon.models.service.repository.SalonServiceProductUsageRepository serviceProductUsageRepository;

    @Mock
    private com.cristiane.salon.models.product.repository.ProductRepository productRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(serviceProductUsageRepository.findBySalonServiceId(any())).thenReturn(java.util.List.of());
    }

    @Test
    void findAll_shouldReturnPageFromRepository() {
        // Arrange
        SalonService s1 = new SalonService(1L, "Corte", "Desc", new BigDecimal("50.0"), true);
        SalonService s2 = new SalonService(2L, "Barba", "Desc", new BigDecimal("30.0"), false);
        Pageable pageable = PageRequest.of(0, 10);
        Page<SalonService> page = new PageImpl<>(Arrays.asList(s1, s2));
        when(salonServiceRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act
        Page<SalonServiceResponse> result = salonServiceManager.findAll(new SalonServiceFilter(null, null), pageable);

        // Assert
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).name()).isEqualTo("Corte");
        assertThat(result.getContent().get(1).name()).isEqualTo("Barba");
        verify(salonServiceRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findById_shouldReturnService_whenServiceExists() {
        // Arrange
        Long id = 1L;
        SalonService s = new SalonService(id, "Corte", "Desc", new BigDecimal("50.0"), true);
        when(salonServiceRepository.findById(id)).thenReturn(Optional.of(s));

        // Act
        SalonServiceResponse result = salonServiceManager.findById(id);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void findById_shouldThrowResourceNotFoundException_whenServiceDoesNotExist() {
        // Arrange
        Long id = 1L;
        when(salonServiceRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> salonServiceManager.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Serviço não encontrado");
    }

    @Test
    void create_shouldThrowBadRequestException_whenPriceIsNegative() {
        // Arrange
        SalonServiceRequest request = new SalonServiceRequest("Corte", "Desc", new BigDecimal("-10.0"), true, null);

        // Act & Assert
        assertThatThrownBy(() -> salonServiceManager.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O preço não pode ser negativo");
    }

    @Test
    void create_shouldSaveService_whenValid() {
        // Arrange
        SalonServiceRequest request = new SalonServiceRequest("Corte", "Desc", new BigDecimal("50.0"), null, null);
        SalonService saved = new SalonService(1L, "Corte", "Desc", new BigDecimal("50.0"), true);
        when(salonServiceRepository.save(any(SalonService.class))).thenReturn(saved);

        // Act
        SalonServiceResponse result = salonServiceManager.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.active()).isTrue(); // default active is true

        verify(salonServiceRepository).save(argThat(service ->
                service.getName().equals("Corte") && service.getActive()
        ));
    }

    @Test
    void create_shouldRespectActiveFlag_whenFalse() {
        // Arrange
        SalonServiceRequest request = new SalonServiceRequest("Corte", "Desc", new BigDecimal("50.0"), false, null);
        SalonService saved = new SalonService(1L, "Corte", "Desc", new BigDecimal("50.0"), false);
        when(salonServiceRepository.save(any(SalonService.class))).thenReturn(saved);

        // Act
        SalonServiceResponse result = salonServiceManager.create(request);

        // Assert
        assertThat(result.active()).isFalse();
    }

    @Test
    void create_withProductUsages_shouldSaveRecipeAndReturnEstimatedCost() {
        com.cristiane.salon.models.product.entity.Product shampoo = new com.cristiane.salon.models.product.entity.Product();
        shampoo.setId(30L);
        shampoo.setName("Shampoo");
        shampoo.setPrice(new BigDecimal("50.00"));
        shampoo.setCostPrice(new BigDecimal("40.00"));
        shampoo.setCapacity(new BigDecimal("1000"));
        shampoo.setUnit(com.cristiane.salon.models.product.entity.ProductUnit.ML);

        when(productRepository.findById(30L)).thenReturn(Optional.of(shampoo));
        when(serviceProductUsageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SalonService saved = new SalonService(1L, "Corte", "Desc", new BigDecimal("50.0"), true);
        when(salonServiceRepository.save(any(SalonService.class))).thenReturn(saved);

        var usageRequest = new ServiceProductUsageRequest(30L, new BigDecimal("30"));
        SalonServiceRequest request = new SalonServiceRequest("Corte", "Desc", new BigDecimal("50.0"), true, List.of(usageRequest));

        SalonServiceResponse result = salonServiceManager.create(request);

        assertThat(result.productUsages()).hasSize(1);
        assertThat(result.productUsages().get(0).productName()).isEqualTo("Shampoo");
        // custo unitário = 40/1000 = 0.04/ml; 30ml = 1.20
        assertThat(result.estimatedProductCost()).isEqualByComparingTo("1.20");
    }

    @Test
    void create_withProductUsageForUnknownProduct_shouldThrowResourceNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        SalonService saved = new SalonService(1L, "Corte", "Desc", new BigDecimal("50.0"), true);
        when(salonServiceRepository.save(any(SalonService.class))).thenReturn(saved);

        var usageRequest = new ServiceProductUsageRequest(99L, new BigDecimal("10"));
        SalonServiceRequest request = new SalonServiceRequest("Corte", "Desc", new BigDecimal("50.0"), true, List.of(usageRequest));

        assertThatThrownBy(() -> salonServiceManager.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto não encontrado");
    }

    @Test
    void update_whenProductUsagesIsNull_shouldNotTouchExistingRecipe() {
        Long id = 1L;
        SalonService service = new SalonService(id, "Corte", "Desc", new BigDecimal("50.0"), true);
        SalonService saved = new SalonService(id, "Corte", "Desc", new BigDecimal("50.0"), true);
        when(salonServiceRepository.findById(id)).thenReturn(Optional.of(service));
        when(salonServiceRepository.save(any(SalonService.class))).thenReturn(saved);

        SalonServiceRequest request = new SalonServiceRequest("Corte", "Desc", new BigDecimal("50.0"), true, null);

        salonServiceManager.update(id, request);

        verify(serviceProductUsageRepository, never()).deleteBySalonServiceId(any());
    }

    @Test
    void update_shouldThrowResourceNotFoundException_whenServiceDoesNotExist() {
        // Arrange
        Long id = 1L;
        SalonServiceRequest request = new SalonServiceRequest("Corte", "Desc", new BigDecimal("50.0"), true, null);
        when(salonServiceRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> salonServiceManager.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_shouldThrowBadRequestException_whenNewPriceIsNegative() {
        // Arrange
        Long id = 1L;
        SalonService service = new SalonService(id, "Corte", "Desc", new BigDecimal("50.0"), true);
        SalonServiceRequest request = new SalonServiceRequest("Corte", "Desc", new BigDecimal("-5.0"), true, null);

        when(salonServiceRepository.findById(id)).thenReturn(Optional.of(service));

        // Act & Assert
        assertThatThrownBy(() -> salonServiceManager.update(id, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O preço não pode ser negativo");
    }

    @Test
    void update_shouldUpdateFieldsAndSave_whenValid() {
        // Arrange
        Long id = 1L;
        SalonService service = new SalonService(id, "Old Corte", "Old Desc", new BigDecimal("40.0"), true);
        SalonServiceRequest request = new SalonServiceRequest("New Corte", "New Desc", new BigDecimal("50.0"), false, null);

        SalonService saved = new SalonService(id, "New Corte", "New Desc", new BigDecimal("50.0"), false);
        when(salonServiceRepository.findById(id)).thenReturn(Optional.of(service));
        when(salonServiceRepository.save(any(SalonService.class))).thenReturn(saved);

        // Act
        SalonServiceResponse result = salonServiceManager.update(id, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("New Corte");
        assertThat(result.description()).isEqualTo("New Desc");
        assertThat(result.price()).isEqualTo(new BigDecimal("50.0"));
        assertThat(result.active()).isFalse();

        verify(salonServiceRepository).save(argThat(s ->
                s.getName().equals("New Corte") &&
                s.getDescription().equals("New Desc") &&
                s.getPrice().equals(new BigDecimal("50.0")) &&
                !s.getActive()
        ));
    }

    @Test
    void delete_shouldMarkServiceAsInactive() {
        // Arrange
        Long id = 1L;
        SalonService service = new SalonService(id, "Corte", "Desc", new BigDecimal("50.0"), true);
        when(salonServiceRepository.findById(id)).thenReturn(Optional.of(service));

        // Act
        salonServiceManager.delete(id);

        // Assert
        assertThat(service.getActive()).isFalse();
        verify(salonServiceRepository).save(service);
    }

    @Test
    void delete_shouldThrowResourceNotFoundException_whenServiceDoesNotExist() {
        // Arrange
        Long id = 1L;
        when(salonServiceRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> salonServiceManager.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reactivate_shouldMarkServiceAsActive() {
        // Arrange
        Long id = 1L;
        SalonService service = new SalonService(id, "Corte", "Desc", new BigDecimal("50.0"), false);
        SalonService saved = new SalonService(id, "Corte", "Desc", new BigDecimal("50.0"), true);

        when(salonServiceRepository.findById(id)).thenReturn(Optional.of(service));
        when(salonServiceRepository.save(service)).thenReturn(saved);

        // Act
        SalonServiceResponse result = salonServiceManager.reactivate(id);

        // Assert
        assertThat(result.active()).isTrue();
        assertThat(service.getActive()).isTrue();
        verify(salonServiceRepository).save(service);
    }

    @Test
    void reactivate_shouldThrowResourceNotFoundException_whenServiceDoesNotExist() {
        // Arrange
        Long id = 1L;
        when(salonServiceRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> salonServiceManager.reactivate(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
