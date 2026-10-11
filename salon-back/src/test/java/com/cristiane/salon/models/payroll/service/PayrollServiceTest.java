package com.cristiane.salon.models.payroll.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.payroll.dto.EmployeePayrollData;
import com.cristiane.salon.models.payroll.dto.PayrollBreakdown;
import com.cristiane.salon.models.payroll.dto.PayrollDtos;
import com.cristiane.salon.models.payroll.entity.EmployeeWorkDay;
import com.cristiane.salon.models.payroll.entity.PayrollPayment;
import com.cristiane.salon.models.payroll.entity.PayrollSalaryPayment;
import com.cristiane.salon.models.payroll.repository.EmployeeWorkDayRepository;
import com.cristiane.salon.models.payroll.repository.PayrollAdjustmentRepository;
import com.cristiane.salon.models.payroll.repository.PayrollPaymentRepository;
import com.cristiane.salon.models.payroll.repository.PayrollSalaryPaymentRepository;
import com.cristiane.salon.models.report.service.ReportService;
import com.cristiane.salon.models.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock private ReportService reportService;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeWorkDayRepository workDayRepository;
    @Mock private PayrollAdjustmentRepository adjustmentRepository;
    @Mock private PayrollPaymentRepository paymentRepository;
    @Mock private PayrollSalaryPaymentRepository salaryRepository;
    @Spy private SalonClock salonClock = new SalonClock(ZoneId.of("America/Recife"));

    @InjectMocks
    private PayrollService payrollService;

    private static final LocalDate APR_1 = LocalDate.of(2026, 4, 1);
    private static final LocalDate APR_15 = LocalDate.of(2026, 4, 15);
    private static final LocalDate APR_16 = LocalDate.of(2026, 4, 16);
    private static final LocalDate APR_30 = LocalDate.of(2026, 4, 30);
    private static final YearMonth APRIL = YearMonth.of(2026, 4);

    @org.junit.jupiter.api.BeforeEach
    void defaults() {
        // Como o ReportService real: sem salário fixo no mês = zero (os testes sobrescrevem).
        lenient().when(reportService.monthlySalary(any(), any())).thenReturn(BigDecimal.ZERO);
    }

    private Employee employee(RemunerationType type, String value) {
        User user = new User();
        user.setId(10L);
        user.setName("Maria");
        Employee emp = new Employee();
        emp.setId(1L);
        emp.setUser(user);
        emp.setRemunerationType(type);
        emp.setRemunerationValue(value != null ? new BigDecimal(value) : null);
        return emp;
    }

    /** Folha com salário fixo (proporcional, como o cálculo devolve) + comissão. */
    private static PayrollBreakdown breakdown(String salary, String commission) {
        return new PayrollBreakdown(new BigDecimal(salary), 0, BigDecimal.ZERO, new BigDecimal(commission),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static EmployeePayrollData data(Employee emp, PayrollBreakdown b) {
        return new EmployeePayrollData(emp, true, List.of(), List.of(), List.of(), List.of(), List.of(), b);
    }

    private static PayrollPayment payment(long id, LocalDate start, LocalDate end, String paid, LocalDate paidAt) {
        PayrollPayment p = new PayrollPayment();
        p.setId(id);
        p.setEmployeeId(1L);
        p.setPeriodStart(start);
        p.setPeriodEnd(end);
        p.setAmountPaid(new BigDecimal(paid));
        p.setCalculatedAmount(new BigDecimal(paid));
        p.setPaidAt(paidAt);
        return p;
    }

    private static PayrollSalaryPayment salaryLine(long paymentId, YearMonth month, String amount) {
        PayrollSalaryPayment s = new PayrollSalaryPayment();
        s.setPaymentId(paymentId);
        s.setEmployeeId(1L);
        s.setSalaryMonth(month.atDay(1));
        s.setAmount(new BigDecimal(amount));
        return s;
    }

    private static PayrollDtos.PaymentRequest request(LocalDate start, LocalDate end, List<LocalDate> months) {
        return new PayrollDtos.PaymentRequest(1L, start, end, months, APR_30, "pix", null);
    }

    // --- Resumo ----------------------------------------------------------------------------------

    @Test
    void mergeIntervals_joinsAdjacentPeriods_ignoresSalaryOnlyPayments_andKeepsGapsSeparate() {
        var merged = PayrollService.mergeIntervals(List.of(
                payment(1, APR_1, APR_15, "500", APR_15),
                payment(2, APR_16, APR_30, "500", APR_30),
                payment(3, null, null, "1000", APR_30),
                payment(4, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "1000", APR_30)));

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0)[1]).isEqualTo(APR_30);
    }

    @Test
    void getSummary_nothingPaid_showsTheMonthlySalaryPendingAndTheDaysOpen() {
        Employee emp = employee(RemunerationType.FIXO_E_COMISSIONADO, "1000.00");
        when(reportService.computePayroll(APR_1, APR_15)).thenReturn(List.of(data(emp, breakdown("500.00", "200.00"))));
        when(reportService.monthlySalary(emp, APRIL)).thenReturn(new BigDecimal("1000.00"));

        // filtro de meio mês: o salário aparece INTEIRO (é mensal)
        var item = payrollService.getSummary(APR_1, APR_15).items().get(0);

        assertThat(item.periodStatus()).isEqualTo(PayrollDtos.PeriodStatus.EM_ABERTO);
        assertThat(item.salaryMonths()).hasSize(1);
        assertThat(item.salaryMonths().get(0).paid()).isFalse();
        assertThat(item.salaryMonths().get(0).amount()).isEqualByComparingTo("1000.00");
        assertThat(item.payableAmount()).isEqualByComparingTo("1200.00"); // 200 de comissão + salário do mês
        assertThat(item.outstanding()).isEqualByComparingTo("1200.00");
        assertThat(item.unpaidRanges()).hasSize(1);
    }

    @Test
    void getSummary_daysPaidAndSalaryPaidSeparately_isFullyPaid() {
        Employee emp = employee(RemunerationType.FIXO_E_COMISSIONADO, "1000.00");
        when(reportService.computePayroll(APR_1, APR_30)).thenReturn(List.of(data(emp, breakdown("1000.00", "200.00"))));
        when(paymentRepository.findAllByOrderByPeriodStartAsc()).thenReturn(List.of(
                payment(1, APR_1, APR_30, "200", APR_30),
                payment(2, null, null, "1000", LocalDate.of(2026, 5, 5))));
        when(salaryRepository.findAll()).thenReturn(List.of(salaryLine(2, APRIL, "1000.00")));
        // os dias do pagamento 1 continuam valendo 200 (nada mudou)
        when(reportService.computeEmployeePayroll(emp, APR_1, APR_30)).thenReturn(data(emp, breakdown("1000.00", "200.00")));

        var item = payrollService.getSummary(APR_1, APR_30).items().get(0);

        assertThat(item.periodStatus()).isEqualTo(PayrollDtos.PeriodStatus.PAGO);
        assertThat(item.outstanding()).isEqualByComparingTo("0");
        assertThat(item.pendingDifference()).isEqualByComparingTo("0");
        assertThat(item.salaryMonths().get(0).paid()).isTrue();
        assertThat(item.salaryMonths().get(0).paidAt()).isEqualTo(LocalDate.of(2026, 5, 5));
        assertThat(item.paidInPeriod()).isEqualByComparingTo("1200");
    }

    @Test
    void getSummary_fixedSalaryOnly_paidSalary_doesNotLeaveDaysAsPending() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(reportService.computePayroll(APR_1, APR_30)).thenReturn(List.of(data(emp, breakdown("1000.00", "0"))));
        when(paymentRepository.findAllByOrderByPeriodStartAsc()).thenReturn(List.of(payment(2, null, null, "1000", APR_30)));
        when(salaryRepository.findAll()).thenReturn(List.of(salaryLine(2, APRIL, "1000.00")));

        var item = payrollService.getSummary(APR_1, APR_30).items().get(0);

        assertThat(item.periodStatus()).isEqualTo(PayrollDtos.PeriodStatus.PAGO);
        assertThat(item.unpaidRanges()).isEmpty(); // nenhum dia com algo a pagar
        assertThat(item.outstanding()).isEqualByComparingTo("0");
    }

    @Test
    void getSummary_partialDaysPaid_estimatesOnlyTheRemainingDays() {
        Employee emp = employee(RemunerationType.COMISSIONADO, null);
        when(reportService.computePayroll(APR_1, APR_30)).thenReturn(List.of(data(emp, breakdown("0", "300.00"))));
        when(paymentRepository.findAllByOrderByPeriodStartAsc()).thenReturn(List.of(payment(1, APR_1, APR_15, "100", APR_15)));
        when(reportService.computeEmployeePayroll(emp, APR_1, APR_15)).thenReturn(data(emp, breakdown("0", "100.00")));
        when(reportService.computeEmployeePayroll(emp, APR_16, APR_30)).thenReturn(data(emp, breakdown("0", "200.00")));

        var item = payrollService.getSummary(APR_1, APR_30).items().get(0);

        assertThat(item.periodStatus()).isEqualTo(PayrollDtos.PeriodStatus.PARCIAL);
        assertThat(item.coveredDays()).isEqualTo(15);
        assertThat(item.unpaidRanges()).hasSize(1);
        assertThat(item.unpaidRanges().get(0).start()).isEqualTo(APR_16);
        assertThat(item.outstanding()).isEqualByComparingTo("200.00");
    }

    @Test
    void getSummary_whenDaysChangedAfterPayment_showsTheDifferenceToPay() {
        Employee emp = employee(RemunerationType.COMISSIONADO, null);
        when(reportService.computePayroll(APR_1, APR_30)).thenReturn(List.of(data(emp, breakdown("0", "300.00"))));
        when(paymentRepository.findAllByOrderByPeriodStartAsc()).thenReturn(List.of(payment(1, APR_1, APR_30, "200", APR_30)));
        // um atendimento corrigido depois de pago: os dias agora valem 300
        when(reportService.computeEmployeePayroll(emp, APR_1, APR_30)).thenReturn(data(emp, breakdown("0", "300.00")));

        var item = payrollService.getSummary(APR_1, APR_30).items().get(0);

        assertThat(item.periodStatus()).isEqualTo(PayrollDtos.PeriodStatus.PAGO);
        assertThat(item.pendingDifference()).isEqualByComparingTo("100.00");
        assertThat(item.outstanding()).isEqualByComparingTo("100.00");
    }

    @Test
    void getSummary_twoMonthsFiltered_listsEachMonthSalary() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        LocalDate may31 = LocalDate.of(2026, 5, 31);
        when(reportService.computePayroll(APR_1, may31)).thenReturn(List.of(data(emp, breakdown("2000.00", "0"))));
        when(paymentRepository.findAllByOrderByPeriodStartAsc()).thenReturn(List.of(payment(2, null, null, "1000", APR_30)));
        when(salaryRepository.findAll()).thenReturn(List.of(salaryLine(2, APRIL, "1000.00")));
        when(reportService.monthlySalary(emp, YearMonth.of(2026, 5))).thenReturn(new BigDecimal("1000.00"));

        var item = payrollService.getSummary(APR_1, may31).items().get(0);

        assertThat(item.salaryMonths()).extracting(PayrollDtos.SalaryMonthStatus::paid).containsExactly(true, false);
        assertThat(item.periodStatus()).isEqualTo(PayrollDtos.PeriodStatus.PARCIAL);
        assertThat(item.outstanding()).isEqualByComparingTo("1000.00");
    }

    // --- Pagamentos ------------------------------------------------------------------------------

    @Test
    void createPayment_withDaysAndSalary_isDaysWithoutSalaryPlusTheWholeMonthlySalary() {
        Employee emp = employee(RemunerationType.FIXO_E_COMISSIONADO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(reportService.computeEmployeePayroll(emp, APR_1, APR_15)).thenReturn(data(emp, breakdown("500.00", "200.00")));
        when(reportService.monthlySalary(emp, APRIL)).thenReturn(new BigDecimal("1000.00"));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            PayrollPayment p = inv.getArgument(0);
            p.setId(5L);
            return p;
        });
        when(salaryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // paga só a 1ª quinzena, mas o salário do mês inteiro
        var response = payrollService.createPayment(request(APR_1, APR_15, List.of(LocalDate.of(2026, 4, 20))));

        assertThat(response.amountPaid()).isEqualByComparingTo("1200.00"); // 200 + 1000 (não 500 proporcional)
        assertThat(response.salaryMonths()).hasSize(1);
        assertThat(response.salaryMonths().get(0).month()).isEqualTo(APR_1); // qualquer dia vira dia 1
        verify(salaryRepository).saveAll(argThat(lines -> {
            var list = (List<PayrollSalaryPayment>) lines;
            return list.size() == 1 && list.get(0).getPaymentId().equals(5L);
        }));
    }

    @Test
    void createPayment_salaryOnly_hasNoDays() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(reportService.monthlySalary(emp, APRIL)).thenReturn(new BigDecimal("1000.00"));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            PayrollPayment p = inv.getArgument(0);
            p.setId(6L);
            return p;
        });
        when(salaryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var response = payrollService.createPayment(request(null, null, List.of(APR_1)));

        assertThat(response.periodStart()).isNull();
        assertThat(response.amountPaid()).isEqualByComparingTo("1000.00");
        verify(reportService, never()).computeEmployeePayroll(any(), any(), any());
    }

    @Test
    void createPayment_rejectsSalaryOfAMonthAlreadyPaid() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(salaryRepository.findByEmployeeId(1L)).thenReturn(List.of(salaryLine(9, APRIL, "1000.00")));
        when(paymentRepository.findById(9L)).thenReturn(Optional.of(payment(9, null, null, "1000", LocalDate.of(2026, 4, 28))));

        assertThatThrownBy(() -> payrollService.createPayment(request(null, null, List.of(APR_1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("abril/2026 já foi pago em 28/04/2026");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_rejectsSalaryWhenThereIsNoFixedSalaryThatMonth() {
        Employee emp = employee(RemunerationType.COMISSIONADO, null);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(reportService.monthlySalary(emp, APRIL)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> payrollService.createPayment(request(null, null, List.of(APR_1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Não há salário fixo");
    }

    @Test
    void createPayment_rejectsEmptyPayment() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));

        assertThatThrownBy(() -> payrollService.createPayment(request(null, null, List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Nada pra registrar");
    }

    @Test
    void createPayment_rejectsPeriodOverlappingAnExistingPayment() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(1L)).thenReturn(List.of(
                payment(1, APR_1, APR_30, "1000", APR_30)));

        assertThatThrownBy(() -> payrollService.createPayment(request(LocalDate.of(2026, 4, 20), LocalDate.of(2026, 5, 20), List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("01/04/2026 a 30/04/2026");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_salaryOnlyPayment_doesNotBlockTheDaysOfTheMonth() {
        Employee emp = employee(RemunerationType.FIXO_E_COMISSIONADO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(1L)).thenReturn(List.of(
                payment(2, null, null, "1000", APR_15)));
        when(reportService.computeEmployeePayroll(emp, APR_1, APR_30)).thenReturn(data(emp, breakdown("1000.00", "300.00")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salaryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var response = payrollService.createPayment(request(APR_1, APR_30, List.of()));

        assertThat(response.amountPaid()).isEqualByComparingTo("300.00"); // só os dias, salário já foi
    }

    @Test
    void updatePayment_rejectsExtendingIntoDaysOfAnotherPayment() {
        Employee emp = employee(RemunerationType.COMISSIONADO, null);
        PayrollPayment first = payment(1, APR_1, APR_15, "500", APR_15);
        PayrollPayment second = payment(2, APR_16, APR_30, "500", APR_30);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(first));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(1L)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> payrollService.updatePayment(1L, request(APR_1, APR_16, List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("16/04/2026 a 30/04/2026");
    }

    @Test
    void updatePayment_keepingItsOwnSalaryMonth_isNotAConflict() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        PayrollPayment own = payment(2, null, null, "1000", APR_30);
        when(paymentRepository.findById(2L)).thenReturn(Optional.of(own));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(salaryRepository.findByEmployeeId(1L)).thenReturn(List.of(salaryLine(2, APRIL, "1000.00")));
        when(reportService.monthlySalary(emp, APRIL)).thenReturn(new BigDecimal("1000.00"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salaryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var response = payrollService.updatePayment(2L, request(null, null, List.of(APR_1)));

        assertThat(response.amountPaid()).isEqualByComparingTo("1000.00");
        verify(salaryRepository).deleteByPaymentId(2L); // troca as linhas de salário do próprio pagamento
    }

    @Test
    void createPayment_rejectsPeriodLongerThanOneYear() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));

        assertThatThrownBy(() -> payrollService.createPayment(request(APR_1, LocalDate.of(2028, 4, 1), List.of())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createPayment_rejectsNegativeValue_insteadOfFailingInTheDatabase() {
        Employee emp = employee(RemunerationType.COMISSIONADO, null);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(reportService.computeEmployeePayroll(emp, APR_1, APR_30)).thenReturn(data(emp, breakdown("0", "-50.00")));

        assertThatThrownBy(() -> payrollService.createPayment(request(APR_1, APR_30, List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("negativo");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void deletePayment_releasesItsSalaryMonths() {
        when(paymentRepository.existsById(5L)).thenReturn(true);

        payrollService.deletePayment(5L);

        verify(salaryRepository).deleteByPaymentId(5L);
        verify(paymentRepository).deleteById(5L);
    }

    @Test
    void previewPayment_sumsDaysWithoutSalaryAndTheChosenMonths_andReportsConflicts() {
        Employee emp = employee(RemunerationType.FIXO_E_COMISSIONADO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(1L)).thenReturn(List.of(
                payment(1, APR_1, APR_15, "500", APR_15)));
        when(reportService.computeEmployeePayroll(emp, APR_1, APR_30)).thenReturn(data(emp, breakdown("1000.00", "300.00")));
        lenient().when(reportService.monthlySalary(emp, APRIL)).thenReturn(new BigDecimal("1000.00"));

        var preview = payrollService.previewPayment(1L, APR_1, APR_30, List.of(APR_1), null);

        assertThat(preview.calculatedAmount()).isEqualByComparingTo("1300.00");
        assertThat(preview.conflict()).contains("01/04/2026 a 15/04/2026");
    }

    @Test
    void previewPayment_flagsNegativeValueAsAProblem() {
        Employee emp = employee(RemunerationType.COMISSIONADO, null);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(reportService.computeEmployeePayroll(emp, APR_1, APR_30)).thenReturn(data(emp, breakdown("0", "-50.00")));

        assertThat(payrollService.previewPayment(1L, APR_1, APR_30, List.of(), null).conflict()).contains("negativo");
    }

    // --- Diárias e lançamentos -------------------------------------------------------------------

    @Test
    void createWorkDay_usesDefaultRate_whenNoAmountInformed() {
        Employee emp = employee(RemunerationType.DIARISTA, "120.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(workDayRepository.findByEmployeeIdAndWorkDate(1L, APR_1)).thenReturn(Optional.empty());
        when(workDayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = payrollService.createWorkDay(new PayrollDtos.WorkDayRequest(1L, APR_1, null, null));

        assertThat(response.amount()).isEqualByComparingTo("120.00");
    }

    @Test
    void createWorkDay_withDifferentValue_doesNotRequireJustification() {
        Employee emp = employee(RemunerationType.DIARISTA, "120.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(workDayRepository.findByEmployeeIdAndWorkDate(1L, APR_1)).thenReturn(Optional.empty());
        when(workDayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = payrollService.createWorkDay(
                new PayrollDtos.WorkDayRequest(1L, APR_1, new BigDecimal("60.00"), "  "));

        assertThat(response.amount()).isEqualByComparingTo("60.00");
        assertThat(response.justification()).isNull();
    }

    @Test
    void createWorkDay_rejectsEmployeeThatIsNotDaily() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));

        assertThatThrownBy(() -> payrollService.createWorkDay(new PayrollDtos.WorkDayRequest(1L, APR_1, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void unmarkWorkDay_keepsATombstoneSoTheAppointmentDayDoesNotCountAgain() {
        Employee emp = employee(RemunerationType.DIARISTA, "120.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(workDayRepository.findByEmployeeIdAndWorkDate(1L, APR_1)).thenReturn(Optional.empty());

        payrollService.unmarkWorkDay(1L, APR_1);

        verify(workDayRepository).save(argThat(d -> d.isRemoved() && d.getWorkDate().equals(APR_1)));
    }

    @Test
    void createWorkDay_onAPreviouslyUnmarkedDay_revivesTheSameRecord() {
        Employee emp = employee(RemunerationType.DIARISTA, "120.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        EmployeeWorkDay tombstone = new EmployeeWorkDay();
        tombstone.setId(4L);
        tombstone.setEmployeeId(1L);
        tombstone.setWorkDate(APR_1);
        tombstone.setAmount(BigDecimal.ZERO);
        tombstone.setRemoved(true);
        when(workDayRepository.findByEmployeeIdAndWorkDate(1L, APR_1)).thenReturn(Optional.of(tombstone));
        when(workDayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = payrollService.createWorkDay(new PayrollDtos.WorkDayRequest(1L, APR_1, null, null));

        assertThat(response.id()).isEqualTo(4L);
        assertThat(response.amount()).isEqualByComparingTo("120.00");
        assertThat(tombstone.isRemoved()).isFalse();
    }

    @Test
    void createWorkDaysBulk_skipsDaysAlreadyMarked() {
        Employee emp = employee(RemunerationType.DIARISTA, "120.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(workDayRepository.findByEmployeeIdAndWorkDate(eq(1L), any())).thenAnswer(inv ->
                APR_1.equals(inv.getArgument(1)) ? Optional.of(new EmployeeWorkDay()) : Optional.empty());
        when(workDayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var created = payrollService.createWorkDaysBulk(
                new PayrollDtos.WorkDayBulkRequest(1L, List.of(APR_1, LocalDate.of(2026, 4, 2))));

        assertThat(created).hasSize(1);
        verify(workDayRepository).save(argThat(d -> d.getWorkDate().equals(LocalDate.of(2026, 4, 2))));
    }

    @Test
    void createAdjustment_rejectsNewVale() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));

        assertThatThrownBy(() -> payrollService.createAdjustment(new PayrollDtos.AdjustmentRequest(
                1L, APR_1, com.cristiane.salon.models.payroll.entity.PayrollAdjustmentType.VALE, new BigDecimal("100"), null)))
                .isInstanceOf(BadRequestException.class);
    }
}
