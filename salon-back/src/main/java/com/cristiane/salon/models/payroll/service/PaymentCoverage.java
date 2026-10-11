package com.cristiane.salon.models.payroll.service;

import com.cristiane.salon.models.payroll.entity.PayrollPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Quais dias de um período estão cobertos por pagamentos da folha e quais não. Usado na Folha
 * de Pagamento e no detalhamento de remunerações do relatório — o que vale é o período que o
 * pagamento cobre, não a data em que ele foi feito.
 */
public final class PaymentCoverage {

    private PaymentCoverage() {}

    /** Faixa de dias; {@code amount} = soma dos pagamentos da faixa (null nas faixas sem pagamento). */
    public record Range(LocalDate start, LocalDate end, BigDecimal amount) {}

    /**
     * Pagamentos que tocam [from, to], com o período REAL de cada um (pode começar antes ou
     * terminar depois do filtro). Pagamentos que se encostam (1–15 e 16–30) viram uma faixa só,
     * com os valores somados.
     */
    public static List<Range> paidRanges(List<PayrollPayment> payments, LocalDate from, LocalDate to) {
        List<PayrollPayment> touching = payments.stream()
                .filter(p -> !p.getPeriodEnd().isBefore(from) && !p.getPeriodStart().isAfter(to))
                .sorted(Comparator.comparing(PayrollPayment::getPeriodStart))
                .toList();
        List<Range> merged = new ArrayList<>();
        for (PayrollPayment p : touching) {
            if (!merged.isEmpty()) {
                Range last = merged.get(merged.size() - 1);
                if (!p.getPeriodStart().isAfter(last.end().plusDays(1))) {
                    LocalDate end = p.getPeriodEnd().isAfter(last.end()) ? p.getPeriodEnd() : last.end();
                    merged.set(merged.size() - 1, new Range(last.start(), end, last.amount().add(p.getAmountPaid())));
                    continue;
                }
            }
            merged.add(new Range(p.getPeriodStart(), p.getPeriodEnd(), p.getAmountPaid()));
        }
        return merged;
    }

    /** Faixas de [from, to] que nenhum pagamento cobre. */
    public static List<Range> unpaidRanges(List<PayrollPayment> payments, LocalDate from, LocalDate to) {
        List<Range> gaps = new ArrayList<>();
        LocalDate cursor = from;
        for (Range paid : paidRanges(payments, from, to)) {
            if (paid.start().isAfter(cursor)) {
                gaps.add(new Range(cursor, paid.start().minusDays(1), null));
            }
            if (!paid.end().isBefore(cursor)) {
                cursor = paid.end().plusDays(1);
            }
            if (cursor.isAfter(to)) break;
        }
        if (!cursor.isAfter(to)) {
            gaps.add(new Range(cursor, to, null));
        }
        return gaps;
    }
}
