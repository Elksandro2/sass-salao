package com.cristiane.salon.models.fixedexpense.repository;

import com.cristiane.salon.models.fixedexpense.entity.FixedExpense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FixedExpenseRepository extends JpaRepository<FixedExpense, Long> {

    List<FixedExpense> findByDateBetween(LocalDate from, LocalDate to);

    Page<FixedExpense> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FixedExpense f WHERE f.date BETWEEN :from AND :to")
    BigDecimal sumAmountByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
