package com.cristiane.salon.models.employee.dto;

import com.cristiane.salon.models.employee.entity.RemunerationType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record EmployeeRequest(
        @NotNull(message = "O ID do usuário é obrigatório")
        Long userId,

        RemunerationType remunerationType,

        /** Salário base — só usado para SALARIO_FIXO/FIXO_E_COMISSIONADO. */
        BigDecimal remunerationValue
) {}
