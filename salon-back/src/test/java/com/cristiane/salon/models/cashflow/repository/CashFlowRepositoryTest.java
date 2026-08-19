package com.cristiane.salon.models.cashflow.repository;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.employee.entity.CommissionScope;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.service.entity.SalonService;
import com.cristiane.salon.models.service.repository.SalonServiceRepository;
import com.cristiane.salon.models.user.entity.Role;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.RoleRepository;
import com.cristiane.salon.models.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova em banco real (não H2) que a constraint UNIQUE(appointment_id) da migration V46
 * realmente impede faturamento duplicado do mesmo agendamento no fluxo de caixa — a checagem em
 * memória (existsByAppointmentId) sozinha não fecha a janela de corrida entre duas transações
 * concorrentes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CashFlowRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CashFlowRepository cashFlowRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalonServiceRepository salonServiceRepository;

    private Appointment appointment;

    @BeforeEach
    void setUp() {
        cashFlowRepository.deleteAll();
        appointmentRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        salonServiceRepository.deleteAll();

        Role clienteRole = roleRepository.findAll().stream()
                .filter(r -> "CLIENTE".equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(new Role(null, "CLIENTE", null)));
        Role adminRole = roleRepository.findAll().stream()
                .filter(r -> "ADMIN".equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN", null)));

        User client = new User();
        client.setName("Cliente de Teste");
        client.setEmail("cliente-cashflow@teste.com");
        client.setPassword("senha123");
        client.setRole(clienteRole);
        client.setActive(true);
        client = userRepository.save(client);

        User employeeUser = new User();
        employeeUser.setName("Funcionária de Teste");
        employeeUser.setEmail("funcionaria-cashflow@teste.com");
        employeeUser.setPassword("senha123");
        employeeUser.setRole(adminRole);
        employeeUser.setActive(true);
        employeeUser = userRepository.save(employeeUser);

        Employee employee = new Employee();
        employee.setUser(employeeUser);
        employee.setRemunerationType(RemunerationType.COMISSIONADO);
        employee.setCommissionScope(CommissionScope.INDIVIDUAL);
        employee.setRemunerationValue(BigDecimal.ZERO);
        employee.setCommissionValue(BigDecimal.TEN);
        employee = employeeRepository.save(employee);

        SalonService salonService = new SalonService();
        salonService.setName("Corte de Cabelo");
        salonService.setDescription("Corte feminino");
        salonService.setPrice(BigDecimal.valueOf(80.00));
        salonService.setActive(true);
        salonService = salonServiceRepository.save(salonService);

        appointment = new Appointment();
        appointment.setClient(client);
        appointment.setEmployee(employee);
        appointment.setStatus(AppointmentStatus.DONE);
        AppointmentServiceItem item = new AppointmentServiceItem();
        item.setAppointment(appointment);
        item.setSalonService(salonService);
        appointment.getServices().add(item);
        appointment = appointmentRepository.save(appointment);
    }

    private CashFlow newIncomeFor(Appointment apt) {
        CashFlow cf = new CashFlow();
        cf.setType(CashFlowType.INCOME);
        cf.setAmount(BigDecimal.valueOf(80.00));
        cf.setDescription("Pagamento do agendamento #" + apt.getId());
        cf.setDate(LocalDate.now());
        cf.setAppointment(apt);
        return cf;
    }

    @Test
    void save_secondEntryForSameAppointment_violatesUniqueConstraint() {
        cashFlowRepository.saveAndFlush(newIncomeFor(appointment));

        assertThatThrownBy(() -> cashFlowRepository.saveAndFlush(newIncomeFor(appointment)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByAppointmentId_reflectsWhetherAppointmentWasBilled() {
        assertThat(cashFlowRepository.existsByAppointmentId(appointment.getId())).isFalse();

        cashFlowRepository.saveAndFlush(newIncomeFor(appointment));

        assertThat(cashFlowRepository.existsByAppointmentId(appointment.getId())).isTrue();
    }

    @Test
    void save_entriesWithoutAppointment_areNotConstrainedByEachOther() {
        CashFlow cf1 = new CashFlow();
        cf1.setType(CashFlowType.EXPENSE);
        cf1.setAmount(BigDecimal.TEN);
        cf1.setDescription("Compra de produto de limpeza");
        cf1.setDate(LocalDate.now());

        CashFlow cf2 = new CashFlow();
        cf2.setType(CashFlowType.EXPENSE);
        cf2.setAmount(BigDecimal.ONE);
        cf2.setDescription("Outra despesa qualquer");
        cf2.setDate(LocalDate.now());

        cashFlowRepository.saveAndFlush(cf1);
        cashFlowRepository.saveAndFlush(cf2);

        assertThat(cashFlowRepository.findAll()).hasSize(2);
    }
}
