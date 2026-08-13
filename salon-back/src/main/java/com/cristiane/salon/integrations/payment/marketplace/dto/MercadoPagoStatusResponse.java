package com.cristiane.salon.integrations.payment.marketplace.dto;

import java.time.Instant;

public record MercadoPagoStatusResponse(boolean connected, Instant connectedAt) {

    public static MercadoPagoStatusResponse notConnected() {
        return new MercadoPagoStatusResponse(false, null);
    }
}
