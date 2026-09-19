package com.cristiane.salon.models.appointment.repository;

import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentServiceItemRepository extends JpaRepository<AppointmentServiceItem, Long> {

    // Usado pra bloquear exclusão definitiva de um serviço já realizado em algum atendimento
    // (ver SalonServiceManager.permanentlyDelete) — apagar de vez corromperia o histórico.
    boolean existsBySalonServiceId(Long salonServiceId);
}
