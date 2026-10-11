package com.cristiane.salon.models.payroll.repository;

import com.cristiane.salon.models.payroll.entity.PayrollAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Long> {

    List<PayrollAdjustment> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    List<PayrollAdjustment> findByEmployeeIdAndDateBetweenOrderByDateAsc(Long employeeId, LocalDate from, LocalDate to);
}
