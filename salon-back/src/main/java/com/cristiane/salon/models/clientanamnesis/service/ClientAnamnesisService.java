package com.cristiane.salon.models.clientanamnesis.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.models.clientanamnesis.dto.ClientAnamnesisRequest;
import com.cristiane.salon.models.clientanamnesis.dto.ClientAnamnesisResponse;
import com.cristiane.salon.models.clientanamnesis.entity.ClientAnamnesis;
import com.cristiane.salon.models.clientanamnesis.repository.ClientAnamnesisRepository;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientAnamnesisService {

    private final ClientAnamnesisRepository clientAnamnesisRepository;
    private final UserRepository userRepository;

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));
    }

    private String nameOrNull(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }

    @Transactional(readOnly = true)
    public ClientAnamnesisResponse findByClientId(Long clientId) {
        ClientAnamnesis anamnesis = clientAnamnesisRepository.findByClientId(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Este cliente ainda não tem ficha de anamnese"));
        return ClientAnamnesisResponse.fromEntity(
                anamnesis,
                nameOrNull(anamnesis.getConsentGivenByUserId()),
                nameOrNull(anamnesis.getUpdatedByUserId())
        );
    }

    /**
     * Sempre upsert: uma ficha por cliente, sem histórico de versões. Na criação, o consentimento
     * é gravado com o autor e o instante atuais; numa edição, o registro original do consentimento
     * é preservado — editar a ficha não é um novo consentimento, é uma correção do mesmo cadastro.
     */
    @Transactional
    public ClientAnamnesisResponse upsert(Long clientId, ClientAnamnesisRequest request) {
        if (!request.consentGiven()) {
            throw new BadRequestException("É necessário o consentimento da cliente para registrar dados de saúde");
        }

        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado"));
        User currentUser = getAuthenticatedUser();

        ClientAnamnesis anamnesis = clientAnamnesisRepository.findByClientId(clientId)
                .orElseGet(() -> {
                    ClientAnamnesis created = new ClientAnamnesis();
                    created.setClient(client);
                    created.setConsentGivenAt(java.time.Instant.now());
                    created.setConsentGivenByUserId(currentUser.getId());
                    return created;
                });

        anamnesis.setAllergies(blankToNull(request.allergies()));
        anamnesis.setHealthConditions(blankToNull(request.healthConditions()));
        anamnesis.setMedications(blankToNull(request.medications()));
        anamnesis.setAdditionalNotes(blankToNull(request.additionalNotes()));
        anamnesis.setSkinType(request.skinType());
        anamnesis.setHairType(request.hairType());
        anamnesis.setUpdatedByUserId(currentUser.getId());

        ClientAnamnesis saved = clientAnamnesisRepository.save(anamnesis);
        return ClientAnamnesisResponse.fromEntity(
                saved,
                nameOrNull(saved.getConsentGivenByUserId()),
                nameOrNull(saved.getUpdatedByUserId())
        );
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    /** Direito ao esquecimento (LGPD, Art. 18, VI) — apaga a ficha por completo, sem soft-delete. */
    @Transactional
    public void delete(Long clientId) {
        if (clientAnamnesisRepository.findByClientId(clientId).isEmpty()) {
            throw new ResourceNotFoundException("Este cliente ainda não tem ficha de anamnese");
        }
        clientAnamnesisRepository.deleteByClientId(clientId);
    }
}
