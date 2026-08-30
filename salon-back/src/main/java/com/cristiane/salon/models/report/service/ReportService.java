package com.cristiane.salon.models.report.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.entity.AppointmentProductItem;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.businesssettings.service.SalonBusinessSettingsService;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.cashflow.repository.CashFlowRepository;
import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.fixedexpense.repository.FixedExpenseRepository;
import com.cristiane.salon.models.report.dto.AppointmentFinancialResponse;
import com.cristiane.salon.models.report.dto.AppointmentProfitResponse;
import com.cristiane.salon.models.report.dto.AppointmentReportResponse;
import com.cristiane.salon.models.report.dto.FinancialReportResponse;
import com.cristiane.salon.models.report.dto.EmployeeFinanceResponse;
import com.cristiane.salon.models.report.dto.PayrollReportResponse;
import com.cristiane.salon.models.report.dto.ServicePricingAnalysisResponse;
import com.cristiane.salon.models.report.dto.ServicePricingItemResponse;
import com.cristiane.salon.models.report.entity.DiaristaWorkedDaysOverride;
import com.cristiane.salon.models.report.repository.DiaristaWorkedDaysOverrideRepository;
import com.cristiane.salon.models.service.entity.SalonService;
import com.cristiane.salon.models.service.entity.SalonServiceProductUsage;
import com.cristiane.salon.models.service.repository.SalonServiceProductUsageRepository;
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
    private final DiaristaWorkedDaysOverrideRepository workedDaysOverrideRepository;
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

    /**
     * Lucro/prejuízo deste atendimento específico: quanto foi cobrado menos o custo dos
     * produtos (consumidos na receita do serviço + vendidos) e a comissão da profissional.
     * Não considera rateio de gastos fixos — isso só entra na análise agregada por serviço.
     */
    @Transactional(readOnly = true)
    public AppointmentProfitResponse getAppointmentProfit(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));

        BigDecimal grossRevenue = appointment.getGrandTotal();

        // Custo de receita congelado no atendimento (ver V72); linha antiga sem snapshot cai no
        // cálculo ao vivo a partir da receita atual do serviço.
        BigDecimal recipeCost = appointment.getServices().stream()
                .map(item -> item.getSnapshotRecipeCost() != null
                        ? item.getSnapshotRecipeCost()
                        : liveRecipeCost(item.getSalonService().getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal productsSoldCost = appointment.getProducts().stream()
                .map(item -> {
                    BigDecimal costPrice = item.getEffectiveCostPrice();
                    if (costPrice == null) return BigDecimal.ZERO;
                    return costPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Employee employee = appointment.getEmployee();
        BigDecimal serviceCommission = computeServiceCommission(employee, appointment.getServices());
        BigDecimal productCommission = computeProductCommission(appointment);

        return AppointmentProfitResponse.of(
                appointment.getId(), grossRevenue, recipeCost, productsSoldCost,
                serviceCommission, productCommission);
    }

    /**
     * Comissão de serviço: soma, pra cada serviço realizado, {@code preço efetivo × % do
     * próprio SalonService} — só se aplica a quem é Comissionada ou Fixo+Comissionada. É exata
     * (não uma estimativa): o % é do serviço, não depende de nenhum contexto além dele mesmo.
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
        BigDecimal pct = appointment.getSnapshotProductCommissionPercent() != null
                ? appointment.getSnapshotProductCommissionPercent()
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
        if (from == null) from = salonClock.today().withDayOfMonth(1);
        if (to == null) to = salonClock.today().plusDays(30);

        List<Appointment> doneAppointments = findAppointmentsInPeriod(from, to).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                .collect(Collectors.toList());

        BigDecimal totalFixedExpenses = fixedExpenseRepository.sumAmountByDateBetween(from, to);

        Map<Long, ServiceAccumulator> accumulators = new java.util.LinkedHashMap<>();

        for (Appointment appointment : doneAppointments) {
            Employee employee = appointment.getEmployee();
            for (AppointmentServiceItem item : appointment.getServices()) {
                SalonService service = item.getSalonService();
                BigDecimal effectivePrice = item.getEffectivePrice() != null ? item.getEffectivePrice() : BigDecimal.ZERO;

                ServiceAccumulator acc = accumulators.computeIfAbsent(service.getId(),
                        id -> new ServiceAccumulator(service));
                acc.count++;
                acc.revenue = acc.revenue.add(effectivePrice);

                // Custo de receita congelado no atendimento (ver V72); linha antiga sem snapshot
                // cai no cálculo pela receita atual do serviço.
                BigDecimal recipeCost = item.getSnapshotRecipeCost() != null
                        ? item.getSnapshotRecipeCost()
                        : liveRecipeCost(service.getId());
                acc.recipeCost = acc.recipeCost.add(recipeCost);

                acc.commission = acc.commission.add(computeServiceCommission(employee, List.of(item)));
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

        // Gastos fixos (aluguel, água, luz, etc.) são lançados numa tela dedicada, separada do
        // Fluxo de Caixa — mas ainda assim são gasto real do salão, então entram no lucro líquido.
        BigDecimal totalFixedExpenses = fixedExpenseRepository.sumAmountByDateBetween(from, to);

        List<Appointment> doneAppointments = findAppointmentsInPeriod(from, to).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                .collect(Collectors.toList());

        Map<Long, Integer> workedDaysOverrides = loadWorkedDaysOverrides(from, to);
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

            RemunerationType empType = employee.getRemunerationType();
            boolean empDaily = empType != null && empType.isDaily();
            WorkedDays worked = empDaily
                    ? resolveWorkedDays(employee.getId(), empDoneAppointments, workedDaysOverrides)
                    : new WorkedDays(0, 0, false);

            PayoutBreakdown breakdown = calcularPagamentoFuncionaria(employee, empDoneAppointments, worked.days());
            totalSalaryPaid = totalSalaryPaid.add(breakdown.salaryPart()).add(breakdown.dailyPart());
            totalCommissionPaid = totalCommissionPaid.add(breakdown.commissionPart());

            employeeFinanceDetails.add(new EmployeeFinanceResponse(
                    employee.getId(),
                    employee.getUser().getName(),
                    empType != null ? empType.name() : null,
                    employee.getRemunerationValue(),
                    doneCount,
                    empDoneValue,
                    empDoneProductsValue,
                    breakdown.totalPayout(),
                    empDaily ? worked.days() : null
            ));
        }

        BigDecimal netProfit = income.subtract(expense).subtract(totalSalaryPaid)
                .subtract(totalCommissionPaid).subtract(totalFixedExpenses);
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

        return new FinancialReportResponse(income, expense, totalSalaryPaid, totalCommissionPaid,
                totalFixedExpenses, netProfit, employeeFinanceDetails, period);
    }

    /**
     * Calcula o pagamento de uma funcionária no período do relatório: salário base (Salário
     * Fixo/Fixo+Comissionado) + comissão de serviço (soma do % de cada serviço realizado, só
     * Comissionada/Fixo+Comissionada) + comissão de produto (% única do salão, vale pra
     * qualquer tipo — inclusive Salário Fixo). Span manual: fica aninhado dentro de
     * "gerar-relatorio-financeiro" no trace, um span por funcionária.
     */
    /**
     * @param daysWorked dias trabalhados no período — só entra na conta para Diarista/Diária+Comissão.
     *                   O relatório financeiro passa 0 (não coleta esse dado); a folha de
     *                   pagamento passa o valor informado na tela.
     */
    @WithSpan("calcular-pagamento-funcionaria")
    private PayoutBreakdown calcularPagamentoFuncionaria(Employee employee, List<Appointment> empDoneAppointments,
                                                        int daysWorked) {
        Span span = Span.current();
        span.setAttribute("funcionaria.id", employee.getId());
        RemunerationType type = employee.getRemunerationType();
        span.setAttribute("funcionaria.tipo_remuneracao", type != null ? type.name() : "N/A");

        BigDecimal value = employee.getRemunerationValue() != null
                ? employee.getRemunerationValue() : BigDecimal.ZERO;

        BigDecimal salaryPart = BigDecimal.ZERO;
        if (type != null && type.hasFixedSalary()) {
            salaryPart = value;
        }

        BigDecimal dailyPart = BigDecimal.ZERO;
        if (type != null && type.isDaily() && daysWorked > 0) {
            dailyPart = value.multiply(BigDecimal.valueOf(daysWorked));
        }

        BigDecimal commissionPart = BigDecimal.ZERO;
        for (Appointment appointment : empDoneAppointments) {
            commissionPart = commissionPart.add(computeServiceCommission(employee, appointment.getServices()));
            commissionPart = commissionPart.add(computeProductCommission(appointment));
        }

        BigDecimal payout = salaryPart.add(dailyPart).add(commissionPart);
        span.setAttribute("funcionaria.pagamento_calculado", payout.doubleValue());
        return new PayoutBreakdown(salaryPart, dailyPart, commissionPart, payout);
    }

    private record PayoutBreakdown(BigDecimal salaryPart, BigDecimal dailyPart, BigDecimal commissionPart,
                                   BigDecimal totalPayout) {}

    /** Dias trabalhados de uma diarista num período: o valor efetivo, a contagem automática e
     *  se o efetivo veio de um ajuste manual salvo. */
    private record WorkedDays(int days, int auto, boolean isOverride) {}

    /**
     * Dias trabalhados de uma diarista no período: por padrão, os dias distintos em que ela foi
     * a profissional de um atendimento concluído; se houver ajuste manual salvo para exatamente
     * este período, ele vence.
     */
    private WorkedDays resolveWorkedDays(Long employeeId, List<Appointment> empDoneAppointments,
                                         Map<Long, Integer> overrides) {
        int auto = (int) empDoneAppointments.stream()
                .map(this::appointmentDate)
                .distinct()
                .count();
        Integer override = overrides.get(employeeId);
        return override != null
                ? new WorkedDays(Math.max(0, override), auto, true)
                : new WorkedDays(auto, auto, false);
    }

    /** Mesma cadeia de fallback dos relatórios: scheduledAt > preferredDate > createdAt. */
    private LocalDate appointmentDate(Appointment a) {
        if (a.getScheduledAt() != null) return a.getScheduledAt().toLocalDate();
        if (a.getPreferredDate() != null) return a.getPreferredDate();
        return a.getCreatedAt().atZone(salonClock.zone()).toLocalDate();
    }

    /** Ajustes manuais de dias trabalhados salvos para exatamente [from, to], por employeeId. */
    private Map<Long, Integer> loadWorkedDaysOverrides(LocalDate from, LocalDate to) {
        return workedDaysOverrideRepository.findByPeriodStartAndPeriodEnd(from, to).stream()
                .collect(Collectors.toMap(DiaristaWorkedDaysOverride::getEmployeeId,
                        DiaristaWorkedDaysOverride::getDaysWorked, (a, b) -> b));
    }

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

        Map<Long, Integer> overrides = loadWorkedDaysOverrides(from, to);
        List<Employee> employees = employeeRepository.findAll();
        List<PayrollReportResponse.PayrollItem> items = new ArrayList<>();

        for (Employee employee : employees) {
            List<Appointment> empDoneAppointments = doneAppointments.stream()
                    .filter(a -> a.getEmployee().getId().equals(employee.getId()))
                    .collect(Collectors.toList());

            BigDecimal empDoneValue = empDoneAppointments.stream()
                    .map(a -> a.getTotalEffectivePrice() != null ? a.getTotalEffectivePrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            RemunerationType type = employee.getRemunerationType();
            boolean daily = type != null && type.isDaily();
            WorkedDays worked = daily
                    ? resolveWorkedDays(employee.getId(), empDoneAppointments, overrides)
                    : new WorkedDays(0, 0, false);

            PayoutBreakdown breakdown = calcularPagamentoFuncionaria(employee, empDoneAppointments, worked.days());

            items.add(new PayrollReportResponse.PayrollItem(
                    employee.getId(),
                    employee.getUser().getName(),
                    type,
                    empDoneValue,
                    breakdown.totalPayout(),
                    daily ? employee.getRemunerationValue() : null,
                    daily ? worked.days() : null,
                    daily ? worked.auto() : null,
                    daily ? worked.isOverride() : null
            ));
        }

        String period = from + " a " + to;
        return new PayrollReportResponse(items, period, from, to);
    }

    // --- Ajuste manual de dias trabalhados de diarista -----------------------------------------

    @Transactional
    public void saveWorkedDaysOverride(Long employeeId, LocalDate periodStart, LocalDate periodEnd, int daysWorked) {
        if (periodStart == null || periodEnd == null || periodStart.isAfter(periodEnd)) {
            throw new com.cristiane.salon.exception.BadRequestException("Período inválido");
        }
        if (daysWorked < 0) {
            throw new com.cristiane.salon.exception.BadRequestException("Os dias trabalhados não podem ser negativos");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Diarista não encontrada"));
        if (employee.getRemunerationType() == null || !employee.getRemunerationType().isDaily()) {
            throw new com.cristiane.salon.exception.BadRequestException(
                    "Só faz sentido ajustar dias trabalhados de quem é Diarista ou Diarista + comissão");
        }
        DiaristaWorkedDaysOverride row = workedDaysOverrideRepository
                .findByEmployeeIdAndPeriodStartAndPeriodEnd(employeeId, periodStart, periodEnd)
                .orElseGet(DiaristaWorkedDaysOverride::new);
        row.setEmployeeId(employeeId);
        row.setPeriodStart(periodStart);
        row.setPeriodEnd(periodEnd);
        row.setDaysWorked(daysWorked);
        workedDaysOverrideRepository.save(row);
    }

    /** Remove o ajuste manual — o período volta a usar a contagem automática. */
    @Transactional
    public void clearWorkedDaysOverride(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        workedDaysOverrideRepository
                .findByEmployeeIdAndPeriodStartAndPeriodEnd(employeeId, periodStart, periodEnd)
                .ifPresent(workedDaysOverrideRepository::delete);
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
