package com.cristiane.salon.models.employee.dto;

import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.entity.CommissionScope;
import java.math.BigDecimal;

public record EmployeeResponse(
        Long id,
        Long userId,
        String name,
        String email,
        String roleName,
        RemunerationType remunerationType,
        CommissionScope commissionScope,
        BigDecimal remunerationValue,
        BigDecimal commissionValue
) {
    public static EmployeeResponse fromEntity(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getUser().getId(),
                employee.getUser().getName(),
                employee.getUser().getEmail(),
                employee.getUser().getRoleName(),
                employee.getRemunerationType(),
                employee.getCommissionScope(),
                employee.getRemunerationValue(),
                employee.getCommissionValue()
        );
    }
}
