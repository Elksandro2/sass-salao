package com.cristiane.salon.models.employee.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.employee.dto.EmployeeBookingResponse;
import com.cristiane.salon.models.employee.dto.EmployeeFilter;
import com.cristiane.salon.models.employee.dto.EmployeeRequest;
import com.cristiane.salon.models.employee.dto.EmployeeResponse;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.employee.specification.EmployeeSpecifications;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> findAll(EmployeeFilter filter, Pageable pageable) {
        return employeeRepository.findAll(EmployeeSpecifications.filter(filter), pageable)
                .map(EmployeeResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<EmployeeBookingResponse> findAllForBooking() {
        return employeeRepository.findAllActiveForBooking().stream()
                .map(EmployeeBookingResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionária não encontrada"));
        return EmployeeResponse.fromEntity(employee);
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        if (employeeRepository.findByUserId(request.userId()).isPresent()) {
            throw new BadRequestException("Este usuário já é uma funcionária");
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (!"FUNCIONARIA".equals(user.getRoleName()) && !"ADMIN".equals(user.getRoleName())
                && !"GERENTE_DE_ATENDIMENTO".equals(user.getRoleName())) {
            throw new BadRequestException("O usuário não tem o papel adequado para ser funcionária");
        }

        Employee employee = new Employee();
        employee.setUser(user);
        // Gerente não atende cliente, então nunca entra no seletor de agendamento — só
        // FUNCIONARIA/ADMIN ficam "bookable" (ver findAllActiveForBooking).
        employee.setBookable(!"GERENTE_DE_ATENDIMENTO".equals(user.getRoleName()));
        validateAndMapRemuneration(employee, request, user.getRoleName());

        return EmployeeResponse.fromEntity(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionária não encontrada"));

        if (request.userId() != null && !request.userId().equals(employee.getUser().getId())) {
            if (employeeRepository.findByUserId(request.userId()).isPresent()) {
                throw new BadRequestException("Este usuário já está vinculado a outra funcionária");
            }
            User user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
            employee.setUser(user);
        }

        validateAndMapRemuneration(employee, request, employee.getUser().getRoleName());

        return EmployeeResponse.fromEntity(employeeRepository.save(employee));
    }

    /**
     * COMISSIONADO não tem mais % própria — a comissão dela vem do {@code SalonService} de cada
     * serviço que realizar, então {@code remunerationValue} fica null (e é ignorado se vier
     * preenchido). SALARIO_FIXO e FIXO_E_COMISSIONADO usam {@code remunerationValue} como
     * salário base, obrigatório.
     */
    private void validateAndMapRemuneration(Employee employee, EmployeeRequest request, String roleName) {
        if (request.remunerationType() != null) {
            if ("GERENTE_DE_ATENDIMENTO".equals(roleName) && request.remunerationType() != RemunerationType.SALARIO_FIXO) {
                throw new BadRequestException(
                        "Gerente de atendimento só pode ter remuneração do tipo Salário Fixo — não presta serviço, então não há comissão");
            }

            employee.setRemunerationType(request.remunerationType());

            if (request.remunerationType() == RemunerationType.COMISSIONADO) {
                employee.setRemunerationValue(null);
            } else {
                if (request.remunerationValue() != null) {
                    if (request.remunerationValue().compareTo(java.math.BigDecimal.ZERO) < 0) {
                        throw new BadRequestException("O valor de remuneração não pode ser negativo");
                    }
                    employee.setRemunerationValue(request.remunerationValue());
                } else {
                    employee.setRemunerationValue(java.math.BigDecimal.ZERO);
                }
            }
        } else {
            employee.setRemunerationType(null);
            employee.setRemunerationValue(null);
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Funcionária não encontrada");
        }
        employeeRepository.deleteById(id);
    }
}
