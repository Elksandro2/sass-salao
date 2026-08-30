package com.cristiane.salon.models.report.repository;

import com.cristiane.salon.models.report.entity.DiaristaWorkedDaysOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaristaWorkedDaysOverrideRepository extends JpaRepository<DiaristaWorkedDaysOverride, Long> {

    Optional<DiaristaWorkedDaysOverride> findByEmployeeIdAndPeriodStartAndPeriodEnd(
            Long employeeId, LocalDate periodStart, LocalDate periodEnd);

    List<DiaristaWorkedDaysOverride> findByPeriodStartAndPeriodEnd(LocalDate periodStart, LocalDate periodEnd);
}
