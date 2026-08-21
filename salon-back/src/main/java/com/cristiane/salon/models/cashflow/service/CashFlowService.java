package com.cristiane.salon.models.cashflow.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.audit.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cristiane.salon.models.cashflow.dto.CashFlowRequest;
import com.cristiane.salon.models.cashflow.dto.CashFlowResponse;
import com.cristiane.salon.models.cashflow.dto.CashFlowItemRequest;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.businesssettings.service.SalonBusinessSettingsService;
import com.cristiane.salon.models.cashflow.repository.CashFlowRepository;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.repository.ProductRepository;
import com.cristiane.salon.utils.DateRangeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CashFlowService {

    private final CashFlowRepository cashFlowRepository;
    private final AppointmentRepository appointmentRepository;
    private final ProductRepository productRepository;
    private final EmployeeRepository employeeRepository;
    private final SalonBusinessSettingsService businessSettingsService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final SalonClock salonClock;

    @Transactional(readOnly = true)
    public List<CashFlowResponse> findByPeriod(LocalDate from, LocalDate to) {
        DateRangeValidator.validate(from, to);
        if (from == null) from = salonClock.today().withDayOfMonth(1);
        if (to == null) to = salonClock.today().plusDays(30);

        return cashFlowRepository.findByDateBetween(from, to).stream()
                .map(CashFlowResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CashFlowResponse> findByPeriod(LocalDate from, LocalDate to, Pageable pageable) {
        DateRangeValidator.validate(from, to);
        if (from == null) from = salonClock.today().withDayOfMonth(1);
        if (to == null) to = salonClock.today().plusDays(30);

        return cashFlowRepository.findByDateBetween(from, to, pageable).map(CashFlowResponse::fromEntity);
    }

    @Transactional
    public CashFlowResponse create(CashFlowRequest request) {
        CashFlow cashFlow = new CashFlow();

        if (request.items() != null && !request.items().isEmpty()) {
            if (!"INCOME".equalsIgnoreCase(request.type())) {
                throw new BadRequestException("Venda de produtos deve ser um registro de entrada (INCOME).");
            }
            cashFlow.setType(CashFlowType.INCOME);

            BigDecimal totalAmount = BigDecimal.ZERO;
            List<String> itemDescriptions = new ArrayList<>();

            for (CashFlowItemRequest item : request.items()) {
                Product product = productRepository.findById(item.productId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + item.productId() + " não encontrado."));

                if (product.getActive() == null || !product.getActive()) {
                    throw new BadRequestException("Produto '" + product.getName() + "' não está ativo.");
                }

                BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.quantity()));
                totalAmount = totalAmount.add(itemTotal);

                itemDescriptions.add(item.quantity() + "x " + product.getName());
            }

            cashFlow.setAmount(totalAmount);

            if (request.employeeId() != null) {
                Employee employee = employeeRepository.findById(request.employeeId())
                        .orElseThrow(() -> new ResourceNotFoundException("Funcionária não encontrada"));
                cashFlow.setEmployee(employee);
                // Comissão de produto é única do salão (SalonBusinessSettings), não por
                // funcionária — vale pra qualquer uma que vender, inclusive Salário Fixo.
                BigDecimal productCommissionPercent = businessSettingsService.getProductCommissionPercent();
                if (productCommissionPercent != null) {
                    cashFlow.setCommissionAmount(
                            totalAmount.multiply(productCommissionPercent)
                                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP));
                }
            }

            String itemsSummary = String.join(", ", itemDescriptions);
            String desc = request.description();
            if (desc == null || desc.trim().isEmpty() || desc.equalsIgnoreCase("Venda de Produtos") || desc.equalsIgnoreCase("Venda de Produto")) {
                cashFlow.setDescription("Venda de Produtos: " + itemsSummary);
            } else {
                cashFlow.setDescription(desc + " (" + itemsSummary + ")");
            }

            cashFlow.setDate(request.date());
        } else {
            try {
                cashFlow.setType(CashFlowType.valueOf(request.type().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Tipo de fluxo de caixa inválido. Use INCOME ou EXPENSE.");
            }
            cashFlow.setAmount(request.amount());
            cashFlow.setDescription(request.description());
            cashFlow.setDate(request.date());

            if (request.appointmentId() != null) {
                Appointment appointment = appointmentRepository.findById(request.appointmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
                cashFlow.setAppointment(appointment);
            }
        }

        CashFlowResponse response = CashFlowResponse.fromEntity(cashFlowRepository.save(cashFlow));

        // Audit Log manual
        try {
            String action = (request.items() != null && !request.items().isEmpty()) 
                ? "PRODUCT_SALE_REGISTERED" 
                : "CASHFLOW_ENTRY_CREATED";
                
            Long userId = null;
            String userEmail = "SYSTEM";
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                userEmail = auth.getName();
                if (auth.getPrincipal() instanceof com.cristiane.salon.models.user.entity.User user) {
                    userId = user.getId();
                }
            }
            
            String details = null;
            try {
                details = objectMapper.writeValueAsString(request);
            } catch (Exception ignored) {}
            
            auditLogService.logAction(
                    userId,
                    userEmail,
                    action,
                    "CashFlow",
                    response.id(),
                    details,
                    "SUCCESS"
            );
        } catch (Exception e) {
            // Log silenciosamente
        }

        return response;
    }

    @Transactional
    public void delete(Long id) {
        if (!cashFlowRepository.existsById(id)) {
            throw new ResourceNotFoundException("Registro não encontrado");
        }
        cashFlowRepository.deleteById(id);
    }
}
