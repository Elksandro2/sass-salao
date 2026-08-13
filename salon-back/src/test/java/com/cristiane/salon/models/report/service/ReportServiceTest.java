package com.cristiane.salon.models.report.service;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.cashflow.repository.CashFlowRepository;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.entity.CommissionScope;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.report.dto.AppointmentReportResponse;
import com.cristiane.salon.models.report.dto.FinancialReportResponse;
import com.cristiane.salon.models.report.dto.EmployeeFinanceResponse;
import com.cristiane.salon.models.report.dto.PayrollReportResponse;
import com.cristiane.salon.models.service.entity.SalonService;
import com.cristiane.salon.models.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.cristiane.salon.config.SalonClock;
import java.time.ZoneId;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private CashFlowRepository cashFlowRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    // SalonClock real, não mock: os testes dependem do "hoje"/"agora" de verdade no fuso
    // do salão, e um mock devolveria null silenciosamente.
    @Spy
    private SalonClock salonClock = new SalonClock(ZoneId.of("America/Recife"));

    @InjectMocks
    private ReportService reportService;

    @BeforeEach
    void setUp() {
    }

    private void withService(Appointment appointment, SalonService svc) {
        com.cristiane.salon.models.appointment.entity.AppointmentServiceItem item =
                new com.cristiane.salon.models.appointment.entity.AppointmentServiceItem();
        item.setAppointment(appointment);
        item.setSalonService(svc);
        appointment.getServices().add(item);
    }

    private void withProduct(Appointment appointment, BigDecimal price, int quantity) {
        com.cristiane.salon.models.product.entity.Product product = new com.cristiane.salon.models.product.entity.Product();
        product.setPrice(price);
        com.cristiane.salon.models.appointment.entity.AppointmentProductItem item =
                new com.cristiane.salon.models.appointment.entity.AppointmentProductItem();
        item.setAppointment(appointment);
        item.setProduct(product);
        item.setQuantity(quantity);
        appointment.getProducts().add(item);
    }

    @Test
    void shouldGenerateFinancialReportCorrectly() {
        // Given
        CashFlow income1 = new CashFlow();
        income1.setType(CashFlowType.INCOME);
        income1.setAmount(new BigDecimal("100.00"));

        CashFlow income2 = new CashFlow();
        income2.setType(CashFlowType.INCOME);
        income2.setAmount(new BigDecimal("50.00"));

        CashFlow expense1 = new CashFlow();
        expense1.setType(CashFlowType.EXPENSE);
        expense1.setAmount(new BigDecimal("30.00"));

        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(income1, income2, expense1));
        when(employeeRepository.findAll()).thenReturn(List.of());

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(salonClock.today(), salonClock.today());

        // Then
        assertEquals(new BigDecimal("150.00"), report.totalIncome());
        assertEquals(new BigDecimal("30.00"), report.totalExpense());
        assertEquals(new BigDecimal("120.00"), report.netProfit());
        assertEquals(BigDecimal.ZERO, report.totalSalaryPaid());
        assertEquals(BigDecimal.ZERO, report.totalCommissionPaid());
    }

    @Test
    void shouldGenerateFinancialReportWithRemunerationsCorrectly() {
        // Given
        CashFlow income = new CashFlow();
        income.setType(CashFlowType.INCOME);
        income.setAmount(new BigDecimal("1000.00"));

        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(income));

        // Create Employees
        User user1 = new User();
        user1.setName("Alice");
        Employee emp1 = new Employee();
        emp1.setId(1L);
        emp1.setUser(user1);
        emp1.setRemunerationType(RemunerationType.SALARIO_FIXO);
        emp1.setRemunerationValue(new BigDecimal("400.00"));

        User user2 = new User();
        user2.setName("Bob");
        Employee emp2 = new Employee();
        emp2.setId(2L);
        emp2.setUser(user2);
        emp2.setRemunerationType(RemunerationType.COMISSIONADO);
        emp2.setCommissionScope(CommissionScope.INDIVIDUAL);
        emp2.setRemunerationValue(new BigDecimal("10.00")); // 10%

        User user3 = new User();
        user3.setName("Carol");
        Employee emp3 = new Employee();
        emp3.setId(3L);
        emp3.setUser(user3);
        emp3.setRemunerationType(RemunerationType.COMISSIONADO);
        emp3.setCommissionScope(CommissionScope.GLOBAL);
        emp3.setRemunerationValue(new BigDecimal("5.00")); // 5%

        when(employeeRepository.findAll()).thenReturn(List.of(emp1, emp2, emp3));

        // Create Appointments for period
        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("200.00"));

        Appointment aptBob = new Appointment();
        aptBob.setStatus(AppointmentStatus.DONE);
        aptBob.setEmployee(emp2);
        withService(aptBob, service);
        aptBob.setScheduledAt(salonClock.now());

        Appointment aptAlice = new Appointment();
        aptAlice.setStatus(AppointmentStatus.DONE);
        aptAlice.setEmployee(emp1);
        withService(aptAlice, service);
        aptAlice.setScheduledAt(salonClock.now());

        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(aptBob, aptAlice));

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(salonClock.today(), salonClock.today());

        // Then
        // total income = 1000
        // emp1: fixed = 400
        // emp2: commission (individual, 10% of 200) = 20.00
        // emp3: commission (global, 5% of all DONE appointments = 5% of 400) = 20.00
        // totalSalaryPaid = 400.00
        // totalCommissionPaid = 20.00 (Bob) + 20.00 (Carol) = 40.00
        // netProfit = 1000 - 0 (expense) - 400 (salary) - 40 (commission) = 560.00
        assertEquals(new BigDecimal("1000.00"), report.totalIncome());
        assertEquals(new BigDecimal("400.00"), report.totalSalaryPaid());
        assertEquals(new BigDecimal("40.00"), report.totalCommissionPaid());
        assertEquals(new BigDecimal("560.00"), report.netProfit());
    }

    @Test
    void shouldGenerateFinancialReportWithHybridRemunerationCorrectly() {
        // Given
        CashFlow income = new CashFlow();
        income.setType(CashFlowType.INCOME);
        income.setAmount(new BigDecimal("1000.00"));

        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(income));

        // Create Hybrid Employee
        User user = new User();
        user.setName("Dave");
        Employee emp = new Employee();
        emp.setId(4L);
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.FIXO_E_COMISSIONADO);
        emp.setRemunerationValue(new BigDecimal("400.00")); // Base salary
        emp.setCommissionValue(new BigDecimal("10.00")); // 10%
        emp.setCommissionScope(CommissionScope.INDIVIDUAL);

        when(employeeRepository.findAll()).thenReturn(List.of(emp));

        // Create Appointment
        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("200.00"));

        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(emp);
        withService(apt, service);
        apt.setScheduledAt(salonClock.now());

        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt));

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(salonClock.today(), salonClock.today());

        // Then
        assertEquals(new BigDecimal("1000.00"), report.totalIncome());
        assertEquals(new BigDecimal("400.00"), report.totalSalaryPaid());
        assertEquals(new BigDecimal("20.00"), report.totalCommissionPaid());
        assertEquals(new BigDecimal("580.00"), report.netProfit());
        
        EmployeeFinanceResponse detail = report.employeeFinanceDetails().get(0);
        assertEquals(new BigDecimal("400.00"), detail.remunerationValue());
        assertEquals(new BigDecimal("10.00"), detail.commissionValue());
        assertEquals(new BigDecimal("420.00"), detail.calculatedPayout());
    }

    @Test
    void shouldGenerateAppointmentReportCorrectly() {
        // Given
        User mockUser = new User();
        mockUser.setName("Employee 1");

        Employee employee = new Employee();
        employee.setUser(mockUser);

        SalonService salonService = new SalonService();
        salonService.setName("Haircut");

        Appointment apt1 = new Appointment();
        apt1.setStatus(AppointmentStatus.DONE);
        apt1.setScheduledAt(salonClock.now().withHour(10));
        apt1.setEmployee(employee);
        withService(apt1, salonService);

        Appointment apt2 = new Appointment();
        apt2.setStatus(AppointmentStatus.PENDING);
        apt2.setScheduledAt(salonClock.now().withHour(14));
        apt2.setEmployee(employee);
        withService(apt2, salonService);

        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt1, apt2));

        // When
        AppointmentReportResponse report = reportService.generateAppointmentReport(salonClock.today(), salonClock.today());

        // Then
        assertEquals(2, report.totalAppointments());
        assertEquals(1, report.done());
        assertEquals(1, report.pending());
        assertEquals(2, report.byEmployee().get("Employee 1"));
        assertEquals(2, report.byService().get("Haircut"));
    }

    @Test
    void shouldGeneratePayrollReportCorrectly() {
        // Create Employees
        User user1 = new User();
        user1.setName("Alice");
        Employee emp1 = new Employee();
        emp1.setId(1L);
        emp1.setUser(user1);
        emp1.setRemunerationType(RemunerationType.SALARIO_FIXO);
        emp1.setRemunerationValue(new BigDecimal("400.00"));

        User user2 = new User();
        user2.setName("Bob");
        Employee emp2 = new Employee();
        emp2.setId(2L);
        emp2.setUser(user2);
        emp2.setRemunerationType(RemunerationType.COMISSIONADO);
        emp2.setCommissionScope(CommissionScope.INDIVIDUAL);
        emp2.setRemunerationValue(new BigDecimal("10.00")); // 10%

        User user3 = new User();
        user3.setName("Carol");
        Employee emp3 = new Employee();
        emp3.setId(3L);
        emp3.setUser(user3);
        emp3.setRemunerationType(RemunerationType.COMISSIONADO);
        emp3.setCommissionScope(CommissionScope.GLOBAL);
        emp3.setRemunerationValue(new BigDecimal("5.00")); // 5%

        User user4 = new User();
        user4.setName("Dave");
        Employee emp4 = new Employee();
        emp4.setId(4L);
        emp4.setUser(user4);
        emp4.setRemunerationType(RemunerationType.FIXO_E_COMISSIONADO);
        emp4.setRemunerationValue(new BigDecimal("300.00")); // Base
        emp4.setCommissionValue(new BigDecimal("10.00")); // 10%
        emp4.setCommissionScope(CommissionScope.INDIVIDUAL);

        when(employeeRepository.findAll()).thenReturn(List.of(emp1, emp2, emp3, emp4));

        // Create Appointments for period
        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("200.00"));

        Appointment aptBob = new Appointment();
        aptBob.setStatus(AppointmentStatus.DONE);
        aptBob.setEmployee(emp2);
        withService(aptBob, service);
        aptBob.setScheduledAt(salonClock.now());

        Appointment aptAlice = new Appointment();
        aptAlice.setStatus(AppointmentStatus.DONE);
        aptAlice.setEmployee(emp1);
        withService(aptAlice, service);
        aptAlice.setScheduledAt(salonClock.now());

        Appointment aptDave = new Appointment();
        aptDave.setStatus(AppointmentStatus.DONE);
        aptDave.setEmployee(emp4);
        withService(aptDave, service);
        aptDave.setScheduledAt(salonClock.now());

        // Total done appointments value = 200 (Bob) + 200 (Alice) + 200 (Dave) = 600
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(aptBob, aptAlice, aptDave));

        // When
        PayrollReportResponse response = reportService.generatePayrollReport(salonClock.today(), salonClock.today());

        // Then
        assertEquals(4, response.items().size());
        
        // Alice: FIXED -> base = 0, payout = 400
        PayrollReportResponse.PayrollItem itemAlice = response.items().stream().filter(i -> i.employeeId().equals(1L)).findFirst().orElseThrow();
        assertEquals(BigDecimal.ZERO, itemAlice.baseAmount());
        assertEquals(new BigDecimal("400.00"), itemAlice.calculatedPay());

        // Bob: COMMISSIONED INDIVIDUAL (10% of 200) -> base = 200, payout = 20
        PayrollReportResponse.PayrollItem itemBob = response.items().stream().filter(i -> i.employeeId().equals(2L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("200.00"), itemBob.baseAmount());
        assertEquals(new BigDecimal("20.00"), itemBob.calculatedPay());

        // Carol: COMMISSIONED GLOBAL (5% of 600) -> base = 600, payout = 30
        PayrollReportResponse.PayrollItem itemCarol = response.items().stream().filter(i -> i.employeeId().equals(3L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("600.00"), itemCarol.baseAmount());
        assertEquals(new BigDecimal("30.00"), itemCarol.calculatedPay());

        // Dave: HYBRID INDIVIDUAL (300 salary + 10% of 200) -> base = 200, payout = 320
        PayrollReportResponse.PayrollItem itemDave = response.items().stream().filter(i -> i.employeeId().equals(4L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("200.00"), itemDave.baseAmount());
        assertEquals(new BigDecimal("320.00"), itemDave.calculatedPay());
    }

    @Test
    void shouldHandleNullDateParamsAndDefaultCorrectly() {
        // Given
        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(employeeRepository.findAll()).thenReturn(List.of());

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(null, null);

        // Then
        LocalDate expectedFrom = salonClock.today().withDayOfMonth(1);
        LocalDate expectedTo = salonClock.today().plusDays(30);
        String expectedPeriod = expectedFrom + " a " + expectedTo;
        assertEquals(expectedPeriod, report.period());
    }

    @Test
    void shouldHandleFallbackDatesInReportPeriodCorrectly() {
        // Given
        User clientUser = new User();
        clientUser.setName("Cliente");
        Employee emp = new Employee();
        emp.setId(1L);
        emp.setUser(clientUser);

        SalonService service = new SalonService();
        service.setName("Corte");
        service.setPrice(BigDecimal.TEN);

        LocalDate from = salonClock.today();
        LocalDate to = salonClock.today().plusDays(5);

        // Appointment 1: using preferredDate
        Appointment apt1 = new Appointment();
        apt1.setStatus(AppointmentStatus.DONE);
        apt1.setEmployee(emp);
        withService(apt1, service);
        apt1.setScheduledAt(null);
        apt1.setPreferredDate(salonClock.today().plusDays(2));

        // Appointment 2: using createdAt
        Appointment apt2 = new Appointment();
        apt2.setStatus(AppointmentStatus.DONE);
        apt2.setEmployee(emp);
        withService(apt2, service);
        apt2.setScheduledAt(null);
        apt2.setPreferredDate(null);
        apt2.setCreatedAt(Instant.now().plus(3, ChronoUnit.DAYS));

        // Appointment 3: sem nenhuma data — não seria retornado pela query real
        // (findAllInPeriod), então nem é incluído no mock do repositório abaixo.

        when(employeeRepository.findAll()).thenReturn(List.of(emp));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt1, apt2));
        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(from, to);

        // Then
        // Should have matched apt1 and apt2, so 2 done appointments in total
        assertEquals(2, report.employeeFinanceDetails().get(0).doneAppointmentsCount());
        assertEquals(BigDecimal.ZERO, report.employeeFinanceDetails().get(0).calculatedPayout());
    }

    @Test
    void shouldCalculateGlobalHybridCommissionCorrectly() {
        // Given
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.FIXO_E_COMISSIONADO);
        emp.setCommissionScope(CommissionScope.GLOBAL);
        emp.setRemunerationValue(new BigDecimal("500.00")); // Base
        emp.setCommissionValue(new BigDecimal("10.00")); // 10%

        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("150.00"));

        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(emp);
        withService(apt, service);
        apt.setScheduledAt(salonClock.now());

        when(employeeRepository.findAll()).thenReturn(List.of(emp));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt));
        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(salonClock.today(), salonClock.today().plusDays(1));

        // Then
        // Global done value = 150.00
        // Salary = 500.00
        // Commission = 10% of 150.00 = 15.00
        // Total payout = 515.00
        assertEquals(new BigDecimal("500.00"), report.totalSalaryPaid());
        assertEquals(new BigDecimal("15.00"), report.totalCommissionPaid());
        EmployeeFinanceResponse detail = report.employeeFinanceDetails().get(0);
        assertEquals(new BigDecimal("515.00"), detail.calculatedPayout());
    }

    @Test
    void shouldHandleNullRemunerationValuesGracefully() {
        // Given
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.FIXO_E_COMISSIONADO);
        emp.setCommissionScope(CommissionScope.INDIVIDUAL);
        emp.setRemunerationValue(null);
        emp.setCommissionValue(null);

        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("100.00"));

        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(emp);
        withService(apt, service);
        apt.setScheduledAt(salonClock.now());

        when(employeeRepository.findAll()).thenReturn(List.of(emp));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt));
        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(salonClock.today(), salonClock.today().plusDays(1));

        // Then
        assertEquals(0, report.totalSalaryPaid().compareTo(BigDecimal.ZERO));
        assertEquals(0, report.totalCommissionPaid().compareTo(BigDecimal.ZERO));
    }

    @Test
    void getEmployeeFinancialHistory_whenEmployeeExists_returnsMappedPage() {
        Employee employee = new Employee();
        employee.setId(7L);

        SalonService service = new SalonService();
        service.setName("Corte");
        service.setPrice(new BigDecimal("85.00"));

        Appointment appointment = new Appointment();
        appointment.setId(1L);
        appointment.setEmployee(employee);
        withService(appointment, service);
        appointment.setStatus(AppointmentStatus.DONE);
        appointment.setScheduledAt(salonClock.now());

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        org.springframework.data.domain.Page<Appointment> page =
                new org.springframework.data.domain.PageImpl<>(List.of(appointment));

        when(employeeRepository.existsById(7L)).thenReturn(true);
        when(appointmentRepository.findByEmployeeIdForFinancialHistory(eq(7L), any(), any(), eq(pageable)))
                .thenReturn(page);

        org.springframework.data.domain.Page<com.cristiane.salon.models.report.dto.AppointmentFinancialResponse> result =
                reportService.getEmployeeFinancialHistory(7L, null, null, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals("Corte", result.getContent().get(0).serviceName());
        assertEquals(0, result.getContent().get(0).price().compareTo(new BigDecimal("85.00")));
    }

    @Test
    void shouldCalculateProductCommissionIndependentlyFromServiceCommission() {
        // Given: funcionária comissionada em serviços (10%) e com uma comissão separada sobre
        // produtos (20%), escopo individual para ambas.
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.COMISSIONADO);
        emp.setCommissionScope(CommissionScope.INDIVIDUAL);
        emp.setRemunerationValue(new BigDecimal("10.00")); // 10% sobre serviços
        emp.setProductCommissionValue(new BigDecimal("20.00")); // 20% sobre produtos

        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("100.00"));

        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(emp);
        withService(apt, service);
        withProduct(apt, new BigDecimal("50.00"), 2); // R$100 em produtos
        apt.setScheduledAt(salonClock.now());

        when(employeeRepository.findAll()).thenReturn(List.of(emp));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt));
        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(salonClock.today(), salonClock.today());

        // Then: comissão de serviço (10% de 100 = 10) + comissão de produto (20% de 100 = 20) = 30
        assertEquals(new BigDecimal("30.00"), report.totalCommissionPaid());
        EmployeeFinanceResponse detail = report.employeeFinanceDetails().get(0);
        assertEquals(new BigDecimal("30.00"), detail.calculatedPayout());
        assertEquals(new BigDecimal("100.00"), detail.doneProductsValue());
    }

    @Test
    void shouldNotApplyProductCommissionWhenNotConfigured() {
        // Given: funcionária comissionada só em serviços, sem productCommissionValue definido.
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.COMISSIONADO);
        emp.setCommissionScope(CommissionScope.INDIVIDUAL);
        emp.setRemunerationValue(new BigDecimal("10.00"));

        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("100.00"));

        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(emp);
        withService(apt, service);
        withProduct(apt, new BigDecimal("50.00"), 2);
        apt.setScheduledAt(salonClock.now());

        when(employeeRepository.findAll()).thenReturn(List.of(emp));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt));
        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(salonClock.today(), salonClock.today());

        // Then: só a comissão de serviço (10% de 100 = 10), produto ignorado
        assertEquals(new BigDecimal("10.00"), report.totalCommissionPaid());
    }

    @Test
    void shouldIncludeProductCommissionInPayrollReport() {
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.COMISSIONADO);
        emp.setCommissionScope(CommissionScope.INDIVIDUAL);
        emp.setRemunerationValue(new BigDecimal("10.00"));
        emp.setProductCommissionValue(new BigDecimal("20.00"));

        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("100.00"));

        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(emp);
        withService(apt, service);
        withProduct(apt, new BigDecimal("50.00"), 2);
        apt.setScheduledAt(salonClock.now());

        when(employeeRepository.findAll()).thenReturn(List.of(emp));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt));

        PayrollReportResponse response = reportService.generatePayrollReport(salonClock.today(), salonClock.today());

        PayrollReportResponse.PayrollItem item = response.items().get(0);
        assertEquals(new BigDecimal("30.00"), item.calculatedPay());
    }

    @Test
    void getEmployeeFinancialHistory_whenEmployeeDoesNotExist_throwsResourceNotFound() {
        when(employeeRepository.existsById(99L)).thenReturn(false);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.cristiane.salon.exception.ResourceNotFoundException.class,
                () -> reportService.getEmployeeFinancialHistory(99L, null, null, pageable)
        );
    }
}

