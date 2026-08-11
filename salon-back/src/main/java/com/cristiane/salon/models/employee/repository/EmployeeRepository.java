package com.cristiane.salon.models.employee.repository;

import com.cristiane.salon.models.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {
    Optional<Employee> findByUserId(Long userId);

    @Query("SELECT e FROM Employee e WHERE e.user.active = true AND e.bookable = true AND e.user.role.name IN ('FUNCIONARIA', 'ADMIN')")
    List<Employee> findAllActiveForBooking();
}
