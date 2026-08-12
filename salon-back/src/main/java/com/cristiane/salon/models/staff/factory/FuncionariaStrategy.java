package com.cristiane.salon.models.staff.factory;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.models.employee.entity.CommissionScope;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.staff.dto.StaffProfileRequest;
import com.cristiane.salon.models.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * FUNCIONARIA atende clientes, então precisa de um registro de Employee com a remuneração
 * definida — é isso que alimenta o cálculo de comissão nos relatórios financeiros.
 */
@Component
@RequiredArgsConstructor
public class FuncionariaStrategy implements StaffRoleStrategy {

    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    private final EmployeeRepository employeeRepository;

    @Override
    public String getRoleName() {
        return "FUNCIONARIA";
    }

    @Override
    public void validate(StaffProfileRequest request) {
        RemunerationType type = request.remunerationType();
        if (type == null) {
            throw new BadRequestException("O tipo de remuneração é obrigatório para funcionárias");
        }

        boolean isCommissioned = type == RemunerationType.COMISSIONADO
                || type == RemunerationType.FIXO_E_COMISSIONADO;

        if (isCommissioned && request.commissionScope() == null) {
            throw new BadRequestException(
                    "O escopo da comissão é obrigatório para funcionárias comissionadas ou mistas");
        }
        if (!isCommissioned && request.commissionScope() != null) {
            throw new BadRequestException(
                    "O escopo da comissão só se aplica a funcionárias comissionadas ou mistas");
        }

        if (request.remunerationValue() == null) {
            throw new BadRequestException("O valor de remuneração é obrigatório para funcionárias");
        }

        // Em COMISSIONADO o "valor de remuneração" é uma porcentagem, então tem teto de 100.
        if (type == RemunerationType.COMISSIONADO
                && request.remunerationValue().compareTo(MAX_PERCENTAGE) > 0) {
            throw new BadRequestException("A porcentagem de comissão não pode exceder 100%");
        }

        if (type == RemunerationType.FIXO_E_COMISSIONADO) {
            if (request.commissionValue() == null) {
                throw new BadRequestException(
                        "A porcentagem de comissão é obrigatória para funcionárias com remuneração mista");
            }
            if (request.commissionValue().compareTo(MAX_PERCENTAGE) > 0) {
                throw new BadRequestException("A porcentagem de comissão não pode exceder 100%");
            }
        }
    }

    @Override
    public void onStaffCreated(User user, StaffProfileRequest request) {
        RemunerationType type = request.remunerationType();
        boolean isCommissioned = type == RemunerationType.COMISSIONADO
                || type == RemunerationType.FIXO_E_COMISSIONADO;

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setRemunerationType(type);
        employee.setRemunerationValue(request.remunerationValue());
        employee.setCommissionScope(isCommissioned ? request.commissionScope() : null);
        employee.setCommissionValue(
                type == RemunerationType.FIXO_E_COMISSIONADO ? request.commissionValue() : null);

        employeeRepository.save(employee);
    }
}
