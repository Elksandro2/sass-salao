package com.cristiane.salon.models.generalnote.repository;

import com.cristiane.salon.models.generalnote.entity.GeneralNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneralNoteRepository extends JpaRepository<GeneralNote, Long> {
    /** Pendentes primeiro, mais recentes no topo dentro de cada grupo. */
    List<GeneralNote> findAllByOrderByDoneAscCreatedAtDesc();
}
