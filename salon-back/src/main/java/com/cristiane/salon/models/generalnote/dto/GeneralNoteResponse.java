package com.cristiane.salon.models.generalnote.dto;

import com.cristiane.salon.models.generalnote.entity.GeneralNote;

import java.time.Instant;

public record GeneralNoteResponse(
        Long id,
        String content,
        String authorName,
        Boolean done,
        Instant createdAt,
        Instant updatedAt
) {
    public static GeneralNoteResponse fromEntity(GeneralNote note) {
        return new GeneralNoteResponse(
                note.getId(),
                note.getContent(),
                note.getAuthor().getName(),
                note.isDone(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
