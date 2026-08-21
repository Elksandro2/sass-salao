package com.cristiane.salon.models.businesssettings.repository;

import com.cristiane.salon.models.businesssettings.entity.SalonBusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalonBusinessSettingsRepository extends JpaRepository<SalonBusinessSettings, Long> {

    Optional<SalonBusinessSettings> findFirstByOrderByIdAsc();
}
