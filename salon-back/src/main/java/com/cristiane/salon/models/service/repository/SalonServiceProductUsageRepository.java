package com.cristiane.salon.models.service.repository;

import com.cristiane.salon.models.service.entity.SalonServiceProductUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalonServiceProductUsageRepository extends JpaRepository<SalonServiceProductUsage, Long> {
    List<SalonServiceProductUsage> findBySalonServiceId(Long salonServiceId);

    void deleteBySalonServiceId(Long salonServiceId);
}
