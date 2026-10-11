package com.cristiane.salon.models.payroll.dto;

import com.cristiane.salon.models.payroll.entity.EmployeeWorkDay;
import com.cristiane.salon.models.payroll.entity.PayrollAdjustment;
import com.cristiane.salon.models.payroll.entity.PayrollAdjustmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Requests e responses da tela Folha de Pagamento, agrupados num arquivo só por serem pequenos. */
public final class PayrollDtos {

    private PayrollDtos() {}

    // --- Dias trabalhados (diarista) --------------------------------------------------------

    public record WorkDayRequest(
            @NotNull(message = "A colaboradora é obrigatória") Long employeeId,
            @NotNull(message = "A data é obrigatória") LocalDate date,
            /** Null = usa a diária padrão cadastrada da colaboradora. */
            @DecimalMin(value = "0", message = "O valor da diária não pode ser negativo") BigDecimal amount,
            @Size(max = 500, message = "A justificativa deve ter no máximo 500 caracteres") String justification
    ) {}

    public record WorkDayBulkRequest(
            @NotNull(message = "A colaboradora é obrigatória") Long employeeId,
            @NotEmpty(message = "Informe ao menos um dia") List<LocalDate> dates
    ) {}

    public record UnmarkWorkDayRequest(
            @NotNull(message = "A colaboradora é obrigatória") Long employeeId,
            @NotNull(message = "A data é obrigatória") LocalDate date
    ) {}

    /**
     * @param id          null quando o dia é automático (teve atendimento e não tem registro próprio)
     * @param defaultRate diária padrão atual da colaboradora — pra tela destacar dia com valor diferente
     * @param automatic   contou sozinho por ter atendimento concluído no dia
     */
    public record WorkDayResponse(
            Long id,
            Long employeeId,
            LocalDate date,
            BigDecimal amount,
            String justification,
            BigDecimal defaultRate,
            boolean automatic
    ) {
        public static WorkDayResponse of(EmployeeWorkDay day, BigDecimal defaultRate) {
            return new WorkDayResponse(day.getId(), day.getEmployeeId(), day.getWorkDate(), day.getAmount(),
                    day.getJustification(), defaultRate, false);
        }

        public static WorkDayResponse of(WorkDayEntry entry, Long employeeId, BigDecimal defaultRate) {
            return new WorkDayResponse(entry.id(), employeeId, entry.date(), entry.amount(),
                    entry.justification(), defaultRate, entry.automatic());
        }
    }

    // --- Acréscimos e abatimentos -----------------------------------------------------------

    public record AdjustmentRequest(
            @NotNull(message = "A colaboradora é obrigatória") Long employeeId,
            @NotNull(message = "A data é obrigatória") LocalDate date,
            @NotNull(message = "O tipo é obrigatório") PayrollAdjustmentType type,
            @NotNull(message = "O valor é obrigatório")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero") BigDecimal amount,
            @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres") String description
    ) {}

    public record AdjustmentResponse(
            Long id,
            Long employeeId,
            LocalDate date,
            PayrollAdjustmentType type,
            BigDecimal amount,
            String description,
            boolean addition
    ) {
        public static AdjustmentResponse of(PayrollAdjustment a) {
            return new AdjustmentResponse(a.getId(), a.getEmployeeId(), a.getDate(), a.getType(), a.getAmount(),
                    a.getDescription(), a.getType().isAddition());
        }
    }

    // --- Pagamentos (período quitado) ---------------------------------------------------------

    /**
     * Registro de "período pago". O valor NÃO vem daqui: é sempre o que o sistema calcula pro
     * período — pagar diferente se faz com um lançamento (bônus/desconto) antes.
     */
    public record PaymentRequest(
            @NotNull(message = "A colaboradora é obrigatória") Long employeeId,
            @NotNull(message = "O início do período é obrigatório") LocalDate periodStart,
            @NotNull(message = "O fim do período é obrigatório") LocalDate periodEnd,
            @NotNull(message = "A data do pagamento é obrigatória") LocalDate paidAt,
            @Size(max = 20) String paymentMethod,
            @Size(max = 1000, message = "A observação deve ter no máximo 1000 caracteres") String notes
    ) {}

    public record PaymentResponse(
            Long id,
            Long employeeId,
            String employeeName,
            LocalDate periodStart,
            LocalDate periodEnd,
            /** Valor que o sistema calculou quando o pagamento foi registrado. */
            BigDecimal calculatedAmount,
            BigDecimal amountPaid,
            LocalDate paidAt,
            String paymentMethod,
            String notes,
            /** Quanto o sistema calcula HOJE pro mesmo período (null quando não foi recalculado). */
            BigDecimal recalculatedAmount,
            /** O recalculado difere do registrado — algo do período foi editado depois de pago. */
            boolean changedSincePayment
    ) {}

    // --- Composição e resumo -------------------------------------------------------------------

    public record BreakdownResponse(
            BigDecimal fixedSalary,
            int workDaysCount,
            BigDecimal dailyTotal,
            BigDecimal serviceCommission,
            BigDecimal appointmentProductCommission,
            BigDecimal saleProductCommission,
            BigDecimal totalCommission,
            BigDecimal additions,
            BigDecimal discounts,
            BigDecimal advances,
            BigDecimal salonCost,
            BigDecimal netPayable
    ) {
        public static BreakdownResponse of(PayrollBreakdown b) {
            return new BreakdownResponse(b.fixedSalary(), b.workDaysCount(), b.dailyTotal(),
                    b.serviceCommission(), b.appointmentProductCommission(), b.saleProductCommission(),
                    b.totalCommission(), b.additions(), b.discounts(), b.advances(), b.salonCost(), b.netPayable());
        }
    }

    /** PAGO = todos os dias do período cobertos por pagamentos registrados. */
    public enum PeriodStatus { PAGO, PARCIAL, EM_ABERTO }

    public record EmployeeSummary(
            Long employeeId,
            String employeeName,
            String remunerationType,
            BigDecimal remunerationValue,
            boolean active,
            int doneAppointmentsCount,
            BreakdownResponse breakdown,
            PeriodStatus periodStatus,
            long coveredDays,
            long totalDays,
            /** Soma dos pagamentos registrados que tocam o período. */
            BigDecimal paidInPeriod,
            /** Quanto ainda falta pagar deste período (zero se quitado). */
            BigDecimal outstanding,
            /** Pagamento mais recente da colaboradora (null = nunca pago) — independe do filtro. */
            LastPayment lastPayment,
            /** Pagamentos que tocam o filtro, com o período REAL de cada um (contíguos unidos). */
            List<CoverageRange> paidRanges,
            /** Dias do filtro sem nenhum pagamento. */
            List<CoverageRange> unpaidRanges,
            /** Quanto o recalculado de hoje difere do valor registrado nos pagamentos que tocam o
             *  filtro (algo foi editado depois de pago). Positivo = falta pagar. */
            BigDecimal pendingDifference,
            /** Há um buraco entre pagamentos registrados (algum período no meio ficou sem pagar). */
            boolean hasPaymentGaps
    ) {}

    /** Faixa de dias; {@code amount} = soma paga na faixa (null em faixa sem pagamento). */
    public record CoverageRange(LocalDate start, LocalDate end, BigDecimal amount) {}

    /** Prévia do "marcar como pago": valor que será registrado e, se houver, o conflito de datas. */
    public record PaymentPreview(BigDecimal calculatedAmount, String conflict, BreakdownResponse breakdown) {}

    /** Data em que foi pago, a que período se refere e quanto. */
    public record LastPayment(LocalDate paidAt, LocalDate periodStart, LocalDate periodEnd, BigDecimal amountPaid) {}

    public record SummaryResponse(
            LocalDate periodStart,
            LocalDate periodEnd,
            List<EmployeeSummary> items,
            BigDecimal totalSalonCost,
            BigDecimal totalNetPayable,
            BigDecimal totalPaidInPeriod,
            BigDecimal totalOutstanding
    ) {}

    public record AppointmentLine(
            Long appointmentId,
            LocalDate date,
            String clientName,
            String serviceNames,
            BigDecimal servicesValue,
            BigDecimal productsValue,
            BigDecimal serviceCommission,
            BigDecimal productCommission,
            boolean commissionManual,
            String paymentStatus
    ) {}

    public record SaleLine(
            Long cashFlowId,
            LocalDate date,
            String description,
            BigDecimal amount,
            BigDecimal commission
    ) {}

    public record EmployeeDetailResponse(
            EmployeeSummary summary,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<AppointmentLine> appointments,
            List<SaleLine> productSales,
            List<WorkDayResponse> workDays,
            /** Dias do período com atendimento concluído que foram desmarcados (não contam como diária). */
            List<LocalDate> unmarkedAppointmentDates,
            List<AdjustmentResponse> adjustments,
            /** Todos os pagamentos da colaboradora (não só os do período), do mais recente pro mais antigo. */
            List<PaymentResponse> payments
    ) {}
}
