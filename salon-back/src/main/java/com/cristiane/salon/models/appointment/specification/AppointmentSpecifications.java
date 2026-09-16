package com.cristiane.salon.models.appointment.specification;

import com.cristiane.salon.models.appointment.dto.AppointmentFilter;
import com.cristiane.salon.models.appointment.entity.Appointment;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class AppointmentSpecifications {

    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);

    private AppointmentSpecifications() {
        throw new IllegalStateException("Utility class");
    }

    public static Specification<Appointment> filter(AppointmentFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (filter.status() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("status"), filter.status()));
                }

                if (filter.paymentStatus() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("paymentStatus"), filter.paymentStatus()));
                }

                if (filter.employeeId() != null) {
                    predicates.add(criteriaBuilder.equal(root.join("employee").get("id"), filter.employeeId()));
                }

                if (filter.clientId() != null) {
                    predicates.add(criteriaBuilder.equal(root.join("client").get("id"), filter.clientId()));
                }

                if (filter.clientName() != null && !filter.clientName().isBlank()) {
                    predicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(root.join("client").get("name")),
                            "%" + filter.clientName().toLowerCase() + "%"
                    ));
                }

                if (filter.startDate() != null || filter.endDate() != null) {
                    LocalDate from = filter.startDate() != null ? filter.startDate() : MIN_DATE;
                    LocalDate to = filter.endDate() != null ? filter.endDate() : MAX_DATE;
                    predicates.add(periodPredicate(root, criteriaBuilder, from, to));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Replica a cadeia de fallback usada em AppointmentRepository.findAllInPeriod
    // (scheduledAt > scheduledDate > preferredDate > createdAt) — mesma regra de negócio, aqui
    // expressa via Criteria API pra poder compor com os demais filtros dinamicamente. O filtro
    // De/Até (e o atalho "Agendamentos de Hoje") tem que achar pelo dia PRA QUAL o atendimento
    // está marcado, não pelo dia em que foi cadastrado — createdAt só entra como último recurso,
    // pra pedido do cliente ainda sem dia nenhum definido (nem scheduledDate nem preferredDate).
    private static Predicate periodPredicate(Root<Appointment> root, CriteriaBuilder cb, LocalDate from, LocalDate to) {
        LocalDateTime startOfDay = from.atStartOfDay();
        LocalDateTime endOfDay = to.atTime(LocalTime.MAX);

        Predicate byScheduledAt = cb.and(
                cb.isNotNull(root.get("scheduledAt")),
                cb.between(root.get("scheduledAt").as(LocalDateTime.class), startOfDay, endOfDay)
        );
        Predicate byScheduledDate = cb.and(
                cb.isNull(root.get("scheduledAt")),
                cb.isNotNull(root.get("scheduledDate")),
                cb.between(root.get("scheduledDate").as(LocalDate.class), from, to)
        );
        Predicate byPreferredDate = cb.and(
                cb.isNull(root.get("scheduledAt")),
                cb.isNull(root.get("scheduledDate")),
                cb.isNotNull(root.get("preferredDate")),
                cb.between(root.get("preferredDate").as(LocalDate.class), from, to)
        );
        Predicate byCreatedAt = cb.and(
                cb.isNull(root.get("scheduledAt")),
                cb.isNull(root.get("scheduledDate")),
                cb.isNull(root.get("preferredDate")),
                cb.isNotNull(root.get("createdAt")),
                cb.between(root.get("createdAt").as(LocalDateTime.class), startOfDay, endOfDay)
        );

        return cb.or(byScheduledAt, byScheduledDate, byPreferredDate, byCreatedAt);
    }
}
