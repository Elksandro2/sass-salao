package com.cristiane.salon.models.report.service;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.businesssettings.service.SalonBusinessSettingsService;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.cashflow.repository.CashFlowRepository;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.report.dto.AppointmentProfitResponse;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Mock
    private com.cristiane.salon.models.service.repository.SalonServiceProductUsageRepository serviceProductUsageRepository;

    @Mock
    private com.cristiane.salon.models.fixedexpense.repository.FixedExpenseRepository fixedExpenseRepository;

    @Mock
    private SalonBusinessSettingsService businessSettingsService;

    @Mock
    private com.cristiane.salon.models.report.repository.DiaristaWorkedDaysOverrideRepository workedDaysOverrideRepository;

    // SalonClock real, não mock: os testes dependem do "hoje"/"agora" de verdade no fuso
    // do salão, e um mock devolveria null silenciosamente.
    @Spy
    private SalonClock salonClock = new SalonClock(ZoneId.of("America/Recife"));

    @InjectMocks
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(fixedExpenseRepository.sumAmountByDateBetween(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        org.mockito.Mockito.lenient()
                .when(workedDaysOverrideRepository.findByPeriodStartAndPeriodEnd(any(), any()))
                .thenReturn(java.util.List.of());
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

        when(employeeRepository.findAll()).thenReturn(List.of(emp1, emp2));

        // Create Appointments for period — Bob's service pays 10% commission
        SalonService serviceFlat = new SalonService();
        serviceFlat.setPrice(new BigDecimal("200.00"));

        SalonService serviceCommissioned = new SalonService();
        serviceCommissioned.setPrice(new BigDecimal("200.00"));
        serviceCommissioned.setCommissionPercent(new BigDecimal("10"));

        Appointment aptBob = new Appointment();
        aptBob.setStatus(AppointmentStatus.DONE);
        aptBob.setEmployee(emp2);
        withService(aptBob, serviceCommissioned);
        aptBob.setScheduledAt(salonClock.now());

        Appointment aptAlice = new Appointment();
        aptAlice.setStatus(AppointmentStatus.DONE);
        aptAlice.setEmployee(emp1);
        withService(aptAlice, serviceFlat);
        aptAlice.setScheduledAt(salonClock.now());

        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(aptBob, aptAlice));

        // When
        FinancialReportResponse report = reportService.generateFinancialReport(salonClock.today(), salonClock.today());

        // Then
        // total income = 1000
        // emp1: fixed = 400
        // emp2: commission (10% of 200) = 20.00
        // totalSalaryPaid = 400.00
        // totalCommissionPaid = 20.00
        // netProfit = 1000 - 0 (expense) - 400 (salary) - 20 (commission) = 580.00
        assertEquals(new BigDecimal("1000.00"), report.totalIncome());
        assertEquals(new BigDecimal("400.00"), report.totalSalaryPaid());
        assertEquals(new BigDecimal("20.00"), report.totalCommissionPaid());
        assertEquals(new BigDecimal("580.00"), report.netProfit());
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

        when(employeeRepository.findAll()).thenReturn(List.of(emp));

        // Create Appointment — the service pays 10% commission
        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("200.00"));
        service.setCommissionPercent(new BigDecimal("10"));

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

        User user4 = new User();
        user4.setName("Dave");
        Employee emp4 = new Employee();
        emp4.setId(4L);
        emp4.setUser(user4);
        emp4.setRemunerationType(RemunerationType.FIXO_E_COMISSIONADO);
        emp4.setRemunerationValue(new BigDecimal("300.00")); // Base

        when(employeeRepository.findAll()).thenReturn(List.of(emp1, emp2, emp4));

        // Create Appointments for period — cada uma tem um serviço próprio de R$200,
        // com % de comissão configurado no serviço (10%) para Bob e Dave.
        SalonService serviceFlat = new SalonService();
        serviceFlat.setPrice(new BigDecimal("200.00"));

        SalonService serviceCommissioned = new SalonService();
        serviceCommissioned.setPrice(new BigDecimal("200.00"));
        serviceCommissioned.setCommissionPercent(new BigDecimal("10"));

        Appointment aptBob = new Appointment();
        aptBob.setStatus(AppointmentStatus.DONE);
        aptBob.setEmployee(emp2);
        withService(aptBob, serviceCommissioned);
        aptBob.setScheduledAt(salonClock.now());

        Appointment aptAlice = new Appointment();
        aptAlice.setStatus(AppointmentStatus.DONE);
        aptAlice.setEmployee(emp1);
        withService(aptAlice, serviceFlat);
        aptAlice.setScheduledAt(salonClock.now());

        Appointment aptDave = new Appointment();
        aptDave.setStatus(AppointmentStatus.DONE);
        aptDave.setEmployee(emp4);
        withService(aptDave, serviceCommissioned);
        aptDave.setScheduledAt(salonClock.now());

        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(aptBob, aptAlice, aptDave));

        // When
        PayrollReportResponse response = reportService.generatePayrollReport(salonClock.today(), salonClock.today());

        // Then
        assertEquals(3, response.items().size());

        // Alice: FIXED -> receita do atendimento = 200, payout = 400 (só salário)
        PayrollReportResponse.PayrollItem itemAlice = response.items().stream().filter(i -> i.employeeId().equals(1L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("200.00"), itemAlice.baseAmount());
        assertEquals(new BigDecimal("400.00"), itemAlice.calculatedPay());

        // Bob: COMISSIONADO (10% de 200) -> receita = 200, payout = 20
        PayrollReportResponse.PayrollItem itemBob = response.items().stream().filter(i -> i.employeeId().equals(2L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("200.00"), itemBob.baseAmount());
        assertEquals(new BigDecimal("20.00"), itemBob.calculatedPay());

        // Dave: HYBRID (300 salário + 10% de 200) -> receita = 200, payout = 320
        PayrollReportResponse.PayrollItem itemDave = response.items().stream().filter(i -> i.employeeId().equals(4L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("200.00"), itemDave.baseAmount());
        assertEquals(new BigDecimal("320.00"), itemDave.calculatedPay());
    }

    private Appointment doneAptOn(Employee emp, SalonService svc, java.time.LocalDateTime when) {
        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(emp);
        withService(apt, svc);
        apt.setScheduledAt(when);
        return apt;
    }

    @Test
    void payroll_diarista_autoCountsDistinctAppointmentDays() {
        User user = new User();
        user.setName("Ella");
        Employee emp = new Employee();
        emp.setId(7L);
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.DIARISTA);
        emp.setRemunerationValue(new BigDecimal("120.00")); // diária

        when(employeeRepository.findAll()).thenReturn(List.of(emp));

        SalonService svc = new SalonService();
        svc.setPrice(new BigDecimal("200.00"));
        svc.setCommissionPercent(new BigDecimal("10")); // ignorado: DIARISTA não recebe comissão de serviço

        // 3 atendimentos, mas em 2 dias distintos -> 2 diárias
        var d1a = doneAptOn(emp, svc, salonClock.now().withHour(9));
        var d1b = doneAptOn(emp, svc, salonClock.now().withHour(15));
        var d2 = doneAptOn(emp, svc, salonClock.now().minusDays(1).withHour(10));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(d1a, d1b, d2));

        PayrollReportResponse response = reportService.generatePayrollReport(salonClock.today(), salonClock.today());

        PayrollReportResponse.PayrollItem item = response.items().get(0);
        assertEquals(new BigDecimal("120.00"), item.dailyRate());
        assertEquals(2, item.daysWorked());
        assertEquals(2, item.daysWorkedAuto());
        assertEquals(Boolean.FALSE, item.daysWorkedIsOverride());
        assertEquals(0, new BigDecimal("240.00").compareTo(item.calculatedPay())); // 120 × 2
    }

    @Test
    void payroll_diarista_manualOverrideWinsOverAutoCount() {
        User user = new User();
        user.setName("Ella");
        Employee emp = new Employee();
        emp.setId(7L);
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.DIARISTA);
        emp.setRemunerationValue(new BigDecimal("120.00"));

        when(employeeRepository.findAll()).thenReturn(List.of(emp));

        SalonService svc = new SalonService();
        svc.setPrice(new BigDecimal("200.00"));
        List<Appointment> apts = List.of(doneAptOn(emp, svc, salonClock.now())); // auto = 1
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any()))
                .thenReturn(apts);

        var override = new com.cristiane.salon.models.report.entity.DiaristaWorkedDaysOverride();
        override.setEmployeeId(7L);
        override.setDaysWorked(18);
        when(workedDaysOverrideRepository.findByPeriodStartAndPeriodEnd(any(), any()))
                .thenReturn(List.of(override));

        PayrollReportResponse response = reportService.generatePayrollReport(salonClock.today(), salonClock.today());

        PayrollReportResponse.PayrollItem item = response.items().get(0);
        assertEquals(18, item.daysWorked());
        assertEquals(1, item.daysWorkedAuto());
        assertEquals(Boolean.TRUE, item.daysWorkedIsOverride());
        assertEquals(0, new BigDecimal("2160.00").compareTo(item.calculatedPay())); // 120 × 18
    }

    @Test
    void payroll_diariaEComissionado_addsServiceCommissionOnTopOfDaily() {
        User user = new User();
        user.setName("Fabi");
        Employee emp = new Employee();
        emp.setId(8L);
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.DIARIA_E_COMISSIONADO);
        emp.setRemunerationValue(new BigDecimal("100.00")); // diária

        when(employeeRepository.findAll()).thenReturn(List.of(emp));

        SalonService svc = new SalonService();
        svc.setPrice(new BigDecimal("200.00"));
        svc.setCommissionPercent(new BigDecimal("10"));
        List<Appointment> apts = List.of(doneAptOn(emp, svc, salonClock.now())); // 1 dia
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any()))
                .thenReturn(apts);

        PayrollReportResponse response = reportService.generatePayrollReport(salonClock.today(), salonClock.today());

        PayrollReportResponse.PayrollItem item = response.items().get(0);
        // 100 × 1 diária + 10% de 200 comissão = 100 + 20 = 120
        assertEquals(0, new BigDecimal("120.00").compareTo(item.calculatedPay()));
    }

    @Test
    void payroll_diarista_withoutAppointments_paysZero() {
        User user = new User();
        user.setName("Gi");
        Employee emp = new Employee();
        emp.setId(9L);
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.DIARISTA);
        emp.setRemunerationValue(new BigDecimal("150.00"));

        when(employeeRepository.findAll()).thenReturn(List.of(emp));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        PayrollReportResponse response = reportService.generatePayrollReport(salonClock.today(), salonClock.today());

        PayrollReportResponse.PayrollItem item = response.items().get(0);
        assertEquals(0, item.daysWorked());
        assertEquals(0, BigDecimal.ZERO.compareTo(item.calculatedPay()));
    }

    @Test
    void financialReport_includesDiaristaPayInNetProfit() {
        CashFlow income = new CashFlow();
        income.setType(CashFlowType.INCOME);
        income.setAmount(new BigDecimal("1000.00"));
        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(income));

        User user = new User();
        user.setName("Ella");
        Employee emp = new Employee();
        emp.setId(7L);
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.DIARISTA);
        emp.setRemunerationValue(new BigDecimal("100.00"));
        when(employeeRepository.findAll()).thenReturn(List.of(emp));

        SalonService svc = new SalonService();
        svc.setPrice(new BigDecimal("200.00"));
        List<Appointment> apts = List.of(
                doneAptOn(emp, svc, salonClock.now()),
                doneAptOn(emp, svc, salonClock.now().minusDays(1))); // 2 dias
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any()))
                .thenReturn(apts);

        FinancialReportResponse report =
                reportService.generateFinancialReport(salonClock.today(), salonClock.today());

        // diária 100 × 2 = 200 entra como salário pago e sai do lucro: 1000 - 200 = 800
        assertEquals(0, new BigDecimal("200.00").compareTo(report.totalSalaryPaid()));
        assertEquals(0, new BigDecimal("800.00").compareTo(report.netProfit()));
        assertEquals(2, report.employeeFinanceDetails().get(0).daysWorked());
    }

    @Test
    void saveWorkedDaysOverride_rejectsNonDiarista() {
        User user = new User();
        user.setName("Bea");
        Employee emp = new Employee();
        emp.setId(3L);
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.COMISSIONADO);
        when(employeeRepository.findById(3L)).thenReturn(java.util.Optional.of(emp));

        assertThatThrownBy(() -> reportService.saveWorkedDaysOverride(
                3L, salonClock.today(), salonClock.today(), 10))
                .isInstanceOf(com.cristiane.salon.exception.BadRequestException.class);
        verify(workedDaysOverrideRepository, never()).save(any());
    }

    @Test
    void saveWorkedDaysOverride_upsertsForDiarista() {
        User user = new User();
        user.setName("Ella");
        Employee emp = new Employee();
        emp.setId(7L);
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.DIARIA_E_COMISSIONADO);
        when(employeeRepository.findById(7L)).thenReturn(java.util.Optional.of(emp));
        when(workedDaysOverrideRepository.findByEmployeeIdAndPeriodStartAndPeriodEnd(eq(7L), any(), any()))
                .thenReturn(java.util.Optional.empty());

        reportService.saveWorkedDaysOverride(7L, salonClock.today().minusDays(30), salonClock.today(), 22);

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.cristiane.salon.models.report.entity.DiaristaWorkedDaysOverride.class);
        verify(workedDaysOverrideRepository).save(captor.capture());
        assertEquals(22, captor.getValue().getDaysWorked());
        assertEquals(7L, captor.getValue().getEmployeeId());
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
    void shouldHandleNullRemunerationValuesGracefully() {
        // Given
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.FIXO_E_COMISSIONADO);
        emp.setRemunerationValue(null);

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
        // Given: funcionária comissionada em serviços (10%, configurado no serviço) e comissão
        // única do salão sobre produtos (20%).
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.COMISSIONADO);

        when(businessSettingsService.getProductCommissionPercent()).thenReturn(new BigDecimal("20.00"));

        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("100.00"));
        service.setCommissionPercent(new BigDecimal("10"));

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
        // Given: comissão de serviço configurada, mas sem % de produto configurado no salão.
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.COMISSIONADO);

        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("100.00"));
        service.setCommissionPercent(new BigDecimal("10"));

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

        when(businessSettingsService.getProductCommissionPercent()).thenReturn(new BigDecimal("20.00"));

        SalonService service = new SalonService();
        service.setPrice(new BigDecimal("100.00"));
        service.setCommissionPercent(new BigDecimal("10"));

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

    @Test
    void getAppointmentProfit_whenNoRecipeOrRetailProducts_shouldEqualRevenueMinusCommission() {
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.COMISSIONADO);

        SalonService service = new SalonService();
        service.setId(1L);
        service.setPrice(new BigDecimal("100.00"));
        service.setCommissionPercent(new BigDecimal("10"));

        Appointment apt = new Appointment();
        apt.setId(5L);
        apt.setEmployee(emp);
        withService(apt, service);

        when(appointmentRepository.findById(5L)).thenReturn(java.util.Optional.of(apt));

        AppointmentProfitResponse result = reportService.getAppointmentProfit(5L);

        assertEquals(new BigDecimal("100.00"), result.grossRevenue());
        assertEquals(BigDecimal.ZERO, result.serviceRecipeCost());
        assertEquals(BigDecimal.ZERO, result.productsSoldCost());
        assertEquals(new BigDecimal("10.00"), result.serviceCommissionCost());
        assertEquals(BigDecimal.ZERO, result.productCommissionCost());
        assertEquals(new BigDecimal("90.00"), result.netProfit());
        assertThat(result.positive()).isTrue();
    }

    @Test
    void getAppointmentProfit_withRecipeCost_shouldDeductFromRevenue() {
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.SALARIO_FIXO);
        emp.setRemunerationValue(new BigDecimal("2000.00"));

        SalonService service = new SalonService();
        service.setId(1L);
        service.setPrice(new BigDecimal("100.00"));

        com.cristiane.salon.models.product.entity.Product coloring =
                new com.cristiane.salon.models.product.entity.Product();
        coloring.setId(10L);
        coloring.setName("Tintura");
        coloring.setCostPrice(new BigDecimal("40.00"));
        coloring.setCapacity(new BigDecimal("1000"));

        com.cristiane.salon.models.service.entity.SalonServiceProductUsage usage =
                new com.cristiane.salon.models.service.entity.SalonServiceProductUsage();
        usage.setSalonService(service);
        usage.setProduct(coloring);
        usage.setQuantityUsed(new BigDecimal("30"));

        when(serviceProductUsageRepository.findBySalonServiceId(1L)).thenReturn(List.of(usage));

        Appointment apt = new Appointment();
        apt.setId(5L);
        apt.setEmployee(emp);
        withService(apt, service);

        when(appointmentRepository.findById(5L)).thenReturn(java.util.Optional.of(apt));

        AppointmentProfitResponse result = reportService.getAppointmentProfit(5L);

        // custo = 40/1000 * 30 = 1.20; salário fixo não gera comissão por atendimento
        assertThat(result.serviceRecipeCost()).isEqualByComparingTo("1.20");
        assertEquals(BigDecimal.ZERO, result.serviceCommissionCost());
        assertEquals(BigDecimal.ZERO, result.productCommissionCost());
        assertThat(result.netProfit()).isEqualByComparingTo("98.80");
    }

    @Test
    void getAppointmentProfit_withProductSaleCommission_shouldReportItSeparatelyFromServiceCommission() {
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.COMISSIONADO);

        when(businessSettingsService.getProductCommissionPercent()).thenReturn(new BigDecimal("20.00"));

        SalonService service = new SalonService();
        service.setId(1L);
        service.setPrice(new BigDecimal("100.00"));
        service.setCommissionPercent(new BigDecimal("10"));

        com.cristiane.salon.models.product.entity.Product shampoo =
                new com.cristiane.salon.models.product.entity.Product();
        shampoo.setId(20L);
        shampoo.setName("Shampoo");
        shampoo.setPrice(new BigDecimal("50.00"));

        Appointment apt = new Appointment();
        apt.setId(5L);
        apt.setEmployee(emp);
        withService(apt, service);
        withProduct(apt, new BigDecimal("50.00"), 1);

        when(appointmentRepository.findById(5L)).thenReturn(java.util.Optional.of(apt));

        AppointmentProfitResponse result = reportService.getAppointmentProfit(5L);

        // Comissão de serviço = 10% de 100 = 10.00; comissão de produto = 20% de 50 = 10.00
        assertThat(result.serviceCommissionCost()).isEqualByComparingTo("10.00");
        assertThat(result.productCommissionCost()).isEqualByComparingTo("10.00");
    }

    @Test
    void getAppointmentProfit_prefersFrozenSnapshotsOverLiveCatalogValues() {
        Employee emp = new Employee();
        emp.setId(1L);
        User user = new User();
        user.setName("Alice");
        emp.setUser(user);
        emp.setRemunerationType(RemunerationType.COMISSIONADO);

        SalonService service = new SalonService();
        service.setId(1L);
        service.setPrice(new BigDecimal("500.00"));       // preço atual (mudou depois)
        service.setCommissionPercent(new BigDecimal("50")); // % atual (mudou depois)

        Appointment apt = new Appointment();
        apt.setId(5L);
        apt.setEmployee(emp);
        apt.setSnapshotProductCommissionPercent(new BigDecimal("5.00"));

        var serviceItem = new com.cristiane.salon.models.appointment.entity.AppointmentServiceItem();
        serviceItem.setAppointment(apt);
        serviceItem.setSalonService(service);
        serviceItem.setSnapshotPrice(new BigDecimal("100.00"));
        serviceItem.setSnapshotCommissionPercent(new BigDecimal("10.00"));
        serviceItem.setSnapshotRecipeCost(new BigDecimal("6.00")); // o "óleo de 6 reais" congelado
        apt.getServices().add(serviceItem);

        var product = new com.cristiane.salon.models.product.entity.Product();
        product.setPrice(new BigDecimal("999.00"));
        product.setCostPrice(new BigDecimal("900.00"));
        var productItem = new com.cristiane.salon.models.appointment.entity.AppointmentProductItem();
        productItem.setAppointment(apt);
        productItem.setProduct(product);
        productItem.setQuantity(1);
        productItem.setSnapshotUnitPrice(new BigDecimal("50.00"));
        productItem.setSnapshotCostPrice(new BigDecimal("20.00"));
        apt.getProducts().add(productItem);

        when(appointmentRepository.findById(5L)).thenReturn(java.util.Optional.of(apt));

        AppointmentProfitResponse result = reportService.getAppointmentProfit(5L);

        // Tudo pelos snapshots, nada pelo catálogo atual:
        assertThat(result.serviceRecipeCost()).isEqualByComparingTo("6.00");
        assertThat(result.productsSoldCost()).isEqualByComparingTo("20.00");
        assertThat(result.serviceCommissionCost()).isEqualByComparingTo("10.00");   // 10% de 100
        assertThat(result.productCommissionCost()).isEqualByComparingTo("2.50");    // 5% de 50
        // receita = serviço 100 + produto 50 = 150 (grand total)
        assertThat(result.grossRevenue()).isEqualByComparingTo("150.00");
    }

    @Test
    void getAppointmentProfit_whenAppointmentNotFound_shouldThrowResourceNotFoundException() {
        when(appointmentRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.cristiane.salon.exception.ResourceNotFoundException.class,
                () -> reportService.getAppointmentProfit(99L)
        );
    }

    @Test
    void generateServicePricingAnalysis_shouldAllocateFixedExpensesProportionallyToRevenueAndSortByWorstMargin() {
        // Given: Corte (R$100, comissão 10% configurada no serviço) feito 2x = R$200 de receita
        Employee commissionedEmployee = new Employee();
        commissionedEmployee.setId(1L);
        commissionedEmployee.setUser(new User());
        commissionedEmployee.setRemunerationType(RemunerationType.COMISSIONADO);

        SalonService corte = new SalonService();
        corte.setId(1L);
        corte.setName("Corte");
        corte.setPrice(new BigDecimal("100.00"));
        corte.setCommissionPercent(new BigDecimal("10"));

        Appointment aptCorte1 = new Appointment();
        aptCorte1.setStatus(AppointmentStatus.DONE);
        aptCorte1.setEmployee(commissionedEmployee);
        withService(aptCorte1, corte);
        aptCorte1.setScheduledAt(salonClock.now());

        Appointment aptCorte2 = new Appointment();
        aptCorte2.setStatus(AppointmentStatus.DONE);
        aptCorte2.setEmployee(commissionedEmployee);
        withService(aptCorte2, corte);
        aptCorte2.setScheduledAt(salonClock.now());

        // Escova (R$50, funcionária SALARIO_FIXO, sem comissão) feito 1x = R$50 de receita
        Employee fixedEmployee = new Employee();
        fixedEmployee.setId(2L);
        fixedEmployee.setUser(new User());
        fixedEmployee.setRemunerationType(RemunerationType.SALARIO_FIXO);
        fixedEmployee.setRemunerationValue(new BigDecimal("2000"));

        SalonService escova = new SalonService();
        escova.setId(2L);
        escova.setName("Escova");
        escova.setPrice(new BigDecimal("50.00"));

        Appointment aptEscova = new Appointment();
        aptEscova.setStatus(AppointmentStatus.DONE);
        aptEscova.setEmployee(fixedEmployee);
        withService(aptEscova, escova);
        aptEscova.setScheduledAt(salonClock.now());

        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(aptCorte1, aptCorte2, aptEscova));
        // R$100 de gasto fixo no período, rateado proporcionalmente à receita: Corte (200/250) = 80, Escova (50/250) = 20
        when(fixedExpenseRepository.sumAmountByDateBetween(any(), any())).thenReturn(new BigDecimal("100.00"));

        // When
        var response = reportService.generateServicePricingAnalysis(salonClock.today(), salonClock.today());

        // Then
        assertThat(response.totalFixedExpenses()).isEqualByComparingTo("100.00");
        assertThat(response.items()).hasSize(2);

        var corteItem = response.items().stream().filter(i -> i.serviceId().equals(1L)).findFirst().orElseThrow();
        assertThat(corteItem.timesPerformed()).isEqualTo(2);
        assertThat(corteItem.totalRevenue()).isEqualByComparingTo("200.00");
        assertThat(corteItem.commissionCostTotal()).isEqualByComparingTo("20.00"); // 10% de 100, 2x
        assertThat(corteItem.fixedExpenseShare()).isEqualByComparingTo("80.00");
        // 200 - 0 (sem receita de produto) - 20 (comissão) - 80 (rateio) = 100
        assertThat(corteItem.netProfit()).isEqualByComparingTo("100.00");

        var escovaItem = response.items().stream().filter(i -> i.serviceId().equals(2L)).findFirst().orElseThrow();
        assertThat(escovaItem.timesPerformed()).isEqualTo(1);
        assertThat(escovaItem.totalRevenue()).isEqualByComparingTo("50.00");
        assertThat(escovaItem.commissionCostTotal()).isEqualByComparingTo(BigDecimal.ZERO); // sem % configurado no serviço
        assertThat(escovaItem.fixedExpenseShare()).isEqualByComparingTo("20.00");
        // 50 - 0 - 0 - 20 = 30
        assertThat(escovaItem.netProfit()).isEqualByComparingTo("30.00");

        // Ordenado por pior margem primeiro: Escova (30) rende menos lucro absoluto que Corte (100)
        assertThat(response.items().get(0).serviceId()).isEqualTo(2L);
        assertThat(response.items().get(1).serviceId()).isEqualTo(1L);
    }

    @Test
    void generateServicePricingAnalysis_whenNoAppointmentsDone_shouldReturnEmptyItems() {
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(fixedExpenseRepository.sumAmountByDateBetween(any(), any())).thenReturn(BigDecimal.ZERO);

        var response = reportService.generateServicePricingAnalysis(salonClock.today(), salonClock.today());

        assertThat(response.items()).isEmpty();
    }

    @Test
    void generateServicePricingAnalysis_whenNetProfitNegative_shouldBeMarkedUnhealthy() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUser(new User());
        employee.setRemunerationType(RemunerationType.SALARIO_FIXO);

        SalonService service = new SalonService();
        service.setId(1L);
        service.setName("Hidratação");
        service.setPrice(new BigDecimal("30.00"));

        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(employee);
        withService(apt, service);
        apt.setScheduledAt(salonClock.now());

        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt));
        // Gasto fixo maior que a receita do único serviço do período
        when(fixedExpenseRepository.sumAmountByDateBetween(any(), any())).thenReturn(new BigDecimal("500.00"));

        var response = reportService.generateServicePricingAnalysis(salonClock.today(), salonClock.today());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).healthy()).isFalse();
        assertThat(response.items().get(0).netProfit()).isEqualByComparingTo("-470.00");
    }

    @Test
    void generateServicePricingAnalysis_usesFrozenRecipeCostSnapshotOverCurrentRecipe() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUser(new User());
        employee.setRemunerationType(RemunerationType.SALARIO_FIXO);

        SalonService service = new SalonService();
        service.setId(1L);
        service.setName("Coloração");
        service.setPrice(new BigDecimal("100.00"));

        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(employee);
        var item = new com.cristiane.salon.models.appointment.entity.AppointmentServiceItem();
        item.setAppointment(apt);
        item.setSalonService(service);
        item.setSnapshotPrice(new BigDecimal("100.00"));
        item.setSnapshotRecipeCost(new BigDecimal("7.00")); // congelado no atendimento
        apt.getServices().add(item);
        apt.setScheduledAt(salonClock.now());

        List<Appointment> apts = List.of(apt);
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(apts);

        var response = reportService.generateServicePricingAnalysis(salonClock.today(), salonClock.today());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).recipeCostTotal()).isEqualByComparingTo("7.00");
        // nunca consultou a receita atual do serviço
        verify(serviceProductUsageRepository, never()).findBySalonServiceId(any());
    }
}
