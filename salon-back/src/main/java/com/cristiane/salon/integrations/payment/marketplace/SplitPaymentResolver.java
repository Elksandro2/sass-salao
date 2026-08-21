package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.employee.entity.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Ponto único de decisão "este pagamento deve ser dividido?" — junta o cálculo
 * ({@link SplitPaymentCalculator}) com a resolução de um access token válido da funcionária
 * ({@link EmployeeMercadoPagoConnectionService}). Se qualquer uma das duas condições faltar
 * (não conectada, sem comissão a receber neste atendimento, token não renova), devolve vazio —
 * quem chama simplesmente cai no fluxo sem split que já existia antes desta feature.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SplitPaymentResolver {

    private final SplitPaymentCalculator calculator;
    private final EmployeeMercadoPagoConnectionService connectionService;

    public record SplitPaymentInfo(BigDecimal applicationFee, BigDecimal employeeShare, String sellerAccessToken) {
    }

    public Optional<SplitPaymentInfo> resolve(Appointment appointment, Employee employee) {
        if (employee == null) {
            return Optional.empty();
        }

        Optional<SplitPaymentCalculator.SplitResult> split = calculator.calculate(appointment, employee);
        if (split.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> accessToken = connectionService.resolveValidAccessToken(employee.getId());
        if (accessToken.isEmpty()) {
            log.info("Funcionária (employeeId={}) tem comissão individual mas não tem Mercado Pago conectado (ou token não renovou) — pagamento segue sem split", employee.getId());
            return Optional.empty();
        }

        return Optional.of(new SplitPaymentInfo(split.get().applicationFee(), split.get().employeeShare(), accessToken.get()));
    }
}
