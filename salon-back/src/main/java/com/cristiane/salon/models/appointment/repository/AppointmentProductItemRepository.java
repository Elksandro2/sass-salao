package com.cristiane.salon.models.appointment.repository;

import com.cristiane.salon.models.appointment.entity.AppointmentProductItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentProductItemRepository extends JpaRepository<AppointmentProductItem, Long> {

    // Usado pra bloquear exclusão definitiva de um produto já vendido em algum atendimento
    // (ver ProductService.permanentlyDelete) — apagar de vez corromperia o histórico.
    boolean existsByProductId(Long productId);
}
