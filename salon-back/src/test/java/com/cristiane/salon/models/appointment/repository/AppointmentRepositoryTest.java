package com.cristiane.salon.models.appointment.repository;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Usa um Postgres real (Testcontainers) porque o bug regredido aqui é específico do driver JDBC
 * do Postgres (inferência de tipo de parâmetro), e não reproduz contra o H2 usado nos testes
 * unitários padrão.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AppointmentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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

    private Employee employee;
    private SalonService salonService;
    private User client;

    @BeforeEach
    void setUp() {
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

        client = new User();
        client.setName("Cliente de Teste");
        client.setEmail("cliente@teste.com");
        client.setPassword("senha123");
        client.setRole(clienteRole);
        client.setActive(true);
        client = userRepository.save(client);

        User employeeUser = new User();
        employeeUser.setName("Funcionária de Teste");
        employeeUser.setEmail("funcionaria@teste.com");
        employeeUser.setPassword("senha123");
        employeeUser.setRole(adminRole);
        employeeUser.setActive(true);
        employeeUser = userRepository.save(employeeUser);

        employee = new Employee();
        employee.setUser(employeeUser);
        employee.setRemunerationType(RemunerationType.COMISSIONADO);
        employee.setRemunerationValue(BigDecimal.ZERO);
        employee = employeeRepository.save(employee);

        salonService = new SalonService();
        salonService.setName("Corte de Cabelo");
        salonService.setDescription("Corte feminino");
        salonService.setPrice(BigDecimal.valueOf(80.00));
        salonService.setActive(true);
        salonService = salonServiceRepository.save(salonService);
    }

    private Appointment newAppointment(AppointmentStatus status, LocalDateTime scheduledAt) {
        Appointment a = new Appointment();
        a.setClient(client);
        a.setEmployee(employee);
        a.setStatus(status);
        a.setScheduledAt(scheduledAt);
        AppointmentServiceItem item = new AppointmentServiceItem();
        item.setAppointment(a);
        item.setSalonService(salonService);
        a.getServices().add(item);
        return a;
    }

    // Regressão: com from/to nulos, o Postgres não conseguia inferir o tipo do parâmetro no
    // "(:from IS NULL OR ...)" e a query quebrava com PSQLException "could not determine data
    // type of parameter $N" — reproduzido em produção ao abrir o Histórico Financeiro por
    // Profissional sem escolher um intervalo de datas.
    @Test
    void findByEmployeeIdForFinancialHistory_withNullFromAndTo_doesNotThrowAndReturnsAppointments() {
        appointmentRepository.save(newAppointment(AppointmentStatus.DONE, LocalDateTime.now()));

        assertThatCode(() -> appointmentRepository.findByEmployeeIdForFinancialHistory(
                employee.getId(), null, null, PageRequest.of(0, 10)
        )).doesNotThrowAnyException();

        Page<Appointment> result = appointmentRepository.findByEmployeeIdForFinancialHistory(
                employee.getId(), null, null, PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findByEmployeeIdForFinancialHistory_withFromOnly_filtersByLowerBound() {
        appointmentRepository.save(newAppointment(AppointmentStatus.DONE, LocalDateTime.of(2026, 1, 1, 10, 0)));
        appointmentRepository.save(newAppointment(AppointmentStatus.DONE, LocalDateTime.of(2026, 8, 1, 10, 0)));

        Page<Appointment> result = appointmentRepository.findByEmployeeIdForFinancialHistory(
                employee.getId(), LocalDateTime.of(2026, 6, 1, 0, 0), null, PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    @Test
    void findByEmployeeIdForFinancialHistory_withFromAndTo_filtersByRange() {
        appointmentRepository.save(newAppointment(AppointmentStatus.DONE, LocalDateTime.of(2026, 1, 1, 10, 0)));
        appointmentRepository.save(newAppointment(AppointmentStatus.DONE, LocalDateTime.of(2026, 6, 15, 10, 0)));
        appointmentRepository.save(newAppointment(AppointmentStatus.DONE, LocalDateTime.of(2026, 12, 1, 10, 0)));

        Page<Appointment> result = appointmentRepository.findByEmployeeIdForFinancialHistory(
                employee.getId(),
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 30, 23, 59),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 6, 15, 10, 0));
    }
}
