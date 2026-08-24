package com.cristiane.salon.models.user.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ConflictException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.models.user.dto.UserCreateRequest;
import com.cristiane.salon.models.user.dto.UserResponse;
import com.cristiane.salon.models.user.dto.UserUpdateRequest;
import com.cristiane.salon.models.user.dto.ClientFilter;
import com.cristiane.salon.models.user.dto.UserFilter;
import com.cristiane.salon.models.user.dto.ClientDetailsResponse;
import com.cristiane.salon.models.user.dto.UserProfileResponse;
import com.cristiane.salon.models.user.entity.Role;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.RoleRepository;
import com.cristiane.salon.models.user.repository.UserRepository;
import com.cristiane.salon.models.user.specification.UserSpecifications;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.appointment.dto.AppointmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.cristiane.salon.models.user.dto.UserCpfInfoResponse;
import com.cristiane.salon.utils.CpfValidator;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;
    private final AppointmentRepository appointmentRepository;
    private final SalonClock salonClock;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll(Boolean includeInactive) {
        List<User> users;
        if (Boolean.TRUE.equals(includeInactive)) {
            users = userRepository.findAll();
        } else {
            users = userRepository.findByActiveTrue();
        }
        return users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role não encontrada"));

        // Só quem efetivamente faz login (equipe/admin) precisa de email+senha. Cliente
        // cadastrado pelo salão pode ter só o nome — email é funcionalidade desligada por
        // feature flag nesta versão, não removida.
        boolean logsIn = !"CLIENTE".equals(role.getName());
        if (logsIn) {
            if (request.email() == null || request.email().isBlank()) {
                throw new BadRequestException("O email é obrigatório para este papel");
            }
            if (request.password() == null || request.password().isBlank()) {
                throw new BadRequestException("A senha é obrigatória para este papel");
            }
        }

        String email = request.email() != null && !request.email().isBlank() ? request.email() : null;
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email já está em uso");
        }

        String rawPassword = request.password() != null && !request.password().isBlank()
                ? request.password()
                // Cliente sem senha ainda não faz login nesta versão — precisa de algo no
                // campo NOT NULL, mas não precisa ser algo que alguém vá digitar.
                : java.util.UUID.randomUUID().toString();

        User user = new User();
        user.setName(request.name());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPhone(request.phone());
        // Opcional no cadastro — só é exigido/validado de fato na hora de gerar um PIX
        // (ver AppointmentService), não aqui.
        if (request.cpf() != null && !request.cpf().isBlank()) {
            user.setCpf(request.cpf().replaceAll("\\D", ""));
        }
        user.setRole(role);

        if (request.active() != null) {
            user.setActive(request.active());
        } else {
            user.setActive(true);
        }

        User savedUser = userRepository.save(user);

        if ("FUNCIONARIA".equals(savedUser.getRoleName())) {
            Employee employee = new Employee();
            employee.setUser(savedUser);
            employeeRepository.save(employee);
        }

        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.findByEmail(request.email()).isPresent()) {
                throw new ConflictException("Email já está em uso por outro usuário");
            }
            user.setEmail(request.email());
        }

        if (request.name() != null) user.setName(request.name());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.cpf() != null && !request.cpf().isBlank()) {
            String cleanCpf = request.cpf().replaceAll("\\D", "");
            if (!CpfValidator.isValid(cleanCpf)) {
                throw new BadRequestException("CPF inválido. Por favor, insira um CPF válido.");
            }
            user.setCpf(cleanCpf);
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.active() != null) user.setActive(request.active());
        
        if (request.roleId() != null) {
            Role role = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role não encontrada"));
            user.setRole(role);
        }

        User savedUser = userRepository.save(user);

        if ("FUNCIONARIA".equals(savedUser.getRoleName())) {
            if (employeeRepository.findByUserId(savedUser.getId()).isEmpty()) {
                Employee employee = new Employee();
                employee.setUser(savedUser);
                employeeRepository.save(employee);
            }
        }

        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public UserResponse restore(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        user.setActive(true);
        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    /**
     * Atualiza o CPF do próprio usuário autenticado (fluxo JIT no pagamento via PIX).
     * Garante atomicidade via @Transactional e verifica duplicação antes de persistir.
     */
    @Transactional
    public UserResponse updateMyCpf(String cpf) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));

        if (cpf == null || cpf.isBlank()) {
            throw new BadRequestException("CPF é obrigatório.");
        }

        String cleanCpf = cpf.replaceAll("\\D", "");
        if (!CpfValidator.isValid(cleanCpf)) {
            throw new BadRequestException("CPF inválido. Por favor, insira um CPF válido.");
        }

        user.setCpf(cleanCpf);
        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public UserCpfInfoResponse getMyCpfInfo() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));

        String cpf = user.getCpf();
        boolean hasSavedCpf = cpf != null && !cpf.isBlank();
        String cpfMasked = "";
        if (hasSavedCpf) {
            String clean = cpf.replaceAll("\\D", "");
            if (clean.length() == 11) {
                cpfMasked = "***.***." + clean.substring(6, 9) + "-";
            } else {
                cpfMasked = cpf;
            }
        }
        return new UserCpfInfoResponse(hasSavedCpf, cpfMasked);
    }

    /**
     * Retorna o perfil completo do usuário autenticado, incluindo a lista de permissões
     * lidas diretamente do banco (não do JWT). Usado pelo endpoint GET /v1/auth/me
     * para popular o estado de autorização no frontend via CanI.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));

        List<String> permissions = java.util.Collections.emptyList();
        if (user.getRole() != null && user.getRole().getPermissions() != null) {
            permissions = user.getRole().getPermissions().stream()
                    .map(p -> p.getHttpMethod() + ":" + p.getEndpoint())
                    .sorted()
                    .collect(Collectors.toList());
        }

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRoleName(),
                user.getCpf(),
                permissions
        );
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAllClients(ClientFilter filter, Pageable pageable) {
        return userRepository.findAll(UserSpecifications.filterClients(filter), pageable)
                .map(UserResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAllUsers(UserFilter filter, Pageable pageable) {
        return userRepository.findAll(UserSpecifications.filterUsers(filter), pageable)
                .map(UserResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ClientDetailsResponse findClientDetailsById(Long id) {
        User client = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado"));

        if (!"CLIENTE".equals(client.getRoleName())) {
            throw new BadRequestException("O usuário informado não é um cliente");
        }

        Long totalAppointments = appointmentRepository.countByClientId(id);
        LocalDateTime lastAppointmentDate = appointmentRepository.findLastAppointmentDateByClientId(id);

        List<Appointment> appointments = appointmentRepository.findByClientId(id);
        List<AppointmentResponse> appointmentResponses = appointments.stream()
                .sorted((a1, a2) -> {
                    // createdAt é instante de máquina e scheduledAt é hora de parede do salão:
                    // converter o primeiro antes de comparar, senão a ordenação erra por 3h nos
                    // agendamentos que ainda não têm horário definido.
                    LocalDateTime d1 = a1.getScheduledAt() != null ? a1.getScheduledAt() : salonClock.toLocalDateTime(a1.getCreatedAt());
                    LocalDateTime d2 = a2.getScheduledAt() != null ? a2.getScheduledAt() : salonClock.toLocalDateTime(a2.getCreatedAt());
                    if (d1 == null && d2 == null) return 0;
                    if (d1 == null) return 1;
                    if (d2 == null) return -1;
                    return d2.compareTo(d1);
                })
                .map(AppointmentResponse::fromEntity)
                .collect(Collectors.toList());

        return new ClientDetailsResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getPhone(),
                client.getCpf(),
                client.getRoleName(),
                client.getActive(),
                client.getCreatedAt(),
                totalAppointments,
                lastAppointmentDate,
                appointmentResponses
        );
    }
}
