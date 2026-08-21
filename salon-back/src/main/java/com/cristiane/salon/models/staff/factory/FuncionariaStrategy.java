package com.cristiane.salon.models.staff.factory;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.staff.dto.StaffProfileRequest;
import com.cristiane.salon.models.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FUNCIONARIA atende clientes, então precisa de um registro de Employee com a remuneração
 * definida — é isso que alimenta o cálculo de comissão nos relatórios financeiros. A comissão
 * em si não é cadastrada aqui: vem do {@code SalonService.commissionPercent} de cada serviço
 * que ela realizar (e da comissão única de produto em {@code SalonBusinessSettings}).
 */
@Component
@RequiredArgsConstructor
public class FuncionariaStrategy implements StaffRoleStrategy {

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

        boolean needsSalary = type == RemunerationType.SALARIO_FIXO
                || type == RemunerationType.FIXO_E_COMISSIONADO;

        if (needsSalary && request.remunerationValue() == null) {
            throw new BadRequestException("O valor do salário é obrigatório para este tipo de remuneração");
        }
    }

    @Override
    public void onStaffCreated(User user, StaffProfileRequest request) {
        RemunerationType type = request.remunerationType();
        boolean needsSalary = type == RemunerationType.SALARIO_FIXO
                || type == RemunerationType.FIXO_E_COMISSIONADO;

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setRemunerationType(type);
        employee.setRemunerationValue(needsSalary ? request.remunerationValue() : null);

        employeeRepository.save(employee);
    }
}
