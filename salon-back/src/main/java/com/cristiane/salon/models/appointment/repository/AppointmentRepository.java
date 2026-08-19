package com.cristiane.salon.models.appointment.repository;

import com.cristiane.salon.models.appointment.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    List<Appointment> findByClientId(Long clientId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.client.id = :clientId")
    Long countByClientId(@Param("clientId") Long clientId);

    @Query("SELECT MAX(a.scheduledAt) FROM Appointment a WHERE a.client.id = :clientId")
    LocalDateTime findLastAppointmentDateByClientId(@Param("clientId") Long clientId);

    // CAST(:from/:to AS timestamp) na checagem IS NULL não é frescura — sem ele, o Postgres
    // não consegue inferir o tipo do parâmetro (ele aparece "nu", só num "? is null", sem
    // nenhum outro contexto de tipo) e a query quebra com "could not determine data type
    // of parameter $N" toda vez que from/to vêm nulos (bug real encontrado em produção).
    @Query("SELECT a FROM Appointment a WHERE a.employee.id = :employeeId "
            + "AND (CAST(:from AS timestamp) IS NULL OR a.scheduledAt >= :from) "
            + "AND (CAST(:to AS timestamp) IS NULL OR a.scheduledAt <= :to)")
    Page<Appointment> findByEmployeeIdForFinancialHistory(
            @Param("employeeId") Long employeeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // Replica no banco a mesma cadeia de fallback que os relatórios usavam em memória
    // (scheduledAt > preferredDate > createdAt): evita carregar a tabela inteira via
    // findAll() só para filtrar por período depois em Java (gargalo achado no OpenTelemetry).
    // O período chega em três tipos de propósito, e não por descuido: scheduledAt é hora de
    // parede do salão (LocalDateTime), preferredDate é data pura (LocalDate) e createdAt é
    // instante de máquina (Instant). Um parâmetro só não consegue ser comparado com os três —
    // quem chama converte o mesmo intervalo para as três representações usando o fuso do salão.
    @Query("SELECT a FROM Appointment a WHERE "
            + "(a.scheduledAt IS NOT NULL AND a.scheduledAt BETWEEN :startOfDay AND :endOfDay) "
            + "OR (a.scheduledAt IS NULL AND a.preferredDate IS NOT NULL AND a.preferredDate BETWEEN :from AND :to) "
            + "OR (a.scheduledAt IS NULL AND a.preferredDate IS NULL AND a.createdAt IS NOT NULL AND a.createdAt BETWEEN :startInstant AND :endInstant)")
    List<Appointment> findAllInPeriod(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant
    );

    // Lembrete D-1 (issue #111): CONFIRMED, ainda não lembrado, agendado dentro da janela do
    // dia seguinte. startOfDay/endOfDay já vêm calculados no fuso America/Recife pelo chamador
    // — não é sensato calcular fuso horário dentro da query.
    @Query("SELECT a FROM Appointment a WHERE a.status = 'CONFIRMED' AND a.remindedAt IS NULL "
            + "AND a.scheduledAt >= :startOfDay AND a.scheduledAt < :endOfDay")
    List<Appointment> findConfirmedNotRemindedBetween(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );
}
