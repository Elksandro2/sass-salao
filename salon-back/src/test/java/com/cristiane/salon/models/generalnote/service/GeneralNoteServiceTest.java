package com.cristiane.salon.models.generalnote.service;

import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.generalnote.dto.GeneralNoteRequest;
import com.cristiane.salon.models.generalnote.dto.GeneralNoteResponse;
import com.cristiane.salon.models.generalnote.entity.GeneralNote;
import com.cristiane.salon.models.generalnote.repository.GeneralNoteRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralNoteServiceTest {

    @Mock
    private GeneralNoteRepository generalNoteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GeneralNoteService generalNoteService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setName("Cristiane");
        admin.setEmail("admin@salao.com");
        admin.setRole(new Role(1L, "ADMIN", null));

        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(admin.getEmail());
        SecurityContext secCtx = mock(SecurityContext.class);
        lenient().when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
        lenient().when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_shouldSaveWithAuthenticatedUserAsAuthor() {
        when(generalNoteRepository.save(any(GeneralNote.class))).thenAnswer(inv -> {
            GeneralNote n = inv.getArgument(0);
            n.setId(10L);
            return n;
        });

        GeneralNoteResponse result = generalNoteService.create(new GeneralNoteRequest("comprar toalha"));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.content()).isEqualTo("comprar toalha");
        assertThat(result.authorName()).isEqualTo("Cristiane");
        assertThat(result.done()).isFalse();

        ArgumentCaptor<GeneralNote> captor = ArgumentCaptor.forClass(GeneralNote.class);
        verify(generalNoteRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthor()).isEqualTo(admin);
    }

    @Test
    void findAll_shouldReturnPendingFirstOrder() {
        GeneralNote n1 = new GeneralNote();
        n1.setId(1L);
        n1.setContent("nota 1");
        n1.setAuthor(admin);
        when(generalNoteRepository.findAllByOrderByDoneAscCreatedAtDesc()).thenReturn(List.of(n1));

        List<GeneralNoteResponse> result = generalNoteService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("nota 1");
    }

    @Test
    void updateContent_whenFound_shouldUpdateAndSave() {
        GeneralNote existing = new GeneralNote();
        existing.setId(5L);
        existing.setContent("velho");
        existing.setAuthor(admin);
        when(generalNoteRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(generalNoteRepository.save(any(GeneralNote.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneralNoteResponse result = generalNoteService.updateContent(5L, new GeneralNoteRequest("novo"));

        assertThat(result.content()).isEqualTo("novo");
    }

    @Test
    void updateContent_whenNotFound_shouldThrowResourceNotFoundException() {
        when(generalNoteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generalNoteService.updateContent(99L, new GeneralNoteRequest("x")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Anotação não encontrada");
    }

    @Test
    void toggleDone_shouldFlipDoneFlag() {
        GeneralNote existing = new GeneralNote();
        existing.setId(5L);
        existing.setContent("nota");
        existing.setAuthor(admin);
        existing.setDone(false);
        when(generalNoteRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(generalNoteRepository.save(any(GeneralNote.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneralNoteResponse result = generalNoteService.toggleDone(5L);
        assertThat(result.done()).isTrue();

        existing.setDone(true);
        GeneralNoteResponse result2 = generalNoteService.toggleDone(5L);
        assertThat(result2.done()).isFalse();
    }

    @Test
    void delete_whenFound_shouldDelete() {
        when(generalNoteRepository.existsById(5L)).thenReturn(true);

        generalNoteService.delete(5L);

        verify(generalNoteRepository).deleteById(5L);
    }

    @Test
    void delete_whenNotFound_shouldThrowResourceNotFoundException() {
        when(generalNoteRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> generalNoteService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Anotação não encontrada");
        verify(generalNoteRepository, never()).deleteById(any());
    }
}
