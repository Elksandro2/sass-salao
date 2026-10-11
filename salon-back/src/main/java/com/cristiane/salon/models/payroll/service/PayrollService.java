package com.cristiane.salon.models.payroll.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.payroll.dto.EmployeePayrollData;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.AdjustmentRequest;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.AdjustmentResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.AppointmentLine;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.BreakdownResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.CoverageRange;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.PaymentPreview;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.SalaryLine;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.SalaryMonthStatus;
import com.cristiane.salon.models.payroll.dto.PayrollBreakdown;
import com.cristiane.salon.models.payroll.entity.PayrollSalaryPayment;
import com.cristiane.salon.models.payroll.repository.PayrollSalaryPaymentRepository;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.EmployeeDetailResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.EmployeeSummary;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.LastPayment;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.PaymentRequest;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.PaymentResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.PeriodStatus;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.SaleLine;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.SummaryResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.WorkDayBulkRequest;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.WorkDayRequest;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.WorkDayResponse;
import com.cristiane.salon.models.payroll.entity.EmployeeWorkDay;
import com.cristiane.salon.models.payroll.entity.PayrollAdjustment;
import com.cristiane.salon.models.payroll.entity.PayrollAdjustmentType;
import com.cristiane.salon.models.payroll.entity.PayrollPayment;
import com.cristiane.salon.models.payroll.repository.EmployeeWorkDayRepository;
import com.cristiane.salon.models.payroll.repository.PayrollAdjustmentRepository;
import com.cristiane.salon.models.payroll.repository.PayrollPaymentRepository;
import com.cristiane.salon.models.report.service.ReportService;
import com.cristiane.salon.utils.DateRangeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Tela "Folha de Pagamento": quanto cada colaboradora tem a receber num período, de onde vem
 * cada valor, e até quando ela já foi paga. O cálculo em si mora no {@link ReportService}
 * (mesma conta usada pelo lucro do relatório financeiro — as duas telas nunca divergem).
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final int MAX_RECALCULATED_PAYMENTS = 24;

    private final ReportService reportService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeWorkDayRepository workDayRepository;
    private final PayrollAdjustmentRepository adjustmentRepository;
    private final PayrollPaymentRepository paymentRepository;
    private final PayrollSalaryPaymentRepository salaryRepository;
    private final SalonClock salonClock;

    // --- Resumo e detalhe ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SummaryResponse getSummary(LocalDate from, LocalDate to) {
        LocalDate[] period = resolvePeriod(from, to);
        from = period[0];
        to = period[1];

        Map<Long, List<PayrollPayment>> paymentsByEmployee = paymentRepository.findAllByOrderByPeriodStartAsc().stream()
                .collect(Collectors.groupingBy(PayrollPayment::getEmployeeId));
        Map<Long, List<PayrollSalaryPayment>> salariesByEmployee = salaryRepository.findAll().stream()
                .collect(Collectors.groupingBy(PayrollSalaryPayment::getEmployeeId));

        List<EmployeeSummary> items = new ArrayList<>();
        for (EmployeePayrollData data : reportService.computePayroll(from, to)) {
            Long id = data.employee().getId();
            items.add(summarize(data, paymentsByEmployee.getOrDefault(id, List.of()),
                    salariesByEmployee.getOrDefault(id, List.of()), from, to));
        }
        items.sort(Comparator.comparing(EmployeeSummary::employeeName, String.CASE_INSENSITIVE_ORDER));

        // Na Folha o salário conta MENSAL (cada mês tocado pelo filtro, inteiro) — é o que se paga.
        BigDecimal totalNet = sum(items, EmployeeSummary::payableAmount);
        BigDecimal totalCost = sum(items, s -> s.payableAmount().add(s.breakdown().advances()));
        BigDecimal totalPaid = sum(items, EmployeeSummary::paidInPeriod);
        BigDecimal totalOutstanding = sum(items, EmployeeSummary::outstanding);

        return new SummaryResponse(from, to, items, totalCost, totalNet, totalPaid, totalOutstanding);
    }

    @Transactional(readOnly = true)
    public EmployeeDetailResponse getEmployeeDetail(Long employeeId, LocalDate from, LocalDate to) {
        LocalDate[] period = resolvePeriod(from, to);
        from = period[0];
        to = period[1];
        Employee employee = findEmployee(employeeId);

        EmployeePayrollData data = reportService.computeEmployeePayroll(employee, from, to);
        List<PayrollPayment> payments = paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(employeeId);
        List<PayrollSalaryPayment> salaries = salaryRepository.findByEmployeeId(employeeId);
        EmployeeSummary summary = summarize(data, payments, salaries, from, to);

        List<AppointmentLine> appointments = data.doneAppointments().stream()
                .map(a -> new AppointmentLine(
                        a.getId(),
                        reportService.appointmentDate(a),
                        a.getClient() != null ? a.getClient().getName() : null,
                        a.getServiceNames(),
                        a.getTotalEffectivePrice(),
                        a.getTotalProductsPrice(),
                        reportService.serviceCommissionOf(a),
                        reportService.productCommissionOf(a),
                        a.getOverrideServiceCommission() != null || a.getOverrideProductCommission() != null,
                        a.getPaymentStatus() != null ? a.getPaymentStatus().name() : null))
                .sorted(Comparator.comparing(AppointmentLine::date))
                .toList();

        List<SaleLine> sales = data.productSales().stream()
                .map(c -> new SaleLine(c.getId(), c.getDate(), c.getDescription(), c.getAmount(),
                        reportService.saleCommissionOf(c)))
                .toList();

        BigDecimal defaultRate = employee.getRemunerationValue();
        List<WorkDayResponse> workDays = data.workDays().stream()
                .map(d -> WorkDayResponse.of(d, employee.getId(), defaultRate))
                .toList();
        List<LocalDate> unmarked = data.unmarkedAppointmentDates();

        List<AdjustmentResponse> adjustments = data.adjustments().stream().map(AdjustmentResponse::of).toList();

        // Recalcula os pagamentos mais recentes pra avisar quando algo do período mudou depois
        // de pago (ex.: um agendamento corrigido, uma diária remarcada).
        List<PayrollPayment> newestFirst = new ArrayList<>(payments);
        newestFirst.sort(Comparator.comparing(PayrollService::sortKey).reversed());
        List<PaymentResponse> paymentResponses = new ArrayList<>();
        for (int i = 0; i < newestFirst.size(); i++) {
            PayrollPayment p = newestFirst.get(i);
            List<PayrollSalaryPayment> own = salariesOf(p, salaries);
            BigDecimal recalculated = i < MAX_RECALCULATED_PAYMENTS ? recalculate(employee, p, own) : null;
            paymentResponses.add(toResponse(p, own, recalculated, employeeName(employee)));
        }

        return new EmployeeDetailResponse(summary, from, to, appointments, sales, workDays, unmarked,
                adjustments, paymentResponses);
    }

    private EmployeeSummary summarize(EmployeePayrollData data, List<PayrollPayment> payments,
                                      List<PayrollSalaryPayment> salaryPayments, LocalDate from, LocalDate to) {
        Employee employee = data.employee();
        Map<Long, PayrollPayment> paymentsById = payments.stream()
                .collect(Collectors.toMap(PayrollPayment::getId, p -> p, (a, b) -> a));

        // --- Salário fixo: um item por mês tocado pelo filtro, inteiro (não proporcional aos dias).
        Map<LocalDate, PayrollSalaryPayment> salaryByMonth = salaryPayments.stream()
                .collect(Collectors.toMap(PayrollSalaryPayment::getSalaryMonth, s -> s, (a, b) -> a));
        List<SalaryMonthStatus> salaryMonths = new ArrayList<>();
        for (YearMonth month : monthsOf(from, to)) {
            PayrollSalaryPayment paid = salaryByMonth.get(month.atDay(1));
            if (paid != null) {
                PayrollPayment payment = paymentsById.get(paid.getPaymentId());
                salaryMonths.add(new SalaryMonthStatus(month.atDay(1), paid.getAmount(), true,
                        payment != null ? payment.getPaidAt() : null));
            } else {
                BigDecimal amount = reportService.monthlySalary(employee, month);
                if (amount.signum() > 0) {
                    salaryMonths.add(new SalaryMonthStatus(month.atDay(1), amount, false, null));
                }
            }
        }
        BigDecimal pendingSalary = salaryMonths.stream().filter(s -> !s.paid())
                .map(SalaryMonthStatus::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salaryOfMonths = salaryMonths.stream()
                .map(SalaryMonthStatus::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- Dias (comissões, diárias, bônus, descontos): parte variável, sem o salário.
        BigDecimal variable = variableOf(data.breakdown());
        List<PaymentCoverage.Range> paid = PaymentCoverage.paidRanges(payments, from, to);
        List<PaymentCoverage.Range> gaps = PaymentCoverage.unpaidRanges(payments, from, to);

        long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
        long uncoveredDays = gaps.stream().mapToLong(r -> ChronoUnit.DAYS.between(r.start(), r.end()) + 1).sum();
        long covered = totalDays - uncoveredDays;

        // Só é "pendente" a faixa de dias que tem algo a pagar — quem só recebe salário fixo não
        // fica com aviso de dias em aberto depois de receber o salário.
        BigDecimal unpaidEstimate = BigDecimal.ZERO;
        List<PaymentCoverage.Range> unpaid = new ArrayList<>();
        boolean wholeFilterUnpaid = gaps.size() == 1 && gaps.get(0).start().equals(from) && gaps.get(0).end().equals(to);
        for (PaymentCoverage.Range gap : gaps) {
            BigDecimal value = wholeFilterUnpaid ? variable : variablePart(employee, gap.start(), gap.end());
            if (value.signum() != 0) {
                unpaid.add(gap);
                unpaidEstimate = unpaidEstimate.add(value);
            }
        }

        // --- Pagamentos que tocam o filtro (pelos dias ou por um salário de mês do filtro).
        Set<LocalDate> filterMonths = monthsOf(from, to).stream().map(m -> m.atDay(1)).collect(Collectors.toSet());
        Set<Long> salaryTouching = salaryPayments.stream()
                .filter(s -> filterMonths.contains(s.getSalaryMonth()))
                .map(PayrollSalaryPayment::getPaymentId)
                .collect(Collectors.toSet());
        List<PayrollPayment> touching = payments.stream()
                .filter(p -> overlapsDays(p, from, to) || salaryTouching.contains(p.getId()))
                .toList();
        BigDecimal paidInPeriod = touching.stream()
                .map(PayrollPayment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingDifference = BigDecimal.ZERO;
        for (PayrollPayment payment : touching) {
            BigDecimal now = recalculate(employee, payment, salariesOf(payment, salaryPayments));
            pendingDifference = pendingDifference.add(now.subtract(payment.getAmountPaid()));
        }
        BigDecimal outstanding = unpaidEstimate.max(BigDecimal.ZERO)
                .add(pendingSalary)
                .add(pendingDifference.max(BigDecimal.ZERO));

        boolean anyPaid = covered > 0 || salaryMonths.stream().anyMatch(SalaryMonthStatus::paid);
        boolean allDone = unpaid.isEmpty() && pendingSalary.signum() == 0;
        // PAGO = não sobrou nada pendente (inclusive quando não havia nada a pagar no período).
        PeriodStatus status = allDone ? PeriodStatus.PAGO
                : anyPaid ? PeriodStatus.PARCIAL : PeriodStatus.EM_ABERTO;

        // Cada colaboradora pode ser paga num dia diferente — o que importa é quando foi o último
        // pagamento dela, não uma data única pra folha toda.
        LastPayment lastPayment = payments.stream()
                .max(Comparator.comparing(PayrollPayment::getPaidAt).thenComparing(PayrollPayment::getId))
                .map(p -> new LastPayment(p.getPaidAt(), p.getPeriodStart(), p.getPeriodEnd(), p.getAmountPaid()))
                .orElse(null);
        boolean hasGaps = mergeIntervals(payments).size() > 1;

        RemunerationType type = employee.getRemunerationType();
        return new EmployeeSummary(
                employee.getId(),
                employeeName(employee),
                type != null ? type.name() : null,
                employee.getRemunerationValue(),
                data.active(),
                data.doneAppointments().size(),
                BreakdownResponse.of(data.breakdown()),
                status, covered, totalDays, paidInPeriod, outstanding, lastPayment,
                toRanges(paid), toRanges(unpaid), pendingDifference, hasGaps,
                variable.add(salaryOfMonths), salaryMonths);
    }

    /** Parte da folha que não é salário fixo (comissões, diárias, bônus, descontos). */
    private static BigDecimal variableOf(PayrollBreakdown breakdown) {
        return breakdown.netPayable().subtract(breakdown.fixedSalary());
    }

    private BigDecimal variablePart(Employee employee, LocalDate start, LocalDate end) {
        return variableOf(reportService.computeEmployeePayroll(employee, start, end).breakdown());
    }

    /** Quanto o pagamento valeria hoje: dias recalculados + salários congelados no registro. */
    private BigDecimal recalculate(Employee employee, PayrollPayment payment, List<PayrollSalaryPayment> salaries) {
        BigDecimal days = payment.getPeriodStart() != null
                ? variablePart(employee, payment.getPeriodStart(), payment.getPeriodEnd())
                : BigDecimal.ZERO;
        return days.add(salaries.stream().map(PayrollSalaryPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static List<PayrollSalaryPayment> salariesOf(PayrollPayment payment, List<PayrollSalaryPayment> all) {
        return all.stream().filter(s -> s.getPaymentId().equals(payment.getId())).toList();
    }

    private static boolean overlapsDays(PayrollPayment p, LocalDate from, LocalDate to) {
        return p.getPeriodStart() != null && !p.getPeriodEnd().isBefore(from) && !p.getPeriodStart().isAfter(to);
    }

    /** Meses do calendário tocados por [from, to]. */
    static List<YearMonth> monthsOf(LocalDate from, LocalDate to) {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    /** Data de referência pra ordenar pagamentos (só de salário não tem período de dias). */
    private static LocalDate sortKey(PayrollPayment p) {
        return p.getPeriodStart() != null ? p.getPeriodStart() : p.getPaidAt();
    }

    private static List<CoverageRange> toRanges(List<PaymentCoverage.Range> ranges) {
        return ranges.stream().map(r -> new CoverageRange(r.start(), r.end(), r.amount())).toList();
    }

    private static String employeeName(Employee employee) {
        return employee.getUser() != null ? employee.getUser().getName() : "—";
    }

    /** Une períodos pagos que se encostam ou se sobrepõem — mais de um intervalo resultante
     *  significa que ficou um buraco sem pagamento entre eles. */
    static List<LocalDate[]> mergeIntervals(List<PayrollPayment> payments) {
        List<PayrollPayment> sorted = new ArrayList<>(payments.stream().filter(p -> p.getPeriodStart() != null).toList());
        sorted.sort(Comparator.comparing(PayrollPayment::getPeriodStart));
        List<LocalDate[]> merged = new ArrayList<>();
        for (PayrollPayment p : sorted) {
            if (!merged.isEmpty()) {
                LocalDate[] last = merged.get(merged.size() - 1);
                if (!p.getPeriodStart().isAfter(last[1].plusDays(1))) {
                    if (p.getPeriodEnd().isAfter(last[1])) last[1] = p.getPeriodEnd();
                    continue;
                }
            }
            merged.add(new LocalDate[]{p.getPeriodStart(), p.getPeriodEnd()});
        }
        return merged;
    }

    // --- Dias trabalhados -----------------------------------------------------------------------

    /**
     * Marca um dia como trabalhado. Se o dia tinha sido desmarcado antes (ou é um dia com
     * atendimento que estava desmarcado), reaproveita o registro em vez de duplicar.
     */
    @Transactional
    public WorkDayResponse createWorkDay(WorkDayRequest request) {
        Employee employee = findDailyEmployee(request.employeeId());
        EmployeeWorkDay day = workDayRepository.findByEmployeeIdAndWorkDate(employee.getId(), request.date())
                .orElse(null);
        if (day != null && !day.isRemoved()) {
            throw new BadRequestException("Este dia já está marcado como trabalhado.");
        }
        if (day == null) {
            day = new EmployeeWorkDay();
            day.setEmployeeId(employee.getId());
            day.setWorkDate(request.date());
        }
        day.setRemoved(false);
        applyWorkDayValues(day, employee, request.amount(), request.justification());
        return WorkDayResponse.of(workDayRepository.save(day), employee.getRemunerationValue());
    }

    /** Remarca de uma vez, com a diária padrão, os dias informados (ex.: os dias com atendimento
     *  que tinham sido desmarcados). Dia já marcado é ignorado, não sobrescrito. */
    @Transactional
    public List<WorkDayResponse> createWorkDaysBulk(WorkDayBulkRequest request) {
        Employee employee = findDailyEmployee(request.employeeId());
        BigDecimal rate = requireDefaultRate(employee);
        List<WorkDayResponse> created = new ArrayList<>();
        for (LocalDate date : new TreeSet<>(request.dates())) {
            if (date == null) continue;
            EmployeeWorkDay day = workDayRepository.findByEmployeeIdAndWorkDate(employee.getId(), date).orElse(null);
            if (day != null && !day.isRemoved()) continue;
            if (day == null) {
                day = new EmployeeWorkDay();
                day.setEmployeeId(employee.getId());
                day.setWorkDate(date);
            }
            day.setRemoved(false);
            day.setAmount(rate);
            day.setJustification(null);
            created.add(WorkDayResponse.of(workDayRepository.save(day), rate));
        }
        return created;
    }

    @Transactional
    public WorkDayResponse updateWorkDay(Long id, WorkDayRequest request) {
        EmployeeWorkDay day = workDayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dia trabalhado não encontrado"));
        Employee employee = findEmployee(day.getEmployeeId());
        if (request.date() != null && !request.date().equals(day.getWorkDate())) {
            if (workDayRepository.findByEmployeeIdAndWorkDate(employee.getId(), request.date()).isPresent()) {
                throw new BadRequestException("Este dia já está marcado como trabalhado.");
            }
            day.setWorkDate(request.date());
        }
        day.setRemoved(false);
        applyWorkDayValues(day, employee, request.amount(), request.justification());
        return WorkDayResponse.of(workDayRepository.save(day), employee.getRemunerationValue());
    }

    /**
     * Desmarca um dia. Não apaga o registro: guarda como "desmarcado", porque um dia com
     * atendimento voltaria a contar sozinho como diária se simplesmente sumisse daqui.
     */
    @Transactional
    public void unmarkWorkDay(Long employeeId, LocalDate date) {
        Employee employee = findEmployee(employeeId);
        EmployeeWorkDay day = workDayRepository.findByEmployeeIdAndWorkDate(employee.getId(), date)
                .orElseGet(() -> {
                    EmployeeWorkDay fresh = new EmployeeWorkDay();
                    fresh.setEmployeeId(employee.getId());
                    fresh.setWorkDate(date);
                    return fresh;
                });
        day.setRemoved(true);
        day.setAmount(BigDecimal.ZERO);
        day.setJustification(null);
        workDayRepository.save(day);
    }

    @Transactional
    public void deleteWorkDay(Long id) {
        EmployeeWorkDay day = workDayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dia trabalhado não encontrado"));
        unmarkWorkDay(day.getEmployeeId(), day.getWorkDate());
    }

    /** Valor do dia: o informado, ou a diária padrão. Justificativa sempre opcional. */
    private void applyWorkDayValues(EmployeeWorkDay day, Employee employee, BigDecimal amount, String justification) {
        BigDecimal value = amount != null ? amount : requireDefaultRate(employee);
        String reason = justification != null && !justification.isBlank() ? justification.trim() : null;
        day.setAmount(value);
        day.setJustification(reason);
    }

    private BigDecimal requireDefaultRate(Employee employee) {
        if (employee.getRemunerationValue() == null) {
            throw new BadRequestException("Cadastre o valor da diária padrão desta colaboradora (Equipe) antes de marcar dias.");
        }
        return employee.getRemunerationValue();
    }

    private Employee findDailyEmployee(Long employeeId) {
        Employee employee = findEmployee(employeeId);
        if (employee.getRemunerationType() == null || !employee.getRemunerationType().isDaily()) {
            throw new BadRequestException("Só dá pra marcar dias trabalhados de quem é Diarista ou Diária + Comissão.");
        }
        return employee;
    }

    // --- Acréscimos e abatimentos ---------------------------------------------------------------

    @Transactional
    public AdjustmentResponse createAdjustment(AdjustmentRequest request) {
        findEmployee(request.employeeId());
        PayrollAdjustment adjustment = new PayrollAdjustment();
        adjustment.setEmployeeId(request.employeeId());
        applyAdjustment(adjustment, request);
        return AdjustmentResponse.of(adjustmentRepository.save(adjustment));
    }

    @Transactional
    public AdjustmentResponse updateAdjustment(Long id, AdjustmentRequest request) {
        PayrollAdjustment adjustment = adjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado"));
        applyAdjustment(adjustment, request);
        return AdjustmentResponse.of(adjustmentRepository.save(adjustment));
    }

    @Transactional
    public void deleteAdjustment(Long id) {
        if (!adjustmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Lançamento não encontrado");
        }
        adjustmentRepository.deleteById(id);
    }

    private void applyAdjustment(PayrollAdjustment adjustment, AdjustmentRequest request) {
        // Vale saiu das opções (simplificação): só continua existindo nos lançamentos antigos.
        if (request.type() == PayrollAdjustmentType.VALE && adjustment.getType() != PayrollAdjustmentType.VALE) {
            throw new BadRequestException("Vale não é mais usado. Para pagar a menos, lance um Desconto.");
        }
        adjustment.setDate(request.date());
        adjustment.setType(request.type());
        adjustment.setAmount(request.amount());
        adjustment.setDescription(request.description() != null && !request.description().isBlank()
                ? request.description().trim() : null);
    }

    // --- Pagamentos -----------------------------------------------------------------------------

    /** Maior período aceito num único pagamento — evita erro de digitação de ano (2026 → 2062). */
    private static final long MAX_PAYMENT_DAYS = 366;

    /**
     * Pagamentos registrados que tocam [from, to] — pelos dias ou por um salário de mês do
     * filtro (todos se o período vier vazio) —, mais recentes primeiro.
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(LocalDate from, LocalDate to, Long employeeId) {
        DateRangeValidator.validate(from, to);
        List<PayrollPayment> all = employeeId != null
                ? paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(employeeId)
                : paymentRepository.findAllByOrderByPeriodStartAsc();
        List<PayrollSalaryPayment> salaries = all.isEmpty() ? List.of()
                : salaryRepository.findByPaymentIdIn(all.stream().map(PayrollPayment::getId).toList());

        List<PayrollPayment> payments = all.stream()
                .filter(p -> touchesFilter(p, salariesOf(p, salaries), from, to))
                .sorted(Comparator.comparing(PayrollService::sortKey).reversed())
                .toList();
        Map<Long, Employee> employees = employeeRepository.findAllById(
                        payments.stream().map(PayrollPayment::getEmployeeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        List<PaymentResponse> result = new ArrayList<>();
        for (PayrollPayment p : payments) {
            Employee employee = employees.get(p.getEmployeeId());
            List<PayrollSalaryPayment> own = salariesOf(p, salaries);
            BigDecimal recalculated = employee != null ? recalculate(employee, p, own) : null;
            result.add(toResponse(p, own, recalculated, employee != null ? employeeName(employee) : "—"));
        }
        return result;
    }

    private static boolean touchesFilter(PayrollPayment p, List<PayrollSalaryPayment> salaries,
                                         LocalDate from, LocalDate to) {
        if (p.getPeriodStart() != null
                && (from == null || !p.getPeriodEnd().isBefore(from))
                && (to == null || !p.getPeriodStart().isAfter(to))) {
            return true;
        }
        return salaries.stream().anyMatch(s -> (from == null || !s.getSalaryMonth().isBefore(from.withDayOfMonth(1)))
                && (to == null || !s.getSalaryMonth().isAfter(to)));
    }

    /** O que seria registrado (dias + salários escolhidos) e o problema, se houver. */
    @Transactional(readOnly = true)
    public PaymentPreview previewPayment(Long employeeId, LocalDate start, LocalDate end,
                                         List<LocalDate> salaryMonths, Long ignorePaymentId) {
        Employee employee = findEmployee(employeeId);
        List<LocalDate> months = normalizeMonths(salaryMonths);
        String problem = paymentProblem(employee, start, end, months, ignorePaymentId);
        PayrollBreakdown days = start != null && end != null && !start.isAfter(end)
                ? reportService.computeEmployeePayroll(employee, start, end).breakdown()
                : null;
        BigDecimal amount = (days != null ? variableOf(days) : BigDecimal.ZERO).add(salaryTotal(employee, months));
        if (problem == null && amount.signum() < 0) {
            problem = negativeMessage(amount);
        }
        return new PaymentPreview(amount, problem, days != null ? BreakdownResponse.of(days) : null);
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        Employee employee = findEmployee(request.employeeId());
        List<LocalDate> months = normalizeMonths(request.salaryMonths());
        assertValid(employee, request.periodStart(), request.periodEnd(), months, null);
        PayrollPayment payment = new PayrollPayment();
        payment.setEmployeeId(employee.getId());
        return applyPayment(payment, employee, request, months);
    }

    /**
     * Edita um pagamento (dias, salários, forma, observação) e recalcula o valor com os dados de
     * hoje — é também o jeito de "atualizar" um pagamento depois que algo dos dias mudou.
     */
    @Transactional
    public PaymentResponse updatePayment(Long id, PaymentRequest request) {
        PayrollPayment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado"));
        if (!payment.getEmployeeId().equals(request.employeeId())) {
            throw new BadRequestException("Não é possível mudar a colaboradora de um pagamento já registrado.");
        }
        Employee employee = findEmployee(payment.getEmployeeId());
        List<LocalDate> months = normalizeMonths(request.salaryMonths());
        assertValid(employee, request.periodStart(), request.periodEnd(), months, id);
        return applyPayment(payment, employee, request, months);
    }

    @Transactional
    public void deletePayment(Long id) {
        if (!paymentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pagamento não encontrado");
        }
        // Os salários quitados saem junto (FK ON DELETE CASCADE) e voltam a ficar pendentes.
        salaryRepository.deleteByPaymentId(id);
        paymentRepository.deleteById(id);
    }

    private void assertValid(Employee employee, LocalDate start, LocalDate end, List<LocalDate> months, Long ignoreId) {
        String problem = paymentProblem(employee, start, end, months, ignoreId);
        if (problem != null) {
            throw new BadRequestException(problem);
        }
    }

    /** Mensagem explicando por que o pagamento não pode ser registrado, ou null se está tudo certo. */
    private String paymentProblem(Employee employee, LocalDate start, LocalDate end, List<LocalDate> months,
                                  Long ignoreId) {
        if ((start == null) != (end == null)) {
            return "Informe o início e o fim do período, ou nenhum dos dois (pagamento só de salário).";
        }
        if (start == null && months.isEmpty()) {
            return "Nada pra registrar: escolha os dias e/ou o salário de algum mês.";
        }
        if (start != null) {
            if (start.isAfter(end)) {
                return "O início do período deve ser antes do fim.";
            }
            if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_PAYMENT_DAYS) {
                return "Um pagamento pode cobrir no máximo 1 ano. Confira as datas.";
            }
            List<PayrollPayment> overlapping = paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(employee.getId()).stream()
                    .filter(other -> !other.getId().equals(ignoreId))
                    .filter(other -> overlapsDays(other, start, end))
                    .toList();
            if (!overlapping.isEmpty()) {
                String which = overlapping.stream()
                        .map(o -> br(o.getPeriodStart()) + " a " + br(o.getPeriodEnd()) + " (pago em " + br(o.getPaidAt()) + ")")
                        .collect(Collectors.joining("; "));
                return "Parte deste período já foi paga: " + which + ". Escolha dias que ainda não foram pagos "
                        + "ou edite aquele pagamento.";
            }
        }
        if (!months.isEmpty()) {
            Map<LocalDate, PayrollSalaryPayment> alreadyPaid = salaryRepository.findByEmployeeId(employee.getId()).stream()
                    .filter(s -> !s.getPaymentId().equals(ignoreId))
                    .collect(Collectors.toMap(PayrollSalaryPayment::getSalaryMonth, s -> s, (a, b) -> a));
            for (LocalDate month : months) {
                if (alreadyPaid.containsKey(month)) {
                    LocalDate paidAt = paymentRepository.findById(alreadyPaid.get(month).getPaymentId())
                            .map(PayrollPayment::getPaidAt).orElse(null);
                    return "O salário de " + monthLabel(month) + " já foi pago"
                            + (paidAt != null ? " em " + br(paidAt) : "") + ".";
                }
                if (reportService.monthlySalary(employee, YearMonth.from(month)).signum() <= 0) {
                    return "Não há salário fixo a pagar em " + monthLabel(month) + " pra esta colaboradora.";
                }
            }
        }
        return null;
    }

    private PaymentResponse applyPayment(PayrollPayment payment, Employee employee, PaymentRequest request,
                                         List<LocalDate> months) {
        payment.setPeriodStart(request.periodStart());
        payment.setPeriodEnd(request.periodEnd());
        // Valor travado: sempre o calculado — dias (sem salário) + salários mensais escolhidos.
        BigDecimal days = request.periodStart() != null
                ? variablePart(employee, request.periodStart(), request.periodEnd())
                : BigDecimal.ZERO;
        List<PayrollSalaryPayment> salaryLines = new ArrayList<>();
        for (LocalDate month : months) {
            PayrollSalaryPayment line = new PayrollSalaryPayment();
            line.setEmployeeId(employee.getId());
            line.setSalaryMonth(month);
            line.setAmount(reportService.monthlySalary(employee, YearMonth.from(month)));
            salaryLines.add(line);
        }
        BigDecimal calculated = days.add(salaryLines.stream().map(PayrollSalaryPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        // Descontos maiores que o que ela tem a receber deixam o valor negativo — não existe
        // "pagamento negativo" (o banco também recusa); melhor explicar do que estourar erro 500.
        if (calculated.signum() < 0) {
            throw new BadRequestException(negativeMessage(calculated));
        }
        payment.setCalculatedAmount(calculated);
        payment.setAmountPaid(calculated);
        payment.setPaidAt(request.paidAt());
        payment.setPaymentMethod(request.paymentMethod() != null && !request.paymentMethod().isBlank()
                ? request.paymentMethod().trim().toUpperCase() : null);
        payment.setNotes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null);
        PayrollPayment saved = paymentRepository.save(payment);

        // Salários do pagamento: substitui o que havia (edição) pelo escolhido agora.
        if (saved.getId() != null) {
            salaryRepository.deleteByPaymentId(saved.getId());
            salaryRepository.flush();
        }
        for (PayrollSalaryPayment line : salaryLines) {
            line.setPaymentId(saved.getId());
        }
        List<PayrollSalaryPayment> savedLines = salaryRepository.saveAll(salaryLines);
        return toResponse(saved, savedLines, null, employeeName(employee));
    }

    private BigDecimal salaryTotal(Employee employee, List<LocalDate> months) {
        return months.stream()
                .map(m -> reportService.monthlySalary(employee, YearMonth.from(m)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Qualquer dia do mês vira o dia 1; repetidos e nulos saem. */
    private static List<LocalDate> normalizeMonths(List<LocalDate> months) {
        if (months == null) return List.of();
        return months.stream()
                .filter(java.util.Objects::nonNull)
                .map(m -> m.withDayOfMonth(1))
                .distinct()
                .sorted()
                .toList();
    }

    private static String monthLabel(LocalDate month) {
        String name = month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
        return name + "/" + month.getYear();
    }

    private static String negativeMessage(BigDecimal value) {
        return "O valor ficou negativo (" + value.toPlainString().replace('.', ',')
                + "): os descontos passam do que ela tem a receber. Ajuste os descontos antes de marcar como pago.";
    }

    private static PaymentResponse toResponse(PayrollPayment p, List<PayrollSalaryPayment> salaries,
                                              BigDecimal recalculated, String employeeName) {
        boolean changed = recalculated != null && recalculated.compareTo(p.getAmountPaid()) != 0;
        List<SalaryLine> lines = salaries.stream()
                .sorted(Comparator.comparing(PayrollSalaryPayment::getSalaryMonth))
                .map(s -> new SalaryLine(s.getSalaryMonth(), s.getAmount()))
                .toList();
        return new PaymentResponse(p.getId(), p.getEmployeeId(), employeeName, p.getPeriodStart(), p.getPeriodEnd(),
                lines, p.getCalculatedAmount(), p.getAmountPaid(), p.getPaidAt(), p.getPaymentMethod(), p.getNotes(),
                recalculated, changed);
    }

    // --- Utilitários ----------------------------------------------------------------------------

    private LocalDate[] resolvePeriod(LocalDate from, LocalDate to) {
        DateRangeValidator.validate(from, to);
        LocalDate today = salonClock.today();
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today.with(TemporalAdjusters.lastDayOfMonth());
        if (start.isAfter(end)) {
            throw new BadRequestException("A data inicial deve ser antes da final.");
        }
        return new LocalDate[]{start, end};
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Colaboradora não encontrada"));
    }

    private static BigDecimal sum(List<EmployeeSummary> items, java.util.function.Function<EmployeeSummary, BigDecimal> f) {
        return items.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String br(LocalDate date) {
        return String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }
}
