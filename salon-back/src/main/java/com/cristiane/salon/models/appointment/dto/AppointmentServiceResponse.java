package com.cristiane.salon.models.appointment.dto;

import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;

import java.math.BigDecimal;

public record AppointmentServiceResponse(
        Long serviceId,
        String serviceName,
        BigDecimal catalogPrice,
        /** Sobrescreve o preço do serviço só para este item (nulo = usa o valor do catálogo). */
        BigDecimal customPrice,
        String customServiceNotes,
        /** Valor realmente cobrado/considerado: customPrice se preenchido, senão o preço do serviço. */
        BigDecimal effectivePrice
) {
    public static AppointmentServiceResponse fromEntity(AppointmentServiceItem item) {
        return new AppointmentServiceResponse(
                item.getSalonService().getId(),
                item.getSalonService().getName(),
                item.getSalonService().getPrice(),
                item.getCustomPrice(),
                item.getCustomServiceNotes(),
                item.getEffectivePrice()
        );
    }
}
