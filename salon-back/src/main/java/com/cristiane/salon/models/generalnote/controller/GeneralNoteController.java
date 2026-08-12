package com.cristiane.salon.models.generalnote.controller;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.models.generalnote.dto.GeneralNoteRequest;
import com.cristiane.salon.models.generalnote.dto.GeneralNoteResponse;
import com.cristiane.salon.models.generalnote.service.GeneralNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/general-notes")
@RequiredArgsConstructor
@Tag(name = "General Notes", description = "Anotações gerais do salão, não vinculadas a cliente/agendamento")
public class GeneralNoteController {

    private final GeneralNoteService generalNoteService;

    @GetMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Lista as anotações gerais (pendentes primeiro)")
    public ResponseEntity<List<GeneralNoteResponse>> findAll() {
        return ResponseEntity.ok(generalNoteService.findAll());
    }

    @PostMapping
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "GENERAL_NOTE_CREATED", entityType = "GeneralNote", captureArgs = true)
    @Operation(summary = "Cria uma anotação geral")
    public ResponseEntity<GeneralNoteResponse> create(@Valid @RequestBody GeneralNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(generalNoteService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "GENERAL_NOTE_UPDATED", entityType = "GeneralNote", captureArgs = true)
    @Operation(summary = "Edita o conteúdo de uma anotação geral")
    public ResponseEntity<GeneralNoteResponse> updateContent(
            @PathVariable Long id, @Valid @RequestBody GeneralNoteRequest request) {
        return ResponseEntity.ok(generalNoteService.updateContent(id, request));
    }

    @PatchMapping("/{id}/done")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "GENERAL_NOTE_TOGGLED", entityType = "GeneralNote", captureArgs = true)
    @Operation(summary = "Alterna pendente/concluída")
    public ResponseEntity<GeneralNoteResponse> toggleDone(@PathVariable Long id) {
        return ResponseEntity.ok(generalNoteService.toggleDone(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "GENERAL_NOTE_DELETED", entityType = "GeneralNote", captureArgs = true)
    @Operation(summary = "Remove uma anotação geral")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        generalNoteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
