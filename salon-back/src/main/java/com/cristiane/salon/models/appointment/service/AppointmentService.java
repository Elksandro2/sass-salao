package com.cristiane.salon.models.appointment.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.exception.BusinessException;
import com.cristiane.salon.models.appointment.dto.GeneratePixRequest;
import com.cristiane.salon.utils.CpfValidator;
import com.cristiane.salon.integrations.payment.service.MercadoPagoPaymentService;
import com.cristiane.salon.models.appointment.dto.AppointmentExpenseRequest;
import com.cristiane.salon.models.appointment.dto.AppointmentFilter;
import com.cristiane.salon.models.appointment.dto.AppointmentProductRequest;
import com.cristiane.salon.models.appointment.dto.AppointmentRequest;
import com.cristiane.salon.models.appointment.dto.AppointmentResponse;
import com.cristiane.salon.models.appointment.dto.AppointmentServiceRequest;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.entity.AppointmentExpenseItem;
import com.cristiane.salon.models.appointment.entity.AppointmentProductItem;
import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.enums.ExpenseValueType;
import com.cristiane.salon.models.appointment.enums.PaymentMethod;
import com.cristiane.salon.models.appointment.enums.PaymentStatus;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.appointment.specification.AppointmentSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.cashflow.repository.CashFlowRepository;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.repository.ProductRepository;
import com.cristiane.salon.models.service.entity.SalonService;
import com.cristiane.salon.models.service.repository.SalonServiceRepository;
import com.cristiane.salon.integrations.email.service.EmailService;
import com.cristiane.salon.integrations.push.service.PushService;
import com.cristiane.salon.models.featureflag.service.FeatureFlagService;
import com.cristiane.salon.models.salonprofile.service.SalonProfileService;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import com.cristiane.salon.models.audit.AuditLogService;
import com.mercadopago.resources.payment.Payment;
import org.springframework.dao.DataIntegrityViolationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final EmployeeRepository employeeRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CashFlowRepository cashFlowRepository;
    private final FeatureFlagService featureFlagService;
    private final EmailService emailService;
    private final PushService pushService;
    private final SalonProfileService salonProfileService;
    private final MercadoPagoPaymentService mercadoPagoPaymentService;
    private final com.cristiane.salon.integrations.payment.marketplace.SplitPaymentResolver splitPaymentResolver;
    private final AuditLogService auditLogService;
    private final SalonClock salonClock;

    private void notifyAdminsOfNewRequest(Appointment appointment) {
        for (User admin : userRepository.findByRole_NameAndActiveTrue("ADMIN")) {
            pushService.sendToUser(admin.getId(), "Nova solicitação de agendamento 📅",
                    appointment.getClient().getName() + " solicitou " + appointment.getServiceNames(),
                    "/admin/appointments");
        }
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));
    }

    private boolean isStaff(User user) {
        String role = user.getRoleName();
        return "ADMIN".equals(role) || "GERENTE_DE_ATENDIMENTO".equals(role);
    }

    /**
     * Quem pode criar um agendamento em nome de um cliente (com horário definido na hora).
     *
     * <p>ADMIN/GERENTE cobrem qualquer profissional. FUNCIONARIA também entra aqui — cliente
     * chega no salão, ela cadastra na hora — mas fica restrita a se atribuir como a profissional
     * do próprio atendimento (ver checagem em {@link #create}); ela não pode agendar em nome de
     * uma colega, diferente de ADMIN/GERENTE.
     */
    private boolean canCreateForClient(User user) {
        return isStaff(user) || "FUNCIONARIA".equals(user.getRoleName());
    }

    /**
     * A funcionária é a profissional atribuída a este atendimento?
     *
     * <p>Compara pelo usuário e não pelo id de Employee porque é o usuário que está autenticado;
     * o vínculo Employee -> User é 1:1.
     */
    private boolean isAssignedProfessional(User user, Appointment appointment) {
        return appointment.getEmployee() != null
                && appointment.getEmployee().getUser() != null
                && appointment.getEmployee().getUser().getId().equals(user.getId());
    }

    /**
     * Quem pode mexer neste agendamento (definir horário, mudar status, recusar).
     *
     * <p>ADMIN e GERENTE_DE_ATENDIMENTO agem sobre qualquer agendamento — a recepção cuida da
     * agenda do salão inteiro. A FUNCIONARIA age só onde ela mesma é a profissional atribuída.
     *
     * <p>Isso conserta os dois lados de um mesmo buraco: a funcionária não conseguia definir o
     * horário nem dos próprios atendimentos (confirm exigia isStaff), e ao mesmo tempo conseguia
     * alterar o status do atendimento de qualquer colega, porque a permissão de
     * {@code PATCH /status} foi concedida ao cargo (migration V24) sem nenhuma checagem de dono.
     */
    private void assertCanManage(Appointment appointment, String acao) {
        User current = getAuthenticatedUser();
        if (isStaff(current) || isAssignedProfessional(current, appointment)) {
            return;
        }
        throw new UnauthorizedException(
                "Você só pode " + acao + " agendamentos em que você é a profissional responsável");
    }

    /**
     * Restringe a listagem à agenda da própria funcionária. ADMIN e GERENTE continuam vendo o
     * salão inteiro; para a FUNCIONARIA, qualquer employeeId que venha da requisição é ignorado,
     * senão bastaria trocar o parâmetro na URL para ver a agenda das colegas.
     */
    private AppointmentFilter restrictToOwnAgendaIfProfessional(AppointmentFilter filter) {
        User current = getAuthenticatedUser();
        if (isStaff(current) || !"FUNCIONARIA".equals(current.getRoleName())) {
            return filter;
        }
        Long ownEmployeeId = employeeRepository.findByUserId(current.getId())
                .map(e -> e.getId())
                .orElseThrow(() -> new UnauthorizedException(
                        "Seu usuário não está vinculado a um cadastro de profissional"));

        return new AppointmentFilter(
                filter.status(), filter.paymentStatus(), ownEmployeeId, filter.clientId(),
                filter.clientName(), filter.startDate(), filter.endDate());
    }

    @Transactional
    public AppointmentResponse create(AppointmentRequest request) {
        User currentUser = getAuthenticatedUser();

        if ("CLIENTE".equals(currentUser.getRoleName()) && !featureFlagService.isEnabled("ENABLE_CUSTOMER_PORTAL")) {
            throw new AccessDeniedException("O portal do cliente está temporariamente desativado.");
        }

        boolean staffCreatesForClient = canCreateForClient(currentUser) && request.clientId() != null;

        if (!staffCreatesForClient && !featureFlagService.isEnabled("CLIENT_BOOKING")) {
            throw new BadRequestException("Agendamentos online para clientes estão temporariamente desativados.");
        }

        User client;
        if (staffCreatesForClient) {
            client = userRepository.findById(request.clientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado"));
        } else {
            client = currentUser;
        }

        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado"));

        if (staffCreatesForClient && "FUNCIONARIA".equals(currentUser.getRoleName())
                && (employee.getUser() == null || !employee.getUser().getId().equals(currentUser.getId()))) {
            throw new UnauthorizedException("Você só pode criar agendamentos em que você é a profissional responsável");
        }

        List<AppointmentServiceRequest> serviceRequests = request.services();
        List<SalonService> resolvedServices = serviceRequests.stream()
                .map(sr -> salonServiceRepository.findById(sr.serviceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado")))
                .collect(Collectors.toList());

        for (SalonService svc : resolvedServices) {
            if (!svc.getActive()) {
                throw new BadRequestException("Este serviço não está disponível: " + svc.getName());
            }
        }

        if (staffCreatesForClient) {
            if (request.scheduledAt() == null) {
                throw new BadRequestException("Informe data e hora do agendamento");
            }
            if (request.scheduledAt().isBefore(salonClock.now())) {
                throw new BadRequestException("Não é possível agendar no passado");
            }

            for (AppointmentServiceRequest sr : serviceRequests) {
                if (sr.customPrice() != null && sr.customPrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException("O preço customizado não pode ser negativo");
                }
            }

            if (request.preferredDate() != null && request.preferredDate().isBefore(salonClock.today())) {
                throw new BadRequestException("A data preferida deve ser hoje ou uma data futura");
            }

            Appointment appointment = new Appointment();
            appointment.setClient(client);
            appointment.setEmployee(employee);
            appointment.setScheduledAt(request.scheduledAt());
            appointment.setPreferredDate(request.preferredDate());
            appointment.setClientNotes(request.clientNotes());
            appointment.setStatus(AppointmentStatus.CONFIRMED);
            appointment.setServices(buildServiceItems(appointment, serviceRequests, resolvedServices, true));
            if (request.products() != null && !request.products().isEmpty()) {
                appointment.setProducts(buildProductItems(appointment, request.products()));
            }

            Appointment saved = appointmentRepository.save(appointment);
            emailService.sendConfirmationNotificationToClient(saved);
            pushService.sendToUser(client.getId(), "Agendamento confirmado! ✅",
                    "Seu horário de " + saved.getServiceNames() + " foi confirmado.", "/my-appointments");
            return AppointmentResponse.fromEntity(saved);
        }

        if (request.scheduledAt() != null) {
            throw new BadRequestException("O horário será definido pelo salão após aceitar seu pedido");
        }

        if (request.preferredDate() != null && request.preferredDate().isBefore(salonClock.today())) {
            throw new BadRequestException("A data preferida deve ser hoje ou uma data futura");
        }

        // Só bloqueia a PREFERÊNCIA do cliente (issue #116) — nunca a equipe: staffCreatesForClient
        // (acima) e confirm() continuam livres para encaixar alguém fora do expediente normal.
        if (request.preferredDate() != null && !salonProfileService.isDayOpen(request.preferredDate().getDayOfWeek())) {
            throw new BadRequestException("O salão não funciona nesse dia da semana. Escolha outra data de preferência.");
        }

        String notes = request.clientNotes();
        if (notes != null && notes.length() > 4000) {
            throw new BadRequestException("Observações muito longas (máx. 4000 caracteres)");
        }

        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setEmployee(employee);
        appointment.setPreferredDate(request.preferredDate());
        appointment.setClientNotes(notes);
        appointment.setStatus(AppointmentStatus.REQUESTED);
        appointment.setServices(buildServiceItems(appointment, serviceRequests, resolvedServices, false));

        Appointment saved = appointmentRepository.save(appointment);
        emailService.sendRequestNotificationToStaff(saved);
        notifyAdminsOfNewRequest(saved);
        return AppointmentResponse.fromEntity(saved);
    }

    /**
     * No fluxo do cliente (auto-agendamento) customPrice/customServiceNotes do request são
     * ignorados — essas sobrescritas só têm efeito quando a equipe cria o agendamento, evitando
     * que o cliente manipule o próprio preço cobrado.
     */
    private List<AppointmentServiceItem> buildServiceItems(Appointment appointment,
                                                            List<AppointmentServiceRequest> serviceRequests,
                                                            List<SalonService> resolvedServices,
                                                            boolean allowCustomization) {
        List<AppointmentServiceItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < serviceRequests.size(); i++) {
            AppointmentServiceRequest sr = serviceRequests.get(i);
            AppointmentServiceItem item = new AppointmentServiceItem();
            item.setAppointment(appointment);
            item.setSalonService(resolvedServices.get(i));
            if (allowCustomization) {
                item.setCustomPrice(sr.customPrice());
                item.setCustomServiceNotes(sr.customServiceNotes());
            }
            items.add(item);
        }
        return items;
    }

    private List<AppointmentProductItem> buildProductItems(Appointment appointment,
                                                             List<AppointmentProductRequest> requests) {
        List<AppointmentProductItem> items = new java.util.ArrayList<>();
        for (AppointmentProductRequest pr : requests) {
            if (pr.customPrice() != null && pr.customPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("O preço customizado do produto não pode ser negativo");
            }
            Product product = productRepository.findById(pr.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
            if (!product.getActive()) {
                throw new BadRequestException("Este produto não está disponível: " + product.getName());
            }
            AppointmentProductItem item = new AppointmentProductItem();
            item.setAppointment(appointment);
            item.setProduct(product);
            item.setQuantity(pr.quantity());
            item.setCustomPrice(pr.customPrice());
            items.add(item);
        }
        return items;
    }

    private List<AppointmentExpenseItem> buildExpenseItems(Appointment appointment,
                                                             List<AppointmentExpenseRequest> requests) {
        List<AppointmentExpenseItem> items = new java.util.ArrayList<>();
        for (AppointmentExpenseRequest er : requests) {
            ExpenseValueType valueType;
            try {
                valueType = ExpenseValueType.valueOf(er.valueType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Tipo de valor de despesa inválido");
            }
            if (valueType == ExpenseValueType.PERCENTAGE && er.value().compareTo(new BigDecimal("100")) > 0) {
                throw new BadRequestException("A porcentagem da despesa não pode ser maior que 100");
            }
            AppointmentExpenseItem item = new AppointmentExpenseItem();
            item.setAppointment(appointment);
            item.setDescription(er.description());
            item.setValueType(valueType);
            item.setValue(er.value());
            items.add(item);
        }
        return items;
    }

    /**
     * O que trava a edição é o PAGAMENTO já ter acontecido (PAID, via plataforma, ou MANUAL,
     * confirmado pela equipe fora da plataforma) — não o status do agendamento em si. Um
     * atendimento marcado DONE mas ainda não pago continua editável (ex.: cliente comprou mais
     * um produto na saída, antes de fechar a conta); se ele já tiver sido faturado no Caixa
     * nesse meio tempo (todo DONE fatura, ver billAppointmentOnce), o valor lançado é
     * resincronizado com o novo total logo após a edição — ver syncCashFlowAmountIfAlreadyBilled.
     */
    private void assertNotBilled(Appointment appointment, String acao) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("Não é possível " + acao + " um agendamento cancelado.");
        }
        if (appointment.getPaymentStatus() == PaymentStatus.PAID || appointment.getPaymentStatus() == PaymentStatus.MANUAL) {
            throw new BusinessException("Não é possível " + acao + " depois que o atendimento foi pago.");
        }
    }

    /**
     * Se este agendamento já tinha sido faturado no Caixa (por ter passado por DONE antes desta
     * edição), mantém o valor lançado em dia com o novo total — sem isso, editar produtos/
     * despesas depois do DONE reintroduziria a divergência que assertNotBilled existe pra evitar.
     */
    private void syncCashFlowAmountIfAlreadyBilled(Appointment appointment) {
        cashFlowRepository.findByAppointmentId(appointment.getId()).ifPresent(cashFlow -> {
            cashFlow.setAmount(appointment.getGrandTotal());
            cashFlowRepository.save(cashFlow);
        });
    }

    /**
     * A cliente às vezes decide fazer mais alguma coisa depois de já ter sido atendida ou de o
     * agendamento já estar confirmado — sem grade de horário fixa, isso não tem nenhum impacto
     * de agenda, só precisa ficar registrado.
     */
    @Transactional
    public AppointmentResponse updateServices(Long id, List<AppointmentServiceRequest> serviceRequests) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        assertCanManage(appointment, "editar os serviços de");
        assertNotBilled(appointment, "editar os serviços de");

        if (serviceRequests == null || serviceRequests.isEmpty()) {
            throw new BadRequestException("Ao menos um serviço é obrigatório");
        }

        List<SalonService> resolvedServices = serviceRequests.stream()
                .map(sr -> salonServiceRepository.findById(sr.serviceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado")))
                .collect(Collectors.toList());

        for (SalonService svc : resolvedServices) {
            if (!svc.getActive()) {
                throw new BadRequestException("Este serviço não está disponível: " + svc.getName());
            }
        }

        for (AppointmentServiceRequest sr : serviceRequests) {
            if (sr.customPrice() != null && sr.customPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("O preço customizado não pode ser negativo");
            }
        }

        appointment.getServices().clear();
        appointment.getServices().addAll(buildServiceItems(appointment, serviceRequests, resolvedServices, true));

        Appointment saved = appointmentRepository.save(appointment);
        syncCashFlowAmountIfAlreadyBilled(saved);
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public AppointmentResponse updateProducts(Long id, List<AppointmentProductRequest> productRequests) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        assertCanManage(appointment, "editar os produtos de");
        assertNotBilled(appointment, "editar os produtos de");

        appointment.getProducts().clear();
        appointment.getProducts().addAll(buildProductItems(appointment, productRequests));

        Appointment saved = appointmentRepository.save(appointment);
        syncCashFlowAmountIfAlreadyBilled(saved);
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public AppointmentResponse updateExpenses(Long id, List<AppointmentExpenseRequest> expenseRequests) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        assertCanManage(appointment, "editar as despesas de");
        assertNotBilled(appointment, "editar as despesas de");

        appointment.getExpenses().clear();
        appointment.getExpenses().addAll(buildExpenseItems(appointment, expenseRequests));

        Appointment saved = appointmentRepository.save(appointment);
        syncCashFlowAmountIfAlreadyBilled(saved);
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public AppointmentResponse confirm(Long id, LocalDateTime scheduledAt) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        assertCanManage(appointment, "definir o horário de");

        if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
            throw new BadRequestException("Apenas solicitações pendentes de confirmação podem ser aprovadas");
        }

        if (scheduledAt.isBefore(salonClock.now())) {
            throw new BadRequestException("Não é possível confirmar um horário no passado");
        }

        appointment.setScheduledAt(scheduledAt);
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        Appointment saved = appointmentRepository.save(appointment);
        emailService.sendConfirmationNotificationToClient(saved);
        pushService.sendToUser(saved.getClient().getId(), "Agendamento confirmado! ✅",
                "Seu horário de " + saved.getServiceNames() + " foi confirmado.", "/my-appointments");
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public AppointmentResponse decline(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        assertCanManage(appointment, "recusar");

        if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
            throw new BadRequestException("Apenas solicitações em análise podem ser recusadas");
        }

        appointment.setStatus(AppointmentStatus.DECLINED);
        Appointment saved = appointmentRepository.save(appointment);
        // Notify client and staff that their request was declined
        emailService.sendCancellationNotification(saved);
        pushService.sendToUser(saved.getClient().getId(), "Agendamento cancelado",
                "Seu agendamento de " + saved.getServiceNames() + " foi cancelado.", "/my-appointments");
        return AppointmentResponse.fromEntity(saved);
    }

    /**
     * Observação interna da equipe sobre o atendimento — separada de clientNotes (o que o
     * cliente escreveu). Editável em qualquer status do agendamento, não só na criação, porque
     * costuma ser preenchida DEPOIS do atendimento acontecer (ver histórico do cliente).
     */
    @Transactional
    public AppointmentResponse updateInternalNotes(Long id, String internalNotes) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        assertCanManage(appointment, "anotar em");

        appointment.setInternalNotes(internalNotes);
        Appointment saved = appointmentRepository.save(appointment);
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointments() {
        User client = getAuthenticatedUser();
        return appointmentRepository.findByClientId(client.getId()).stream()
                .map(AppointmentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> findAll(AppointmentFilter filter, Pageable pageable) {
        AppointmentFilter scoped = restrictToOwnAgendaIfProfessional(filter);
        return appointmentRepository.findAll(AppointmentSpecifications.filter(scoped), pageable)
                .map(AppointmentResponse::fromEntity);
    }

    /** Usado internamente (ex.: motor de recomendações) quando é preciso o conjunto completo, sem paginação. */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> findAllInternal() {
        return appointmentRepository.findAll().stream()
                .map(AppointmentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppointmentResponse findById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));

        User currentUser = getAuthenticatedUser();
        boolean isAdmin = isStaff(currentUser);

        if (!isAdmin && !appointment.getClient().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Você não tem permissão para visualizar este agendamento");
        }

        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse generatePixPayment(Long id, GeneratePixRequest request) {
        if (!featureFlagService.isEnabled("MERCADO_PAGO_ATIVO")) {
            throw new BadRequestException("Pagamento via PIX está temporariamente desativado.");
        }

        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));

        User currentUser = getAuthenticatedUser();
        boolean isAdmin = isStaff(currentUser);

        // Regra 1: Apenas o dono do agendamento ou a equipe do salão podem gerar o PIX
        if (!isAdmin && !appointment.getClient().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Você não tem permissão para gerar pagamento para este agendamento");
        }

        // Regra 2: Não gerar se já estiver pago
        if (appointment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Este agendamento já está pago.");
        }

        // Regra 3: Não gerar para agendamentos cancelados (terminal state)
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestException("Não é possível gerar PIX para um agendamento cancelado.");
        }

        BigDecimal amount = appointment.getGrandTotal();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Este serviço não possui um valor configurado para cobrança.");
        }

        // Idempotency: Check if there's already a pending PIX payment
        if (appointment.getPaymentStatus() == PaymentStatus.PENDING &&
                appointment.getPixQrCode() != null &&
                appointment.getPaymentId() != null) {
            return AppointmentResponse.fromEntity(appointment);
        }

        String clientCpf;
        if (request != null && Boolean.TRUE.equals(request.useSavedCpf())) {
            clientCpf = appointment.getClient().getCpf();
            if (clientCpf == null || clientCpf.isBlank()) {
                throw new BadRequestException("CPF é obrigatório para gerar o PIX. Por favor, cadastre seu CPF antes de continuar.");
            }
        } else {
            if (request == null || request.cpf() == null || request.cpf().isBlank()) {
                throw new BadRequestException("CPF é obrigatório para gerar o PIX. Por favor, cadastre seu CPF antes de continuar.");
            }
            String cleanCpf = request.cpf().replaceAll("\\D", "");
            if (!CpfValidator.isValid(cleanCpf)) {
                throw new BadRequestException("CPF inválido. Por favor, insira um CPF válido.");
            }

            // Persist the updated CPF to the database
            User client = appointment.getClient();
            client.setCpf(cleanCpf);
            userRepository.save(client);
            clientCpf = cleanCpf;
        }

        // Gera a cobrança na API do Mercado Pago com dados reais do cliente
        String description = "Pagamento do agendamento #" + appointment.getId() + " - " + appointment.getServiceNames();
        String payerEmail = appointment.getClient().getEmail();
        String payerName = appointment.getClient().getName();

        var splitInfo = splitPaymentResolver.resolve(appointment, appointment.getEmployee());
        Payment payment = mercadoPagoPaymentService.createPixPayment(
                amount, description, payerEmail, payerName, clientCpf, appointment.getId(), splitInfo);

        // Extrai o "Copia e Cola" de dentro da resposta complexa da API
        String qrCodeCopiaECola = payment.getPointOfInteraction().getTransactionData().getQrCode();

        // Salva os dados no banco e marca que está aguardando o cliente pagar
        appointment.setPaymentId(payment.getId());
        appointment.setPixQrCode(qrCodeCopiaECola);
        appointment.setPaymentStatus(PaymentStatus.PENDING);

        Appointment saved = appointmentRepository.save(appointment);
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public void processPixPaymentWebhook(Long paymentId) {
        // 1. Double-Check: Consulta o Mercado Pago
        Payment payment = mercadoPagoPaymentService.getPayment(paymentId);
        
        // 2. Verifica se é um pagamento real e se foi aprovado
        if (payment == null || !"approved".equals(payment.getStatus())) {
            log.warn("Webhook ignorado. Pagamento {} não existe ou não está aprovado.", paymentId);
            return; 
        }

        // 3. Pega aquele external_reference que enviamos na hora de criar o PIX
        Long appointmentId = Long.valueOf(payment.getExternalReference());
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);

        // 4. Se não achar o agendamento ou se já estiver pago, não faz nada (Idempotência)
        if (appointment == null || appointment.getPaymentStatus() == PaymentStatus.PAID) {
            return; 
        }

        // 5. MARCA COMO PAGO!
        appointment.setPaymentStatus(PaymentStatus.PAID);
        appointment.setPaymentMethod(PaymentMethod.PIX);
        appointmentRepository.save(appointment);

        // 6. Lança a receita no Fluxo de Caixa financeiro do salão
        billAppointmentOnce(appointment, payment.getTransactionAmount(),
                "Pagamento PIX do agendamento #" + appointment.getId() + " - " + appointment.getServiceNames());
        
        pushService.sendToUser(appointment.getClient().getId(), "Pagamento recebido e confirmado! ✅",
                "O pagamento do seu agendamento de " + appointment.getServiceNames() + " foi confirmado.", "/my-appointments");

        try {
            emailService.sendPaymentConfirmationNotificationToClient(appointment);
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de confirmação de pagamento (efeito colateral): {}", e.getMessage());
        }

        // 7. Registra no log de auditoria
        auditLogService.logAction(
                appointment.getClient().getId(),
                appointment.getClient().getEmail(),
                "PIX_PAYMENT_CONFIRMED",
                "Appointment",
                appointment.getId(),
                "Pagamento PIX do agendamento #" + appointment.getId() + " recebido com sucesso via webhook.",
                "SUCCESS"
        );
        
        log.info("✅ SUCESSO! Agendamento {} marcado como PAGO e dinheiro lançado no Caixa.", appointmentId);
    }

    @Transactional
    public AppointmentResponse cancel(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));

        User currentUser = getAuthenticatedUser();
        boolean isAdmin = isStaff(currentUser);

        if (!isAdmin && !appointment.getClient().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Você não tem permissão para cancelar este agendamento");
        }

        // Guard clause: estado terminal — já cancelado não pode voltar atrás
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("Agendamentos pagos ou cancelados não podem ter seu status alterado.");
        }

        // Guard clause: não é possível cancelar um agendamento com pagamento confirmado sem estorno prévio
        if (appointment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Não é possível cancelar um agendamento que já foi pago. Realize o estorno antes de cancelar.");
        }

        if (appointment.getStatus() == AppointmentStatus.DONE) {
            throw new BadRequestException("Não é possível cancelar um agendamento já concluído");
        }

        if (appointment.getStatus() == AppointmentStatus.DECLINED) {
            throw new BadRequestException("Esta solicitação já foi recusada");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        emailService.sendCancellationNotification(saved);
        pushService.sendToUser(saved.getClient().getId(), "Agendamento cancelado",
                "Seu agendamento de " + saved.getServiceNames() + " foi cancelado.", "/my-appointments");
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, String statusStr) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        assertCanManage(appointment, "alterar o status de");

        // Guard clause: estado terminal — agendamento cancelado não permite mais alterações de status
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("Agendamentos pagos ou cancelados não podem ter seu status alterado.");
        }

        try {
            AppointmentStatus status = AppointmentStatus.valueOf(statusStr.toUpperCase());
            if (status == AppointmentStatus.REQUESTED) {
                throw new BadRequestException("Status inválido para esta operação");
            }
            if ((status == AppointmentStatus.CONFIRMED || status == AppointmentStatus.DONE)
                    && appointment.getScheduledAt() == null) {
                throw new BadRequestException("É necessário ter data e hora definidas neste agendamento");
            }

            // Guard clause desacoplado: bloqueia alterações de status para não-DONE quando o pagamento está finalizado.
            // Permite DONE mesmo se já pago (ex: concluir serviço após pagamento PIX confirmado).
            if (status != AppointmentStatus.DONE &&
                    (appointment.getPaymentStatus() == PaymentStatus.PAID ||
                     appointment.getPaymentStatus() == PaymentStatus.CANCELLED)) {
                throw new BusinessException("Agendamentos pagos ou cancelados não podem ter seu status alterado.");
            }

            appointment.setStatus(status);

            if (status == AppointmentStatus.DONE) {
                billAppointmentOnce(appointment, appointment.getGrandTotal(),
                        "Pagamento do agendamento #" + appointment.getId() + " - " + appointment.getServiceNames());
            }

            Appointment saved = appointmentRepository.save(appointment);

            // Trigger email/push notifications based on resulting status
            if (status == AppointmentStatus.CONFIRMED) {
                emailService.sendConfirmationNotificationToClient(saved);
                pushService.sendToUser(saved.getClient().getId(), "Agendamento confirmado! ✅",
                        "Seu horário de " + saved.getServiceNames() + " foi confirmado.", "/my-appointments");
            } else if (status == AppointmentStatus.CANCELLED || status == AppointmentStatus.DECLINED) {
                emailService.sendCancellationNotification(saved);
                pushService.sendToUser(saved.getClient().getId(), "Agendamento cancelado",
                        "Seu agendamento de " + saved.getServiceNames() + " foi cancelado.", "/my-appointments");
            }

            return AppointmentResponse.fromEntity(saved);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Status inválido");
        }
    }

    @Transactional
    public AppointmentResponse updatePaymentStatus(Long id, String paymentStatusStr, String paymentMethodStr) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        assertCanManage(appointment, "alterar o pagamento de");

        // Guard clause: agendamento cancelado não permite mais alterações de pagamento
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("Agendamentos pagos ou cancelados não podem ter seu status alterado.");
        }

        // Guard clause: status de pagamento em estado terminal
        if (appointment.getPaymentStatus() == PaymentStatus.PAID ||
                appointment.getPaymentStatus() == PaymentStatus.CANCELLED) {
            throw new BusinessException("Agendamentos pagos ou cancelados não podem ter seu status alterado.");
        }

        try {
            PaymentStatus paymentStatus = PaymentStatus.valueOf(paymentStatusStr.toUpperCase());

            // Apenas o Webhook do Mercado Pago tem permissão de sistema para transitar um agendamento de PENDING para PAID.
            // O endpoint manual do admin não deve permitir essa transição sem um ID de pagamento válido.
            if (paymentStatus == PaymentStatus.PAID && appointment.getPaymentStatus() == PaymentStatus.PENDING) {
                if (appointment.getPaymentId() == null) {
                    throw new BusinessException("Transição manual para PAGO não permitida para agendamentos pendentes sem um ID de pagamento válido.");
                }
            }

            appointment.setPaymentStatus(paymentStatus);

            // Forma de pagamento é escolhida manualmente quando o admin confirma pagamento
            // recebido fora da plataforma (crédito, débito, PIX presencial, dinheiro).
            if (paymentStatus == PaymentStatus.MANUAL && paymentMethodStr != null && !paymentMethodStr.isBlank()) {
                try {
                    appointment.setPaymentMethod(PaymentMethod.valueOf(paymentMethodStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Forma de pagamento inválida");
                }
            }

            if (paymentStatus == PaymentStatus.PAID) {
                billAppointmentOnce(appointment, appointment.getGrandTotal(),
                        "Pagamento (Confirmado Admin) do agendamento #" + appointment.getId() + " - " + appointment.getServiceNames());
            }

            Appointment saved = appointmentRepository.save(appointment);
            return AppointmentResponse.fromEntity(saved);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Status de pagamento inválido");
        }
    }

    // existsByAppointmentId evita a maioria das faturas duplicadas, mas ainda existe uma janela
    // de corrida entre o exists e o save (ex.: webhook duplicado do Mercado Pago chegando quase
    // simultâneo). A constraint UNIQUE(appointment_id) no banco (V46) é a garantia de verdade —
    // se ela disparar, é porque outra transação já faturou este agendamento primeiro, então o
    // catch aqui só transforma isso em um no-op silencioso em vez de propagar erro 500.
    private void billAppointmentOnce(Appointment appointment, BigDecimal amount, String description) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        if (cashFlowRepository.existsByAppointmentId(appointment.getId())) {
            return;
        }
        try {
            CashFlow cashFlow = new CashFlow();
            cashFlow.setType(CashFlowType.INCOME);
            cashFlow.setAmount(amount);
            cashFlow.setDescription(description);
            cashFlow.setDate(salonClock.today());
            cashFlow.setAppointment(appointment);
            cashFlowRepository.save(cashFlow);
        } catch (DataIntegrityViolationException e) {
            log.warn("Agendamento {} já havia sido faturado por outra transação concorrente — ignorando.",
                    appointment.getId());
        }
    }
}
