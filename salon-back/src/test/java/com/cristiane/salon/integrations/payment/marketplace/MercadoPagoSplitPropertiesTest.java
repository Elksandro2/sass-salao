package com.cristiane.salon.integrations.payment.marketplace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoSplitPropertiesTest {

    private MercadoPagoSplitProperties props() {
        return new MercadoPagoSplitProperties();
    }

    @Test
    void isConfigured_whenAllFieldsSet_shouldBeTrue() {
        MercadoPagoSplitProperties p = props();
        p.setClientId("id");
        p.setClientSecret("secret");
        p.setOauthRedirectUri("https://example.com/callback");

        assertThat(p.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_whenFieldsNull_shouldBeFalse() {
        assertThat(props().isConfigured()).isFalse();
    }

    @Test
    void isConfigured_whenFieldsBlank_shouldBeFalse() {
        MercadoPagoSplitProperties p = props();
        p.setClientId("  ");
        p.setClientSecret("secret");
        p.setOauthRedirectUri("https://example.com/callback");

        assertThat(p.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_whenOnlySomeFieldsSet_shouldBeFalse() {
        MercadoPagoSplitProperties p = props();
        p.setClientId("id");
        p.setClientSecret("secret");

        assertThat(p.isConfigured()).isFalse();
    }
}
