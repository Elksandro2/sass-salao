package com.cristiane.salon.models.appointment.controller;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.models.appointment.dto.AppointmentFilter;
import com.cristiane.salon.models.appointment.dto.AppointmentRequest;
import com.cristiane.salon.models.appointment.dto.AppointmentResponse;
import com.cristiane.salon.models.appointment.dto.ConfirmAppointmentRequest;
import com.cristiane.salon.models.appointment.dto.GeneratePixRequest;
import com.cristiane.salon.models.appointment.dto.UpdateAppointmentExpensesRequest;
import com.cristiane.salon.models.appointment.dto.UpdateAppointmentProductsRequest;
import com.cristiane.salon.models.appointment.dto.UpdateAppointmentServicesRequest;
import com.cristiane.salon.models.appointment.dto.UpdateInternalNotesRequest;
import com.cristiane.salon.models.appointment.service.AppointmentService;
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
@RequestMapping("/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments", description = "Endpoints para gerenciamento de agendamentos")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "APPOINTMENT_CREATED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Cliente solicita agenda ou equipe cria agendamento com horário")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.create(request));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "APPOINTMENT_CONFIRMED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Confirma solicitação do cliente definindo data e hora")
    public ResponseEntity<AppointmentResponse> confirm(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmAppointmentRequest body) {
        return ResponseEntity.ok(appointmentService.confirm(id, body.scheduledAt()));
    }

    @PatchMapping("/{id}/internal-notes")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "APPOINTMENT_INTERNAL_NOTES_UPDATED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Define a observação interna da equipe sobre o agendamento (Admin/Gerente/Funcionária responsável)")
    public ResponseEntity<AppointmentResponse> updateInternalNotes(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInternalNotesRequest body) {
        return ResponseEntity.ok(appointmentService.updateInternalNotes(id, body.internalNotes()));
    }

    @PatchMapping("/{id}/services")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "APPOINTMENT_SERVICES_UPDATED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Define os serviços de um agendamento (Admin/Gerente/Funcionária responsável)")
    public ResponseEntity<AppointmentResponse> updateServices(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentServicesRequest body) {
        return ResponseEntity.ok(appointmentService.updateServices(id, body.services()));
    }

    @PatchMapping("/{id}/products")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "APPOINTMENT_PRODUCTS_UPDATED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Define os produtos vendidos num agendamento (Admin/Gerente/Funcionária responsável)")
    public ResponseEntity<AppointmentResponse> updateProducts(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentProductsRequest body) {
        return ResponseEntity.ok(appointmentService.updateProducts(id, body.products()));
    }

    @PatchMapping("/{id}/expenses")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "APPOINTMENT_EXPENSES_UPDATED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Define as despesas itemizadas de um agendamento (Admin/Gerente/Funcionária responsável)")
    public ResponseEntity<AppointmentResponse> updateExpenses(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentExpensesRequest body) {
        return ResponseEntity.ok(appointmentService.updateExpenses(id, body.expenses()));
    }

    @PatchMapping("/{id}/decline")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "APPOINTMENT_DECLINED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Recusa solicitação de agendamento do cliente")
    public ResponseEntity<AppointmentResponse> decline(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.decline(id));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lista os agendamentos do usuário logado (Cliente)")
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments() {
        return ResponseEntity.ok(appointmentService.getMyAppointments());
    }

    @GetMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Lista todos os agendamentos com filtros e paginação (Admin/Gerente)")
    public ResponseEntity<Page<AppointmentResponse>> findAll(
            @Valid AppointmentFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(appointmentService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Busca um agendamento por ID")
    public ResponseEntity<AppointmentResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.findById(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "APPOINTMENT_CANCELLED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Cancela um agendamento (Dono ou Admin)")
    public ResponseEntity<AppointmentResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.cancel(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "APPOINTMENT_STATUS_CHANGED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Atualiza o status de um agendamento (Admin/Gerente/Funcionária)")
    public ResponseEntity<AppointmentResponse> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ResponseEntity.ok(appointmentService.updateStatus(id, status));
    }

    @PatchMapping("/{id}/payment-status")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "APPOINTMENT_PAYMENT_STATUS_CHANGED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Atualiza o status de pagamento de um agendamento (Admin/Gerente/Funcionária)")
    public ResponseEntity<AppointmentResponse> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam String paymentStatus,
            @RequestParam(required = false) String paymentMethod) {
        return ResponseEntity.ok(appointmentService.updatePaymentStatus(id, paymentStatus, paymentMethod));
    }

    @PostMapping("/{id}/pix")
    @PreAuthorize("isAuthenticated()") // Permite cliente e admin, pois a validação real de dono da reserva é feita lá no Service
    @Auditable(action = "PIX_GENERATED", entityType = "Appointment", captureArgs = true)
    @Operation(summary = "Gera a chave PIX (Copia e Cola) para um agendamento")
    public ResponseEntity<AppointmentResponse> generatePix(
            @PathVariable Long id,
            @Valid @RequestBody GeneratePixRequest request) {
        return ResponseEntity.ok(appointmentService.generatePixPayment(id, request));
    }
}
