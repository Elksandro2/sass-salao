package com.cristiane.salon.models.generalnote.service;

import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.models.generalnote.dto.GeneralNoteRequest;
import com.cristiane.salon.models.generalnote.dto.GeneralNoteResponse;
import com.cristiane.salon.models.generalnote.entity.GeneralNote;
import com.cristiane.salon.models.generalnote.repository.GeneralNoteRepository;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeneralNoteService {

    private final GeneralNoteRepository generalNoteRepository;
    private final UserRepository userRepository;

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));
    }

    @Transactional(readOnly = true)
    public List<GeneralNoteResponse> findAll() {
        return generalNoteRepository.findAllByOrderByDoneAscCreatedAtDesc().stream()
                .map(GeneralNoteResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public GeneralNoteResponse create(GeneralNoteRequest request) {
        GeneralNote note = new GeneralNote();
        note.setContent(request.content());
        note.setAuthor(getAuthenticatedUser());
        return GeneralNoteResponse.fromEntity(generalNoteRepository.save(note));
    }

    @Transactional
    public GeneralNoteResponse updateContent(Long id, GeneralNoteRequest request) {
        GeneralNote note = getOrThrow(id);
        note.setContent(request.content());
        return GeneralNoteResponse.fromEntity(generalNoteRepository.save(note));
    }

    @Transactional
    public GeneralNoteResponse toggleDone(Long id) {
        GeneralNote note = getOrThrow(id);
        note.setDone(!note.isDone());
        return GeneralNoteResponse.fromEntity(generalNoteRepository.save(note));
    }

    @Transactional
    public void delete(Long id) {
        if (!generalNoteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Anotação não encontrada");
        }
        generalNoteRepository.deleteById(id);
    }

    private GeneralNote getOrThrow(Long id) {
        return generalNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anotação não encontrada"));
    }
}
