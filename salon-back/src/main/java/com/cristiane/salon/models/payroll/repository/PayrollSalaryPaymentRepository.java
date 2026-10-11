package com.cristiane.salon.models.payroll.repository;

import com.cristiane.salon.models.payroll.entity.PayrollSalaryPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PayrollSalaryPaymentRepository extends JpaRepository<PayrollSalaryPayment, Long> {

    List<PayrollSalaryPayment> findByEmployeeId(Long employeeId);

    List<PayrollSalaryPayment> findByPaymentId(Long paymentId);

    List<PayrollSalaryPayment> findByPaymentIdIn(Collection<Long> paymentIds);

    @Modifying
    @Query("DELETE FROM PayrollSalaryPayment s WHERE s.paymentId = :paymentId")
    void deleteByPaymentId(@Param("paymentId") Long paymentId);
}
