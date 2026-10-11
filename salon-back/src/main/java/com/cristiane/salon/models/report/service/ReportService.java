package com.cristiane.salon.models.report.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.entity.AppointmentProductItem;
import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.businesssettings.service.SalonBusinessSettingsService;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.cashflow.repository.CashFlowRepository;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.fixedexpense.repository.FixedExpenseRepository;
import com.cristiane.salon.models.payroll.dto.EmployeePayrollData;
import com.cristiane.salon.models.payroll.dto.PayrollBreakdown;
import com.cristiane.salon.models.payroll.dto.PayrollDtos;
import com.cristiane.salon.models.payroll.entity.PayrollPayment;
import com.cristiane.salon.models.payroll.entity.PayrollSalaryPayment;
import com.cristiane.salon.models.payroll.repository.PayrollSalaryPaymentRepository;
import com.cristiane.salon.models.payroll.repository.PayrollPaymentRepository;
import com.cristiane.salon.models.payroll.service.PaymentCoverage;
import com.cristiane.salon.models.payroll.dto.WorkDayEntry;
import com.cristiane.salon.models.payroll.entity.EmployeeWorkDay;
import com.cristiane.salon.models.payroll.entity.PayrollAdjustment;
import com.cristiane.salon.models.payroll.entity.PayrollAdjustmentType;
import com.cristiane.salon.models.payroll.repository.EmployeeWorkDayRepository;
import com.cristiane.salon.models.payroll.repository.PayrollAdjustmentRepository;
import com.cristiane.salon.models.report.dto.AppointmentFinancialResponse;
import com.cristiane.salon.models.report.dto.AppointmentProfitOverrideRequest;
import com.cristiane.salon.models.report.dto.AppointmentProfitResponse;
import com.cristiane.salon.models.report.dto.AppointmentReportResponse;
import com.cristiane.salon.models.report.dto.EmployeeFinanceResponse;
import com.cristiane.salon.models.report.dto.FinancialReportResponse;
import com.cristiane.salon.models.report.dto.ServicePricingAnalysisResponse;
import com.cristiane.salon.models.report.dto.ServicePricingItemResponse;
import com.cristiane.salon.models.service.entity.SalonService;
import com.cristiane.salon.models.service.entity.SalonServiceProductUsage;
import com.cristiane.salon.models.service.repository.SalonServiceProductUsageRepository;
import com.cristiane.salon.models.staff.entity.StaffProfile;
import com.cristiane.salon.models.staff.repository.StaffProfileRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CashFlowRepository cashFlowRepository;
    private final AppointmentRepository appointmentRepository;
    private final EmployeeRepository employeeRepository;
    private final SalonServiceProductUsageRepository serviceProductUsageRepository;
    private final FixedExpenseRepository fixedExpenseRepository;
    private final SalonBusinessSettingsService businessSettingsService;
    private final EmployeeWorkDayRepository workDayRepository;
    private final PayrollAdjustmentRepository adjustmentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final PayrollPaymentRepository paymentRepository;
    private final PayrollSalaryPaymentRepository salaryPaymentRepository;
    private final SalonClock salonClock;

    /** Padrão de todos os filtros de período: o mês corrente inteiro (do dia 1 ao último dia). */
    private LocalDate defaultFrom() {
        return salonClock.today().withDayOfMonth(1);
    }

    private LocalDate defaultTo() {
        return salonClock.today().with(TemporalAdjusters.lastDayOfMonth());
    }

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

    /**
     * Lucro/prejuízo deste atendimento específico: quanto foi cobrado menos o custo dos
     * produtos (consumidos na receita do serviço + vendidos) e a comissão da profissional.
     * Não considera rateio de gastos fixos — isso só entra na análise agregada por serviço.
     * Valores digitados à mão para este atendimento (ver {@link #updateAppointmentProfitOverride})
     * vencem o cálculo automático.
     */
    @Transactional(readOnly = true)
    public AppointmentProfitResponse getAppointmentProfit(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        return buildProfit(appointment);
    }

    @Transactional
    public AppointmentProfitResponse updateAppointmentProfitOverride(Long appointmentId,
                                                                    AppointmentProfitOverrideRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        appointment.setOverrideRecipeCost(request.serviceRecipeCost());
        appointment.setOverrideProductsCost(request.productsSoldCost());
        appointment.setOverrideServiceCommission(request.serviceCommissionCost());
        appointment.setOverrideProductCommission(request.productCommissionCost());
        boolean anyOverride = request.serviceRecipeCost() != null || request.productsSoldCost() != null
                || request.serviceCommissionCost() != null || request.productCommissionCost() != null;
        String note = request.note() != null && !request.note().isBlank() ? request.note().trim() : null;
        appointment.setProfitOverrideNote(anyOverride ? note : null);
        return buildProfit(appointmentRepository.save(appointment));
    }

    private AppointmentProfitResponse buildProfit(Appointment appointment) {
        return AppointmentProfitResponse.of(
                appointment.getId(), appointment.getGrandTotal(),
                autoRecipeCost(appointment), appointment.getOverrideRecipeCost(),
                autoProductsSoldCost(appointment), appointment.getOverrideProductsCost(),
                computeServiceCommission(appointment.getEmployee(), appointment.getServices()),
                appointment.getOverrideServiceCommission(),
                computeProductCommission(appointment), appointment.getOverrideProductCommission(),
                appointment.getProfitOverrideNote());
    }

    // --- Valores de um atendimento, já considerando o que foi digitado à mão -------------------

    /** Comissão de serviço que a profissional recebe por este atendimento. */
    public BigDecimal serviceCommissionOf(Appointment appointment) {
        return appointment.getOverrideServiceCommission() != null
                ? appointment.getOverrideServiceCommission()
                : computeServiceCommission(appointment.getEmployee(), appointment.getServices());
    }

    /** Comissão da vendedora numa venda avulsa (Fluxo de Caixa). */
    public BigDecimal saleCommissionOf(CashFlow sale) {
        BigDecimal commission = sale.effectiveCommission(businessSettingsService.getProductCommissionPercent());
        return commission != null ? commission : BigDecimal.ZERO;
    }

    /** Comissão sobre produtos vendidos dentro deste atendimento. */
    public BigDecimal productCommissionOf(Appointment appointment) {
        return appointment.getOverrideProductCommission() != null
                ? appointment.getOverrideProductCommission()
                : computeProductCommission(appointment);
    }

    /** Custo da receita (produtos consumidos nos serviços): congelado no atendimento (ver V72);
     *  linha antiga sem snapshot cai no cálculo ao vivo a partir da receita atual do serviço. */
    private BigDecimal autoRecipeCost(Appointment appointment) {
        return appointment.getServices().stream()
                .map(this::recipeCostOfItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal recipeCostOfItem(AppointmentServiceItem item) {
        return item.getSnapshotRecipeCost() != null
                ? item.getSnapshotRecipeCost()
                : liveRecipeCost(item.getSalonService().getId());
    }

    private BigDecimal autoProductsSoldCost(Appointment appointment) {
        return appointment.getProducts().stream()
                .map(item -> {
                    BigDecimal costPrice = item.getEffectiveCostPrice();
                    if (costPrice == null) return BigDecimal.ZERO;
                    return costPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Comissão de serviço: soma, pra cada serviço realizado, {@code preço efetivo × % do
     * próprio SalonService} — só se aplica a quem recebe comissão de serviço (Comissionada,
     * Fixo+Comissionada, Diária+Comissão).
     */
    private BigDecimal computeServiceCommission(Employee employee, List<AppointmentServiceItem> items) {
        if (employee == null || employee.getRemunerationType() == null
                || !employee.getRemunerationType().paysServiceCommission()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (AppointmentServiceItem item : items) {
            BigDecimal pct = item.getEffectiveCommissionPercent();
            if (pct == null) continue;
            BigDecimal price = item.getEffectivePrice() != null ? item.getEffectivePrice() : BigDecimal.ZERO;
            total = total.add(price.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        }
        return total;
    }

    /**
     * Comissão de produto: % de comissão de produto sobre produtos vendidos — vale pra QUALQUER
     * tipo de remuneração, inclusive Salário Fixo (venda de produto é incentivo, exceção
     * deliberada). Usa o % congelado no agendamento (ver V72); agendamento antigo sem snapshot
     * cai no % atual de {@link SalonBusinessSettingsService}.
     */
    private BigDecimal computeProductCommission(Appointment appointment) {
        // Sem % congelado (ou 0%, ex.: atendimento feito antes de configurar a comissão) usa o %
        // atual — senão o produto vendido nunca entraria na folha da profissional.
        BigDecimal snapshot = appointment.getSnapshotProductCommissionPercent();
        BigDecimal pct = snapshot != null && snapshot.signum() > 0
                ? snapshot
                : businessSettingsService.getProductCommissionPercent();
        if (pct == null || pct.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (AppointmentProductItem item : appointment.getProducts()) {
            BigDecimal price = item.getEffectiveTotalPrice() != null ? item.getEffectiveTotalPrice() : BigDecimal.ZERO;
            total = total.add(price.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        }
        return total;
    }

    /** Custo da receita atual do serviço (fallback para agendamentos sem snapshot). */
    private BigDecimal liveRecipeCost(Long salonServiceId) {
        return serviceProductUsageRepository.findBySalonServiceId(salonServiceId).stream()
                .map(SalonServiceProductUsage::getEstimatedCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Análise agregada de preço por TIPO de serviço do catálogo, com rateio dos gastos fixos do
     * período — diferente de {@link #getAppointmentProfit(Long)}, que é o lucro de um atendimento
     * isolado sem rateio. Responde "tô cobrando certo por esse serviço ou preciso reajustar?":
     * soma receita, custo de produtos (receita do serviço) e comissão de cada execução DONE do
     * serviço no período, e distribui os gastos fixos proporcionalmente à receita de cada
     * serviço (quem fatura mais absorve mais gasto fixo). Só entram serviços de fato realizados
     * no período — não lista o catálogo inteiro com zeros.
     */
    @Transactional(readOnly = true)
    public ServicePricingAnalysisResponse generateServicePricingAnalysis(LocalDate from, LocalDate to) {
        DateRangeValidator.validate(from, to);
        if (from == null) from = defaultFrom();
        if (to == null) to = defaultTo();

        List<Appointment> doneAppointments = findDoneAppointmentsInPeriod(from, to);

        BigDecimal totalFixedExpenses = fixedExpenseRepository.sumAmountByDateBetween(from, to);

        Map<Long, ServiceAccumulator> accumulators = new java.util.LinkedHashMap<>();

        for (Appointment appointment : doneAppointments) {
            Employee employee = appointment.getEmployee();
            BigDecimal appointmentServicesTotal = appointment.getTotalEffectivePrice();
            int serviceCount = appointment.getServices().size();
            for (AppointmentServiceItem item : appointment.getServices()) {
                SalonService service = item.getSalonService();
                BigDecimal effectivePrice = item.getEffectivePrice() != null ? item.getEffectivePrice() : BigDecimal.ZERO;

                ServiceAccumulator acc = accumulators.computeIfAbsent(service.getId(),
                        id -> new ServiceAccumulator(service));
                acc.count++;
                acc.revenue = acc.revenue.add(effectivePrice);

                // Valor manual do atendimento (ver V86) é do atendimento inteiro — distribui entre
                // os serviços dele proporcionalmente ao preço de cada um.
                BigDecimal recipeCost = appointment.getOverrideRecipeCost() != null
                        ? shareOf(appointment.getOverrideRecipeCost(), effectivePrice, appointmentServicesTotal, serviceCount)
                        : recipeCostOfItem(item);
                acc.recipeCost = acc.recipeCost.add(recipeCost);

                BigDecimal commission = appointment.getOverrideServiceCommission() != null
                        ? shareOf(appointment.getOverrideServiceCommission(), effectivePrice, appointmentServicesTotal, serviceCount)
                        : computeServiceCommission(employee, List.of(item));
                acc.commission = acc.commission.add(commission);
            }
        }

        BigDecimal totalRevenueAllServices = accumulators.values().stream()
                .map(acc -> acc.revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ServicePricingItemResponse> items = new ArrayList<>();
        for (ServiceAccumulator acc : accumulators.values()) {
            BigDecimal fixedExpenseShare = totalRevenueAllServices.signum() > 0
                    ? totalFixedExpenses.multiply(acc.revenue)
                            .divide(totalRevenueAllServices, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            items.add(ServicePricingItemResponse.of(
                    acc.service.getId(), acc.service.getName(), acc.service.getPrice(), acc.count,
                    acc.revenue, acc.recipeCost, acc.commission, fixedExpenseShare));
        }

        // Pior margem primeiro — é o que a Cristiane mais precisa ver de cara.
        items.sort(java.util.Comparator.comparing(ServicePricingItemResponse::netProfit));

        String period = from + " a " + to;
        return new ServicePricingAnalysisResponse(items, totalFixedExpenses, period);
    }

    /** Fatia de {@code total} que cabe a um item que vale {@code part} de {@code whole}; com
     *  {@code whole} zero divide igualmente entre os {@code count} itens. */
    private static BigDecimal shareOf(BigDecimal total, BigDecimal part, BigDecimal whole, int count) {
        if (whole != null && whole.signum() > 0) {
            return total.multiply(part).divide(whole, 2, RoundingMode.HALF_UP);
        }
        return count > 0 ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : total;
    }

    /** Acumulador mutável de uma linha da análise por serviço enquanto os agendamentos são percorridos. */
    private static final class ServiceAccumulator {
        final SalonService service;
        long count = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal recipeCost = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;

        ServiceAccumulator(SalonService service) {
            this.service = service;
        }
    }

    @WithSpan("gerar-relatorio-financeiro")
    @Transactional(readOnly = true)
    public FinancialReportResponse generateFinancialReport(
            @SpanAttribute("relatorio.data_inicio") LocalDate from,
            @SpanAttribute("relatorio.data_fim") LocalDate to) {
        DateRangeValidator.validate(from, to);
        if (from == null) from = defaultFrom();
        if (to == null) to = defaultTo();

        List<CashFlow> cashFlows = cashFlowRepository.findByDateBetween(from, to);

        BigDecimal income = cashFlows.stream()
                .filter(cf -> cf.getType() == CashFlowType.INCOME)
                .map(CashFlow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expense = cashFlows.stream()
                .filter(cf -> cf.getType() == CashFlowType.EXPENSE)
                .map(CashFlow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Gastos fixos (aluguel, água, luz, etc.) são lançados numa tela dedicada, separada do
        // Fluxo de Caixa — mas ainda assim são gasto real do salão, então entram no lucro líquido.
        BigDecimal totalFixedExpenses = fixedExpenseRepository.sumAmountByDateBetween(from, to);

        List<EmployeePayrollData> payroll = computePayroll(from, to);
        List<PayrollPayment> allPayments = paymentRepository.findAllByOrderByPeriodStartAsc();
        Map<Long, List<PayrollPayment>> paymentsByEmployee = allPayments.stream()
                .collect(Collectors.groupingBy(PayrollPayment::getEmployeeId));
        Map<Long, LocalDate> paidAtById = allPayments.stream()
                .collect(Collectors.toMap(PayrollPayment::getId, PayrollPayment::getPaidAt, (a, b) -> a));
        Map<Long, List<PayrollSalaryPayment>> salariesByEmployee = salaryPaymentRepository.findAll().stream()
                .collect(Collectors.groupingBy(PayrollSalaryPayment::getEmployeeId));
        List<EmployeeFinanceResponse> employeeFinanceDetails = new ArrayList<>();

        // "Salários" aqui é tudo que não é comissão: salário fixo proporcional, diárias e os
        // acréscimos/descontos lançados na folha. Vale não entra (já é parte desse custo).
        BigDecimal totalSalaryPaid = BigDecimal.ZERO;
        BigDecimal totalCommissionPaid = BigDecimal.ZERO;

        for (EmployeePayrollData data : payroll) {
            Employee employee = data.employee();
            PayrollBreakdown breakdown = data.breakdown();
            totalSalaryPaid = totalSalaryPaid.add(breakdown.baseAndExtras());
            totalCommissionPaid = totalCommissionPaid.add(breakdown.totalCommission());

            RemunerationType empType = employee.getRemunerationType();
            boolean showDays = (empType != null && empType.isDaily()) || breakdown.workDaysCount() > 0;
            employeeFinanceDetails.add(new EmployeeFinanceResponse(
                    employee.getId(),
                    employee.getUser().getName(),
                    empType != null ? empType.name() : null,
                    employee.getRemunerationValue(),
                    data.doneAppointments().size(),
                    sumServices(data.doneAppointments()),
                    sumProducts(data.doneAppointments()),
                    breakdown.salonCost(),
                    showDays ? breakdown.workDaysCount() : null,
                    breakdown.netPayable(),
                    coverage(PaymentCoverage.paidRanges(paymentsByEmployee.getOrDefault(employee.getId(), List.of()), from, to)),
                    coverage(pendingDayRanges(employee, PaymentCoverage.unpaidRanges(
                            paymentsByEmployee.getOrDefault(employee.getId(), List.of()), from, to), from, to, breakdown)),
                    salaryMonthsOf(employee, salariesByEmployee.getOrDefault(employee.getId(), List.of()), paidAtById, from, to)
            ));
        }

        BigDecimal netProfit = income.subtract(expense).subtract(totalSalaryPaid)
                .subtract(totalCommissionPaid).subtract(totalFixedExpenses);
        String period = from + " a " + to;

        Span.current().setAttribute("relatorio.lucro_liquido", netProfit.doubleValue());
        Span.current().setAttribute("relatorio.funcionarias.total", payroll.size());

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

        return new FinancialReportResponse(income, expense, totalSalaryPaid, totalCommissionPaid,
                totalFixedExpenses, netProfit, employeeFinanceDetails, period);
    }

    /** Só as faixas sem pagamento que têm algo a pagar (comissão, diária, lançamento) — salário
     *  é mensal e aparece à parte. */
    private List<PaymentCoverage.Range> pendingDayRanges(Employee employee, List<PaymentCoverage.Range> gaps,
                                                         LocalDate from, LocalDate to, PayrollBreakdown whole) {
        List<PaymentCoverage.Range> pending = new ArrayList<>();
        for (PaymentCoverage.Range gap : gaps) {
            PayrollBreakdown b = gap.start().equals(from) && gap.end().equals(to)
                    ? whole
                    : computeEmployeePayroll(employee, gap.start(), gap.end()).breakdown();
            if (b.netPayable().subtract(b.fixedSalary()).signum() != 0) {
                pending.add(gap);
            }
        }
        return pending;
    }

    /** Salário fixo de cada mês tocado por [from, to]: pago (com a data) ou pendente. */
    private List<PayrollDtos.SalaryMonthStatus> salaryMonthsOf(Employee employee, List<PayrollSalaryPayment> paid,
                                                               Map<Long, LocalDate> paidAtById,
                                                               LocalDate from, LocalDate to) {
        Map<LocalDate, PayrollSalaryPayment> byMonth = paid.stream()
                .collect(Collectors.toMap(PayrollSalaryPayment::getSalaryMonth, s -> s, (a, b) -> a));
        List<PayrollDtos.SalaryMonthStatus> result = new ArrayList<>();
        for (java.time.YearMonth m = java.time.YearMonth.from(from); !m.isAfter(java.time.YearMonth.from(to)); m = m.plusMonths(1)) {
            PayrollSalaryPayment line = byMonth.get(m.atDay(1));
            if (line != null) {
                result.add(new PayrollDtos.SalaryMonthStatus(m.atDay(1), line.getAmount(), true,
                        paidAtById.get(line.getPaymentId())));
            } else {
                BigDecimal amount = monthlySalary(employee, m);
                if (amount.signum() > 0) {
                    result.add(new PayrollDtos.SalaryMonthStatus(m.atDay(1), amount, false, null));
                }
            }
        }
        return result;
    }

    private static List<PayrollDtos.CoverageRange> coverage(List<PaymentCoverage.Range> ranges) {
        return ranges.stream().map(r -> new PayrollDtos.CoverageRange(r.start(), r.end(), r.amount())).toList();
    }

    private static BigDecimal sumServices(List<Appointment> appointments) {
        return appointments.stream()
                .map(a -> a.getTotalEffectivePrice() != null ? a.getTotalEffectivePrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumProducts(List<Appointment> appointments) {
        return appointments.stream()
                .map(Appointment::getTotalProductsPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // --- Folha de pagamento ---------------------------------------------------------------------

    /**
     * Folha de todas as colaboradoras no período [from, to]. Fonte única do cálculo de
     * remuneração — usada pelo relatório financeiro (custo no lucro) e pela tela Folha de
     * Pagamento. Entra quem está ativa, e também quem foi desativada mas teve movimento no
     * período (atendimento, venda, diária ou lançamento), pra nada ficar sem pagar.
     */
    @Transactional(readOnly = true)
    public List<EmployeePayrollData> computePayroll(LocalDate from, LocalDate to) {
        List<Appointment> done = findDoneAppointmentsInPeriod(from, to);
        List<CashFlow> sales = cashFlowRepository.findSalesWithEmployeeBetween(from, to);
        List<EmployeeWorkDay> workDays = workDayRepository.findByWorkDateBetweenOrderByWorkDateAsc(from, to);
        List<PayrollAdjustment> adjustments = adjustmentRepository.findByDateBetweenOrderByDateAsc(from, to);

        List<EmployeePayrollData> result = new ArrayList<>();
        for (Employee employee : employeeRepository.findAll()) {
            Long id = employee.getId();
            List<Appointment> empDone = done.stream()
                    .filter(a -> a.getEmployee() != null && id.equals(a.getEmployee().getId()))
                    .collect(Collectors.toList());
            List<CashFlow> empSales = sales.stream()
                    .filter(c -> c.getEmployee() != null && id.equals(c.getEmployee().getId()))
                    .collect(Collectors.toList());
            List<EmployeeWorkDay> empDays = workDays.stream()
                    .filter(d -> id.equals(d.getEmployeeId()))
                    .collect(Collectors.toList());
            List<PayrollAdjustment> empAdjustments = adjustments.stream()
                    .filter(a -> id.equals(a.getEmployeeId()))
                    .collect(Collectors.toList());

            boolean active = employee.getUser() == null || Boolean.TRUE.equals(employee.getUser().getActive());
            boolean hasMovement = !empDone.isEmpty() || !empSales.isEmpty()
                    || !empDays.isEmpty() || !empAdjustments.isEmpty();
            if (!active && !hasMovement) {
                continue;
            }
            result.add(buildPayroll(employee, active, from, to, empDone, empSales, empDays, empAdjustments));
        }
        return result;
    }

    /** Folha de uma colaboradora específica no período. */
    @Transactional(readOnly = true)
    public EmployeePayrollData computeEmployeePayroll(Employee employee, LocalDate from, LocalDate to) {
        Long id = employee.getId();
        List<Appointment> empDone = findDoneAppointmentsInPeriod(from, to).stream()
                .filter(a -> a.getEmployee() != null && id.equals(a.getEmployee().getId()))
                .collect(Collectors.toList());
        List<CashFlow> empSales = cashFlowRepository.findSalesWithEmployeeBetween(from, to).stream()
                .filter(c -> c.getEmployee() != null && id.equals(c.getEmployee().getId()))
                .collect(Collectors.toList());
        List<EmployeeWorkDay> empDays = workDayRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(id, from, to);
        List<PayrollAdjustment> empAdjustments = adjustmentRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(id, from, to);
        boolean active = employee.getUser() == null || Boolean.TRUE.equals(employee.getUser().getActive());
        return buildPayroll(employee, active, from, to, empDone, empSales, empDays, empAdjustments);
    }

    /**
     * Calcula o pagamento de uma colaboradora no período: salário fixo proporcional
     * (Salário Fixo/Fixo+Comissionado) + diárias (ver {@link #resolveWorkDays}) + comissão de serviço + comissão de
     * produto (em atendimento e em venda avulsa) + acréscimos − descontos. Span manual: fica
     * aninhado dentro do span de quem chamou, um por funcionária.
     */
    @WithSpan("calcular-pagamento-funcionaria")
    private EmployeePayrollData buildPayroll(Employee employee, boolean active, LocalDate from, LocalDate to,
                                             List<Appointment> empDone, List<CashFlow> empSales,
                                             List<EmployeeWorkDay> empDays, List<PayrollAdjustment> empAdjustments) {
        Span span = Span.current();
        span.setAttribute("funcionaria.id", employee.getId());
        RemunerationType type = employee.getRemunerationType();
        span.setAttribute("funcionaria.tipo_remuneracao", type != null ? type.name() : "N/A");

        // Salário fixo só para quem está ativa: desligada não continua recebendo salário pelo
        // resto do mês — o que faltar acertar entra como lançamento avulso.
        BigDecimal fixedSalary = BigDecimal.ZERO;
        if (active && type != null && type.hasFixedSalary() && employee.getRemunerationValue() != null) {
            LocalDate start = from;
            LocalDate hiredAt = hiredAtOf(employee);
            if (hiredAt != null && hiredAt.isAfter(start)) {
                start = hiredAt;
            }
            fixedSalary = proratedMonthlySalary(employee.getRemunerationValue(), start, to);
        }

        List<WorkDayEntry> workDays = new ArrayList<>();
        List<LocalDate> unmarkedAppointmentDates = new ArrayList<>();
        resolveWorkDays(employee, from, to, empDone, empDays, workDays, unmarkedAppointmentDates);
        BigDecimal dailyTotal = workDays.stream()
                .map(WorkDayEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal serviceCommission = BigDecimal.ZERO;
        BigDecimal appointmentProductCommission = BigDecimal.ZERO;
        for (Appointment appointment : empDone) {
            serviceCommission = serviceCommission.add(serviceCommissionOf(appointment));
            appointmentProductCommission = appointmentProductCommission.add(productCommissionOf(appointment));
        }

        BigDecimal currentPct = empSales.isEmpty() ? null : businessSettingsService.getProductCommissionPercent();
        BigDecimal saleProductCommission = empSales.stream()
                .map(c -> c.effectiveCommission(currentPct))
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal additions = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal advances = BigDecimal.ZERO;
        for (PayrollAdjustment adjustment : empAdjustments) {
            if (adjustment.getType().isAddition()) {
                additions = additions.add(adjustment.getAmount());
            } else if (adjustment.getType() == PayrollAdjustmentType.VALE) {
                advances = advances.add(adjustment.getAmount());
            } else {
                discounts = discounts.add(adjustment.getAmount());
            }
        }

        PayrollBreakdown breakdown = new PayrollBreakdown(fixedSalary, workDays.size(), dailyTotal,
                serviceCommission, appointmentProductCommission, saleProductCommission,
                additions, discounts, advances);
        span.setAttribute("funcionaria.pagamento_calculado", breakdown.netPayable().doubleValue());
        return new EmployeePayrollData(employee, active, empDone, empSales, workDays,
                unmarkedAppointmentDates, empAdjustments, breakdown);
    }

    /**
     * Diárias que contam no período. Pra diarista, todo dia com atendimento concluído já conta
     * sozinho com a diária padrão — ninguém precisa lembrar de marcar. Os registros salvos são as
     * exceções: dia trabalhado sem atendimento, dia com valor próprio (e justificativa), ou dia
     * com atendimento desmarcado de propósito ({@code removed}), que então não conta.
     *
     * @param workDays                 saída: diárias que contam, ordenadas por data
     * @param unmarkedAppointmentDates saída: dias com atendimento que foram desmarcados
     */
    private void resolveWorkDays(Employee employee, LocalDate from, LocalDate to, List<Appointment> empDone,
                                 List<EmployeeWorkDay> records, List<WorkDayEntry> workDays,
                                 List<LocalDate> unmarkedAppointmentDates) {
        Map<LocalDate, EmployeeWorkDay> byDate = new java.util.HashMap<>();
        for (EmployeeWorkDay record : records) {
            byDate.put(record.getWorkDate(), record);
            if (!record.isRemoved()) {
                workDays.add(new WorkDayEntry(record.getId(), record.getWorkDate(), record.getAmount(),
                        record.getJustification(), false));
            }
        }

        RemunerationType type = employee.getRemunerationType();
        if (type != null && type.isDaily()) {
            java.util.Set<LocalDate> appointmentDates = empDone.stream()
                    .map(this::appointmentDate)
                    .filter(d -> d != null && !d.isBefore(from) && !d.isAfter(to))
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
            BigDecimal rate = employee.getRemunerationValue();
            for (LocalDate date : appointmentDates) {
                EmployeeWorkDay record = byDate.get(date);
                if (record != null) {
                    if (record.isRemoved()) unmarkedAppointmentDates.add(date);
                } else if (rate != null) {
                    workDays.add(new WorkDayEntry(null, date, rate, null, true));
                }
            }
        }
        workDays.sort(java.util.Comparator.comparing(WorkDayEntry::date));
    }

    /**
     * Salário mensal proporcional aos dias de [from, to]: cada mês tocado pelo período contribui
     * com {@code salário × dias do período naquele mês / dias do mês}. Mês inteiro = salário
     * exato; meio mês = metade; três meses inteiros = três salários. Antes o salário entrava
     * inteiro uma vez só, qualquer que fosse o período filtrado (uma semana custava um mês).
     */
    public static BigDecimal proratedMonthlySalary(BigDecimal monthly, LocalDate from, LocalDate to) {
        if (monthly == null || from == null || to == null || from.isAfter(to)) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate monthEnd = cursor.with(TemporalAdjusters.lastDayOfMonth());
            LocalDate segmentEnd = monthEnd.isBefore(to) ? monthEnd : to;
            long days = ChronoUnit.DAYS.between(cursor, segmentEnd) + 1;
            int monthLength = cursor.lengthOfMonth();
            total = total.add(days == monthLength
                    ? monthly
                    : monthly.multiply(BigDecimal.valueOf(days))
                            .divide(BigDecimal.valueOf(monthLength), 2, RoundingMode.HALF_UP));
            cursor = segmentEnd.plusDays(1);
        }
        return total;
    }

    /**
     * Salário fixo do mês inteiro, como é pago na folha (o lucro do relatório continua
     * proporcional aos dias filtrados — ver {@link #proratedMonthlySalary}). Primeiro mês de
     * quem foi contratada no meio dele sai proporcional desde a admissão; antes da admissão,
     * desligada ou sem salário fixo, zero.
     */
    public BigDecimal monthlySalary(Employee employee, java.time.YearMonth month) {
        RemunerationType type = employee.getRemunerationType();
        boolean active = employee.getUser() == null || Boolean.TRUE.equals(employee.getUser().getActive());
        if (!active || type == null || !type.hasFixedSalary() || employee.getRemunerationValue() == null) {
            return BigDecimal.ZERO;
        }
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        LocalDate hiredAt = hiredAtOf(employee);
        if (hiredAt != null) {
            if (hiredAt.isAfter(end)) return BigDecimal.ZERO;
            if (hiredAt.isAfter(start)) start = hiredAt;
        }
        return proratedMonthlySalary(employee.getRemunerationValue(), start, end);
    }

    private LocalDate hiredAtOf(Employee employee) {
        if (employee.getUser() == null) return null;
        return staffProfileRepository.findByUserId(employee.getUser().getId())
                .map(StaffProfile::getHiredAt)
                .orElse(null);
    }

    /** Mesma cadeia de fallback dos relatórios: scheduledAt > scheduledDate > preferredDate > createdAt. */
    public LocalDate appointmentDate(Appointment a) {
        if (a.isScheduled()) return a.getEffectiveScheduledDate();
        if (a.getPreferredDate() != null) return a.getPreferredDate();
        return a.getCreatedAt().atZone(salonClock.zone()).toLocalDate();
    }

    @Transactional(readOnly = true)
    public AppointmentReportResponse generateAppointmentReport(LocalDate from, LocalDate to) {
        DateRangeValidator.validate(from, to);
        final LocalDate fromDate = from == null ? defaultFrom() : from;
        final LocalDate toDate = to == null ? defaultTo() : to;

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

    private List<Appointment> findDoneAppointmentsInPeriod(LocalDate from, LocalDate to) {
        return findAppointmentsInPeriod(from, to).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                .collect(Collectors.toList());
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
