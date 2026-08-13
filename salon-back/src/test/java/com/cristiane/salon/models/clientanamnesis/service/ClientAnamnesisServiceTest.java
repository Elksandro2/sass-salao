package com.cristiane.salon.models.clientanamnesis.service;

import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.clientanamnesis.dto.ClientAnamnesisRequest;
import com.cristiane.salon.models.clientanamnesis.dto.ClientAnamnesisResponse;
import com.cristiane.salon.models.clientanamnesis.entity.ClientAnamnesis;
import com.cristiane.salon.models.clientanamnesis.enums.HairType;
import com.cristiane.salon.models.clientanamnesis.enums.SkinType;
import com.cristiane.salon.models.clientanamnesis.repository.ClientAnamnesisRepository;
import com.cristiane.salon.models.user.entity.Role;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientAnamnesisServiceTest {

    @Mock
    private ClientAnamnesisRepository clientAnamnesisRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClientAnamnesisService clientAnamnesisService;

    private User staff;
    private User client;

    @BeforeEach
    void setUp() {
        staff = new User();
        staff.setId(1L);
        staff.setName("Cristiane");
        staff.setEmail("admin@salao.com");
        staff.setRole(new Role(1L, "ADMIN", null));

        client = new User();
        client.setId(20L);
        client.setName("Ana Cliente");
        client.setEmail("ana@example.com");
        client.setRole(new Role(2L, "CLIENTE", null));

        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(staff.getEmail());
        SecurityContext secCtx = mock(SecurityContext.class);
        lenient().when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
        lenient().when(userRepository.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ClientAnamnesisRequest validRequest() {
        return new ClientAnamnesisRequest(
                "Alergia a niquel", "Nenhuma", "Nenhum", "Prefere produtos sem cheiro",
                SkinType.MISTA, HairType.CACHEADO, true);
    }

    @Test
    void upsert_whenNoExistingRecord_shouldCreateWithConsentFromCurrentUser() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(client));
        when(userRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(clientAnamnesisRepository.findByClientId(20L)).thenReturn(Optional.empty());
        when(clientAnamnesisRepository.save(any(ClientAnamnesis.class))).thenAnswer(inv -> {
            ClientAnamnesis a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        ClientAnamnesisResponse result = clientAnamnesisService.upsert(20L, validRequest());

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.clientId()).isEqualTo(20L);
        assertThat(result.allergies()).isEqualTo("Alergia a niquel");
        assertThat(result.skinType()).isEqualTo(SkinType.MISTA);
        assertThat(result.hairType()).isEqualTo(HairType.CACHEADO);
        assertThat(result.consentGivenByName()).isEqualTo("Cristiane");
        assertThat(result.updatedByName()).isEqualTo("Cristiane");

        ArgumentCaptor<ClientAnamnesis> captor = ArgumentCaptor.forClass(ClientAnamnesis.class);
        verify(clientAnamnesisRepository).save(captor.capture());
        assertThat(captor.getValue().getConsentGivenByUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getConsentGivenAt()).isNotNull();
    }

    @Test
    void upsert_whenExistingRecord_shouldPreserveOriginalConsentMetadata() {
        User originalStaff = new User();
        originalStaff.setId(2L);
        originalStaff.setName("Gerente Antiga");

        Instant originalConsentAt = Instant.parse("2026-01-01T10:00:00Z");
        ClientAnamnesis existing = new ClientAnamnesis();
        existing.setId(50L);
        existing.setClient(client);
        existing.setAllergies("Velho valor");
        existing.setConsentGivenAt(originalConsentAt);
        existing.setConsentGivenByUserId(2L);

        when(userRepository.findById(20L)).thenReturn(Optional.of(client));
        when(userRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(userRepository.findById(2L)).thenReturn(Optional.of(originalStaff));
        when(clientAnamnesisRepository.findByClientId(20L)).thenReturn(Optional.of(existing));
        when(clientAnamnesisRepository.save(any(ClientAnamnesis.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientAnamnesisResponse result = clientAnamnesisService.upsert(20L, validRequest());

        assertThat(result.allergies()).isEqualTo("Alergia a niquel");
        assertThat(result.consentGivenByName()).isEqualTo("Gerente Antiga");
        assertThat(result.consentGivenAt()).isEqualTo(originalConsentAt);
        assertThat(result.updatedByName()).isEqualTo("Cristiane");
    }

    @Test
    void upsert_whenConsentNotGiven_shouldThrowBadRequestException() {
        ClientAnamnesisRequest request = new ClientAnamnesisRequest(
                "Alergia", null, null, null, null, null, false);

        assertThatThrownBy(() -> clientAnamnesisService.upsert(20L, request))
                .isInstanceOf(com.cristiane.salon.exception.BadRequestException.class)
                .hasMessage("É necessário o consentimento da cliente para registrar dados de saúde");
        verify(clientAnamnesisRepository, never()).save(any());
    }

    @Test
    void upsert_whenClientNotFound_shouldThrowResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientAnamnesisService.upsert(99L, validRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cliente não encontrado");
    }

    @Test
    void upsert_shouldStoreBlankFieldsAsNull() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(client));
        when(userRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(clientAnamnesisRepository.findByClientId(20L)).thenReturn(Optional.empty());
        when(clientAnamnesisRepository.save(any(ClientAnamnesis.class))).thenAnswer(inv -> {
            ClientAnamnesis a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        ClientAnamnesisRequest request = new ClientAnamnesisRequest("", "  ", null, null, null, null, true);

        ClientAnamnesisResponse result = clientAnamnesisService.upsert(20L, request);

        assertThat(result.allergies()).isNull();
        assertThat(result.healthConditions()).isNull();
        assertThat(result.medications()).isNull();
        assertThat(result.additionalNotes()).isNull();
    }

    @Test
    void findByClientId_whenFound_shouldReturnResponse() {
        ClientAnamnesis existing = new ClientAnamnesis();
        existing.setId(50L);
        existing.setClient(client);
        existing.setAllergies("Poeira");
        existing.setConsentGivenAt(Instant.now());
        existing.setConsentGivenByUserId(1L);
        when(clientAnamnesisRepository.findByClientId(20L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(staff));

        ClientAnamnesisResponse result = clientAnamnesisService.findByClientId(20L);

        assertThat(result.allergies()).isEqualTo("Poeira");
        assertThat(result.consentGivenByName()).isEqualTo("Cristiane");
    }

    @Test
    void findByClientId_whenNotFound_shouldThrowResourceNotFoundException() {
        when(clientAnamnesisRepository.findByClientId(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientAnamnesisService.findByClientId(20L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Este cliente ainda não tem ficha de anamnese");
    }

    @Test
    void delete_whenFound_shouldDeleteByClientId() {
        ClientAnamnesis existing = new ClientAnamnesis();
        existing.setId(50L);
        when(clientAnamnesisRepository.findByClientId(20L)).thenReturn(Optional.of(existing));

        clientAnamnesisService.delete(20L);

        verify(clientAnamnesisRepository).deleteByClientId(20L);
    }

    @Test
    void delete_whenNotFound_shouldThrowResourceNotFoundException() {
        when(clientAnamnesisRepository.findByClientId(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientAnamnesisService.delete(20L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Este cliente ainda não tem ficha de anamnese");
        verify(clientAnamnesisRepository, never()).deleteByClientId(any());
    }
}
