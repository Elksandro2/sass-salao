package com.cristiane.salon.models.employee.dto;

import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import java.math.BigDecimal;

public record EmployeeResponse(
        Long id,
        Long userId,
        String name,
        String email,
        String roleName,
        RemunerationType remunerationType,
        BigDecimal remunerationValue
) {
    public static EmployeeResponse fromEntity(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getUser().getId(),
                employee.getUser().getName(),
                employee.getUser().getEmail(),
                employee.getUser().getRoleName(),
                employee.getRemunerationType(),
                employee.getRemunerationValue()
        );
    }
}
