package com.cristiane.salon.models.payroll.controller;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.AdjustmentRequest;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.AdjustmentResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.EmployeeDetailResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.PaymentRequest;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.PaymentResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.SummaryResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.WorkDayBulkRequest;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.WorkDayRequest;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.WorkDayResponse;
import com.cristiane.salon.models.payroll.service.PayrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Folha de pagamento das colaboradoras. Sem permissão cadastrada pra nenhum cargo de propósito:
 * salário é informação sensível, então só ADMIN/SYSADMIN (que têm acesso total) enxergam.
 */
@RestController
@RequestMapping("/v1/payroll")
@RequiredArgsConstructor
@Tag(name = "Payroll", description = "Folha de pagamento das colaboradoras (Admin)")
public class PayrollController {

    private final PayrollService payrollService;

    @GetMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Resumo da folha de todas as colaboradoras no período (Admin)")
    public ResponseEntity<SummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(payrollService.getSummary(from, to));
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Folha detalhada de uma colaboradora: atendimentos, vendas, diárias, lançamentos e pagamentos (Admin)")
    public ResponseEntity<EmployeeDetailResponse> getEmployeeDetail(
            @PathVariable Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(payrollService.getEmployeeDetail(employeeId, from, to));
    }

    // --- Dias trabalhados -----------------------------------------------------------------------

    @PostMapping("/work-days")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_WORK_DAY_CREATED", entityType = "EmployeeWorkDay", captureArgs = true)
    @Operation(summary = "Marca um dia trabalhado de diarista (Admin)")
    public ResponseEntity<WorkDayResponse> createWorkDay(@Valid @RequestBody WorkDayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollService.createWorkDay(request));
    }

    @PostMapping("/work-days/bulk")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_WORK_DAYS_BULK_CREATED", entityType = "EmployeeWorkDay", captureArgs = true)
    @Operation(summary = "Marca vários dias trabalhados com a diária padrão (Admin)")
    public ResponseEntity<List<WorkDayResponse>> createWorkDaysBulk(@Valid @RequestBody WorkDayBulkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollService.createWorkDaysBulk(request));
    }

    @PutMapping("/work-days/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_WORK_DAY_UPDATED", entityType = "EmployeeWorkDay", captureArgs = true)
    @Operation(summary = "Edita valor/justificativa de um dia trabalhado (Admin)")
    public ResponseEntity<WorkDayResponse> updateWorkDay(@PathVariable Long id, @Valid @RequestBody WorkDayRequest request) {
        return ResponseEntity.ok(payrollService.updateWorkDay(id, request));
    }

    @DeleteMapping("/work-days/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_WORK_DAY_DELETED", entityType = "EmployeeWorkDay", captureArgs = true)
    @Operation(summary = "Desmarca um dia trabalhado (Admin)")
    public ResponseEntity<Void> deleteWorkDay(@PathVariable Long id) {
        payrollService.deleteWorkDay(id);
        return ResponseEntity.noContent().build();
    }

    // --- Acréscimos e abatimentos ---------------------------------------------------------------

    @PostMapping("/adjustments")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_ADJUSTMENT_CREATED", entityType = "PayrollAdjustment", captureArgs = true)
    @Operation(summary = "Lança bonificação, 13º, férias, vale, desconto... na folha (Admin)")
    public ResponseEntity<AdjustmentResponse> createAdjustment(@Valid @RequestBody AdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollService.createAdjustment(request));
    }

    @PutMapping("/adjustments/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_ADJUSTMENT_UPDATED", entityType = "PayrollAdjustment", captureArgs = true)
    @Operation(summary = "Edita um lançamento da folha (Admin)")
    public ResponseEntity<AdjustmentResponse> updateAdjustment(@PathVariable Long id, @Valid @RequestBody AdjustmentRequest request) {
        return ResponseEntity.ok(payrollService.updateAdjustment(id, request));
    }

    @DeleteMapping("/adjustments/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_ADJUSTMENT_DELETED", entityType = "PayrollAdjustment", captureArgs = true)
    @Operation(summary = "Exclui um lançamento da folha (Admin)")
    public ResponseEntity<Void> deleteAdjustment(@PathVariable Long id) {
        payrollService.deleteAdjustment(id);
        return ResponseEntity.noContent().build();
    }

    // --- Pagamentos -----------------------------------------------------------------------------

    @PostMapping("/payments")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_PAYMENT_CREATED", entityType = "PayrollPayment", captureArgs = true)
    @Operation(summary = "Registra que a colaboradora foi paga por um período (Admin)")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollService.createPayment(request));
    }

    @PutMapping("/payments/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_PAYMENT_UPDATED", entityType = "PayrollPayment", captureArgs = true)
    @Operation(summary = "Edita um pagamento registrado (Admin)")
    public ResponseEntity<PaymentResponse> updatePayment(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(payrollService.updatePayment(id, request));
    }

    @DeleteMapping("/payments/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PAYROLL_PAYMENT_DELETED", entityType = "PayrollPayment", captureArgs = true)
    @Operation(summary = "Exclui um pagamento registrado — o período volta a ficar em aberto (Admin)")
    public ResponseEntity<Void> deletePayment(@PathVariable Long id) {
        payrollService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }
}
