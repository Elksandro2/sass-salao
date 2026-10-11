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
import com.cristiane.salon.models.payroll.dto.PayrollDtos.EmployeeDetailResponse;
import com.cristiane.salon.models.payroll.dto.PayrollDtos.EmployeeSummary;
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
import java.util.Objects;
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

        // "Folha quitada até": a menor data "pago até" entre as colaboradoras ativas que têm
        // remuneração cadastrada — é a data até a qual NINGUÉM está com salário atrasado.
        List<EmployeeSummary> considered = items.stream()
                .filter(s -> s.active() && s.remunerationType() != null)
                .toList();
        LocalDate paidUntilAll = null;
        List<String> behind = new ArrayList<>();
        if (!considered.isEmpty()) {
            boolean someoneNeverPaid = considered.stream().anyMatch(s -> s.paidUntil() == null);
            if (someoneNeverPaid) {
                behind = considered.stream().filter(s -> s.paidUntil() == null)
                        .map(EmployeeSummary::employeeName).toList();
            } else {
                paidUntilAll = considered.stream().map(EmployeeSummary::paidUntil)
                        .min(Comparator.naturalOrder()).orElse(null);
                LocalDate min = paidUntilAll;
                behind = considered.stream().filter(s -> s.paidUntil().equals(min))
                        .map(EmployeeSummary::employeeName).toList();
            }
        }

        return new SummaryResponse(from, to, items, totalCost, totalNet, totalPaid, totalOutstanding,
                paidUntilAll, behind);
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
                        c.getCommissionAmount() != null ? c.getCommissionAmount() : BigDecimal.ZERO))
                .toList();

        BigDecimal defaultRate = employee.getRemunerationValue();
        List<WorkDayResponse> workDays = data.workDays().stream()
                .map(d -> WorkDayResponse.of(d, defaultRate))
                .toList();

        Set<LocalDate> marked = data.workDays().stream().map(EmployeeWorkDay::getWorkDate)
                .collect(Collectors.toSet());
        List<LocalDate> unmarked = data.doneAppointments().stream()
                .map(reportService::appointmentDate)
                .filter(Objects::nonNull)
                .filter(d -> !marked.contains(d))
                .collect(Collectors.toCollection(TreeSet::new))
                .stream().toList();

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
            paymentResponses.add(toResponse(p, recalculated));
        }

        return new EmployeeDetailResponse(summary, from, to, appointments, sales, workDays, unmarked,
                adjustments, paymentResponses);
    }

    private EmployeeSummary summarize(EmployeePayrollData data, List<PayrollPayment> payments,
                                      LocalDate from, LocalDate to) {
        Employee employee = data.employee();
        List<LocalDate[]> merged = mergeIntervals(payments);

        long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
        long covered = 0;
        for (LocalDate[] interval : merged) {
            LocalDate start = interval[0].isAfter(from) ? interval[0] : from;
            LocalDate end = interval[1].isBefore(to) ? interval[1] : to;
            if (!start.isAfter(end)) {
                covered += ChronoUnit.DAYS.between(start, end) + 1;
            }
        }
        PeriodStatus status = covered >= totalDays ? PeriodStatus.PAGO
                : covered > 0 ? PeriodStatus.PARCIAL : PeriodStatus.EM_ABERTO;

        LocalDate finalFrom = from;
        LocalDate finalTo = to;
        BigDecimal paidInPeriod = payments.stream()
                .filter(p -> !p.getPeriodEnd().isBefore(finalFrom) && !p.getPeriodStart().isAfter(finalTo))
                .map(PayrollPayment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPayable = data.breakdown().netPayable();
        BigDecimal outstanding = status == PeriodStatus.PAGO
                ? BigDecimal.ZERO
                : netPayable.subtract(paidInPeriod).max(BigDecimal.ZERO);

        LocalDate paidUntil = merged.isEmpty() ? null : merged.get(0)[1];
        boolean hasGaps = merged.size() > 1;

        RemunerationType type = employee.getRemunerationType();
        return new EmployeeSummary(
                employee.getId(),
                employee.getUser() != null ? employee.getUser().getName() : "—",
                type != null ? type.name() : null,
                employee.getRemunerationValue(),
                data.active(),
                data.doneAppointments().size(),
                BreakdownResponse.of(data.breakdown()),
                status, covered, totalDays, paidInPeriod, outstanding, paidUntil, hasGaps);
    }

    /** Une períodos pagos que se encostam ou se sobrepõem — o primeiro intervalo resultante
     *  termina no "pago até" (o resto, se houver, está depois de um buraco). */
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

    @Transactional
    public WorkDayResponse createWorkDay(WorkDayRequest request) {
        Employee employee = findDailyEmployee(request.employeeId());
        if (workDayRepository.findByEmployeeIdAndWorkDate(employee.getId(), request.date()).isPresent()) {
            throw new BadRequestException("Este dia já está marcado como trabalhado.");
        }
        EmployeeWorkDay day = new EmployeeWorkDay();
        day.setEmployeeId(employee.getId());
        day.setWorkDate(request.date());
        applyWorkDayValues(day, employee, request.amount(), request.justification());
        return WorkDayResponse.of(workDayRepository.save(day), employee.getRemunerationValue());
    }

    /** Marca vários dias de uma vez com a diária padrão (ex.: "marcar os dias com atendimento").
     *  Dias já marcados são ignorados, não sobrescritos. */
    @Transactional
    public List<WorkDayResponse> createWorkDaysBulk(WorkDayBulkRequest request) {
        Employee employee = findDailyEmployee(request.employeeId());
        BigDecimal rate = requireDefaultRate(employee);
        List<WorkDayResponse> created = new ArrayList<>();
        for (LocalDate date : new TreeSet<>(request.dates())) {
            if (date == null || workDayRepository.findByEmployeeIdAndWorkDate(employee.getId(), date).isPresent()) {
                continue;
            }
            EmployeeWorkDay day = new EmployeeWorkDay();
            day.setEmployeeId(employee.getId());
            day.setWorkDate(date);
            day.setAmount(rate);
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
        applyWorkDayValues(day, employee, request.amount(), request.justification());
        return WorkDayResponse.of(workDayRepository.save(day), employee.getRemunerationValue());
    }

    @Transactional
    public void deleteWorkDay(Long id) {
        if (!workDayRepository.existsById(id)) {
            throw new ResourceNotFoundException("Dia trabalhado não encontrado");
        }
        workDayRepository.deleteById(id);
    }

    /** Valor do dia: o informado, ou a diária padrão. Diferente da padrão exige justificativa. */
    private void applyWorkDayValues(EmployeeWorkDay day, Employee employee, BigDecimal amount, String justification) {
        BigDecimal defaultRate = employee.getRemunerationValue();
        BigDecimal value = amount != null ? amount : requireDefaultRate(employee);
        String reason = justification != null && !justification.isBlank() ? justification.trim() : null;
        boolean differs = defaultRate == null || value.compareTo(defaultRate) != 0;
        if (differs && reason == null) {
            throw new BadRequestException("Informe a justificativa: o valor deste dia é diferente da diária padrão.");
        }
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
        adjustment.setDate(request.date());
        adjustment.setType(request.type());
        adjustment.setAmount(request.amount());
        adjustment.setDescription(request.description() != null && !request.description().isBlank()
                ? request.description().trim() : null);
    }

    // --- Pagamentos -----------------------------------------------------------------------------

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        Employee employee = findEmployee(request.employeeId());
        validatePaymentPeriod(request, null);
        PayrollPayment payment = new PayrollPayment();
        payment.setEmployeeId(employee.getId());
        applyPayment(payment, employee, request);
        return toResponse(paymentRepository.save(payment), null);
    }

    /** Editar um pagamento recalcula e recongela o valor calculado do período. */
    @Transactional
    public PaymentResponse updatePayment(Long id, PaymentRequest request) {
        PayrollPayment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado"));
        if (!payment.getEmployeeId().equals(request.employeeId())) {
            throw new BadRequestException("Não é possível mudar a colaboradora de um pagamento já registrado.");
        }
        Employee employee = findEmployee(payment.getEmployeeId());
        validatePaymentPeriod(request, id);
        applyPayment(payment, employee, request);
        return toResponse(paymentRepository.save(payment), null);
    }

    @Transactional
    public void deletePayment(Long id) {
        if (!paymentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pagamento não encontrado");
        }
        paymentRepository.deleteById(id);
    }

    private void validatePaymentPeriod(PaymentRequest request, Long ignoreId) {
        if (request.periodStart().isAfter(request.periodEnd())) {
            throw new BadRequestException("O início do período deve ser antes do fim.");
        }
        for (PayrollPayment other : paymentRepository.findByEmployeeIdOrderByPeriodStartAsc(request.employeeId())) {
            if (other.getId().equals(ignoreId)) continue;
            boolean overlaps = !other.getPeriodEnd().isBefore(request.periodStart())
                    && !other.getPeriodStart().isAfter(request.periodEnd());
            if (overlaps) {
                throw new BadRequestException("Já existe um pagamento registrado de " + br(other.getPeriodStart())
                        + " a " + br(other.getPeriodEnd()) + " que cobre parte deste período. "
                        + "Edite aquele pagamento ou escolha outro período.");
            }
        }
    }

    private void applyPayment(PayrollPayment payment, Employee employee, PaymentRequest request) {
        payment.setPeriodStart(request.periodStart());
        payment.setPeriodEnd(request.periodEnd());
        payment.setCalculatedAmount(reportService
                .computeEmployeePayroll(employee, request.periodStart(), request.periodEnd())
                .breakdown().netPayable());
        payment.setAmountPaid(request.amountPaid());
        payment.setPaidAt(request.paidAt());
        payment.setPaymentMethod(request.paymentMethod() != null && !request.paymentMethod().isBlank()
                ? request.paymentMethod().trim().toUpperCase() : null);
        payment.setNotes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null);
    }

    private static PaymentResponse toResponse(PayrollPayment p, BigDecimal recalculated) {
        boolean changed = recalculated != null && recalculated.compareTo(p.getCalculatedAmount()) != 0;
        return new PaymentResponse(p.getId(), p.getEmployeeId(), p.getPeriodStart(), p.getPeriodEnd(),
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
