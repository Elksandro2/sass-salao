package com.cristiane.salon.models.staff.factory;

import com.cristiane.salon.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffRoleStrategyFactoryTest {

    private final FuncionariaStrategy funcionariaStrategy = new FuncionariaStrategy(null);
    private final GerenteDeAtendimentoStrategy gerenteStrategy = new GerenteDeAtendimentoStrategy(null);
    private final StaffRoleStrategyFactory factory =
            new StaffRoleStrategyFactory(List.of(funcionariaStrategy, gerenteStrategy));

    @Test
    void resolve_whenFuncionaria_shouldReturnFuncionariaStrategy() {
        assertThat(factory.resolve("FUNCIONARIA")).isSameAs(funcionariaStrategy);
    }

    @Test
    void resolve_whenGerente_shouldReturnGerenteStrategy() {
        assertThat(factory.resolve("GERENTE_DE_ATENDIMENTO")).isSameAs(gerenteStrategy);
    }

    @Test
    void resolve_whenUnsupportedRole_shouldThrowBadRequestException() {
        // Allow-list: um papel sem strategy (ex.: ADMIN, SYSADMIN, CLIENTE) tem que ser
        // rejeitado aqui — é isso que impede criar um ADMIN por este endpoint.
        assertThatThrownBy(() -> factory.resolve("ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FUNCIONARIA")
                .hasMessageContaining("GERENTE_DE_ATENDIMENTO");
    }

    @Test
    void resolve_whenSysadminRole_shouldThrow() {
        assertThatThrownBy(() -> factory.resolve("SYSADMIN")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void resolve_whenClienteRole_shouldThrow() {
        assertThatThrownBy(() -> factory.resolve("CLIENTE")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void supports_shouldReflectRegisteredStrategies() {
        assertThat(factory.supports("FUNCIONARIA")).isTrue();
        assertThat(factory.supports("GERENTE_DE_ATENDIMENTO")).isTrue();
        assertThat(factory.supports("ADMIN")).isFalse();
    }
}
