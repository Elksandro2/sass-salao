package com.cristiane.salon.models.employee.controller;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.models.employee.dto.EmployeeActingRequest;
import com.cristiane.salon.models.employee.dto.EmployeeActingResponse;
import com.cristiane.salon.models.employee.dto.EmployeeBookingResponse;
import com.cristiane.salon.models.employee.dto.EmployeeFilter;
import com.cristiane.salon.models.employee.dto.EmployeeRequest;
import com.cristiane.salon.models.employee.dto.EmployeeResponse;
import com.cristiane.salon.models.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Endpoints para gerenciamento de funcionárias")
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Lista todas as funcionárias com filtros e paginação (Admin/Gerente)")
    public ResponseEntity<Page<EmployeeResponse>> findAll(
            @Valid EmployeeFilter filter,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(employeeService.findAll(filter, pageable));
    }

    @GetMapping("/booking")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lista funcionárias para agendamento (público)")
    public ResponseEntity<List<EmployeeBookingResponse>> findAllForBooking() {
        return ResponseEntity.ok(employeeService.findAllForBooking());
    }

    @GetMapping("/me/acting")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Diz se o usuário logado atua como profissional nos agendamentos")
    public ResponseEntity<EmployeeActingResponse> getMyActing() {
        return ResponseEntity.ok(employeeService.getMyActingProfile());
    }

    @PutMapping("/me/acting")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "EMPLOYEE_ACTING_TOGGLED", entityType = "Employee", captureArgs = true)
    @Operation(summary = "Liga/desliga a atuação do admin logado como profissional nos agendamentos")
    public ResponseEntity<EmployeeActingResponse> setMyActing(@Valid @RequestBody EmployeeActingRequest request) {
        return ResponseEntity.ok(employeeService.setMyActing(request.acting()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Busca uma funcionária por ID (Admin/Gerente)")
    public ResponseEntity<EmployeeResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.findById(id));
    }

    @PostMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "EMPLOYEE_CREATED", entityType = "Employee", captureArgs = true)
    @Operation(summary = "Cria uma nova funcionária (Admin/Gerente)")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "EMPLOYEE_UPDATED", entityType = "Employee", captureArgs = true)
    @Operation(summary = "Atualiza uma funcionária (Admin/Gerente)")
    public ResponseEntity<EmployeeResponse> update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(employeeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "EMPLOYEE_DELETED", entityType = "Employee", captureArgs = true)
    @Operation(summary = "Remove uma funcionária (Admin/Gerente)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
