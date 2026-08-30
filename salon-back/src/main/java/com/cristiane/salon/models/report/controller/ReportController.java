package com.cristiane.salon.models.report.controller;

import com.cristiane.salon.models.report.dto.AppointmentFinancialResponse;
import com.cristiane.salon.models.report.dto.AppointmentProfitResponse;
import com.cristiane.salon.models.report.dto.AppointmentReportResponse;
import com.cristiane.salon.models.report.dto.FinancialReportResponse;
import com.cristiane.salon.models.report.dto.PayrollReportResponse;
import com.cristiane.salon.models.report.dto.ServicePricingAnalysisResponse;
import com.cristiane.salon.models.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Endpoints para relatórios e dashboards (Admin)")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/financial")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Gera relatório financeiro de receitas, despesas e lucro (Admin)")
    public ResponseEntity<FinancialReportResponse> getFinancialReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.generateFinancialReport(from, to));
    }

    @GetMapping("/appointments")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Gera relatório de agendamentos e métricas de atendimento (Admin)")
    public ResponseEntity<AppointmentReportResponse> getAppointmentReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.generateAppointmentReport(from, to));
    }

    @GetMapping("/payroll")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Gera folha de pagamento e cálculo de comissões por funcionária (Admin)")
    public ResponseEntity<PayrollReportResponse> getPayrollReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false)
            @Parameter(description = "Dias trabalhados por diarista no período, formato \"employeeId:dias,employeeId:dias\"")
            String daysWorked) {
        return ResponseEntity.ok(reportService.generatePayrollReport(from, to, parseDaysWorked(daysWorked)));
    }

    /** Converte "5:20,7:18" em {5->20, 7->18}. Entradas malformadas são ignoradas. */
    static Map<Long, Integer> parseDaysWorked(String raw) {
        Map<Long, Integer> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String pair : raw.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length != 2) continue;
            try {
                long employeeId = Long.parseLong(parts[0].trim());
                int days = Integer.parseInt(parts[1].trim());
                if (days >= 0) {
                    result.put(employeeId, days);
                }
            } catch (NumberFormatException ignored) {
                // par malformado — ignora em vez de derrubar o relatório inteiro
            }
        }
        return result;
    }

    @GetMapping("/appointments/{id}/profit")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Lucro/prejuízo estimado de um atendimento específico (Admin/Gerente)")
    public ResponseEntity<AppointmentProfitResponse> getAppointmentProfit(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.getAppointmentProfit(id));
    }

    @GetMapping("/service-pricing")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Análise de preço por tipo de serviço, com rateio dos gastos fixos (Admin/Gerente)")
    public ResponseEntity<ServicePricingAnalysisResponse> getServicePricingAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.generateServicePricingAnalysis(from, to));
    }

    @GetMapping("/financial/employees/{employeeId}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Histórico financeiro de agendamentos de um profissional específico (Admin/Gerente)")
    public ResponseEntity<Page<AppointmentFinancialResponse>> getEmployeeFinancialHistory(
            @PathVariable Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reportService.getEmployeeFinancialHistory(employeeId, from, to, pageable));
    }
}
