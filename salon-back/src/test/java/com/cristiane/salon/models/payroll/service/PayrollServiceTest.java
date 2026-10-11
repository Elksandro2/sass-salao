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
import com.cristiane.salon.models.payroll.repository.EmployeeWorkDayRepository;
import com.cristiane.salon.models.payroll.repository.PayrollAdjustmentRepository;
import com.cristiane.salon.models.payroll.repository.PayrollPaymentRepository;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    @Spy private SalonClock salonClock = new SalonClock(ZoneId.of("America/Recife"));

    @InjectMocks
    private PayrollService payrollService;

    private static final LocalDate APR_1 = LocalDate.of(2026, 4, 1);
    private static final LocalDate APR_30 = LocalDate.of(2026, 4, 30);

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

    private static PayrollBreakdown breakdownPaying(String net) {
        BigDecimal v = new BigDecimal(net);
        return new PayrollBreakdown(v, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static PayrollPayment payment(long id, LocalDate start, LocalDate end, String paid) {
        PayrollPayment p = new PayrollPayment();
        p.setId(id);
        p.setEmployeeId(1L);
        p.setPeriodStart(start);
        p.setPeriodEnd(end);
        p.setAmountPaid(new BigDecimal(paid));
        p.setCalculatedAmount(new BigDecimal(paid));
        p.setPaidAt(end);
        return p;
    }

    @Test
    void mergeIntervals_joinsAdjacentPeriods_andKeepsGapsSeparate() {
        var merged = PayrollService.mergeIntervals(List.of(
                payment(1, APR_1, LocalDate.of(2026, 4, 15), "500"),
                payment(2, LocalDate.of(2026, 4, 16), APR_30, "500"),
                payment(3, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "1000")));

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0)[1]).isEqualTo(APR_30); // "pago até" = fim do primeiro bloco contínuo
    }

    @Test
    void getSummary_marksPeriodPaid_whenPaymentsCoverEveryDay_andComputesPaidUntilForEveryone() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(reportService.computePayroll(APR_1, APR_30)).thenReturn(List.of(
                new EmployeePayrollData(emp, true, List.of(), List.of(), List.of(), List.of(), List.of(), breakdownPaying("1000.00"))));
        when(paymentRepository.findAllByOrderByPeriodStartAsc()).thenReturn(List.of(
                payment(1, APR_1, LocalDate.of(2026, 4, 15), "500"),
                payment(2, LocalDate.of(2026, 4, 16), APR_30, "500")));

        var summary = payrollService.getSummary(APR_1, APR_30);

        var item = summary.items().get(0);
        assertThat(item.periodStatus()).isEqualTo(PayrollDtos.PeriodStatus.PAGO);
        assertThat(item.outstanding()).isEqualByComparingTo("0");
        assertThat(item.paidUntil()).isEqualTo(APR_30);
        assertThat(summary.payrollPaidUntil()).isEqualTo(APR_30);
    }

    @Test
    void getSummary_partialCoverage_leavesTheDifferenceOutstanding() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(reportService.computePayroll(APR_1, APR_30)).thenReturn(List.of(
                new EmployeePayrollData(emp, true, List.of(), List.of(), List.of(), List.of(), List.of(), breakdownPaying("1000.00"))));
        when(paymentRepository.findAllByOrderByPeriodStartAsc()).thenReturn(List.of(
                payment(1, APR_1, LocalDate.of(2026, 4, 15), "500")));

        var item = payrollService.getSummary(APR_1, APR_30).items().get(0);

        assertThat(item.periodStatus()).isEqualTo(PayrollDtos.PeriodStatus.PARCIAL);
        assertThat(item.coveredDays()).isEqualTo(15);
        assertThat(item.outstanding()).isEqualByComparingTo("500.00");
    }

    @Test
    void getSummary_whenSomeoneWasNeverPaid_hasNoGlobalPaidUntil_andNamesWhoIsBehind() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(reportService.computePayroll(APR_1, APR_30)).thenReturn(List.of(
                new EmployeePayrollData(emp, true, List.of(), List.of(), List.of(), List.of(), List.of(), breakdownPaying("1000.00"))));
        when(paymentRepository.findAllByOrderByPeriodStartAsc()).thenReturn(List.of());

        var summary = payrollService.getSummary(APR_1, APR_30);

        assertThat(summary.payrollPaidUntil()).isNull();
        assertThat(summary.behindEmployees()).containsExactly("Maria");
    }

    @Test
    void createPayment_rejectsPeriodOverlappingAnExistingPayment() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(1L)).thenReturn(List.of(
                payment(1, APR_1, APR_30, "1000")));

        var request = new PayrollDtos.PaymentRequest(1L, LocalDate.of(2026, 4, 20), LocalDate.of(2026, 5, 20),
                new BigDecimal("1000"), LocalDate.of(2026, 5, 20), "PIX", null);

        assertThatThrownBy(() -> payrollService.createPayment(request)).isInstanceOf(BadRequestException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_freezesTheCalculatedAmountOfThePeriod() {
        Employee emp = employee(RemunerationType.SALARIO_FIXO, "1000.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(1L)).thenReturn(List.of());
        when(reportService.computeEmployeePayroll(emp, APR_1, APR_30)).thenReturn(
                new EmployeePayrollData(emp, true, List.of(), List.of(), List.of(), List.of(), List.of(), breakdownPaying("980.00")));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            PayrollPayment p = inv.getArgument(0);
            p.setId(5L);
            return p;
        });

        var response = payrollService.createPayment(new PayrollDtos.PaymentRequest(1L, APR_1, APR_30,
                new BigDecimal("1000.00"), APR_30, "pix", null));

        assertThat(response.calculatedAmount()).isEqualByComparingTo("980.00");
        assertThat(response.amountPaid()).isEqualByComparingTo("1000.00");
        assertThat(response.paymentMethod()).isEqualTo("PIX");
    }

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
    void createWorkDay_withDifferentValue_requiresJustification() {
        Employee emp = employee(RemunerationType.DIARISTA, "120.00");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(workDayRepository.findByEmployeeIdAndWorkDate(1L, APR_1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payrollService.createWorkDay(
                new PayrollDtos.WorkDayRequest(1L, APR_1, new BigDecimal("60.00"), "  ")))
                .isInstanceOf(BadRequestException.class);
        verify(workDayRepository, never()).save(any());
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
}
