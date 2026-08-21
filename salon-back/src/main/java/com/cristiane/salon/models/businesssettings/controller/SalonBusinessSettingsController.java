package com.cristiane.salon.models.businesssettings.controller;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.models.businesssettings.dto.SalonBusinessSettingsResponse;
import com.cristiane.salon.models.businesssettings.dto.SalonBusinessSettingsUpdateRequest;
import com.cristiane.salon.models.businesssettings.service.SalonBusinessSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Configurações financeiras internas do salão — hoje só a comissão única sobre produtos
 * vendidos. Diferente de {@code SalonProfile}, nada aqui é público: só ADMIN/SYSADMIN (bypass
 * automático do {@code VerifyUserPermissions}, sem permissão concedida a mais ninguém).
 */
@RestController
@RequestMapping("/v1/admin/business-settings")
@RequiredArgsConstructor
@Tag(name = "Business Settings", description = "Configurações financeiras internas do salão (Admin)")
public class SalonBusinessSettingsController {

    private final SalonBusinessSettingsService service;

    @GetMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Consulta as configurações financeiras do salão (Admin)")
    public ResponseEntity<SalonBusinessSettingsResponse> getSettings() {
        return ResponseEntity.ok(service.getSettings());
    }

    @PutMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "UPDATE", entityType = "SalonBusinessSettings", captureArgs = true)
    @Operation(summary = "Atualiza a comissão única sobre produtos vendidos (Admin)")
    public ResponseEntity<SalonBusinessSettingsResponse> updateSettings(
            @Valid @RequestBody SalonBusinessSettingsUpdateRequest request) {
        return ResponseEntity.ok(service.updateSettings(request));
    }
}
