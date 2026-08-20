package com.cristiane.salon.models.cashflow.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.audit.AuditLogService;
import com.cristiane.salon.models.cashflow.dto.CashFlowItemRequest;
import com.cristiane.salon.models.cashflow.dto.CashFlowRequest;
import com.cristiane.salon.models.cashflow.dto.CashFlowResponse;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.cashflow.repository.CashFlowRepository;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.repository.ProductRepository;
import com.cristiane.salon.models.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.cristiane.salon.config.SalonClock;
import java.time.ZoneId;

@ExtendWith(MockitoExtension.class)
class CashFlowServiceTest {

    @Mock
    private CashFlowRepository cashFlowRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    // SalonClock real, não mock: os testes dependem do "hoje"/"agora" de verdade no fuso
    // do salão, e um mock devolveria null silenciosamente.
    @Spy
    private SalonClock salonClock = new SalonClock(ZoneId.of("America/Recife"));

    @InjectMocks
    private CashFlowService cashFlowService;

    private Product activeProduct;
    private Product inactiveProduct;

    @BeforeEach
    void setUp() {
        activeProduct = new Product();
        activeProduct.setId(1L);
        activeProduct.setName("Shampoo");
        activeProduct.setPrice(BigDecimal.valueOf(50.00));
        activeProduct.setActive(true);

        inactiveProduct = new Product();
        inactiveProduct.setId(2L);
        inactiveProduct.setName("Condicionador");
        inactiveProduct.setPrice(BigDecimal.valueOf(40.00));
        inactiveProduct.setActive(false);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findByPeriod_whenDatesPassed_shouldQueryRepository() {
        // Arrange
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 15);
        CashFlow cf = new CashFlow();
        cf.setId(10L);
        cf.setType(CashFlowType.INCOME);
        cf.setAmount(BigDecimal.TEN);
        cf.setDate(LocalDate.of(2026, 6, 5));
        when(cashFlowRepository.findByDateBetween(from, to)).thenReturn(List.of(cf));

        // Act
        List<CashFlowResponse> result = cashFlowService.findByPeriod(from, to);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        verify(cashFlowRepository).findByDateBetween(from, to);
    }

    @Test
    void findByPeriod_whenFromIsNull_shouldDefaultToFirstDayOfMonth() {
        // Arrange
        LocalDate expectedFrom = salonClock.today().withDayOfMonth(1);
        LocalDate to = salonClock.today().plusDays(10);
        when(cashFlowRepository.findByDateBetween(eq(expectedFrom), eq(to))).thenReturn(List.of());

        // Act
        cashFlowService.findByPeriod(null, to);

        // Assert
        verify(cashFlowRepository).findByDateBetween(expectedFrom, to);
    }

    @Test
    void findByPeriod_whenToIsNull_shouldDefaultTo30DaysFuture() {
        // Arrange
        LocalDate from = salonClock.today().minusDays(5);
        LocalDate expectedTo = salonClock.today().plusDays(30);
        when(cashFlowRepository.findByDateBetween(eq(from), eq(expectedTo))).thenReturn(List.of());

        // Act
        cashFlowService.findByPeriod(from, null);

        // Assert
        verify(cashFlowRepository).findByDateBetween(from, expectedTo);
    }

    @Test
    void findByPeriodPaginated_whenDatesPassed_shouldQueryRepositoryWithPageable() {
        // Arrange
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 15);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        CashFlow cf = new CashFlow();
        cf.setId(10L);
        cf.setType(CashFlowType.INCOME);
        cf.setAmount(BigDecimal.TEN);
        cf.setDate(LocalDate.of(2026, 6, 5));
        org.springframework.data.domain.Page<CashFlow> page =
                new org.springframework.data.domain.PageImpl<>(List.of(cf));
        when(cashFlowRepository.findByDateBetween(from, to, pageable)).thenReturn(page);

        // Act
        org.springframework.data.domain.Page<CashFlowResponse> result =
                cashFlowService.findByPeriod(from, to, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(10L);
        verify(cashFlowRepository).findByDateBetween(from, to, pageable);
    }

    @Test
    void findByPeriodPaginated_whenDatesNull_shouldDefaultDates() {
        // Arrange
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        LocalDate expectedFrom = salonClock.today().withDayOfMonth(1);
        LocalDate expectedTo = salonClock.today().plusDays(30);
        when(cashFlowRepository.findByDateBetween(eq(expectedFrom), eq(expectedTo), eq(pageable)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        // Act
        cashFlowService.findByPeriod(null, null, pageable);

        // Assert
        verify(cashFlowRepository).findByDateBetween(expectedFrom, expectedTo, pageable);
    }

    // --- create with items (Product Sale) ---

    @Test
    void create_whenSaleAndTypeIsNotIncome_shouldThrowBadRequestException() {
        // Arrange
        CashFlowItemRequest item = new CashFlowItemRequest(1L, 2);
        CashFlowRequest request = new CashFlowRequest("EXPENSE", BigDecimal.ZERO, "desc", salonClock.today(), null, List.of(item));

        // Act & Assert
        assertThatThrownBy(() -> cashFlowService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Venda de produtos deve ser um registro de entrada (INCOME).");
    }

    @Test
    void create_whenSaleAndProductNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        CashFlowItemRequest item = new CashFlowItemRequest(99L, 2);
        CashFlowRequest request = new CashFlowRequest("INCOME", BigDecimal.ZERO, "desc", salonClock.today(), null, List.of(item));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cashFlowService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto com ID 99 não encontrado.");
    }

    @Test
    void create_whenSaleAndProductInactive_shouldThrowBadRequestException() {
        // Arrange
        CashFlowItemRequest item = new CashFlowItemRequest(2L, 2);
        CashFlowRequest request = new CashFlowRequest("INCOME", BigDecimal.ZERO, "desc", salonClock.today(), null, List.of(item));
        when(productRepository.findById(2L)).thenReturn(Optional.of(inactiveProduct));

        // Act & Assert
        assertThatThrownBy(() -> cashFlowService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Produto 'Condicionador' não está ativo.");
    }

    @Test
    void create_whenSaleSuccessAndDefaultDescription_shouldSaveAndAudit() {
        // Arrange
        CashFlowItemRequest item1 = new CashFlowItemRequest(1L, 2); // 2 * 50 = 100
        CashFlowRequest request = new CashFlowRequest("INCOME", BigDecimal.ZERO, "Venda de Produtos", salonClock.today(), null, List.of(item1));
        when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct));

        CashFlow saved = new CashFlow();
        saved.setId(50L);
        saved.setType(CashFlowType.INCOME);
        saved.setAmount(BigDecimal.valueOf(100.00));
        saved.setDescription("Venda de Produtos: 2x Shampoo");
        saved.setDate(salonClock.today());

        when(cashFlowRepository.save(any(CashFlow.class))).thenReturn(saved);

        // Security Context
        User user = new User();
        user.setId(10L);
        user.setEmail("admin@example.com");
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin@example.com");
        when(auth.getPrincipal()).thenReturn(user);
        SecurityContext secCtx = mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);

        // Act
        CashFlowResponse response = cashFlowService.create(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.amount()).isEqualTo(BigDecimal.valueOf(100.00));
        assertThat(response.description()).isEqualTo("Venda de Produtos: 2x Shampoo");

        verify(cashFlowRepository).save(any(CashFlow.class));
        verify(auditLogService).logAction(
                eq(10L),
                eq("admin@example.com"),
                eq("PRODUCT_SALE_REGISTERED"),
                eq("CashFlow"),
                eq(50L),
                any(),
                eq("SUCCESS")
        );
    }

    @Test
    void create_whenSaleSuccessAndCustomDescription_shouldConcatenateItemsDescription() {
        // Arrange
        CashFlowItemRequest item1 = new CashFlowItemRequest(1L, 1);
        CashFlowRequest request = new CashFlowRequest("INCOME", BigDecimal.ZERO, "Venda especial", salonClock.today(), null, List.of(item1));
        when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct));

        CashFlow saved = new CashFlow();
        saved.setId(50L);
        saved.setType(CashFlowType.INCOME);
        saved.setAmount(BigDecimal.valueOf(50.00));
        saved.setDescription("Venda especial (1x Shampoo)");
        saved.setDate(salonClock.today());

        when(cashFlowRepository.save(any(CashFlow.class))).thenReturn(saved);

        // Act
        CashFlowResponse response = cashFlowService.create(request);

        // Assert
        assertThat(response.description()).isEqualTo("Venda especial (1x Shampoo)");
    }

    // --- create without items ---

    @Test
    void create_whenNoItemsAndInvalidType_shouldThrowBadRequestException() {
        // Arrange
        CashFlowRequest request = new CashFlowRequest("INVALID", BigDecimal.TEN, "desc", salonClock.today(), null, null);

        // Act & Assert
        assertThatThrownBy(() -> cashFlowService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tipo de fluxo de caixa inválido. Use INCOME ou EXPENSE.");
    }

    @Test
    void create_whenNoItemsAndAppointmentNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        CashFlowRequest request = new CashFlowRequest("INCOME", BigDecimal.TEN, "desc", salonClock.today(), 99L, null);
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cashFlowService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Agendamento não encontrado");
    }

    @Test
    void create_whenNoItemsSuccess_shouldSaveAndAuditWithSystemUser() throws JsonProcessingException {
        // Arrange
        Appointment app = new Appointment();
        app.setId(5L);
        CashFlowRequest request = new CashFlowRequest("EXPENSE", BigDecimal.TEN, "desc", salonClock.today(), 5L, null);
        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(app));

        CashFlow saved = new CashFlow();
        saved.setId(60L);
        saved.setType(CashFlowType.EXPENSE);
        saved.setAmount(BigDecimal.TEN);
        saved.setDescription("desc");
        saved.setDate(salonClock.today());
        saved.setAppointment(app);
        when(cashFlowRepository.save(any(CashFlow.class))).thenReturn(saved);

        // Security Context is null (e.g. system background)
        SecurityContextHolder.clearContext();

        // Act
        CashFlowResponse response = cashFlowService.create(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(60L);
        verify(cashFlowRepository).save(any(CashFlow.class));
        verify(auditLogService).logAction(
                isNull(),
                eq("SYSTEM"),
                eq("CASHFLOW_ENTRY_CREATED"),
                eq("CashFlow"),
                eq(60L),
                any(),
                eq("SUCCESS")
        );
    }

    @Test
    void create_whenAuditLogThrowsException_shouldSilentFail() {
        // Arrange
        CashFlowRequest request = new CashFlowRequest("INCOME", BigDecimal.TEN, "desc", salonClock.today(), null, null);
        CashFlow saved = new CashFlow();
        saved.setId(60L);
        saved.setType(CashFlowType.INCOME);
        saved.setAmount(BigDecimal.TEN);
        saved.setDescription("desc");
        saved.setDate(salonClock.today());
        when(cashFlowRepository.save(any(CashFlow.class))).thenReturn(saved);

        // force audit mock to throw exception
        doThrow(new RuntimeException("Audit service error")).when(auditLogService)
                .logAction(any(), any(), any(), any(), any(), any(), any());

        // Act & Assert (Should not throw exception)
        CashFlowResponse response = cashFlowService.create(request);
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(60L);
    }

    // --- delete ---

    @Test
    void delete_whenCashFlowNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(cashFlowRepository.existsById(99L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> cashFlowService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Registro não encontrado");
        verify(cashFlowRepository, never()).deleteById(any());
    }

    @Test
    void delete_whenCashFlowExists_shouldCallDeleteById() {
        // Arrange
        when(cashFlowRepository.existsById(10L)).thenReturn(true);

        // Act
        cashFlowService.delete(10L);

        // Assert
        verify(cashFlowRepository).deleteById(10L);
    }
}
