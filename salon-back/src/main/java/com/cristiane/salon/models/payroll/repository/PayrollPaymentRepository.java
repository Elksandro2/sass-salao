package com.cristiane.salon.models.payroll.repository;

import com.cristiane.salon.models.payroll.entity.PayrollPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, Long> {

    List<PayrollPayment> findByEmployeeIdOrderByPeriodStartAsc(Long employeeId);

    List<PayrollPayment> findAllByOrderByPeriodStartAsc();
}
