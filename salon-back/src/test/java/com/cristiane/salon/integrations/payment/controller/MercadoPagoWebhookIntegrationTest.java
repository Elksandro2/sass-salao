package com.cristiane.salon.integrations.payment.controller;

import com.cristiane.salon.integrations.payment.service.MercadoPagoPaymentService;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.enums.PaymentStatus;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.audit.AuditLog;
import com.cristiane.salon.models.audit.AuditLogRepository;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.cashflow.repository.CashFlowRepository;
import com.cristiane.salon.integrations.email.service.EmailService;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.service.entity.SalonService;
import com.cristiane.salon.models.service.repository.SalonServiceRepository;
import com.cristiane.salon.models.user.entity.Role;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.RoleRepository;
import com.cristiane.salon.models.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.resources.payment.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class MercadoPagoWebhookIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalonServiceRepository salonServiceRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CashFlowRepository cashFlowRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MercadoPagoPaymentService mercadoPagoPaymentService;

    @MockitoBean
    private EmailService emailService;

    private Appointment appointment;
    private SalonService salonService;

    @BeforeEach
    void setUp() {
        // Limpar dados na ordem correta devido a FKs
        cashFlowRepository.deleteAll();
        appointmentRepository.deleteAll();
        auditLogRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        salonServiceRepository.deleteAll();

        // Buscar roles já populadas pelas migrations do Flyway
        Role clienteRole = roleRepository.findAll().stream()
                .filter(r -> "CLIENTE".equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(new Role(null, "CLIENTE", null)));

        Role adminRole = roleRepository.findAll().stream()
                .filter(r -> "ADMIN".equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN", null)));

        // Criar usuário cliente
        User clientUser = new User();
        clientUser.setName("Cliente de Teste");
        clientUser.setEmail("cliente@teste.com");
        clientUser.setPassword("senha123");
        clientUser.setRole(clienteRole);
        clientUser.setActive(true);
        clientUser = userRepository.save(clientUser);

        // Criar usuário funcionário
        User employeeUser = new User();
        employeeUser.setName("Funcionária de Teste");
        employeeUser.setEmail("funcionaria@teste.com");
        employeeUser.setPassword("senha123");
        employeeUser.setRole(adminRole);
        employeeUser.setActive(true);
        employeeUser = userRepository.save(employeeUser);

        // Criar funcionário
        Employee employee = new Employee();
        employee.setUser(employeeUser);
        employee.setRemunerationType(RemunerationType.COMISSIONADO);
        employee.setRemunerationValue(BigDecimal.ZERO);
        employee = employeeRepository.save(employee);

        // Criar serviço
        salonService = new SalonService();
        salonService.setName("Corte de Cabelo");
        salonService.setDescription("Corte feminino premium");
        salonService.setPrice(BigDecimal.valueOf(80.00));
        salonService.setActive(true);
        salonService = salonServiceRepository.save(salonService);

        // Criar agendamento elegível para pagamento PIX
        appointment = new Appointment();
        appointment.setClient(clientUser);
        appointment.setEmployee(employee);
        appointment.setScheduledAt(LocalDateTime.now().plusDays(1));
        appointment.setPreferredDate(LocalDate.now().plusDays(1));
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setPaymentStatus(PaymentStatus.PENDING);
        appointment.setPaymentId(987654321L);
        appointment.setPixQrCode("mocked_pix_copia_e_cola_code");

        AppointmentServiceItem serviceItem = new AppointmentServiceItem();
        serviceItem.setAppointment(appointment);
        serviceItem.setSalonService(salonService);
        appointment.getServices().add(serviceItem);

        appointment = appointmentRepository.save(appointment);

        when(mercadoPagoPaymentService.isValidSignature(any(), any(), any())).thenAnswer(invocation -> {
            String sig = invocation.getArgument(0);
            return "valid_sig_test".equals(sig);
        });
    }

    @Test
    @WithMockUser
    void receiveNotification_whenValidWebhookAndApprovedPayment_confirmsPaymentAndSavesIncomeAndAuditLog() throws Exception {
        // Setup mock Mercado Pago response
        Payment mpPayment = mock(Payment.class);
        when(mpPayment.getStatus()).thenReturn("approved");
        when(mpPayment.getExternalReference()).thenReturn(appointment.getId().toString());
        when(mpPayment.getTransactionAmount()).thenReturn(BigDecimal.valueOf(80.00));

        when(mercadoPagoPaymentService.getPayment(987654321L)).thenReturn(mpPayment);

        // Webhook Payload
        String body = "{\"action\":\"payment.updated\",\"type\":\"payment\",\"data\":{\"id\":\"987654321\"}}";

        // Perform Webhook Request
        mvc.perform(post("/v1/webhooks/mercadopago")
                        .header("x-signature", "valid_sig_test")
                        .header("x-request-id", "req-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Assert 1: Appointment payment status is updated to PAID
        Appointment updatedAppointment = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(updatedAppointment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(updatedAppointment.getPaymentMethod()).isEqualTo(com.cristiane.salon.models.appointment.enums.PaymentMethod.PIX);

        // Assert 2: Cash flow income has been created
        List<CashFlow> cashFlows = cashFlowRepository.findAll();
        assertThat(cashFlows).hasSize(1);
        CashFlow income = cashFlows.get(0);
        assertThat(income.getType()).isEqualTo(CashFlowType.INCOME);
        assertThat(income.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(80.00));
        assertThat(income.getAppointment().getId()).isEqualTo(appointment.getId());

        // Assert 3: Audit log contains PIX_PAYMENT_CONFIRMED success entry
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        AuditLog logEntry = auditLogs.get(0);
        assertThat(logEntry.getAction()).isEqualTo("PIX_PAYMENT_CONFIRMED");
        assertThat(logEntry.getEntityType()).isEqualTo("Appointment");
        assertThat(logEntry.getEntityId()).isEqualTo(appointment.getId());
        assertThat(logEntry.getStatus()).isEqualTo("SUCCESS");

        // Assert 4: EmailService triggered to notify client
        verify(emailService, times(1)).sendPaymentConfirmationNotificationToClient(any(Appointment.class));
    }

    @Test
    @WithMockUser
    void receiveNotification_whenIdempotencyHookReceived_doesNotDuplicatePaymentOrCashFlow() throws Exception {
        // Setup mock Mercado Pago response
        Payment mpPayment = mock(Payment.class);
        when(mpPayment.getStatus()).thenReturn("approved");
        when(mpPayment.getExternalReference()).thenReturn(appointment.getId().toString());
        when(mpPayment.getTransactionAmount()).thenReturn(BigDecimal.valueOf(80.00));

        when(mercadoPagoPaymentService.getPayment(987654321L)).thenReturn(mpPayment);

        String body = "{\"action\":\"payment.updated\",\"type\":\"payment\",\"data\":{\"id\":\"987654321\"}}";

        // 1st request
        mvc.perform(post("/v1/webhooks/mercadopago")
                        .header("x-signature", "valid_sig_test")
                        .header("x-request-id", "req-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // 2nd request (duplicate webhook)
        mvc.perform(post("/v1/webhooks/mercadopago")
                        .header("x-signature", "valid_sig_test")
                        .header("x-request-id", "req-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Assert: Appointment is still PAID
        Appointment updatedAppointment = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(updatedAppointment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Assert: Exactly ONE cash flow entry exists (no duplicate entry)
        List<CashFlow> cashFlows = cashFlowRepository.findAll();
        assertThat(cashFlows).hasSize(1);

        // Assert: Email notification only sent once (or we did not invoke process twice)
        verify(emailService, times(1)).sendPaymentConfirmationNotificationToClient(any(Appointment.class));
    }

    @Test
    @WithMockUser
    void receiveNotification_whenSignatureInvalid_returns403AndDoesNotProcess() throws Exception {
        String body = "{\"action\":\"payment.updated\",\"type\":\"payment\",\"data\":{\"id\":\"987654321\"}}";

        mvc.perform(post("/v1/webhooks/mercadopago")
                        .header("x-signature", "invalid_sig_value")
                        .header("x-request-id", "req-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        // Assert: DB remains unchanged
        Appointment updatedAppointment = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(updatedAppointment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

        List<CashFlow> cashFlows = cashFlowRepository.findAll();
        assertThat(cashFlows).isEmpty();

        verify(mercadoPagoPaymentService, never()).getPayment(anyLong());
        verifyNoInteractions(emailService);
    }

    @Test
    @WithMockUser
    void receiveNotification_whenPaymentNotApproved_doesNotChangeStatusOrInsertCashFlow() throws Exception {
        // Setup mock Mercado Pago response status as "pending" instead of "approved"
        Payment mpPayment = mock(Payment.class);
        when(mpPayment.getStatus()).thenReturn("pending");
        when(mpPayment.getExternalReference()).thenReturn(appointment.getId().toString());

        when(mercadoPagoPaymentService.getPayment(987654321L)).thenReturn(mpPayment);

        String body = "{\"action\":\"payment.updated\",\"type\":\"payment\",\"data\":{\"id\":\"987654321\"}}";

        mvc.perform(post("/v1/webhooks/mercadopago")
                        .header("x-signature", "valid_sig_test")
                        .header("x-request-id", "req-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Assert: DB unchanged
        Appointment updatedAppointment = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(updatedAppointment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

        List<CashFlow> cashFlows = cashFlowRepository.findAll();
        assertThat(cashFlows).isEmpty();
    }

    @Test
    @WithMockUser
    void receiveNotification_whenPaymentDoesNotExistInMercadoPago_returns200AndLogsErrorGracefully() throws Exception {
        // Mock Mercado Pago client returning null (e.g. payment doesn't exist)
        when(mercadoPagoPaymentService.getPayment(987654321L)).thenReturn(null);

        String body = "{\"action\":\"payment.updated\",\"type\":\"payment\",\"data\":{\"id\":\"987654321\"}}";

        mvc.perform(post("/v1/webhooks/mercadopago")
                        .header("x-signature", "valid_sig_test")
                        .header("x-request-id", "req-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Assert: DB unchanged
        Appointment updatedAppointment = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(updatedAppointment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @WithMockUser
    void receiveNotification_whenPayloadMalformed_returns200AndDoesNotThrowOrCrash() throws Exception {
        String malformedBody = "{ malformed json payload ";

        mvc.perform(post("/v1/webhooks/mercadopago")
                        .header("x-signature", "valid_sig_test")
                        .header("x-request-id", "req-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedBody))
                .andExpect(status().isOk());

        // Assert: DB remains untouched
        Appointment updatedAppointment = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(updatedAppointment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
