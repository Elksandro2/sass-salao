package com.cristiane.salon.models.payroll.repository;

import com.cristiane.salon.models.payroll.entity.EmployeeWorkDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeWorkDayRepository extends JpaRepository<EmployeeWorkDay, Long> {

    List<EmployeeWorkDay> findByWorkDateBetweenOrderByWorkDateAsc(LocalDate from, LocalDate to);

    List<EmployeeWorkDay> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(Long employeeId, LocalDate from, LocalDate to);

    Optional<EmployeeWorkDay> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
}
