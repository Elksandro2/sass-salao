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
    private com.cristiane.salon.models.payroll.repository.EmployeeWorkDayRepository workDayRepository;

    @Mock
    private com.cristiane.salon.models.payroll.repository.PayrollAdjustmentRepository adjustmentRepository;

    @Mock
    private com.cristiane.salon.models.staff.repository.StaffProfileRepository staffProfileRepository;

    // SalonClock real, não mock: os testes dependem do "hoje"/"agora" de verdade no fuso
    // do salão, e um mock devolveria null silenciosamente.
    @Spy
    private SalonClock salonClock = new SalonClock(ZoneId.of("America/Recife"));

    @InjectMocks
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        today = salonClock.today();
        now = salonClock.now();
        org.mockito.Mockito.lenient().when(fixedExpenseRepository.sumAmountByDateBetween(any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    // "Hoje"/"agora" capturados uma vez, fora de qualquer when(...): chamar o @Spy do relógio
    // no meio de um stubbing quebra o Mockito (UnfinishedStubbingException).
    private LocalDate today;
    private java.time.LocalDateTime now;

    private LocalDate monthStart() {
        return today.withDayOfMonth(1);
    }

    private LocalDate monthEnd() {
        return today.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
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

        // When — mês inteiro: o salário fixo entra exato (é proporcional aos dias do período)
        FinancialReportResponse report = reportService.generateFinancialReport(monthStart(), monthEnd());

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
        FinancialReportResponse report = reportService.generateFinancialReport(monthStart(), monthEnd());

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

    private Appointment doneAptOn(Employee emp, SalonService svc, java.time.LocalDateTime when) {
        Appointment apt = new Appointment();
        apt.setStatus(AppointmentStatus.DONE);
        apt.setEmployee(emp);
        withService(apt, svc);
        apt.setScheduledAt(when);
        return apt;
    }

    private com.cristiane.salon.models.payroll.dto.PayrollBreakdown payrollOf(Long employeeId, LocalDate from, LocalDate to) {
        return reportService.computePayroll(from, to).stream()
                .filter(d -> d.employee().getId().equals(employeeId))
                .findFirst().orElseThrow().breakdown();
    }

    private Employee employee(long id, String name, RemunerationType type, String value) {
        User user = new User();
        user.setId(id + 100);
        user.setName(name);
        Employee emp = new Employee();
        emp.setId(id);
        emp.setUser(user);
        emp.setRemunerationType(type);
        emp.setRemunerationValue(value != null ? new BigDecimal(value) : null);
        return emp;
    }

    @Test
    void computePayroll_fullMonth_combinesSalaryAndServiceCommission() {
        Employee alice = employee(1L, "Alice", RemunerationType.SALARIO_FIXO, "400.00");
        Employee bob = employee(2L, "Bob", RemunerationType.COMISSIONADO, null);
        Employee dave = employee(4L, "Dave", RemunerationType.FIXO_E_COMISSIONADO, "300.00");
        when(employeeRepository.findAll()).thenReturn(List.of(alice, bob, dave));

        SalonService serviceFlat = new SalonService();
        serviceFlat.setPrice(new BigDecimal("200.00"));
        SalonService serviceCommissioned = new SalonService();
        serviceCommissioned.setPrice(new BigDecimal("200.00"));
        serviceCommissioned.setCommissionPercent(new BigDecimal("10"));

        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                doneAptOn(bob, serviceCommissioned, now),
                doneAptOn(alice, serviceFlat, now),
                doneAptOn(dave, serviceCommissioned, now)));

        assertEquals(0, new BigDecimal("400.00").compareTo(payrollOf(1L, monthStart(), monthEnd()).netPayable()));
        assertEquals(0, new BigDecimal("20.00").compareTo(payrollOf(2L, monthStart(), monthEnd()).netPayable()));
        assertEquals(0, new BigDecimal("320.00").compareTo(payrollOf(4L, monthStart(), monthEnd()).netPayable()));
    }

    @Test
    void proratedMonthlySalary_isExactForWholeMonthsAndProportionalOtherwise() {
        BigDecimal salary = new BigDecimal("3000.00");
        // Abril tem 30 dias: mês inteiro = salário cheio; 15 dias = metade
        assertEquals(0, salary.compareTo(ReportService.proratedMonthlySalary(salary,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))));
        assertEquals(0, new BigDecimal("1500.00").compareTo(ReportService.proratedMonthlySalary(salary,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15))));
        // Três meses inteiros = três salários (antes contava um só, qualquer que fosse o período)
        assertEquals(0, new BigDecimal("9000.00").compareTo(ReportService.proratedMonthlySalary(salary,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30))));
        // Uma semana não custa mais um mês inteiro
        assertEquals(0, new BigDecimal("700.00").compareTo(ReportService.proratedMonthlySalary(salary,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 7))));
    }

    @Test
    void computePayroll_fixedSalaryStartsAtHireDate() {
        Employee alice = employee(1L, "Alice", RemunerationType.SALARIO_FIXO, "3000.00");
        when(employeeRepository.findAll()).thenReturn(List.of(alice));
        var profile = new com.cristiane.salon.models.staff.entity.StaffProfile();
        profile.setHiredAt(LocalDate.of(2026, 4, 16));
        when(staffProfileRepository.findByUserId(101L)).thenReturn(java.util.Optional.of(profile));

        var breakdown = payrollOf(1L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertEquals(0, new BigDecimal("1500.00").compareTo(breakdown.fixedSalary())); // 15 de 30 dias
    }

    @Test
    void computePayroll_inactiveEmployeeWithoutMovement_isLeftOut() {
        Employee gone = employee(1L, "Ex", RemunerationType.SALARIO_FIXO, "2000.00");
        gone.getUser().setActive(false);
        when(employeeRepository.findAll()).thenReturn(List.of(gone));

        assertEquals(0, reportService.computePayroll(monthStart(), monthEnd()).size());
    }

    private com.cristiane.salon.models.payroll.entity.EmployeeWorkDay workDay(long employeeId, LocalDate date, String amount) {
        var day = new com.cristiane.salon.models.payroll.entity.EmployeeWorkDay();
        day.setEmployeeId(employeeId);
        day.setWorkDate(date);
        day.setAmount(new BigDecimal(amount));
        return day;
    }

    @Test
    void computePayroll_diarista_paysEachMarkedDayWithItsOwnValue() {
        Employee ella = employee(7L, "Ella", RemunerationType.DIARISTA, "120.00");
        when(employeeRepository.findAll()).thenReturn(List.of(ella));
        // dois dias com a diária padrão e um meio período a R$ 60
        when(workDayRepository.findByWorkDateBetweenOrderByWorkDateAsc(any(), any())).thenReturn(List.of(
                workDay(7L, monthStart(), "120.00"),
                workDay(7L, monthStart().plusDays(1), "120.00"),
                workDay(7L, monthStart().plusDays(2), "60.00")));

        var breakdown = payrollOf(7L, monthStart(), monthEnd());

        assertEquals(3, breakdown.workDaysCount());
        assertEquals(0, new BigDecimal("300.00").compareTo(breakdown.dailyTotal()));
        assertEquals(0, new BigDecimal("300.00").compareTo(breakdown.netPayable()));
    }

    @Test
    void computePayroll_diarista_withoutMarkedDays_paysZeroEvenWithAppointments() {
        Employee ella = employee(7L, "Ella", RemunerationType.DIARISTA, "120.00");
        when(employeeRepository.findAll()).thenReturn(List.of(ella));
        SalonService svc = new SalonService();
        svc.setPrice(new BigDecimal("200.00"));
        svc.setCommissionPercent(new BigDecimal("10")); // ignorado: DIARISTA não recebe comissão de serviço
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(doneAptOn(ella, svc, now)));

        assertEquals(0, BigDecimal.ZERO.compareTo(payrollOf(7L, monthStart(), monthEnd()).netPayable()));
    }

    @Test
    void computePayroll_diariaEComissionado_addsServiceCommissionOnTopOfDaily() {
        Employee fabi = employee(8L, "Fabi", RemunerationType.DIARIA_E_COMISSIONADO, "100.00");
        when(employeeRepository.findAll()).thenReturn(List.of(fabi));
        SalonService svc = new SalonService();
        svc.setPrice(new BigDecimal("200.00"));
        svc.setCommissionPercent(new BigDecimal("10"));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(doneAptOn(fabi, svc, now)));
        when(workDayRepository.findByWorkDateBetweenOrderByWorkDateAsc(any(), any()))
                .thenReturn(List.of(workDay(8L, today, "100.00")));

        // 100 de diária + 10% de 200 de comissão
        assertEquals(0, new BigDecimal("120.00").compareTo(payrollOf(8L, monthStart(), monthEnd()).netPayable()));
    }

    @Test
    void computePayroll_includesCommissionOfStandaloneProductSales() {
        Employee bob = employee(2L, "Bob", RemunerationType.COMISSIONADO, null);
        when(employeeRepository.findAll()).thenReturn(List.of(bob));
        CashFlow sale = new CashFlow();
        sale.setType(CashFlowType.INCOME);
        sale.setAmount(new BigDecimal("150.00"));
        sale.setEmployee(bob);
        sale.setCommissionAmount(new BigDecimal("15.00"));
        when(cashFlowRepository.findSalesWithEmployeeBetween(any(), any())).thenReturn(List.of(sale));

        var breakdown = payrollOf(2L, monthStart(), monthEnd());

        assertEquals(0, new BigDecimal("15.00").compareTo(breakdown.saleProductCommission()));
        assertEquals(0, new BigDecimal("15.00").compareTo(breakdown.netPayable()));
    }

    private com.cristiane.salon.models.payroll.entity.PayrollAdjustment adjustment(
            long employeeId, com.cristiane.salon.models.payroll.entity.PayrollAdjustmentType type, String amount) {
        var adj = new com.cristiane.salon.models.payroll.entity.PayrollAdjustment();
        adj.setEmployeeId(employeeId);
        adj.setDate(today);
        adj.setType(type);
        adj.setAmount(new BigDecimal(amount));
        return adj;
    }

    @Test
    void computePayroll_bonusAddsDiscountSubtracts_andAdvanceOnlyReducesWhatIsLeftToPay() {
        Employee alice = employee(1L, "Alice", RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findAll()).thenReturn(List.of(alice));
        when(adjustmentRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of(
                adjustment(1L, com.cristiane.salon.models.payroll.entity.PayrollAdjustmentType.BONIFICACAO, "200.00"),
                adjustment(1L, com.cristiane.salon.models.payroll.entity.PayrollAdjustmentType.DESCONTO, "50.00"),
                adjustment(1L, com.cristiane.salon.models.payroll.entity.PayrollAdjustmentType.VALE, "300.00")));

        var breakdown = payrollOf(1L, monthStart(), monthEnd());

        assertEquals(0, new BigDecimal("1150.00").compareTo(breakdown.salonCost()));  // 1000 + 200 - 50
        assertEquals(0, new BigDecimal("850.00").compareTo(breakdown.netPayable()));  // custo - vale de 300
    }

    @Test
    void computePayroll_usesManualCommissionOfAppointmentWhenSet() {
        Employee bob = employee(2L, "Bob", RemunerationType.COMISSIONADO, null);
        when(employeeRepository.findAll()).thenReturn(List.of(bob));
        SalonService svc = new SalonService();
        svc.setPrice(new BigDecimal("200.00"));
        svc.setCommissionPercent(new BigDecimal("10")); // automático daria 20
        Appointment apt = doneAptOn(bob, svc, now);
        apt.setOverrideServiceCommission(new BigDecimal("35.00"));
        when(appointmentRepository.findAllInPeriod(any(), any(), any(), any(), any(), any())).thenReturn(List.of(apt));

        assertEquals(0, new BigDecimal("35.00").compareTo(payrollOf(2L, monthStart(), monthEnd()).serviceCommission()));
    }

    @Test
    void financialReport_includesDiaristaPayInNetProfit() {
        CashFlow income = new CashFlow();
        income.setType(CashFlowType.INCOME);
        income.setAmount(new BigDecimal("1000.00"));
        when(cashFlowRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(income));

        Employee ella = employee(7L, "Ella", RemunerationType.DIARISTA, "100.00");
        when(employeeRepository.findAll()).thenReturn(List.of(ella));
        when(workDayRepository.findByWorkDateBetweenOrderByWorkDateAsc(any(), any())).thenReturn(List.of(
                workDay(7L, today, "100.00"),
                workDay(7L, today.minusDays(1), "100.00")));

        FinancialReportResponse report =
                reportService.generateFinancialReport(today.minusDays(1), today);

        // diária 100 × 2 = 200 entra como salário pago e sai do lucro: 1000 - 200 = 800
        assertEquals(0, new BigDecimal("200.00").compareTo(report.totalSalaryPaid()));
        assertEquals(0, new BigDecimal("800.00").compareTo(report.netProfit()));
        assertEquals(2, report.employeeFinanceDetails().get(0).daysWorked());
    }

    @Test
    void appointmentProfit_manualValuesReplaceAutomaticOnes_andKeepAutoForReference() {
        Employee bob = employee(2L, "Bob", RemunerationType.COMISSIONADO, null);
        SalonService svc = new SalonService();
        svc.setId(5L);
        svc.setPrice(new BigDecimal("200.00"));
        svc.setCommissionPercent(new BigDecimal("10"));
        Appointment apt = doneAptOn(bob, svc, now);
        apt.setId(9L);
        apt.setOverrideServiceCommission(new BigDecimal("50.00"));
        apt.setOverrideRecipeCost(new BigDecimal("30.00"));
        apt.setProfitOverrideNote("combinado com ela");
        when(appointmentRepository.findById(9L)).thenReturn(java.util.Optional.of(apt));

        var profit = reportService.getAppointmentProfit(9L);

        assertEquals(0, new BigDecimal("50.00").compareTo(profit.serviceCommissionCost()));
        assertEquals(0, new BigDecimal("20.00").compareTo(profit.autoServiceCommissionCost()));
        assertEquals(0, new BigDecimal("30.00").compareTo(profit.serviceRecipeCost()));
        assertEquals(0, new BigDecimal("120.00").compareTo(profit.netProfit())); // 200 - 30 - 50
        assertEquals(true, profit.serviceCommissionCostManual());
        assertEquals(false, profit.productCommissionCostManual());
        assertEquals("combinado com ela", profit.overrideNote());
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
        LocalDate expectedTo = monthEnd();
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
    void shouldIncludeProductCommissionInPayroll() {
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

        assertEquals(0, new BigDecimal("30.00").compareTo(payrollOf(1L, monthStart(), monthEnd()).netPayable()));
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
    void getAppointmentProfit_whenServiceHasNoPriceAtAll_shouldTreatItAsZeroRevenueInsteadOfCrashing() {
        // Serviço "a combinar" (sem preço no catálogo) e sem customPrice/snapshot — não deve
        // estourar NPE; a receita desse item entra como zero (visível pra dona conferir, não some).
        Employee emp = new Employee();
        emp.setId(1L);
        emp.setUser(new User());
        emp.setRemunerationType(RemunerationType.SALARIO_FIXO);

        SalonService service = new SalonService();
        service.setId(1L);
        service.setPrice(null);

        Appointment apt = new Appointment();
        apt.setId(5L);
        apt.setEmployee(emp);
        withService(apt, service);
        when(appointmentRepository.findById(5L)).thenReturn(java.util.Optional.of(apt));

        AppointmentProfitResponse result = reportService.getAppointmentProfit(5L);

        assertThat(result.grossRevenue()).isEqualByComparingTo("0.00");
        assertThat(result.netProfit()).isEqualByComparingTo("0.00");
        assertThat(result.positive()).isTrue();
    }

    @Test
    void getAppointmentProfit_whenProductHasNoCostDataRegistered_shouldSkipItsCostInsteadOfCrashing() {
        // Produto vendido sem custeio cadastrado (sem costPrice/capacity) — custo entra como
        // zero, não derruba o cálculo do resto do atendimento.
        Employee emp = new Employee();
        emp.setId(1L);
        emp.setUser(new User());
        emp.setRemunerationType(RemunerationType.SALARIO_FIXO);

        SalonService service = new SalonService();
        service.setId(1L);
        service.setPrice(new BigDecimal("100.00"));

        Appointment apt = new Appointment();
        apt.setId(5L);
        apt.setEmployee(emp);
        withService(apt, service);
        withProduct(apt, new BigDecimal("30.00"), 2); // produto sem costPrice/capacity cadastrados
        when(appointmentRepository.findById(5L)).thenReturn(java.util.Optional.of(apt));

        AppointmentProfitResponse result = reportService.getAppointmentProfit(5L);

        assertThat(result.productsSoldCost()).isEqualByComparingTo("0.00");
        // receita = 100 (serviço) + 30×2 (produto) = 160; sem custo, sem comissão (Salário Fixo)
        assertThat(result.grossRevenue()).isEqualByComparingTo("160.00");
        assertThat(result.netProfit()).isEqualByComparingTo("160.00");
    }

    @Test
    void getAppointmentProfit_whenEmployeeHasNoRemunerationType_shouldChargeNoCommission() {
        // Funcionária cadastrada sem tipo de remuneração definido — não deve estourar NPE nem
        // cobrar comissão indevida.
        Employee emp = new Employee();
        emp.setId(1L);
        emp.setUser(new User());
        emp.setRemunerationType(null);

        SalonService service = new SalonService();
        service.setId(1L);
        service.setPrice(new BigDecimal("100.00"));
        service.setCommissionPercent(new BigDecimal("10")); // % existe no serviço, mas ninguém recebe

        Appointment apt = new Appointment();
        apt.setId(5L);
        apt.setEmployee(emp);
        withService(apt, service);
        when(appointmentRepository.findById(5L)).thenReturn(java.util.Optional.of(apt));

        AppointmentProfitResponse result = reportService.getAppointmentProfit(5L);

        assertThat(result.serviceCommissionCost()).isEqualByComparingTo("0.00");
        assertThat(result.netProfit()).isEqualByComparingTo("100.00");
    }

    @Test
    void getAppointmentProfit_whenMultipleServicesMixSnapshotAndLiveRecipeCost_shouldSumBoth() {
        // Um serviço já congelado (V72) e outro antigo sem snapshot (cai no cálculo ao vivo) no
        // mesmo agendamento — os dois precisam somar corretamente.
        Employee emp = new Employee();
        emp.setId(1L);
        emp.setUser(new User());
        emp.setRemunerationType(RemunerationType.SALARIO_FIXO);

        SalonService serviceA = new SalonService();
        serviceA.setId(1L);
        serviceA.setPrice(new BigDecimal("100.00"));

        SalonService serviceB = new SalonService();
        serviceB.setId(2L);
        serviceB.setPrice(new BigDecimal("50.00"));

        var productForB = new com.cristiane.salon.models.product.entity.Product();
        productForB.setId(20L);
        productForB.setCostPrice(new BigDecimal("40.00"));
        productForB.setCapacity(new BigDecimal("1000"));
        var usageForB = new com.cristiane.salon.models.service.entity.SalonServiceProductUsage();
        usageForB.setSalonService(serviceB);
        usageForB.setProduct(productForB);
        usageForB.setQuantityUsed(new BigDecimal("50")); // custo ao vivo = 40/1000 * 50 = 2.00
        when(serviceProductUsageRepository.findBySalonServiceId(2L)).thenReturn(List.of(usageForB));

        Appointment apt = new Appointment();
        apt.setId(5L);
        apt.setEmployee(emp);

        var itemA = new com.cristiane.salon.models.appointment.entity.AppointmentServiceItem();
        itemA.setAppointment(apt);
        itemA.setSalonService(serviceA);
        itemA.setSnapshotPrice(new BigDecimal("100.00"));
        itemA.setSnapshotRecipeCost(new BigDecimal("6.00")); // congelado
        apt.getServices().add(itemA);

        var itemB = new com.cristiane.salon.models.appointment.entity.AppointmentServiceItem();
        itemB.setAppointment(apt);
        itemB.setSalonService(serviceB);
        // sem snapshot -> cai no cálculo ao vivo (2.00)
        apt.getServices().add(itemB);

        when(appointmentRepository.findById(5L)).thenReturn(java.util.Optional.of(apt));

        AppointmentProfitResponse result = reportService.getAppointmentProfit(5L);

        assertThat(result.serviceRecipeCost()).isEqualByComparingTo("8.00"); // 6.00 + 2.00
        assertThat(result.grossRevenue()).isEqualByComparingTo("150.00"); // 100 + 50
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
