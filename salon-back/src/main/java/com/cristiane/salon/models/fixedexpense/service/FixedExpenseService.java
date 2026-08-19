package com.cristiane.salon.models.fixedexpense.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.models.fixedexpense.dto.FixedExpenseRequest;
import com.cristiane.salon.models.fixedexpense.dto.FixedExpenseResponse;
import com.cristiane.salon.models.fixedexpense.entity.FixedExpense;
import com.cristiane.salon.models.fixedexpense.repository.FixedExpenseRepository;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import com.cristiane.salon.utils.DateRangeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FixedExpenseService {

    private final FixedExpenseRepository fixedExpenseRepository;
    private final UserRepository userRepository;
    private final SalonClock salonClock;

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));
    }

    private LocalDate[] resolvePeriod(LocalDate from, LocalDate to) {
        DateRangeValidator.validate(from, to);
        LocalDate resolvedFrom = from != null ? from : salonClock.today().withDayOfMonth(1);
        LocalDate resolvedTo = to != null ? to : salonClock.today().plusDays(30);
        return new LocalDate[]{resolvedFrom, resolvedTo};
    }

    @Transactional(readOnly = true)
    public Page<FixedExpenseResponse> findByPeriod(LocalDate from, LocalDate to, Pageable pageable) {
        LocalDate[] period = resolvePeriod(from, to);
        return fixedExpenseRepository.findByDateBetweenOrderByDateDesc(period[0], period[1], pageable)
                .map(FixedExpenseResponse::fromEntity);
    }

    /** Usado internamente pelos relatórios — soma total do período, sem paginação. */
    @Transactional(readOnly = true)
    public BigDecimal sumByPeriod(LocalDate from, LocalDate to) {
        LocalDate[] period = resolvePeriod(from, to);
        return fixedExpenseRepository.sumAmountByDateBetween(period[0], period[1]);
    }

    @Transactional
    public FixedExpenseResponse create(FixedExpenseRequest request) {
        FixedExpense expense = new FixedExpense();
        expense.setDescription(request.description().trim());
        expense.setAmount(request.amount());
        expense.setDate(request.date());
        expense.setCreatedByUserId(getAuthenticatedUser().getId());
        return FixedExpenseResponse.fromEntity(fixedExpenseRepository.save(expense));
    }

    @Transactional
    public void delete(Long id) {
        if (!fixedExpenseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Gasto fixo não encontrado");
        }
        fixedExpenseRepository.deleteById(id);
    }
}
