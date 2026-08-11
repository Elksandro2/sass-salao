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
 * GERENTE_DE_ATENDIMENTO não presta serviço ao cliente, então não entra no seletor de
 * agendamento (Employee.bookable = false) e não tem comissão — mas continua recebendo
 * salário, então ganha um Employee com remunerationType fixo em SALARIO_FIXO só para
 * alimentar o cálculo de folha no relatório financeiro.
 */
@Component
@RequiredArgsConstructor
public class GerenteDeAtendimentoStrategy implements StaffRoleStrategy {

    private final EmployeeRepository employeeRepository;

    @Override
    public String getRoleName() {
        return "GERENTE_DE_ATENDIMENTO";
    }

    @Override
    public void validate(StaffProfileRequest request) {
        if (request.remunerationType() != null && request.remunerationType() != RemunerationType.SALARIO_FIXO) {
            throw new BadRequestException(
                    "Gerente de atendimento só pode ter remuneração do tipo Salário Fixo — não presta serviço, então não há comissão");
        }
        if (request.commissionScope() != null || request.commissionValue() != null) {
            throw new BadRequestException(
                    "Dados de comissão não se aplicam ao papel de gerente de atendimento");
        }
        if (request.remunerationType() == RemunerationType.SALARIO_FIXO && request.remunerationValue() == null) {
            throw new BadRequestException("O valor do salário fixo é obrigatório");
        }
    }

    @Override
    public void onStaffCreated(User user, StaffProfileRequest request) {
        if (request.remunerationType() != RemunerationType.SALARIO_FIXO) {
            return;
        }

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setBookable(false);
        employee.setRemunerationType(RemunerationType.SALARIO_FIXO);
        employee.setRemunerationValue(request.remunerationValue());

        employeeRepository.save(employee);
    }
}
