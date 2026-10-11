package com.cristiane.salon.models.cashflow.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.BusinessException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.audit.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cristiane.salon.models.cashflow.dto.CashFlowRequest;
import com.cristiane.salon.models.cashflow.dto.CashFlowResponse;
import com.cristiane.salon.models.cashflow.dto.SaleUpdateRequest;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.entity.CashFlowItem;
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
        if (to == null) to = salonClock.today().with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        return cashFlowRepository.findByDateBetween(from, to).stream()
                .map(cf -> CashFlowResponse.fromEntity(cf, businessSettingsService.getProductCommissionPercent()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CashFlowResponse> findByPeriod(LocalDate from, LocalDate to, Pageable pageable) {
        DateRangeValidator.validate(from, to);
        if (from == null) from = salonClock.today().withDayOfMonth(1);
        if (to == null) to = salonClock.today().with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        BigDecimal currentPct = businessSettingsService.getProductCommissionPercent();
        return cashFlowRepository.findByDateBetween(from, to, pageable)
                .map(cf -> CashFlowResponse.fromEntity(cf, currentPct));
    }

    @Transactional
    public CashFlowResponse create(CashFlowRequest request) {
        CashFlow cashFlow = new CashFlow();

        if (request.items() != null && !request.items().isEmpty()) {
            if (!"INCOME".equalsIgnoreCase(request.type())) {
                throw new BadRequestException("Venda de produtos deve ser um registro de entrada (INCOME).");
            }
            cashFlow.setType(CashFlowType.INCOME);
            List<SaleUpdateRequest.Item> items = request.items().stream()
                    .map(i -> {
                        if (i.customPrice() != null && i.customPrice().compareTo(BigDecimal.ZERO) < 0) {
                            throw new BadRequestException("O preço customizado não pode ser negativo");
                        }
                        return new SaleUpdateRequest.Item(i.productId(), i.quantity(), i.customPrice());
                    })
                    .toList();
            applySaleItems(cashFlow, items, request.description(), true);
            // Comissão de produto é única do salão (SalonBusinessSettings), não por funcionária —
            // vale pra qualquer uma que vender, inclusive Salário Fixo. O % fica congelado na venda.
            cashFlow.setCommissionPercent(businessSettingsService.getProductCommissionPercent());
            applySeller(cashFlow, request.employeeId(), null);
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

    /**
     * Edita um lançamento manual (tipo, valor, descrição, data) — só o que foi cadastrado direto
     * nesta tela. Lançamento gerado automaticamente por um agendamento (billAppointmentOnce)
     * não pode ser editado por aqui: editar pelo próprio agendamento mantém os dois em dia
     * (ver syncCashFlowAmountIfAlreadyBilled no AppointmentService), editar aqui os deixaria
     * dessincronizados. Vendedor/comissão de venda avulsa também não mudam nesta edição — só
     * corrige o essencial (valor, descrição, data, tipo).
     */
    @Transactional
    public CashFlowResponse update(Long id, CashFlowRequest request) {
        CashFlow cashFlow = cashFlowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro não encontrado"));

        if (cashFlow.getAppointment() != null) {
            throw new BusinessException(
                    "Este lançamento foi gerado automaticamente por um agendamento — edite os serviços, produtos ou despesas do agendamento em vez de editar aqui.");
        }
        if (!cashFlow.getItems().isEmpty() || cashFlow.getEmployee() != null) {
            throw new BusinessException("Este lançamento é uma venda de produto — edite pela edição da venda.");
        }

        CashFlowType type;
        try {
            type = CashFlowType.valueOf(request.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tipo de fluxo de caixa inválido. Use INCOME ou EXPENSE.");
        }

        cashFlow.setType(type);
        cashFlow.setAmount(request.amount());
        cashFlow.setDescription(request.description());
        cashFlow.setDate(request.date());

        return CashFlowResponse.fromEntity(cashFlowRepository.save(cashFlow));
    }

    @Transactional(readOnly = true)
    public CashFlowResponse findById(Long id) {
        return CashFlowResponse.fromEntity(cashFlowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro não encontrado")),
                businessSettingsService.getProductCommissionPercent());
    }

    /**
     * Edita uma venda avulsa por completo — itens, vendedora, data, comissão — como se edita um
     * agendamento. Venda antiga (sem itens registrados) é editada pelo total. A comissão é
     * recalculada com o % congelado na venda, a não ser que venha digitada à mão.
     */
    @Transactional
    public CashFlowResponse updateSale(Long id, SaleUpdateRequest request) {
        CashFlow cashFlow = cashFlowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro não encontrado"));
        if (cashFlow.getAppointment() != null) {
            throw new BusinessException(
                    "Este lançamento foi gerado por um agendamento — edite pelo próprio agendamento.");
        }
        if (cashFlow.getType() != CashFlowType.INCOME) {
            throw new BadRequestException("Só lançamentos de entrada podem ser editados como venda.");
        }

        if (request.items() != null && !request.items().isEmpty()) {
            applySaleItems(cashFlow, request.items(), request.description(), false);
        } else {
            if (!cashFlow.getItems().isEmpty()) {
                throw new BadRequestException("A venda precisa ter ao menos um produto.");
            }
            if (request.amount() == null) {
                throw new BadRequestException("Informe o valor da venda.");
            }
            cashFlow.setAmount(request.amount());
            if (request.description() != null && !request.description().isBlank()) {
                cashFlow.setDescription(truncate(request.description().trim(), 500));
            }
        }
        cashFlow.setDate(request.date());
        if (cashFlow.getCommissionPercent() == null || cashFlow.getCommissionPercent().signum() <= 0) {
            // Venda anterior à V87: congela o % vigente agora pra ela.
            cashFlow.setCommissionPercent(businessSettingsService.getProductCommissionPercent());
        }
        applySeller(cashFlow, request.employeeId(), request.commissionAmount());
        return CashFlowResponse.fromEntity(cashFlowRepository.save(cashFlow));
    }

    /** Substitui os itens da venda, recalcula o total e remonta a descrição. */
    private void applySaleItems(CashFlow cashFlow, List<SaleUpdateRequest.Item> requests, String note,
                                boolean requireActive) {
        cashFlow.getItems().clear();
        BigDecimal total = BigDecimal.ZERO;
        List<String> summary = new ArrayList<>();
        for (SaleUpdateRequest.Item req : requests) {
            Product product = productRepository.findById(req.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + req.productId() + " não encontrado."));
            // Na criação o produto precisa estar ativo; ao editar uma venda antiga, um produto
            // desativado depois não pode impedir a correção.
            if (requireActive && (product.getActive() == null || !product.getActive())) {
                throw new BadRequestException("Produto '" + product.getName() + "' não está ativo.");
            }
            if (req.unitPrice() == null && product.getPrice() == null) {
                throw new BadRequestException("O produto '" + product.getName()
                        + "' não tem preço de venda cadastrado — informe o preço desta venda.");
            }
            // Preço especial pra essa venda (ex.: cliente fiel, negociação) sobrescreve o do
            // catálogo — só nesta venda, o cadastro do produto não muda.
            BigDecimal unitPrice = req.unitPrice() != null ? req.unitPrice() : product.getPrice();
            CashFlowItem item = new CashFlowItem();
            item.setCashFlow(cashFlow);
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setQuantity(req.quantity());
            item.setUnitPrice(unitPrice);
            cashFlow.getItems().add(item);
            total = total.add(item.getTotal());

            boolean isCustomPrice = product.getPrice() == null || unitPrice.compareTo(product.getPrice()) != 0;
            summary.add(req.quantity() + "x " + product.getName()
                    + (isCustomPrice ? " (a R$ " + unitPrice.setScale(2, java.math.RoundingMode.HALF_UP) + " cada)" : ""));
        }
        cashFlow.setAmount(total);

        String itemsSummary = String.join(", ", summary);
        // O front manda a descrição automática ("Venda de Produtos: ...") de volta; só um texto
        // livre de verdade vira prefixo — antes a descrição saía com os itens repetidos.
        boolean autoDescription = note == null || note.isBlank()
                || note.trim().toLowerCase().startsWith("venda de produto");
        String composed = autoDescription
                ? "Venda de Produtos: " + itemsSummary
                : note.trim() + " (" + itemsSummary + ")";
        // Um carrinho com muitos itens pode passar do limite da coluna mesmo sem texto livre —
        // trunca em vez de estourar a constraint do banco (ver V82).
        cashFlow.setDescription(truncate(composed, 500));
    }

    /** Vendedora e comissão: sem vendedora não há comissão; comissão manual vence o cálculo. */
    private void applySeller(CashFlow cashFlow, Long employeeId, BigDecimal manualCommission) {
        if (employeeId == null) {
            cashFlow.setEmployee(null);
            cashFlow.setCommissionAmount(null);
            cashFlow.setCommissionManual(false);
            return;
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionária não encontrada"));
        cashFlow.setEmployee(employee);
        if (manualCommission != null) {
            cashFlow.setCommissionAmount(manualCommission);
            cashFlow.setCommissionManual(true);
            return;
        }
        BigDecimal pct = cashFlow.getCommissionPercent();
        cashFlow.setCommissionAmount(pct == null || pct.signum() <= 0 ? null
                : cashFlow.getAmount().multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP));
        cashFlow.setCommissionManual(false);
    }

    @Transactional
    public void delete(Long id) {
        if (!cashFlowRepository.existsById(id)) {
            throw new ResourceNotFoundException("Registro não encontrado");
        }
        cashFlowRepository.deleteById(id);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
