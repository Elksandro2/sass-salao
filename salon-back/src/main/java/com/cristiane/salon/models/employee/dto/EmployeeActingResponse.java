package com.cristiane.salon.models.employee.dto;

import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;

import java.math.BigDecimal;

/**
 * Estado da "atuação como profissional" do usuário logado (usado no Meu Perfil do admin).
 *
 * <p>{@code hasProfile} = já existe um cadastro de {@link Employee} vinculado ao usuário;
 * {@code acting} = esse cadastro está agendável ({@code bookable}), ou seja, aparece no seletor
 * de profissional dos agendamentos.
 */
public record EmployeeActingResponse(
        boolean hasProfile,
        boolean acting,
        RemunerationType remunerationType,
        BigDecimal remunerationValue
) {
    public static EmployeeActingResponse notActing() {
        return new EmployeeActingResponse(false, false, null, null);
    }

    public static EmployeeActingResponse fromEntity(Employee employee) {
        return new EmployeeActingResponse(
                true,
                employee.isBookable(),
                employee.getRemunerationType(),
                employee.getRemunerationValue()
        );
    }
}
