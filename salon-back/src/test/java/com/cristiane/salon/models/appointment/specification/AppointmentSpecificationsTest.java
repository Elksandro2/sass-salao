package com.cristiane.salon.models.appointment.specification;

import com.cristiane.salon.models.appointment.dto.AppointmentFilter;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a Specification com um banco Postgres real (Testcontainers), já que os predicados de
 * fallback de data (scheduledAt &gt; preferredDate &gt; createdAt) usam Criteria API e não fazem
 * sentido mockados — o que importa aqui é a query gerada, não a lógica de negócio isolada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AppointmentSpecificationsTest {

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
    private User alice;
    private User bob;

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

        alice = new User();
        alice.setName("Alice Souza");
        alice.setEmail("alice@teste.com");
        alice.setPassword("senha123");
        alice.setRole(clienteRole);
        alice.setActive(true);
        alice = userRepository.save(alice);

        bob = new User();
        bob.setName("Bob Pereira");
        bob.setEmail("bob@teste.com");
        bob.setPassword("senha123");
        bob.setRole(clienteRole);
        bob.setActive(true);
        bob = userRepository.save(bob);

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
        employee.setCommissionScope(CommissionScope.INDIVIDUAL);
        employee.setRemunerationValue(BigDecimal.ZERO);
        employee.setCommissionValue(BigDecimal.TEN);
        employee = employeeRepository.save(employee);

        salonService = new SalonService();
        salonService.setName("Corte de Cabelo");
        salonService.setDescription("Corte feminino");
        salonService.setPrice(BigDecimal.valueOf(80.00));
        salonService.setActive(true);
        salonService = salonServiceRepository.save(salonService);
    }

    private Appointment newAppointment(User client, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setClient(client);
        a.setEmployee(employee);
        a.setStatus(status);
        AppointmentServiceItem item = new AppointmentServiceItem();
        item.setAppointment(a);
        item.setSalonService(salonService);
        a.getServices().add(item);
        return a;
    }

    @Test
    void filter_byClientNamePartialCaseInsensitive_returnsMatchingAppointments() {
        Appointment aliceApt = newAppointment(alice, AppointmentStatus.DONE);
        aliceApt.setScheduledAt(LocalDateTime.now());
        appointmentRepository.save(aliceApt);

        Appointment bobApt = newAppointment(bob, AppointmentStatus.DONE);
        bobApt.setScheduledAt(LocalDateTime.now());
        appointmentRepository.save(bobApt);

        AppointmentFilter filter = new AppointmentFilter(null, null, null, null, "ALICE", null, null);
        List<Appointment> result = appointmentRepository.findAll(
                AppointmentSpecifications.filter(filter), PageRequest.of(0, 10)
        ).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClient().getId()).isEqualTo(alice.getId());
    }

    @Test
    void filter_byPeriod_matchesPendingAppointmentUsingPreferredDateFallback() {
        // PENDING/REQUESTED normalmente não tem scheduledAt ainda — só preferredDate.
        Appointment pending = newAppointment(alice, AppointmentStatus.PENDING);
        pending.setPreferredDate(LocalDate.of(2026, 8, 15));
        appointmentRepository.save(pending);

        Appointment outOfRange = newAppointment(bob, AppointmentStatus.PENDING);
        outOfRange.setPreferredDate(LocalDate.of(2026, 1, 1));
        appointmentRepository.save(outOfRange);

        AppointmentFilter filter = new AppointmentFilter(
                AppointmentStatus.PENDING, null, employee.getId(), null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        List<Appointment> result = appointmentRepository.findAll(
                AppointmentSpecifications.filter(filter), PageRequest.of(0, 10)
        ).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClient().getId()).isEqualTo(alice.getId());
    }

    @Test
    void filter_byPeriod_prefersScheduledAtOverPreferredDateWhenBothPresent() {
        Appointment apt = newAppointment(alice, AppointmentStatus.CONFIRMED);
        apt.setScheduledAt(LocalDateTime.of(2026, 8, 10, 14, 0));
        apt.setPreferredDate(LocalDate.of(2026, 1, 1)); // fora do período, mas não deve ser usado
        appointmentRepository.save(apt);

        AppointmentFilter filter = new AppointmentFilter(
                null, null, null, null, null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        List<Appointment> result = appointmentRepository.findAll(
                AppointmentSpecifications.filter(filter), PageRequest.of(0, 10)
        ).getContent();

        assertThat(result).hasSize(1);
    }

    @Test
    void filter_combiningMultipleFilters_appliesAllOfThem() {
        Appointment matching = newAppointment(alice, AppointmentStatus.PENDING);
        matching.setPreferredDate(LocalDate.of(2026, 8, 15));
        appointmentRepository.save(matching);

        Appointment wrongStatus = newAppointment(alice, AppointmentStatus.CONFIRMED);
        wrongStatus.setPreferredDate(LocalDate.of(2026, 8, 15));
        appointmentRepository.save(wrongStatus);

        AppointmentFilter filter = new AppointmentFilter(
                AppointmentStatus.PENDING, null, employee.getId(), null, "alice",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        List<Appointment> result = appointmentRepository.findAll(
                AppointmentSpecifications.filter(filter), PageRequest.of(0, 10)
        ).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(matching.getId());
    }

    @Test
    void filter_withNoFilters_returnsAllAppointments() {
        appointmentRepository.save(newAppointment(alice, AppointmentStatus.PENDING));
        appointmentRepository.save(newAppointment(bob, AppointmentStatus.DONE));

        List<Appointment> result = appointmentRepository.findAll(
                AppointmentSpecifications.filter(null), PageRequest.of(0, 10)
        ).getContent();

        assertThat(result).hasSize(2);
    }
}
