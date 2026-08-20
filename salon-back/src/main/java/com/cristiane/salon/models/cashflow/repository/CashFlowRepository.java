package com.cristiane.salon.models.cashflow.repository;

import com.cristiane.salon.models.cashflow.entity.CashFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashFlowRepository extends JpaRepository<CashFlow, Long> {

    // Usado pelo relatório financeiro (ReportService), que precisa do período inteiro
    // de uma vez para somar entradas/saídas — não é uma listagem para o usuário navegar.
    @Query("SELECT c FROM CashFlow c WHERE c.date >= :startDate AND c.date <= :endDate ORDER BY c.date DESC")
    List<CashFlow> findByDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Usado pela tela de Fluxo de Caixa (listagem paginada) — mesmo filtro, mas sem
    // carregar o período inteiro de uma vez.
    @Query("SELECT c FROM CashFlow c WHERE c.date >= :startDate AND c.date <= :endDate")
    Page<CashFlow> findByDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

    // Usado para checar se um agendamento já foi lançado no caixa antes de lançar de novo
    // (evita faturamento duplicado) — substitui um findAll().stream().anyMatch(...) que fazia
    // full table scan a cada confirmação de pagamento.
    boolean existsByAppointmentId(Long appointmentId);

    // Usado pra manter o valor lançado no Caixa em dia quando produtos/serviços/despesas de um
    // agendamento já faturado (mas ainda não pago) são editados — ver assertNotBilled.
    Optional<CashFlow> findByAppointmentId(Long appointmentId);
}
