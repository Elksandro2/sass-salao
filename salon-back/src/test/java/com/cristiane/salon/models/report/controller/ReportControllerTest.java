package com.cristiane.salon.models.report.controller;

import com.cristiane.salon.controllers.BaseControllerTest;

import com.cristiane.salon.models.report.controller.ReportController;
import com.cristiane.salon.models.report.dto.AppointmentFinancialResponse;
import com.cristiane.salon.models.report.dto.AppointmentReportResponse;
import com.cristiane.salon.models.report.dto.FinancialReportResponse;
import com.cristiane.salon.models.report.dto.PayrollReportResponse;
import com.cristiane.salon.models.report.service.ReportService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
class ReportControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void findByPeriodReturns200_whenAuthorized() throws Exception {
        FinancialReportResponse response = new FinancialReportResponse(
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, List.of(), "Period"
        );
        when(reportService.generateFinancialReport(any(), any())).thenReturn(response);

        mvc.perform(get("/v1/reports/financial?from=2026-05-01&to=2026-05-16")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("Period"));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void getPayrollReportReturns200() throws Exception {
        PayrollReportResponse response = new PayrollReportResponse(List.of(), "Period", null, null);
        when(reportService.generatePayrollReport(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mvc.perform(get("/v1/reports/payroll?from=2026-05-01&to=2026-05-16")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("Period"));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void getAppointmentReportReturns200() throws Exception {
        AppointmentReportResponse response = new AppointmentReportResponse(0L, 0L, 0L, 0L, 0L, Map.of(), Map.of(), "Period");
        when(reportService.generateAppointmentReport(any(), any())).thenReturn(response);

        mvc.perform(get("/v1/reports/appointments?from=2026-05-01&to=2026-05-16")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("Period"));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void getEmployeeFinancialHistoryReturns200() throws Exception {
        AppointmentFinancialResponse response = new AppointmentFinancialResponse(
                1L, null, null, "Corte", new BigDecimal("85.00"), "DONE", "PAID"
        );
        org.springframework.data.domain.Page<AppointmentFinancialResponse> page =
                new org.springframework.data.domain.PageImpl<>(List.of(response));
        when(reportService.getEmployeeFinancialHistory(any(), any(), any(), any())).thenReturn(page);

        mvc.perform(get("/v1/reports/financial/employees/7")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].serviceName").value("Corte"));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void saveWorkedDaysReturns204AndDelegates() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/v1/reports/payroll/worked-days")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":7,\"periodStart\":\"2026-05-01\",\"periodEnd\":\"2026-05-31\",\"daysWorked\":22}"))
                .andExpect(status().isNoContent());

        verify(reportService).saveWorkedDaysOverride(eq(7L),
                eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")), eq(22));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void clearWorkedDaysReturns204AndDelegates() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/v1/reports/payroll/worked-days")
                .param("employeeId", "7")
                .param("periodStart", "2026-05-01")
                .param("periodEnd", "2026-05-31"))
                .andExpect(status().isNoContent());

        verify(reportService).clearWorkedDaysOverride(eq(7L),
                eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-31")));
    }
}
