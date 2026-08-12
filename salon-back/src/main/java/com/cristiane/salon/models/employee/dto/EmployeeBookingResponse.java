package com.cristiane.salon.models.employee.dto;

import com.cristiane.salon.models.employee.entity.Employee;

public record EmployeeBookingResponse(
        Long id,
        Long userId,
        String name
) {
    public static EmployeeBookingResponse fromEntity(Employee employee) {
        return new EmployeeBookingResponse(
                employee.getId(),
                employee.getUser().getId(),
                employee.getUser().getName()
        );
    }
}
