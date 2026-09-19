package com.cristiane.salon.models.service.repository;

import com.cristiane.salon.models.service.entity.SalonServiceProductUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalonServiceProductUsageRepository extends JpaRepository<SalonServiceProductUsage, Long> {
    List<SalonServiceProductUsage> findBySalonServiceId(Long salonServiceId);

    void deleteBySalonServiceId(Long salonServiceId);

    // Usado pra bloquear exclusão definitiva de um produto que algum serviço ainda usa na
    // receita (ver ProductService.permanentlyDelete) — precisa tirar da receita antes de excluir.
    boolean existsByProductId(Long productId);
}
