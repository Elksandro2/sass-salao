package com.cristiane.salon.controllers;

import com.cristiane.salon.models.ai.service.AiConfigService;
import com.cristiane.salon.models.ai.service.McpTokenService;
import com.cristiane.salon.models.ai.service.RecommendationService;
import com.cristiane.salon.models.appointment.service.AppointmentService;
import com.cristiane.salon.models.cashflow.service.CashFlowService;
import com.cristiane.salon.models.employee.service.EmployeeService;
import com.cristiane.salon.models.featureflag.service.FeatureFlagService;
import com.cristiane.salon.models.product.service.ProductService;
import com.cristiane.salon.models.report.service.ReportService;
import com.cristiane.salon.models.service.service.SalonServiceManager;
import com.cristiane.salon.integrations.email.outbox.service.EmailOutboxService;
import com.cristiane.salon.integrations.push.repository.PushSubscriptionRepository;
import com.cristiane.salon.models.salonprofile.service.SalonProfileService;
import com.cristiane.salon.models.staff.service.StaffPixService;
import com.cristiane.salon.models.staff.service.StaffProfileService;
import com.cristiane.salon.models.user.service.AuthService;
import com.cristiane.salon.models.user.service.PasswordResetService;
import com.cristiane.salon.models.user.service.RoleService;
import com.cristiane.salon.models.user.service.UserService;
import com.cristiane.salon.integrations.payment.service.MercadoPagoPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
class ErrorScenariosTest extends BaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AppointmentService appointmentService;

    @MockitoBean
    private CashFlowService cashFlowService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private SalonServiceManager salonServiceManager;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private FeatureFlagService featureFlagService;

    @MockitoBean
    private MercadoPagoPaymentService mercadoPagoPaymentService;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AiConfigService aiConfigService;

    @MockitoBean
    private RecommendationService recommendationService;

    @MockitoBean
    private McpTokenService mcpTokenService;

    @MockitoBean
    private StaffProfileService staffProfileService;

    @MockitoBean
    private StaffPixService staffPixService;

    @MockitoBean
    private EmailOutboxService emailOutboxService;

    @MockitoBean
    private PushSubscriptionRepository pushSubscriptionRepository;

    @MockitoBean
    private SalonProfileService salonProfileService;

    @MockitoBean
    private com.cristiane.salon.models.generalnote.service.GeneralNoteService generalNoteService;

    @MockitoBean
    private com.cristiane.salon.integrations.payment.marketplace.EmployeeMercadoPagoConnectionService employeeMercadoPagoConnectionService;

    @MockitoBean
    private com.cristiane.salon.models.clientanamnesis.service.ClientAnamnesisService clientAnamnesisService;

    @MockitoBean
    private com.cristiane.salon.models.fixedexpense.service.FixedExpenseService fixedExpenseService;

    @Test
    void whenInvalidAppointment_thenReturns400() throws Exception {
        String invalidJson = "{}";

        mvc.perform(post("/v1/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // Disabled because method security is disabled in WebMvcTest slice
    // @Test
    // @WithMockUser(roles = { "USER" })
    // void whenForbiddenOnCashFlow_thenReturns403() throws Exception {
    //     when(verifyUserPermissions.userOwnResourceOrHasPermission(null)).thenReturn(false);
    //
    //     String body = "{\"type\":\"INCOME\",\"amount\":100.0,\"description\":\"test\",\"date\":\"2026-05-16\"}";
    //
    //     mvc.perform(post("/v1/cashflow")
    //             .contentType(MediaType.APPLICATION_JSON)
    //             .content(body))
    //             .andExpect(status().isForbidden());
    // }

    @Test
    @WithMockUser
    void whenServiceThrows_thenReturns500() throws Exception {
        when(appointmentService.create(any())).thenThrow(new RuntimeException("boom"));

        String body = "{\"employeeId\":1,\"services\":[{\"serviceId\":1}]}";

        mvc.perform(post("/v1/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser
    void whenMissingRequestParam_thenReturns400() throws Exception {
        // PATCH without 'status' request param should return 400
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/v1/appointments/1/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void whenUnknownEndpoint_thenReturns404() throws Exception {
        mvc.perform(get("/v1/this-path-does-not-exist")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
