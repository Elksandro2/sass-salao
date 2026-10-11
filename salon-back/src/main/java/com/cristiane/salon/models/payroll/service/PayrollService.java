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
    private final SalonClock salonClock;

    // --- Resumo e detalhe ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SummaryResponse getSummary(LocalDate from, LocalDate to) {
        LocalDate[] period = resolvePeriod(from, to);
        from = period[0];
        to = period[1];

        Map<Long, List<PayrollPayment>> paymentsByEmployee = paymentRepository.findAllByOrderByPeriodStartAsc().stream()
                .collect(Collectors.groupingBy(PayrollPayment::getEmployeeId));

        List<EmployeeSummary> items = new ArrayList<>();
        for (EmployeePayrollData data : reportService.computePayroll(from, to)) {
            List<PayrollPayment> payments = paymentsByEmployee.getOrDefault(data.employee().getId(), List.of());
            items.add(summarize(data, payments, from, to));
        }
        items.sort(Comparator.comparing(EmployeeSummary::employeeName, String.CASE_INSENSITIVE_ORDER));

        BigDecimal totalCost = sum(items, s -> s.breakdown().salonCost());
        BigDecimal totalNet = sum(items, s -> s.breakdown().netPayable());
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
        EmployeeSummary summary = summarize(data, payments, from, to);

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
        newestFirst.sort(Comparator.comparing(PayrollPayment::getPeriodStart).reversed());
        List<PaymentResponse> paymentResponses = new ArrayList<>();
        for (int i = 0; i < newestFirst.size(); i++) {
            PayrollPayment p = newestFirst.get(i);
            BigDecimal recalculated = i < MAX_RECALCULATED_PAYMENTS
                    ? reportService.computeEmployeePayroll(employee, p.getPeriodStart(), p.getPeriodEnd())
                            .breakdown().netPayable()
                    : null;
            paymentResponses.add(toResponse(p, recalculated, employeeName(employee)));
        }

        return new EmployeeDetailResponse(summary, from, to, appointments, sales, workDays, unmarked,
                adjustments, paymentResponses);
    }

    private EmployeeSummary summarize(EmployeePayrollData data, List<PayrollPayment> payments,
                                      LocalDate from, LocalDate to) {
        Employee employee = data.employee();

        List<PaymentCoverage.Range> paid = PaymentCoverage.paidRanges(payments, from, to);
        List<PaymentCoverage.Range> unpaid = PaymentCoverage.unpaidRanges(payments, from, to);

        long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
        long uncoveredDays = unpaid.stream().mapToLong(r -> ChronoUnit.DAYS.between(r.start(), r.end()) + 1).sum();
        long covered = totalDays - uncoveredDays;
        PeriodStatus status = covered >= totalDays ? PeriodStatus.PAGO
                : covered > 0 ? PeriodStatus.PARCIAL : PeriodStatus.EM_ABERTO;

        List<PayrollPayment> touching = payments.stream()
                .filter(p -> !p.getPeriodEnd().isBefore(from) && !p.getPeriodStart().isAfter(to))
                .toList();
        BigDecimal paidInPeriod = touching.stream()
                .map(PayrollPayment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Dias sem pagamento: estimativa do sistema pra exatamente esses dias. Dias pagos: o que
        // foi registrado — mais a diferença, se algo do período mudou depois de pago.
        BigDecimal unpaidEstimate = BigDecimal.ZERO;
        if (unpaid.size() == 1 && unpaid.get(0).start().equals(from) && unpaid.get(0).end().equals(to)) {
            unpaidEstimate = data.breakdown().netPayable();
        } else {
            for (PaymentCoverage.Range gap : unpaid) {
                unpaidEstimate = unpaidEstimate.add(reportService
                        .computeEmployeePayroll(employee, gap.start(), gap.end()).breakdown().netPayable());
            }
        }
        BigDecimal pendingDifference = BigDecimal.ZERO;
        for (PayrollPayment payment : touching) {
            BigDecimal now = reportService.computeEmployeePayroll(employee, payment.getPeriodStart(), payment.getPeriodEnd())
                    .breakdown().netPayable();
            pendingDifference = pendingDifference.add(now.subtract(payment.getAmountPaid()));
        }
        BigDecimal outstanding = unpaidEstimate.max(BigDecimal.ZERO).add(pendingDifference.max(BigDecimal.ZERO));

        // Cada colaboradora pode ser paga num dia diferente — o que importa é quando foi o último
        // pagamento dela, não uma data única pra folha toda.
        LastPayment lastPayment = payments.stream()
                .max(Comparator.comparing(PayrollPayment::getPaidAt)
                        .thenComparing(PayrollPayment::getPeriodEnd))
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
                toRanges(paid), toRanges(unpaid), pendingDifference, hasGaps);
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
        List<PayrollPayment> sorted = new ArrayList<>(payments);
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

    /** Pagamentos registrados que tocam [from, to] (todos se o período vier vazio), mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(LocalDate from, LocalDate to, Long employeeId) {
        DateRangeValidator.validate(from, to);
        List<PayrollPayment> payments = (employeeId != null
                ? paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(employeeId)
                : paymentRepository.findAllByOrderByPeriodStartAsc()).stream()
                .filter(p -> from == null || !p.getPeriodEnd().isBefore(from))
                .filter(p -> to == null || !p.getPeriodStart().isAfter(to))
                .sorted(Comparator.comparing(PayrollPayment::getPeriodStart).reversed())
                .toList();
        Map<Long, Employee> employees = employeeRepository.findAllById(
                        payments.stream().map(PayrollPayment::getEmployeeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        List<PaymentResponse> result = new ArrayList<>();
        for (PayrollPayment p : payments) {
            Employee employee = employees.get(p.getEmployeeId());
            BigDecimal recalculated = employee != null
                    ? reportService.computeEmployeePayroll(employee, p.getPeriodStart(), p.getPeriodEnd()).breakdown().netPayable()
                    : null;
            result.add(toResponse(p, recalculated, employee != null ? employeeName(employee) : "—"));
        }
        return result;
    }

    /** O que seria registrado ao marcar [start, end] como pago, e o conflito de datas, se houver. */
    @Transactional(readOnly = true)
    public PaymentPreview previewPayment(Long employeeId, LocalDate start, LocalDate end, Long ignorePaymentId) {
        Employee employee = findEmployee(employeeId);
        String conflict = periodProblem(employeeId, start, end, ignorePaymentId);
        BigDecimal amount = start.isAfter(end) ? BigDecimal.ZERO
                : reportService.computeEmployeePayroll(employee, start, end).breakdown().netPayable();
        return new PaymentPreview(amount, conflict);
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        Employee employee = findEmployee(request.employeeId());
        assertValidPeriod(request.employeeId(), request.periodStart(), request.periodEnd(), null);
        PayrollPayment payment = new PayrollPayment();
        payment.setEmployeeId(employee.getId());
        applyPayment(payment, employee, request);
        return toResponse(paymentRepository.save(payment), null, employeeName(employee));
    }

    /**
     * Edita um pagamento (datas, forma, observação) e recalcula o valor do período com os dados
     * de hoje — é também o jeito de "atualizar" um pagamento depois que algo do período mudou.
     */
    @Transactional
    public PaymentResponse updatePayment(Long id, PaymentRequest request) {
        PayrollPayment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado"));
        if (!payment.getEmployeeId().equals(request.employeeId())) {
            throw new BadRequestException("Não é possível mudar a colaboradora de um pagamento já registrado.");
        }
        Employee employee = findEmployee(payment.getEmployeeId());
        assertValidPeriod(payment.getEmployeeId(), request.periodStart(), request.periodEnd(), id);
        applyPayment(payment, employee, request);
        return toResponse(paymentRepository.save(payment), null, employeeName(employee));
    }

    @Transactional
    public void deletePayment(Long id) {
        if (!paymentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pagamento não encontrado");
        }
        paymentRepository.deleteById(id);
    }

    private void assertValidPeriod(Long employeeId, LocalDate start, LocalDate end, Long ignoreId) {
        String problem = periodProblem(employeeId, start, end, ignoreId);
        if (problem != null) {
            throw new BadRequestException(problem);
        }
    }

    /** Mensagem explicando por que o período não pode ser pago, ou null se está tudo certo. */
    private String periodProblem(Long employeeId, LocalDate start, LocalDate end, Long ignoreId) {
        if (start == null || end == null) {
            return "Informe o início e o fim do período.";
        }
        if (start.isAfter(end)) {
            return "O início do período deve ser antes do fim.";
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_PAYMENT_DAYS) {
            return "Um pagamento pode cobrir no máximo 1 ano. Confira as datas.";
        }
        List<PayrollPayment> overlapping = paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(employeeId).stream()
                .filter(other -> !other.getId().equals(ignoreId))
                .filter(other -> !other.getPeriodEnd().isBefore(start) && !other.getPeriodStart().isAfter(end))
                .toList();
        if (overlapping.isEmpty()) {
            return null;
        }
        String which = overlapping.stream()
                .map(o -> br(o.getPeriodStart()) + " a " + br(o.getPeriodEnd()) + " (pago em " + br(o.getPaidAt()) + ")")
                .collect(Collectors.joining("; "));
        return "Parte deste período já foi paga: " + which + ". Escolha dias que ainda não foram pagos "
                + "ou edite aquele pagamento.";
    }

    private void applyPayment(PayrollPayment payment, Employee employee, PaymentRequest request) {
        payment.setPeriodStart(request.periodStart());
        payment.setPeriodEnd(request.periodEnd());
        // Valor travado: sempre o calculado pelo sistema pra esse período.
        BigDecimal calculated = reportService
                .computeEmployeePayroll(employee, request.periodStart(), request.periodEnd())
                .breakdown().netPayable();
        payment.setCalculatedAmount(calculated);
        payment.setAmountPaid(calculated);
        payment.setPaidAt(request.paidAt());
        payment.setPaymentMethod(request.paymentMethod() != null && !request.paymentMethod().isBlank()
                ? request.paymentMethod().trim().toUpperCase() : null);
        payment.setNotes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null);
    }

    private static PaymentResponse toResponse(PayrollPayment p, BigDecimal recalculated, String employeeName) {
        boolean changed = recalculated != null && recalculated.compareTo(p.getAmountPaid()) != 0;
        return new PaymentResponse(p.getId(), p.getEmployeeId(), employeeName, p.getPeriodStart(), p.getPeriodEnd(),
                p.getCalculatedAmount(), p.getAmountPaid(), p.getPaidAt(), p.getPaymentMethod(), p.getNotes(),
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
