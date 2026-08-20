package com.cristiane.salon.models.appointment.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.exception.BusinessException;
import com.cristiane.salon.models.appointment.dto.GeneratePixRequest;
import com.cristiane.salon.models.appointment.dto.AppointmentRequest;
import com.cristiane.salon.models.appointment.dto.AppointmentResponse;
import com.cristiane.salon.models.appointment.dto.AppointmentServiceRequest;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.repository.CashFlowRepository;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.service.entity.SalonService;
import com.cristiane.salon.models.service.repository.SalonServiceRepository;
import com.cristiane.salon.integrations.email.service.EmailService;
import com.cristiane.salon.integrations.push.service.PushService;
import com.cristiane.salon.models.featureflag.service.FeatureFlagService;
import com.cristiane.salon.models.salonprofile.service.SalonProfileService;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import com.cristiane.salon.models.audit.AuditLogService;
import com.cristiane.salon.integrations.payment.service.MercadoPagoPaymentService;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentPointOfInteraction;
import com.mercadopago.resources.payment.PaymentTransactionData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.cristiane.salon.config.SalonClock;
import java.time.ZoneId;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SalonServiceRepository salonServiceRepository;

    @Mock
    private com.cristiane.salon.models.product.repository.ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CashFlowRepository cashFlowRepository;

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private EmailService emailService;

    @Mock
    private PushService pushService;

    @Mock
    private SalonProfileService salonProfileService;

    @Mock
    private MercadoPagoPaymentService mercadoPagoPaymentService;

    @Mock
    private com.cristiane.salon.integrations.payment.marketplace.SplitPaymentResolver splitPaymentResolver;

    @Mock
    private AuditLogService auditLogService;

    // SalonClock real, não mock: os testes dependem do "hoje"/"agora" de verdade no fuso
    // do salão, e um mock devolveria null silenciosamente.
    @Spy
    private SalonClock salonClock = new SalonClock(ZoneId.of("America/Recife"));

    @InjectMocks
    private AppointmentService appointmentService;

    private User clientUser;
    private User staffUser;
    /** Dona do Employee usado nos testes (employee.user.id == 12). */
    private User professionalUser;
    /** Outra funcionária, para provar que ela NÃO alcança os atendimentos da colega. */
    private User otherProfessionalUser;
    private Employee employee;
    private SalonService salonService;

    @BeforeEach
    void setUp() {
        clientUser = new User();
        clientUser.setId(10L);
        clientUser.setName("Cliente");
        clientUser.setEmail("client@example.com");
        clientUser.setRole(new com.cristiane.salon.models.user.entity.Role(1L, "CLIENTE", null));

        staffUser = new User();
        staffUser.setId(11L);
        staffUser.setName("Admin");
        staffUser.setEmail("admin@example.com");
        staffUser.setRole(new com.cristiane.salon.models.user.entity.Role(2L, "ADMIN", null));

        employee = new Employee();
        employee.setId(5L);
        employee.setUser(new User());
        employee.getUser().setId(12L);

        professionalUser = new User();
        professionalUser.setId(12L);
        professionalUser.setName("Profissional");
        professionalUser.setEmail("profissional@example.com");
        professionalUser.setRole(new com.cristiane.salon.models.user.entity.Role(3L, "FUNCIONARIA", null));

        otherProfessionalUser = new User();
        otherProfessionalUser.setId(13L);
        otherProfessionalUser.setName("Colega");
        otherProfessionalUser.setEmail("colega@example.com");
        otherProfessionalUser.setRole(new com.cristiane.salon.models.user.entity.Role(3L, "FUNCIONARIA", null));

        salonService = new SalonService();
        salonService.setId(8L);
        salonService.setName("Corte");
        salonService.setPrice(BigDecimal.valueOf(100.00));
        salonService.setActive(true);

        // Padrão permissivo: testes que não são sobre horário de funcionamento não precisam
        // se preocupar com isso. Os testes dedicados ao bloqueio sobrescrevem para false.
        lenient().when(salonProfileService.isDayOpen(any())).thenReturn(true);

        // Padrão: sem split (comportamento igual ao que já existia antes da feature). Os
        // testes dedicados ao split sobrescrevem isso.
        lenient().when(splitPaymentResolver.resolve(any(), any())).thenReturn(java.util.Optional.empty());

        // Padrão: agindo como ADMIN. Agora que confirm/decline/updateStatus checam de quem é o
        // agendamento, todos eles precisam de um usuário autenticado no contexto.
        mockAuthenticatedUser(staffUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthenticatedUser(User user) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(user.getEmail());
        SecurityContext secCtx = mock(SecurityContext.class);
        lenient().when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private com.cristiane.salon.models.appointment.entity.AppointmentServiceItem withService(
            Appointment appointment, SalonService svc) {
        return withService(appointment, svc, null, null);
    }

    private com.cristiane.salon.models.appointment.entity.AppointmentServiceItem withService(
            Appointment appointment, SalonService svc, BigDecimal customPrice, String customServiceNotes) {
        com.cristiane.salon.models.appointment.entity.AppointmentServiceItem item =
                new com.cristiane.salon.models.appointment.entity.AppointmentServiceItem();
        item.setAppointment(appointment);
        item.setSalonService(svc);
        item.setCustomPrice(customPrice);
        item.setCustomServiceNotes(customServiceNotes);
        appointment.getServices().add(item);
        return item;
    }

    @Test
    void create_whenUserNotAuthenticated_shouldThrowUnauthorizedException() {
        // Arrange
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("unknown@example.com");
        SecurityContext secCtx = mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, null, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Usuário não autenticado");
    }

    @Test
    void create_whenClientAndPortalDisabled_shouldThrowAccessDeniedException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(false);
        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, null, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("O portal do cliente está temporariamente desativado.");
    }

    @Test
    void create_whenClientAndBookingDisabled_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(false);
        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, null, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Agendamentos online para clientes estão temporariamente desativados.");
    }

    @Test
    void create_whenStaffAndClientNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, null, 99L);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cliente não encontrado");
    }

    @Test
    void create_whenEmployeeNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.empty());

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, null, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Profissional não encontrado");
    }

    @Test
    void create_whenServiceNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.empty());

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, null, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Serviço não encontrado");
    }

    @Test
    void create_whenServiceInactive_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        salonService.setActive(false);
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, null, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Este serviço não está disponível: Corte");
    }

    // --- Staff create flow tests ---

    @Test
    void create_whenStaffFlowAndScheduledAtNull_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, null, 10L);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Informe data e hora do agendamento");
    }

    @Test
    void create_whenStaffFlowAndScheduledAtInPast_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, salonClock.now().minusDays(1), null, null, 10L);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Não é possível agendar no passado");
    }

    @Test
    void create_whenStaffFlowAndPreferredDateInPast_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, salonClock.now().plusDays(1), salonClock.today().minusDays(1), null, 10L);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A data preferida deve ser hoje ou uma data futura");
    }

    @Test
    void create_whenStaffFlowSuccess_shouldSaveAndSendConfirmation() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        LocalDateTime targetTime = salonClock.now().plusDays(1);

        Appointment saved = new Appointment();
        saved.setId(100L);
        saved.setClient(clientUser);
        saved.setEmployee(employee);
        withService(saved, salonService);
        saved.setScheduledAt(targetTime);
        saved.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.save(any(Appointment.class))).thenReturn(saved);

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, targetTime, salonClock.today().plusDays(1), "notes", 10L);

        // Act
        AppointmentResponse result = appointmentService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED.name());
        verify(emailService).sendConfirmationNotificationToClient(saved);
        // Horário de funcionamento (issue #116) só bloqueia a PREFERÊNCIA do cliente — a equipe
        // continua livre para encaixar alguém fora do expediente, mesmo com preferredDate setado.
        verify(salonProfileService, never()).isDayOpen(any());
    }

    @Test
    void create_whenFuncionariaCreatesForHerself_shouldSucceed() {
        // Arrange — cliente chega no salão, a própria funcionária cadastra o atendimento dela.
        mockAuthenticatedUser(professionalUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        LocalDateTime targetTime = salonClock.now().plusDays(1);
        Appointment saved = new Appointment();
        saved.setId(101L);
        saved.setClient(clientUser);
        saved.setEmployee(employee);
        withService(saved, salonService);
        saved.setScheduledAt(targetTime);
        saved.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(saved);

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, targetTime, null, null, 10L);

        AppointmentResponse result = appointmentService.create(request);

        assertThat(result.id()).isEqualTo(101L);
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void create_whenFuncionariaCreatesForAnotherEmployee_shouldThrowUnauthorized() {
        // Arrange — ela não pode agendar em nome de uma colega, só de si mesma.
        Employee colleagueEmployee = new Employee();
        colleagueEmployee.setId(6L);
        colleagueEmployee.setUser(otherProfessionalUser);

        mockAuthenticatedUser(professionalUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(6L)).thenReturn(Optional.of(colleagueEmployee));

        LocalDateTime targetTime = salonClock.now().plusDays(1);
        AppointmentRequest request = new AppointmentRequest(6L, List.of(new AppointmentServiceRequest(8L, null, null)), null, targetTime, null, null, 10L);

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("profissional responsável");
        verify(appointmentRepository, never()).save(any());
    }

    // --- Serviço como template (customPrice/customServiceNotes) ---

    @Test
    void create_whenStaffFlowWithCustomServiceValues_shouldPersistThemOnTheAppointment() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        LocalDateTime targetTime = salonClock.now().plusDays(1);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, new BigDecimal("200.00"), "Cabelo mais longo, precisa de mais tempo")), null, targetTime, null, null, 10L);

        // Act
        AppointmentResponse result = appointmentService.create(request);

        // Assert: cadastro do serviço (salonService) permanece intacto — só o agendamento leva o override.
        assertThat(result.services()).hasSize(1);
        var item = result.services().get(0);
        assertThat(item.customPrice()).isEqualByComparingTo("200.00");
        assertThat(item.customServiceNotes()).isEqualTo("Cabelo mais longo, precisa de mais tempo");
        assertThat(item.effectivePrice()).isEqualByComparingTo("200.00");
        assertThat(result.totalPrice()).isEqualByComparingTo("200.00");
        assertThat(salonService.getPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void create_whenStaffFlowWithoutCustomServiceValues_shouldFallBackToCatalogValues() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        LocalDateTime targetTime = salonClock.now().plusDays(1);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, targetTime, null, null, 10L);

        // Act
        AppointmentResponse result = appointmentService.create(request);

        // Assert
        assertThat(result.services()).hasSize(1);
        var item = result.services().get(0);
        assertThat(item.customPrice()).isNull();
        assertThat(result.totalPrice()).isEqualByComparingTo(salonService.getPrice());
    }

    // --- Múltiplos serviços por agendamento ---

    @Test
    void create_whenStaffFlowWithMultipleServices_shouldSumPricesAndPersistBothItems() {
        // Arrange: dois serviços no mesmo agendamento, cada um com seu próprio override.
        mockAuthenticatedUser(staffUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        SalonService coloring = new SalonService();
        coloring.setId(9L);
        coloring.setName("Coloração");
        coloring.setPrice(BigDecimal.valueOf(150.00));
        coloring.setActive(true);
        when(salonServiceRepository.findById(9L)).thenReturn(Optional.of(coloring));

        LocalDateTime targetTime = salonClock.now().plusDays(1);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentRequest request = new AppointmentRequest(
                5L,
                List.of(
                        new AppointmentServiceRequest(8L, null, null),
                        new AppointmentServiceRequest(9L, new BigDecimal("120.00"), null)
                ),
                null,
                targetTime, null, null, 10L
        );

        // Act
        AppointmentResponse result = appointmentService.create(request);

        // Assert: soma dos dois serviços (100 catálogo + 120 customizado)
        assertThat(result.services()).hasSize(2);
        assertThat(result.totalPrice()).isEqualByComparingTo("220.00");
    }

    @Test
    void create_whenClientFlowWithMultipleServices_shouldIgnoreCustomPriceOverride() {
        // Arrange: cliente tenta injetar customPrice, mas o fluxo do cliente deve ignorá-lo.
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        SalonService coloring = new SalonService();
        coloring.setId(9L);
        coloring.setName("Coloração");
        coloring.setPrice(BigDecimal.valueOf(150.00));
        coloring.setActive(true);
        when(salonServiceRepository.findById(9L)).thenReturn(Optional.of(coloring));

        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentRequest request = new AppointmentRequest(
                5L,
                List.of(
                        new AppointmentServiceRequest(8L, null, null),
                        new AppointmentServiceRequest(9L, new BigDecimal("1.00"), null)
                ),
                null,
                null, null, null, null
        );

        // Act
        AppointmentResponse result = appointmentService.create(request);

        // Assert: preço customizado enviado pelo cliente é ignorado — usa o catálogo (100 + 150 = 250)
        assertThat(result.services()).hasSize(2);
        assertThat(result.totalPrice()).isEqualByComparingTo("250.00");
        result.services().forEach(item -> assertThat(item.customPrice()).isNull());
    }

    @Test
    void create_whenCustomPriceIsNegative_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, new BigDecimal("-1.00"), null)), null, salonClock.now().plusDays(1), null, null, 10L);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O preço customizado não pode ser negativo");
    }

    // --- Client create flow tests ---

    @Test
    void create_whenClientFlowAndScheduledAtNotNull_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, salonClock.now().plusDays(1), null, null, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O horário será definido pelo salão após aceitar seu pedido");
    }

    @Test
    void create_whenClientFlowAndPreferredDateInPast_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, salonClock.today().minusDays(1), null, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A data preferida deve ser hoje ou uma data futura");
    }

    @Test
    void create_whenClientFlowAndPreferredDateFallsOnClosedDay_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));
        when(salonProfileService.isDayOpen(any())).thenReturn(false);

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, salonClock.today().plusDays(3), null, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O salão não funciona nesse dia da semana. Escolha outra data de preferência.");
    }

    @Test
    void create_whenClientFlowAndNoPreferredDate_shouldNotCheckBusinessHoursAtAll() {
        // Arrange: sem data de preferência, não há o que validar contra o horário de funcionamento.
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        Appointment saved = new Appointment();
        saved.setId(101L);
        saved.setClient(clientUser);
        saved.setEmployee(employee);
        withService(saved, salonService);
        saved.setStatus(AppointmentStatus.REQUESTED);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(saved);

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, null, null);

        // Act
        appointmentService.create(request);

        // Assert
        verify(salonProfileService, never()).isDayOpen(any());
    }

    @Test
    void create_whenClientFlowAndNotesTooLong_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        String longNotes = "a".repeat(4001);
        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, null, longNotes, null);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Observações muito longas (máx. 4000 caracteres)");
    }

    @Test
    void create_whenClientFlowSuccess_shouldSaveAndSendRequestNotification() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")).thenReturn(true);
        when(featureFlagService.isEnabled("CLIENT_BOOKING")).thenReturn(true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));

        Appointment saved = new Appointment();
        saved.setId(101L);
        saved.setClient(clientUser);
        saved.setEmployee(employee);
        withService(saved, salonService);
        saved.setStatus(AppointmentStatus.REQUESTED);

        when(appointmentRepository.save(any(Appointment.class))).thenReturn(saved);

        AppointmentRequest request = new AppointmentRequest(5L, List.of(new AppointmentServiceRequest(8L, null, null)), null, null, salonClock.today().plusDays(2), "my notes", null);

        // Act
        AppointmentResponse result = appointmentService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.status()).isEqualTo(AppointmentStatus.REQUESTED.name());
        verify(emailService).sendRequestNotificationToStaff(saved);
    }

    // --- confirm tests ---

    @Test
    void confirm_whenNeitherStaffNorAssignedProfessional_shouldThrowUnauthorizedException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setEmployee(employee);
        apt.setStatus(AppointmentStatus.REQUESTED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.confirm(1L, salonClock.now().plusDays(1)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("profissional responsável");
    }

    @Test
    void confirm_whenAppointmentNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.confirm(99L, salonClock.now()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Agendamento não encontrado");
    }

    @Test
    void confirm_whenStatusNotRequested_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.confirm(1L, salonClock.now()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Apenas solicitações pendentes de confirmação podem ser aprovadas");
    }

    @Test
    void confirm_whenScheduledAtInPast_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setStatus(AppointmentStatus.REQUESTED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.confirm(1L, salonClock.now().minusHours(1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Não é possível confirmar um horário no passado");
    }

    @Test
    void confirm_whenSuccess_shouldSetScheduledAtAndStatusConfirmed() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setStatus(AppointmentStatus.REQUESTED);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setClient(clientUser);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        LocalDateTime targetTime = salonClock.now().plusHours(2);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.confirm(1L, targetTime);

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED.name());
        verify(emailService).sendConfirmationNotificationToClient(apt);
        // confirm() é ação da equipe — nunca deveria ficar preso ao horário de funcionamento
        // (é justamente onde a Cristiane encaixa alguém fora do expediente, se quiser).
        verify(salonProfileService, never()).isDayOpen(any());
    }

    // --- decline tests ---

    @Test
    void decline_whenNeitherStaffNorAssignedProfessional_shouldThrowUnauthorizedException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setEmployee(employee);
        apt.setStatus(AppointmentStatus.REQUESTED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.decline(1L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("profissional responsável");
    }

    // ---------------------------------------------------------------------------------------
    // Escopo da funcionária: ela age nos atendimentos DELA, e só neles.
    // Antes desta mudança havia dois defeitos opostos: ela não conseguia definir horário nem
    // dos próprios atendimentos, e conseguia mudar o status dos atendimentos das colegas.
    // ---------------------------------------------------------------------------------------

    @Test
    void confirm_whenAssignedProfessional_shouldBeAllowed() {
        // Arrange
        mockAuthenticatedUser(professionalUser); // é a dona do employee do agendamento
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setEmployee(employee);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.REQUESTED);
        apt.getServices().add(withService(apt, salonService));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime horario = salonClock.now().plusDays(1);

        // Act
        AppointmentResponse result = appointmentService.confirm(1L, horario);

        // Assert
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.scheduledAt()).isEqualTo(horario);
    }

    @Test
    void confirm_whenOtherProfessionalsAppointment_shouldThrowUnauthorizedException() {
        // Arrange
        mockAuthenticatedUser(otherProfessionalUser); // funcionária, mas não é a atribuída
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setEmployee(employee);
        apt.setStatus(AppointmentStatus.REQUESTED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.confirm(1L, salonClock.now().plusDays(1)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("profissional responsável");
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void updateStatus_whenOtherProfessionalsAppointment_shouldThrowUnauthorizedException() {
        // Este era o furo de segurança: a migration V24 concedeu PATCH /status à FUNCIONARIA
        // sem nenhuma checagem de dono, então ela alcançava o atendimento de qualquer colega.
        // Arrange
        mockAuthenticatedUser(otherProfessionalUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setEmployee(employee);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.updateStatus(1L, "DONE"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("profissional responsável");
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void updateStatus_whenAssignedProfessional_shouldBeAllowed() {
        // Arrange
        mockAuthenticatedUser(professionalUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setEmployee(employee);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.updateStatus(1L, "CANCELLED");

        // Assert
        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    void findAll_whenProfessional_shouldForceHerOwnEmployeeIdIgnoringTheRequestedOne() {
        // Trocar ?employeeId= na URL não pode revelar a agenda das colegas.
        // Arrange
        mockAuthenticatedUser(professionalUser);
        when(employeeRepository.findByUserId(12L)).thenReturn(Optional.of(employee));
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(appointmentRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        // Act: pede explicitamente a agenda de OUTRA profissional (id 999)
        appointmentService.findAll(
                new com.cristiane.salon.models.appointment.dto.AppointmentFilter(
                        null, null, 999L, null, null, null, null), pageable);

        // Assert: o serviço buscou o Employee da usuária logada para sobrescrever o filtro
        verify(employeeRepository).findByUserId(12L);
    }

    @Test
    void findAll_whenStaff_shouldNotRestrictToAnyEmployee() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(appointmentRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        // Act
        appointmentService.findAll(
                new com.cristiane.salon.models.appointment.dto.AppointmentFilter(
                        null, null, null, null, null, null, null), pageable);

        // Assert: ADMIN/GERENTE continuam vendo o salão inteiro
        verify(employeeRepository, never()).findByUserId(any());
    }

    @Test
    void decline_whenAppointmentNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.decline(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Agendamento não encontrado");
    }

    @Test
    void decline_whenStatusNotRequested_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.decline(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Apenas solicitações em análise podem ser recusadas");
    }

    @Test
    void decline_whenSuccess_shouldSetStatusDeclined() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setStatus(AppointmentStatus.REQUESTED);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.decline(1L);

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.DECLINED.name());
        verify(emailService).sendCancellationNotification(apt);
    }

    // --- updateInternalNotes ---

    @Test
    void updateInternalNotes_whenStaff_shouldSaveNoteRegardlessOfStatus() {
        // Arrange — nota interna é preenchida com frequência DEPOIS do atendimento (histórico
        // do cliente), então precisa funcionar em qualquer status, não só REQUESTED/CONFIRMED.
        mockAuthenticatedUser(staffUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(employee);
        apt.setClient(clientUser);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.updateInternalNotes(1L, "Cliente trouxe foto de referência");

        // Assert
        assertThat(result.internalNotes()).isEqualTo("Cliente trouxe foto de referência");
    }

    @Test
    void updateInternalNotes_whenAssignedProfessional_shouldBeAllowed() {
        mockAuthenticatedUser(professionalUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setEmployee(employee);
        apt.setClient(clientUser);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponse result = appointmentService.updateInternalNotes(1L, "nota");

        assertThat(result.internalNotes()).isEqualTo("nota");
    }

    @Test
    void updateInternalNotes_whenOtherProfessionalsAppointment_shouldThrowUnauthorizedException() {
        mockAuthenticatedUser(otherProfessionalUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setEmployee(employee);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        assertThatThrownBy(() -> appointmentService.updateInternalNotes(1L, "nota"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("profissional responsável");
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void updateInternalNotes_whenAppointmentNotFound_shouldThrowResourceNotFoundException() {
        mockAuthenticatedUser(staffUser);
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.updateInternalNotes(99L, "nota"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Agendamento não encontrado");
    }

    // --- getMyAppointments ---

    @Test
    void getMyAppointments_shouldReturnClientAppointments() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        when(appointmentRepository.findByClientId(10L)).thenReturn(List.of(apt));

        // Act
        List<AppointmentResponse> result = appointmentService.getMyAppointments();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    // --- findAll ---

    @Test
    void findAll_shouldReturnPageFromRepository() {
        // Arrange
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        org.springframework.data.domain.Page<Appointment> page =
                new org.springframework.data.domain.PageImpl<>(List.of(apt));
        when(appointmentRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
                .thenReturn(page);

        // Act
        org.springframework.data.domain.Page<AppointmentResponse> result = appointmentService.findAll(
                new com.cristiane.salon.models.appointment.dto.AppointmentFilter(null, null, null, null, null, null, null), pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
    }

    @Test
    void findAllInternal_shouldReturnAllAppointmentsUnpaginated() {
        // Arrange
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        when(appointmentRepository.findAll()).thenReturn(List.of(apt));

        // Act
        List<AppointmentResponse> result = appointmentService.findAllInternal();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    // --- cancel tests ---

    @Test
    void cancel_whenAppointmentNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.cancel(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Agendamento não encontrado");
    }

    @Test
    void cancel_whenNotOwnerAndNotStaff_shouldThrowUnauthorizedException() {
        // Arrange
        mockAuthenticatedUser(clientUser); // client ID is 10
        User otherClient = new User();
        otherClient.setId(99L);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(otherClient); // Owned by 99
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.cancel(1L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Você não tem permissão para cancelar este agendamento");
    }

    @Test
    void cancel_whenStatusIsDone_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.DONE);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.cancel(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Não é possível cancelar um agendamento já concluído");
    }

    @Test
    void cancel_whenStatusIsDeclined_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.DECLINED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.cancel(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Esta solicitação já foi recusada");
    }

    @Test
    void cancel_whenStatusIsCancelled_shouldThrowBusinessException() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.cancel(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agendamentos pagos ou cancelados não podem ter seu status alterado.");
    }

    @Test
    void cancel_whenSuccessByOwner_shouldSetStatusCancelled() {
        // Arrange
        mockAuthenticatedUser(clientUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.cancel(1L);

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED.name());
        verify(emailService).sendCancellationNotification(apt);
    }

    @Test
    void cancel_whenSuccessByStaff_shouldSetStatusCancelled() {
        // Arrange
        mockAuthenticatedUser(staffUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.REQUESTED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.cancel(1L);

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED.name());
        verify(emailService).sendCancellationNotification(apt);
    }

    @Test
    void cancel_whenPaymentIsPaid_shouldThrowBusinessException() {
        // Não é possível cancelar um agendamento com pagamento confirmado sem estorno prévio
        mockAuthenticatedUser(staffUser);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setPaymentStatus(com.cristiane.salon.models.appointment.enums.PaymentStatus.PAID);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.cancel(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("estorno");
    }



    // --- updateStatus tests ---

    @Test
    void updateStatus_whenNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.updateStatus(99L, "CONFIRMED"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Agendamento não encontrado");
    }

    @Test
    void updateStatus_whenInvalidStatusString_shouldThrowBadRequestException() {
        // Arrange
        Appointment apt = new Appointment();
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.updateStatus(1L, "INVALID_STATE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Status inválido");
    }

    @Test
    void updateStatus_whenStatusRequested_shouldThrowBadRequestException() {
        // Arrange
        Appointment apt = new Appointment();
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.updateStatus(1L, "REQUESTED"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Status inválido para esta operação");
    }

    @Test
    void updateStatus_whenStatusConfirmedOrDoneButScheduledAtNull_shouldThrowBadRequestException() {
        // Arrange
        Appointment apt = new Appointment();
        apt.setScheduledAt(null);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.updateStatus(1L, "CONFIRMED"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("É necessário ter data e hora definidas neste agendamento");

        assertThatThrownBy(() -> appointmentService.updateStatus(1L, "DONE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("É necessário ter data e hora definidas neste agendamento");
    }

    @Test
    void updateStatus_whenStatusConfirmed_shouldSaveAndNotifyClient() {
        // Arrange
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.updateStatus(1L, "CONFIRMED");

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED.name());
        verify(emailService).sendConfirmationNotificationToClient(apt);
    }

    @Test
    void updateStatus_whenStatusCancelled_shouldSaveAndNotifyCancellation() {
        // Arrange
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.updateStatus(1L, "CANCELLED");

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED.name());
        verify(emailService).sendCancellationNotification(apt);
    }

    @Test
    void updateStatus_whenStatusDoneAndNoPrice_shouldNotAutoBill() {
        // Arrange
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        
        salonService.setPrice(null); // Price is null
        withService(apt, salonService);
        
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.updateStatus(1L, "DONE");

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.DONE.name());
        verify(cashFlowRepository, never()).save(any());
    }

    @Test
    void updateStatus_whenStatusDoneAndPriceZero_shouldNotAutoBill() {
        // Arrange
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        
        salonService.setPrice(BigDecimal.ZERO); // Price is zero
        withService(apt, salonService);
        
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.updateStatus(1L, "DONE");

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.DONE.name());
        verify(cashFlowRepository, never()).save(any());
    }

    @Test
    void updateStatus_whenStatusDoneAndAlreadyBilled_shouldNotDuplicateBill() {
        // Arrange
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(cashFlowRepository.existsByAppointmentId(1L)).thenReturn(true);

        // Act
        AppointmentResponse result = appointmentService.updateStatus(1L, "DONE");

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.DONE.name());
        verify(cashFlowRepository, never()).save(any());
    }

    @Test
    void updateStatus_whenStatusDoneAndNotYetBilled_shouldAutoBillSuccess() {
        // Arrange
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.updateStatus(1L, "DONE");

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.DONE.name());

        ArgumentCaptor<CashFlow> cashFlowCaptor = ArgumentCaptor.forClass(CashFlow.class);
        verify(cashFlowRepository).save(cashFlowCaptor.capture());
        assertThat(cashFlowCaptor.getValue().getAmount()).isEqualByComparingTo(salonService.getPrice());
    }

    @Test
    void updateStatus_whenStatusDoneWithProducts_shouldBillGrandTotal() {
        // Arrange: agendamento com serviço (R$100) + 2 unidades de um produto (R$50 cada) —
        // o valor faturado deve ser a soma (R$200).
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);

        com.cristiane.salon.models.product.entity.Product product = shampooProduct();
        var productItem = new com.cristiane.salon.models.appointment.entity.AppointmentProductItem();
        productItem.setAppointment(apt);
        productItem.setProduct(product);
        productItem.setQuantity(2);
        apt.setProducts(List.of(productItem));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        appointmentService.updateStatus(1L, "DONE");

        // Assert
        ArgumentCaptor<CashFlow> cashFlowCaptor = ArgumentCaptor.forClass(CashFlow.class);
        verify(cashFlowRepository).save(cashFlowCaptor.capture());
        assertThat(cashFlowCaptor.getValue().getAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void updateStatus_whenStatusDoneWithCustomPrice_shouldBillCustomPriceInsteadOfCatalogPrice() {
        // Arrange: preço customizado (R$200) para este agendamento específico, sem alterar o
        // preço de catálogo do serviço (R$100 no fixture salonService).
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService, new BigDecimal("200.00"), null);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        appointmentService.updateStatus(1L, "DONE");

        // Assert
        ArgumentCaptor<CashFlow> cashFlowCaptor = ArgumentCaptor.forClass(CashFlow.class);
        verify(cashFlowRepository).save(cashFlowCaptor.capture());
        assertThat(cashFlowCaptor.getValue().getAmount()).isEqualByComparingTo("200.00");
        assertThat(salonService.getPrice()).isEqualByComparingTo("100.00");
    }

    // --- generatePixPayment tests ---

    @Test
    void generatePixPayment_whenClientHasNoCpf_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser); // clientUser does NOT have CPF set

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.generatePixPayment(1L, new GeneratePixRequest(true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CPF é obrigatório para gerar o PIX");
    }

    @Test
    void generatePixPayment_whenAppointmentAlreadyPaid_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setPaymentStatus(com.cristiane.salon.models.appointment.enums.PaymentStatus.PAID);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.generatePixPayment(1L, new GeneratePixRequest(true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Este agendamento já está pago.");
    }

    @Test
    void generatePixPayment_whenClientNotOwner_shouldThrowUnauthorizedException() {
        // Arrange
        mockAuthenticatedUser(clientUser); // ID 10

        User otherClient = new User();
        otherClient.setId(99L);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(otherClient); // Owned by ID 99
        apt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.generatePixPayment(1L, new GeneratePixRequest(true, null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Você não tem permissão para gerar pagamento para este agendamento");
    }

    @Test
    void generatePixPayment_whenStatusIsRequested_shouldThrowOnCpfNotFoundInsteadOfStatusBlock() {
        // REQUESTED não bloqueia mais a geração de PIX (nova regra desacoplada).
        // O bloqueio agora ocorre na verificação de CPF, já que clientUser não tem CPF configurado.
        mockAuthenticatedUser(clientUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.REQUESTED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert: não lança mais por causa do status, mas pelo CPF ausente
        assertThatThrownBy(() -> appointmentService.generatePixPayment(1L, new GeneratePixRequest(true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CPF é obrigatório");
    }


    @Test
    void generatePixPayment_whenStatusIsDone_shouldSucceedToAllowPaymentAfterService() {
        // Arrange: DONE agora permite gerar PIX (cliente paga após serviço concluído)
        clientUser.setCpf("09123456752");
        mockAuthenticatedUser(clientUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.DONE);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        Payment payment = mock(Payment.class);
        PaymentPointOfInteraction poi = mock(PaymentPointOfInteraction.class);
        PaymentTransactionData td = mock(PaymentTransactionData.class);
        when(payment.getPointOfInteraction()).thenReturn(poi);
        when(poi.getTransactionData()).thenReturn(td);
        when(td.getQrCode()).thenReturn("mocked_qr_done");
        when(payment.getId()).thenReturn(77L);
        when(mercadoPagoPaymentService.createPixPayment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(payment);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse response = appointmentService.generatePixPayment(1L, new GeneratePixRequest(true, null));

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.pixQrCode()).isEqualTo("mocked_qr_done");
    }

    @Test
    void generatePixPayment_whenStatusIsCancelled_shouldThrowBadRequestException() {
        // Arrange: CANCELLED bloqueia geração de PIX
        mockAuthenticatedUser(clientUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.CANCELLED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.generatePixPayment(1L, new GeneratePixRequest(true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void generatePixPayment_whenServiceHasNoPrice_shouldThrowBadRequestException() {
        // Arrange
        clientUser.setCpf("12345678901");
        mockAuthenticatedUser(clientUser);

        salonService.setPrice(null);
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.generatePixPayment(1L, new GeneratePixRequest(true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("não possui um valor configurado");
    }

    @Test
    void generatePixPayment_whenCustomPriceSet_shouldChargeCustomPriceInsteadOfCatalogPrice() {
        // Arrange
        clientUser.setCpf("09123456752");
        mockAuthenticatedUser(clientUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService, new BigDecimal("200.00"), null);
        apt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        Payment payment = mock(Payment.class);
        PaymentPointOfInteraction poi = mock(PaymentPointOfInteraction.class);
        PaymentTransactionData td = mock(PaymentTransactionData.class);
        when(payment.getPointOfInteraction()).thenReturn(poi);
        when(poi.getTransactionData()).thenReturn(td);
        when(td.getQrCode()).thenReturn("mocked_qr_custom_price");
        when(payment.getId()).thenReturn(88L);
        when(mercadoPagoPaymentService.createPixPayment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(payment);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        appointmentService.generatePixPayment(1L, new GeneratePixRequest(true, null));

        // Assert: cobra os R$200 customizados, não os R$100 do catálogo
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(mercadoPagoPaymentService).createPixPayment(amountCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("200.00");
    }


    @Test
    void generatePixPayment_whenInvalidCpfPassed_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(clientUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.generatePixPayment(1L, new GeneratePixRequest(false, "11111111111")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CPF inválido. Por favor, insira um CPF válido.");
    }

    @Test
    void updateStatus_whenAppointmentIsPaid_transitionToDone_shouldSucceed() {
        // A nova regra: DONE pode ser atingido mesmo quando o pagamento está PAID
        // Ex: serviço concluído após o pagamento PIX ter sido confirmado
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setScheduledAt(salonClock.now().plusDays(1));
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setPaymentStatus(com.cristiane.salon.models.appointment.enums.PaymentStatus.PAID);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.updateStatus(1L, "DONE");

        // Assert: DONE é permitido mesmo com pagamento PAID
        assertThat(result.status()).isEqualTo(AppointmentStatus.DONE.name());
    }

    @Test
    void updateStatus_whenAppointmentIsPaid_transitionToNonDone_shouldThrowBusinessException() {
        // CONFIRMED → CANCELLED é bloqueado quando o pagamento está PAID
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setPaymentStatus(com.cristiane.salon.models.appointment.enums.PaymentStatus.PAID);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.updateStatus(1L, "CANCELLED"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agendamentos pagos ou cancelados não podem ter seu status alterado.");
    }


    @Test
    void updatePaymentStatus_whenManualPaidTransitionWithoutPaymentId_shouldThrowBusinessException() {
        // Arrange
        mockAuthenticatedUser(staffUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setPaymentStatus(com.cristiane.salon.models.appointment.enums.PaymentStatus.PENDING);
        apt.setPaymentId(null); // No payment ID

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.updatePaymentStatus(1L, "PAID", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Transição manual para PAGO não permitida para agendamentos pendentes sem um ID de pagamento válido.");
    }

    @Test
    void updatePaymentStatus_whenManualWithPaymentMethod_shouldSetPaymentMethod() {
        // Arrange
        mockAuthenticatedUser(staffUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setPaymentStatus(com.cristiane.salon.models.appointment.enums.PaymentStatus.PENDING);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse result = appointmentService.updatePaymentStatus(1L, "MANUAL", "DINHEIRO");

        // Assert
        assertThat(result.paymentStatus()).isEqualTo("MANUAL");
        assertThat(result.paymentMethod()).isEqualTo("DINHEIRO");
    }

    @Test
    void updatePaymentStatus_whenPaymentMethodInvalid_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(staffUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setStatus(AppointmentStatus.CONFIRMED);
        apt.setPaymentStatus(com.cristiane.salon.models.appointment.enums.PaymentStatus.PENDING);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.updatePaymentStatus(1L, "MANUAL", "BITCOIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Forma de pagamento inválida");
    }

    @Test
    void generatePixPayment_whenCpfAlreadyUsedByAnotherUser_shouldGenerateSuccessfully() {
        // Arrange
        mockAuthenticatedUser(clientUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Mock payment response
        Payment payment = mock(Payment.class);
        PaymentPointOfInteraction poi = mock(PaymentPointOfInteraction.class);
        PaymentTransactionData td = mock(PaymentTransactionData.class);
        when(payment.getPointOfInteraction()).thenReturn(poi);
        when(poi.getTransactionData()).thenReturn(td);
        when(td.getQrCode()).thenReturn("mocked_qr_code");
        when(payment.getId()).thenReturn(12345L);

        when(mercadoPagoPaymentService.createPixPayment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(payment);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse response = appointmentService.generatePixPayment(1L, new GeneratePixRequest(false, "09123456752"));

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.pixQrCode()).isEqualTo("mocked_qr_code");
        assertThat(clientUser.getCpf()).isEqualTo("09123456752");
        verify(userRepository).save(clientUser);
    }

    @Test
    void generatePixPayment_whenAdminGeneratesForClient_shouldSaveCpfToClientAndUseClientDetails() {
        // Arrange - authenticated user is admin/staff, clientUser owns the appointment
        mockAuthenticatedUser(staffUser);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Mock payment response
        Payment payment = mock(Payment.class);
        PaymentPointOfInteraction poi = mock(PaymentPointOfInteraction.class);
        PaymentTransactionData td = mock(PaymentTransactionData.class);
        when(payment.getPointOfInteraction()).thenReturn(poi);
        when(poi.getTransactionData()).thenReturn(td);
        when(td.getQrCode()).thenReturn("mocked_qr_code");
        when(payment.getId()).thenReturn(12345L);

        when(mercadoPagoPaymentService.createPixPayment(
                eq(salonService.getPrice()),
                anyString(),
                eq(clientUser.getEmail()), // Payer email should be client's
                eq(clientUser.getName()),  // Payer name should be client's
                eq("09123456752"),
                eq(1L),
                any()
        )).thenReturn(payment);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppointmentResponse response = appointmentService.generatePixPayment(1L, new GeneratePixRequest(false, "09123456752"));

        // Assert
        assertThat(response).isNotNull();
        assertThat(clientUser.getCpf()).isEqualTo("09123456752");
        assertThat(staffUser.getCpf()).isNull(); // Should NOT set on admin
        verify(userRepository).save(clientUser);
        verify(userRepository, never()).save(staffUser);
    }

    @Test
    void findById_whenAppointmentNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Agendamento não encontrado");
    }

    @Test
    void findById_whenUserNotOwnerAndNotAdmin_shouldThrowUnauthorizedException() {
        // Arrange
        mockAuthenticatedUser(clientUser); // ID 10
        User otherClient = new User();
        otherClient.setId(99L);

        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(otherClient); // Owned by ID 99

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.findById(1L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Você não tem permissão para visualizar este agendamento");
    }

    @Test
    void findById_whenUserIsOwner_shouldReturnAppointment() {
        // Arrange
        mockAuthenticatedUser(clientUser); // ID 10
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act
        AppointmentResponse response = appointmentService.findById(1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void findById_whenUserIsAdminButNotOwner_shouldReturnAppointment() {
        // Arrange
        mockAuthenticatedUser(staffUser); // Admin
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        // Act
        AppointmentResponse response = appointmentService.findById(1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
    }

    // --- updateProducts / updateExpenses tests ---

    private com.cristiane.salon.models.product.entity.Product shampooProduct() {
        com.cristiane.salon.models.product.entity.Product product = new com.cristiane.salon.models.product.entity.Product();
        product.setId(30L);
        product.setName("Shampoo");
        product.setPrice(new BigDecimal("50.00"));
        product.setActive(true);
        return product;
    }

    private Appointment appointmentWithStatus(AppointmentStatus status) {
        Appointment apt = new Appointment();
        apt.setId(1L);
        apt.setClient(clientUser);
        apt.setEmployee(employee);
        withService(apt, salonService);
        apt.setStatus(status);
        return apt;
    }

    @Test
    void updateServices_whenValid_shouldReplaceServicesAndReturnUpdatedTotal() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.of(salonService));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new AppointmentServiceRequest(8L, new BigDecimal("120.00"), "Cabelo mais longo");

        AppointmentResponse result = appointmentService.updateServices(1L, List.of(request));

        assertThat(result.services()).hasSize(1);
        assertThat(result.services().get(0).customPrice()).isEqualByComparingTo("120.00");
        assertThat(result.totalPrice()).isEqualByComparingTo("120.00");
    }

    @Test
    void updateServices_whenEmptyList_shouldThrowBadRequestException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        assertThatThrownBy(() -> appointmentService.updateServices(1L, List.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateServices_whenServiceNotFound_shouldThrowResourceNotFoundException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(salonServiceRepository.findById(8L)).thenReturn(Optional.empty());

        var request = new AppointmentServiceRequest(8L, null, null);

        assertThatThrownBy(() -> appointmentService.updateServices(1L, List.of(request)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Serviço não encontrado");
    }

    @Test
    void updateServices_whenAppointmentAlreadyDone_shouldThrowBusinessException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.DONE);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        var request = new AppointmentServiceRequest(8L, null, null);

        assertThatThrownBy(() -> appointmentService.updateServices(1L, List.of(request)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateProducts_whenValid_shouldReplaceProductsAndReturnUpdatedTotals() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(productRepository.findById(30L)).thenReturn(Optional.of(shampooProduct()));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentProductRequest(30L, 2, null);

        AppointmentResponse result = appointmentService.updateProducts(1L, List.of(request));

        assertThat(result.products()).hasSize(1);
        assertThat(result.totalProductsPrice()).isEqualByComparingTo("100.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void updateProducts_whenProductNotFound_shouldThrowResourceNotFoundException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(productRepository.findById(30L)).thenReturn(Optional.empty());

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentProductRequest(30L, 1, null);

        assertThatThrownBy(() -> appointmentService.updateProducts(1L, List.of(request)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto não encontrado");
    }

    @Test
    void updateProducts_whenProductInactive_shouldThrowBadRequestException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        com.cristiane.salon.models.product.entity.Product inactive = shampooProduct();
        inactive.setActive(false);
        when(productRepository.findById(30L)).thenReturn(Optional.of(inactive));

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentProductRequest(30L, 1, null);

        assertThatThrownBy(() -> appointmentService.updateProducts(1L, List.of(request)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Este produto não está disponível: Shampoo");
    }

    @Test
    void updateProducts_whenCustomPriceNegative_shouldThrowBadRequestException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentProductRequest(
                30L, 1, new BigDecimal("-5.00"));

        assertThatThrownBy(() -> appointmentService.updateProducts(1L, List.of(request)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O preço customizado do produto não pode ser negativo");
    }

    @Test
    void updateProducts_whenAppointmentCancelled_shouldThrowBusinessException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentProductRequest(30L, 1, null);

        assertThatThrownBy(() -> appointmentService.updateProducts(1L, List.of(request)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateProducts_whenAppointmentAlreadyDone_shouldThrowBusinessException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.DONE);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentProductRequest(30L, 1, null);

        assertThatThrownBy(() -> appointmentService.updateProducts(1L, List.of(request)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateExpenses_whenValid_shouldReplaceExpensesAndReturnUpdatedTotals() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentExpenseRequest(
                "Material extra", "FIXED", new BigDecimal("20.00"));

        AppointmentResponse result = appointmentService.updateExpenses(1L, List.of(request));

        assertThat(result.expenses()).hasSize(1);
        assertThat(result.totalExpensesAmount()).isEqualByComparingTo("20.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("80.00");
    }

    @Test
    void updateExpenses_whenValueTypeInvalid_shouldThrowBadRequestException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentExpenseRequest(
                "Material extra", "INVALID_TYPE", new BigDecimal("20.00"));

        assertThatThrownBy(() -> appointmentService.updateExpenses(1L, List.of(request)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tipo de valor de despesa inválido");
    }

    @Test
    void updateExpenses_whenPercentageOver100_shouldThrowBadRequestException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentExpenseRequest(
                "Taxa", "PERCENTAGE", new BigDecimal("150"));

        assertThatThrownBy(() -> appointmentService.updateExpenses(1L, List.of(request)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A porcentagem da despesa não pode ser maior que 100");
    }

    @Test
    void updateExpenses_whenAppointmentPaid_shouldThrowBusinessException() {
        Appointment apt = appointmentWithStatus(AppointmentStatus.CONFIRMED);
        apt.setPaymentStatus(com.cristiane.salon.models.appointment.enums.PaymentStatus.PAID);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        var request = new com.cristiane.salon.models.appointment.dto.AppointmentExpenseRequest(
                "Taxa", "FIXED", new BigDecimal("10"));

        assertThatThrownBy(() -> appointmentService.updateExpenses(1L, List.of(request)))
                .isInstanceOf(BusinessException.class);
    }
}

