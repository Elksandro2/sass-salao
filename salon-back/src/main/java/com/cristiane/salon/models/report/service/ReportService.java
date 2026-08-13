package com.cristiane.salon.models.report.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.ResourceNotFoundException;
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
import com.cristiane.salon.models.report.dto.AppointmentFinancialResponse;
import com.cristiane.salon.models.report.dto.AppointmentReportResponse;
import com.cristiane.salon.models.report.dto.FinancialReportResponse;
import com.cristiane.salon.models.report.dto.EmployeeFinanceResponse;
import com.cristiane.salon.models.report.dto.PayrollReportResponse;
import com.cristiane.salon.utils.DateRangeValidator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final CashFlowRepository cashFlowRepository;
    private final AppointmentRepository appointmentRepository;
    private final EmployeeRepository employeeRepository;
    private final SalonClock salonClock;

    @Transactional(readOnly = true)
    public Page<AppointmentFinancialResponse> getEmployeeFinancialHistory(
            Long employeeId, LocalDate from, LocalDate to, Pageable pageable) {
        DateRangeValidator.validate(from, to);
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Funcionária não encontrada");
        }

        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(LocalTime.MAX) : null;

        return appointmentRepository
                .findByEmployeeIdForFinancialHistory(employeeId, fromDateTime, toDateTime, pageable)
                .map(AppointmentFinancialResponse::fromEntity);
    }

    @WithSpan("gerar-relatorio-financeiro")
    @Transactional(readOnly = true)
    public FinancialReportResponse generateFinancialReport(
            @SpanAttribute("relatorio.data_inicio") LocalDate from,
            @SpanAttribute("relatorio.data_fim") LocalDate to) {
        DateRangeValidator.validate(from, to);
        if (from == null) from = salonClock.today().withDayOfMonth(1);
        if (to == null) to = salonClock.today().plusDays(30);

        List<CashFlow> cashFlows = cashFlowRepository.findByDateBetween(from, to);

        BigDecimal income = cashFlows.stream()
                .filter(cf -> cf.getType() == CashFlowType.INCOME)
                .map(CashFlow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expense = cashFlows.stream()
                .filter(cf -> cf.getType() == CashFlowType.EXPENSE)
                .map(CashFlow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Appointment> doneAppointments = findAppointmentsInPeriod(from, to).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                .collect(Collectors.toList());

        BigDecimal globalDoneAppointmentsValue = doneAppointments.stream()
                .map(a -> a.getTotalEffectivePrice() != null ? a.getTotalEffectivePrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal globalDoneProductsValue = doneAppointments.stream()
                .map(Appointment::getTotalProductsPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Employee> employees = employeeRepository.findAll();
        List<EmployeeFinanceResponse> employeeFinanceDetails = new ArrayList<>();

        BigDecimal totalSalaryPaid = BigDecimal.ZERO;
        BigDecimal totalCommissionPaid = BigDecimal.ZERO;

        for (Employee employee : employees) {
            List<Appointment> empDoneAppointments = doneAppointments.stream()
                    .filter(a -> a.getEmployee().getId().equals(employee.getId()))
                    .collect(Collectors.toList());

            long doneCount = empDoneAppointments.size();
            BigDecimal empDoneValue = empDoneAppointments.stream()
                    .map(a -> a.getTotalEffectivePrice() != null ? a.getTotalEffectivePrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal empDoneProductsValue = empDoneAppointments.stream()
                    .map(Appointment::getTotalProductsPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            PayoutBreakdown breakdown = calcularPagamentoFuncionaria(employee, empDoneValue, globalDoneAppointmentsValue,
                    empDoneProductsValue, globalDoneProductsValue);
            totalSalaryPaid = totalSalaryPaid.add(breakdown.salaryPart());
            totalCommissionPaid = totalCommissionPaid.add(breakdown.commissionPart());

            employeeFinanceDetails.add(new EmployeeFinanceResponse(
                    employee.getId(),
                    employee.getUser().getName(),
                    employee.getRemunerationType() != null ? employee.getRemunerationType().name() : null,
                    employee.getRemunerationValue(),
                    employee.getCommissionScope() != null ? employee.getCommissionScope().name() : null,
                    employee.getCommissionValue(),
                    employee.getProductCommissionValue(),
                    doneCount,
                    empDoneValue,
                    empDoneProductsValue,
                    breakdown.totalPayout()
            ));
        }

        BigDecimal netProfit = income.subtract(expense).subtract(totalSalaryPaid).subtract(totalCommissionPaid);
        String period = from + " a " + to;

        Span.current().setAttribute("relatorio.lucro_liquido", netProfit.doubleValue());
        Span.current().setAttribute("relatorio.funcionarias.total", employees.size());

        // Log estruturado: os campos do MDC são promovidos a atributos do log no Loki
        // (via ponte MDC do agente OTel), então dá pra filtrar/agrupar por eles no LogQL
        // sem precisar fazer parsing de texto livre.
        MDC.put("relatorio.periodo", period);
        MDC.put("relatorio.lucro_liquido", netProfit.toPlainString());
        try {
            log.info("Relatório financeiro gerado");
        } finally {
            MDC.remove("relatorio.periodo");
            MDC.remove("relatorio.lucro_liquido");
        }

        return new FinancialReportResponse(income, expense, totalSalaryPaid, totalCommissionPaid, netProfit, employeeFinanceDetails, period);
    }

    /**
     * Calcula o pagamento de uma funcionária no período do relatório (salário fixo,
     * comissão individual/global, ou a combinação dos dois). Span manual: fica aninhado
     * dentro de "gerar-relatorio-financeiro" no trace, um span por funcionária.
     */
    @WithSpan("calcular-pagamento-funcionaria")
    private PayoutBreakdown calcularPagamentoFuncionaria(
            Employee employee, BigDecimal empDoneValue, BigDecimal globalDoneAppointmentsValue,
            BigDecimal empDoneProductsValue, BigDecimal globalDoneProductsValue) {

        Span span = Span.current();
        span.setAttribute("funcionaria.id", employee.getId());
        span.setAttribute("funcionaria.tipo_remuneracao",
                employee.getRemunerationType() != null ? employee.getRemunerationType().name() : "N/A");

        BigDecimal salaryPart = BigDecimal.ZERO;
        BigDecimal commissionPart = BigDecimal.ZERO;
        BigDecimal payout = BigDecimal.ZERO;

        if (employee.getRemunerationType() == RemunerationType.SALARIO_FIXO) {
            payout = employee.getRemunerationValue() != null ? employee.getRemunerationValue() : BigDecimal.ZERO;
            salaryPart = payout;
        } else if (employee.getRemunerationType() == RemunerationType.COMISSIONADO) {
            BigDecimal pct = employee.getRemunerationValue() != null ? employee.getRemunerationValue() : BigDecimal.ZERO;
            if (employee.getCommissionScope() == CommissionScope.INDIVIDUAL) {
                payout = empDoneValue.multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            } else if (employee.getCommissionScope() == CommissionScope.GLOBAL) {
                payout = globalDoneAppointmentsValue.multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            }
            BigDecimal productCommission = calcularComissaoProdutos(employee, empDoneProductsValue, globalDoneProductsValue);
            payout = payout.add(productCommission);
            commissionPart = payout;
        } else if (employee.getRemunerationType() == RemunerationType.FIXO_E_COMISSIONADO) {
            BigDecimal salary = employee.getRemunerationValue() != null ? employee.getRemunerationValue() : BigDecimal.ZERO;
            BigDecimal pct = employee.getCommissionValue() != null ? employee.getCommissionValue() : BigDecimal.ZERO;
            BigDecimal commission = BigDecimal.ZERO;
            if (employee.getCommissionScope() == CommissionScope.INDIVIDUAL) {
                commission = empDoneValue.multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            } else if (employee.getCommissionScope() == CommissionScope.GLOBAL) {
                commission = globalDoneAppointmentsValue.multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            }
            commission = commission.add(calcularComissaoProdutos(employee, empDoneProductsValue, globalDoneProductsValue));
            salaryPart = salary;
            commissionPart = commission;
            payout = salary.add(commission);
        }

        span.setAttribute("funcionaria.pagamento_calculado", payout.doubleValue());
        return new PayoutBreakdown(salaryPart, commissionPart, payout);
    }

    /**
     * Comissão única (%) sobre produtos vendidos, independente da comissão de serviços — usa o
     * mesmo {@link CommissionScope} da funcionária. Só se aplica a quem é comissionada
     * (SALARIO_FIXO não chega a chamar este método) e tem o percentual configurado.
     */
    private BigDecimal calcularComissaoProdutos(
            Employee employee, BigDecimal empDoneProductsValue, BigDecimal globalDoneProductsValue) {
        if (employee.getProductCommissionValue() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal pct = employee.getProductCommissionValue();
        BigDecimal base = employee.getCommissionScope() == CommissionScope.GLOBAL
                ? globalDoneProductsValue
                : empDoneProductsValue;
        return base.multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }

    private record PayoutBreakdown(BigDecimal salaryPart, BigDecimal commissionPart, BigDecimal totalPayout) {}

    @Transactional(readOnly = true)
    public AppointmentReportResponse generateAppointmentReport(LocalDate from, LocalDate to) {
        DateRangeValidator.validate(from, to);
        final LocalDate fromDate = from == null ? salonClock.today().withDayOfMonth(1) : from;
        final LocalDate toDate = to == null ? salonClock.today().plusDays(30) : to;

        List<Appointment> appointments = findAppointmentsInPeriod(fromDate, toDate);

        long pending = appointments.stream().filter(a ->
                a.getStatus() == AppointmentStatus.PENDING || a.getStatus() == AppointmentStatus.REQUESTED).count();
        long confirmed = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED).count();
        long done = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.DONE).count();
        long cancelled = appointments.stream().filter(a ->
                a.getStatus() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.DECLINED).count();

        Map<String, Long> byEmployee = appointments.stream()
                .collect(Collectors.groupingBy(a -> a.getEmployee().getUser().getName(), Collectors.counting()));

        Map<String, Long> byService = appointments.stream()
                .flatMap(a -> a.getServices().stream())
                .collect(Collectors.groupingBy(item -> item.getSalonService().getName(), Collectors.counting()));

        String period = fromDate + " a " + toDate;

        return new AppointmentReportResponse(
                appointments.size(),
                pending,
                confirmed,
                done,
                cancelled,
                byEmployee,
                byService,
                period
        );
    }

    @Transactional(readOnly = true)
    public PayrollReportResponse generatePayrollReport(LocalDate from, LocalDate to) {
        DateRangeValidator.validate(from, to);
        if (from == null) from = salonClock.today().withDayOfMonth(1);
        if (to == null) to = salonClock.today().plusDays(30);

        List<Appointment> doneAppointments = findAppointmentsInPeriod(from, to).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                .collect(Collectors.toList());

        BigDecimal globalDoneAppointmentsValue = doneAppointments.stream()
                .map(a -> a.getTotalEffectivePrice() != null ? a.getTotalEffectivePrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal globalDoneProductsValue = doneAppointments.stream()
                .map(Appointment::getTotalProductsPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Employee> employees = employeeRepository.findAll();
        List<PayrollReportResponse.PayrollItem> items = new ArrayList<>();

        for (Employee employee : employees) {
            List<Appointment> empDoneAppointments = doneAppointments.stream()
                    .filter(a -> a.getEmployee().getId().equals(employee.getId()))
                    .collect(Collectors.toList());

            BigDecimal empDoneValue = empDoneAppointments.stream()
                    .map(a -> a.getTotalEffectivePrice() != null ? a.getTotalEffectivePrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal empDoneProductsValue = empDoneAppointments.stream()
                    .map(Appointment::getTotalProductsPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal payout = BigDecimal.ZERO;
            BigDecimal baseAmount = BigDecimal.ZERO;

            if (employee.getRemunerationType() == RemunerationType.SALARIO_FIXO) {
                payout = employee.getRemunerationValue() != null ? employee.getRemunerationValue() : BigDecimal.ZERO;
                baseAmount = BigDecimal.ZERO;
            } else if (employee.getRemunerationType() == RemunerationType.COMISSIONADO) {
                BigDecimal pct = employee.getRemunerationValue() != null ? employee.getRemunerationValue() : BigDecimal.ZERO;
                if (employee.getCommissionScope() == CommissionScope.INDIVIDUAL) {
                    baseAmount = empDoneValue;
                } else if (employee.getCommissionScope() == CommissionScope.GLOBAL) {
                    baseAmount = globalDoneAppointmentsValue;
                }
                payout = baseAmount.multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                payout = payout.add(calcularComissaoProdutos(employee, empDoneProductsValue, globalDoneProductsValue));
            } else if (employee.getRemunerationType() == RemunerationType.FIXO_E_COMISSIONADO) {
                BigDecimal salary = employee.getRemunerationValue() != null ? employee.getRemunerationValue() : BigDecimal.ZERO;
                BigDecimal pct = employee.getCommissionValue() != null ? employee.getCommissionValue() : BigDecimal.ZERO;
                BigDecimal commissionPart = BigDecimal.ZERO;
                if (employee.getCommissionScope() == CommissionScope.INDIVIDUAL) {
                    baseAmount = empDoneValue;
                } else if (employee.getCommissionScope() == CommissionScope.GLOBAL) {
                    baseAmount = globalDoneAppointmentsValue;
                }
                commissionPart = baseAmount.multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                commissionPart = commissionPart.add(calcularComissaoProdutos(employee, empDoneProductsValue, globalDoneProductsValue));
                payout = salary.add(commissionPart);
            }

            items.add(new PayrollReportResponse.PayrollItem(
                    employee.getId(),
                    employee.getUser().getName(),
                    employee.getRemunerationType(),
                    employee.getCommissionScope(),
                    baseAmount,
                    payout
            ));
        }

        String period = from + " a " + to;
        return new PayrollReportResponse(items, period);
    }

    /**
     * Busca agendamentos no período direto no banco (fallback scheduledAt > preferredDate >
     * createdAt), em vez de carregar {@code appointmentRepository.findAll()} inteiro e filtrar
     * em memória — gargalo identificado via OpenTelemetry (ver relatório de observabilidade):
     * essa query crescia sem limite junto com o histórico de agendamentos do salão.
     */
    private List<Appointment> findAppointmentsInPeriod(LocalDate from, LocalDate to) {
        LocalDateTime startOfDay = from.atStartOfDay();
        LocalDateTime endOfDay = to.atTime(LocalTime.MAX);
        // O mesmo intervalo também como instante: "00:00 do dia X no salão" corresponde a um
        // ponto diferente da linha do tempo conforme o fuso, e createdAt só entende instante.
        return appointmentRepository.findAllInPeriod(from, to, startOfDay, endOfDay,
                startOfDay.atZone(salonClock.zone()).toInstant(),
                endOfDay.atZone(salonClock.zone()).toInstant());
    }
}
