package com.cristiane.salon.models.clientanamnesis.repository;

import com.cristiane.salon.models.clientanamnesis.entity.ClientAnamnesis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientAnamnesisRepository extends JpaRepository<ClientAnamnesis, Long> {
    Optional<ClientAnamnesis> findByClientId(Long clientId);

    void deleteByClientId(Long clientId);
}
