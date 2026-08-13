package com.cristiane.salon.integrations.payment.marketplace.repository;

import com.cristiane.salon.integrations.payment.marketplace.entity.EmployeeMercadoPagoAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeMercadoPagoAccountRepository extends JpaRepository<EmployeeMercadoPagoAccount, Long> {
    Optional<EmployeeMercadoPagoAccount> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);
}
