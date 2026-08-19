package com.cristiane.salon.models.fixedexpense.controller;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.models.fixedexpense.dto.FixedExpenseRequest;
import com.cristiane.salon.models.fixedexpense.dto.FixedExpenseResponse;
import com.cristiane.salon.models.fixedexpense.service.FixedExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/fixed-expenses")
@RequiredArgsConstructor
@Tag(name = "Fixed Expenses", description = "Gastos fixos/operacionais do salão (Admin/Gerente)")
public class FixedExpenseController {

    private final FixedExpenseService fixedExpenseService;

    @GetMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Lista os gastos fixos por período, paginado (Admin/Gerente)")
    public ResponseEntity<Page<FixedExpenseResponse>> findByPeriod(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(fixedExpenseService.findByPeriod(from, to, pageable));
    }

    @PostMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "FIXED_EXPENSE_CREATED", entityType = "FixedExpense", captureArgs = true)
    @Operation(summary = "Cria um gasto fixo (Admin/Gerente)")
    public ResponseEntity<FixedExpenseResponse> create(@Valid @RequestBody FixedExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fixedExpenseService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "FIXED_EXPENSE_DELETED", entityType = "FixedExpense", captureArgs = true)
    @Operation(summary = "Exclui um gasto fixo (Admin/Gerente)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fixedExpenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
