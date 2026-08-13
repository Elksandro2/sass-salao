package com.cristiane.salon.models.clientanamnesis.controller;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.models.clientanamnesis.dto.ClientAnamnesisRequest;
import com.cristiane.salon.models.clientanamnesis.dto.ClientAnamnesisResponse;
import com.cristiane.salon.models.clientanamnesis.service.ClientAnamnesisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/clients/{clientId}/anamnesis")
@RequiredArgsConstructor
@Tag(name = "Client Anamnesis", description = "Ficha de anamnese (dado de saúde) do cliente — acesso restrito, LGPD")
public class ClientAnamnesisController {

    private final ClientAnamnesisService clientAnamnesisService;

    @GetMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Busca a ficha de anamnese de um cliente (Admin/Gerente)")
    public ResponseEntity<ClientAnamnesisResponse> findByClientId(@PathVariable Long clientId) {
        return ResponseEntity.ok(clientAnamnesisService.findByClientId(clientId));
    }

    @PutMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "CLIENT_ANAMNESIS_SAVED", entityType = "ClientAnamnesis", captureArgs = true)
    @Operation(summary = "Cria ou atualiza a ficha de anamnese de um cliente (Admin/Gerente)")
    public ResponseEntity<ClientAnamnesisResponse> upsert(
            @PathVariable Long clientId, @Valid @RequestBody ClientAnamnesisRequest request) {
        return ResponseEntity.ok(clientAnamnesisService.upsert(clientId, request));
    }

    @DeleteMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "CLIENT_ANAMNESIS_DELETED", entityType = "ClientAnamnesis", captureArgs = true)
    @Operation(summary = "Apaga a ficha de anamnese de um cliente — direito ao esquecimento (Admin/Gerente)")
    public ResponseEntity<Void> delete(@PathVariable Long clientId) {
        clientAnamnesisService.delete(clientId);
        return ResponseEntity.noContent().build();
    }
}
