package com.cristiane.salon.models.cashflow.controller;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.models.cashflow.dto.CashFlowRequest;
import com.cristiane.salon.models.cashflow.dto.CashFlowResponse;
import com.cristiane.salon.models.cashflow.dto.SaleUpdateRequest;
import com.cristiane.salon.models.cashflow.service.CashFlowService;
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
@RequestMapping("/v1/cashflow")
@RequiredArgsConstructor
@Tag(name = "CashFlow", description = "Endpoints para fluxo de caixa (Admin)")
public class CashFlowController {

    private final CashFlowService cashFlowService;

    @GetMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Lista o fluxo de caixa por período, paginado (Admin)")
    public ResponseEntity<Page<CashFlowResponse>> findByPeriod(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(cashFlowService.findByPeriod(from, to, pageable));
    }

    @PostMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Cria um registro manual no fluxo de caixa (Admin)")
    public ResponseEntity<CashFlowResponse> create(@Valid @RequestBody CashFlowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cashFlowService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "CASHFLOW_ENTRY_UPDATED", entityType = "CashFlow", captureArgs = true)
    @Operation(summary = "Edita um registro manual do fluxo de caixa (Admin/Gerente) — só se não vier de um agendamento")
    public ResponseEntity<CashFlowResponse> update(@PathVariable Long id, @Valid @RequestBody CashFlowRequest request) {
        return ResponseEntity.ok(cashFlowService.update(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Busca um lançamento do fluxo de caixa (Admin/Gerente)")
    public ResponseEntity<CashFlowResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(cashFlowService.findById(id));
    }

    @PutMapping("/{id}/sale")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "PRODUCT_SALE_UPDATED", entityType = "CashFlow", captureArgs = true)
    @Operation(summary = "Edita uma venda avulsa de produto: itens, vendedora, data e comissão (Admin/Gerente)")
    public ResponseEntity<CashFlowResponse> updateSale(@PathVariable Long id, @Valid @RequestBody SaleUpdateRequest request) {
        return ResponseEntity.ok(cashFlowService.updateSale(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "CASHFLOW_ENTRY_DELETED", entityType = "CashFlow", captureArgs = true)
    @Operation(summary = "Exclui um registro do fluxo de caixa (Admin)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cashFlowService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
