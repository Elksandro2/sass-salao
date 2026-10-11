package com.cristiane.salon.models.payroll.controller;

import com.cristiane.salon.controllers.BaseControllerTest;
import com.cristiane.salon.models.payroll.dto.PayrollDtos;
import com.cristiane.salon.models.payroll.service.PayrollService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayrollController.class)
class PayrollControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PayrollService payrollService;

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void preview_acceptsSeveralSalaryMonthsAndNoDays() throws Exception {
        List<LocalDate> months = List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
        when(payrollService.previewPayment(eq(7L), isNull(), isNull(), eq(months), isNull()))
                .thenReturn(new PayrollDtos.PaymentPreview(new BigDecimal("3000.00"), null, null));

        mvc.perform(get("/v1/payroll/payments/preview")
                        .param("employeeId", "7")
                        .param("salaryMonths", "2026-10-01")
                        .param("salaryMonths", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAmount").value(3000.00));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void createPayment_acceptsSalaryOnlyBody() throws Exception {
        when(payrollService.createPayment(org.mockito.ArgumentMatchers.any())).thenReturn(
                new PayrollDtos.PaymentResponse(1L, 7L, "Maria", null, null,
                        List.of(new PayrollDtos.SalaryLine(LocalDate.of(2026, 10, 1), new BigDecimal("1500.00"))),
                        new BigDecimal("1500.00"), new BigDecimal("1500.00"), LocalDate.of(2026, 10, 31),
                        "PIX", null, null, false));

        mvc.perform(post("/v1/payroll/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":7,\"periodStart\":null,\"periodEnd\":null,"
                                + "\"salaryMonths\":[\"2026-10-01\"],\"paidAt\":\"2026-10-31\",\"paymentMethod\":\"PIX\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.salaryMonths[0].month").value("2026-10-01"));

        verify(payrollService).createPayment(argThat(r -> r.periodStart() == null
                && r.salaryMonths().equals(List.of(LocalDate.of(2026, 10, 1)))));
    }
}
