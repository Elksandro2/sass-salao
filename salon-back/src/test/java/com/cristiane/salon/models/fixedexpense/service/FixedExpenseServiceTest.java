package com.cristiane.salon.models.fixedexpense.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.fixedexpense.dto.FixedExpenseRequest;
import com.cristiane.salon.models.fixedexpense.dto.FixedExpenseResponse;
import com.cristiane.salon.models.fixedexpense.entity.FixedExpense;
import com.cristiane.salon.models.fixedexpense.repository.FixedExpenseRepository;
import com.cristiane.salon.models.user.entity.Role;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixedExpenseServiceTest {

    @Mock
    private FixedExpenseRepository fixedExpenseRepository;

    @Mock
    private UserRepository userRepository;

    private final SalonClock salonClock = new SalonClock(ZoneId.of("America/Recife"));

    private FixedExpenseService fixedExpenseService;

    private User admin;

    @BeforeEach
    void setUp() {
        fixedExpenseService = new FixedExpenseService(fixedExpenseRepository, userRepository, salonClock);

        admin = new User();
        admin.setId(1L);
        admin.setName("Cristiane");
        admin.setEmail("admin@salao.com");
        admin.setRole(new Role(1L, "ADMIN", null));

        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(admin.getEmail());
        SecurityContext secCtx = mock(SecurityContext.class);
        lenient().when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
        lenient().when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_shouldSaveWithAuthenticatedUserAsAuthor() {
        when(fixedExpenseRepository.save(any(FixedExpense.class))).thenAnswer(inv -> {
            FixedExpense e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });

        FixedExpenseResponse result = fixedExpenseService.create(
                new FixedExpenseRequest("Aluguel", new BigDecimal("1500.00"), LocalDate.of(2026, 8, 1)));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.description()).isEqualTo("Aluguel");
        assertThat(result.amount()).isEqualByComparingTo("1500.00");

        ArgumentCaptor<FixedExpense> captor = ArgumentCaptor.forClass(FixedExpense.class);
        verify(fixedExpenseRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedByUserId()).isEqualTo(1L);
    }

    @Test
    void findByPeriod_shouldDelegateToRepository() {
        FixedExpense expense = new FixedExpense();
        expense.setId(5L);
        expense.setDescription("Água");
        expense.setAmount(new BigDecimal("80.00"));
        expense.setDate(LocalDate.of(2026, 8, 5));

        var pageable = PageRequest.of(0, 20);
        when(fixedExpenseRepository.findByDateBetweenOrderByDateDesc(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(expense)));

        var result = fixedExpenseService.findByPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).description()).isEqualTo("Água");
    }

    @Test
    void sumByPeriod_shouldReturnRepositorySum() {
        when(fixedExpenseRepository.sumAmountByDateBetween(any(), any())).thenReturn(new BigDecimal("2500.00"));

        BigDecimal result = fixedExpenseService.sumByPeriod(salonClock.today().withDayOfMonth(1), salonClock.today());

        assertThat(result).isEqualByComparingTo("2500.00");
    }

    @Test
    void delete_whenFound_shouldDelete() {
        when(fixedExpenseRepository.existsById(5L)).thenReturn(true);

        fixedExpenseService.delete(5L);

        verify(fixedExpenseRepository).deleteById(5L);
    }

    @Test
    void delete_whenNotFound_shouldThrowResourceNotFoundException() {
        when(fixedExpenseRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> fixedExpenseService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Gasto fixo não encontrado");
        verify(fixedExpenseRepository, never()).deleteById(any());
    }
}
