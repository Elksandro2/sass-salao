package com.cristiane.salon.models.businesssettings.service;

import com.cristiane.salon.models.businesssettings.dto.SalonBusinessSettingsResponse;
import com.cristiane.salon.models.businesssettings.dto.SalonBusinessSettingsUpdateRequest;
import com.cristiane.salon.models.businesssettings.entity.SalonBusinessSettings;
import com.cristiane.salon.models.businesssettings.repository.SalonBusinessSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SalonBusinessSettingsService {

    private final SalonBusinessSettingsRepository repository;

    @Transactional(readOnly = true)
    public SalonBusinessSettingsResponse getSettings() {
        return SalonBusinessSettingsResponse.fromEntity(loadOrCreate());
    }

    @Transactional
    public SalonBusinessSettingsResponse updateSettings(SalonBusinessSettingsUpdateRequest request) {
        SalonBusinessSettings settings = loadOrCreate();
        settings.setProductCommissionPercent(request.productCommissionPercent());
        return SalonBusinessSettingsResponse.fromEntity(repository.save(settings));
    }

    /**
     * Percentual de comissão sobre produtos vendidos, pronto pra uso em cálculo — null se não
     * configurado (equivale a comissão de produto desligada, ninguém recebe nada por vender).
     * Usado pelo ReportService e pelo split de pagamento automático.
     */
    @Transactional(readOnly = true)
    public BigDecimal getProductCommissionPercent() {
        return loadOrCreate().getProductCommissionPercent();
    }

    private SalonBusinessSettings loadOrCreate() {
        return repository.findFirstByOrderByIdAsc().orElseGet(() -> repository.save(new SalonBusinessSettings()));
    }
}
