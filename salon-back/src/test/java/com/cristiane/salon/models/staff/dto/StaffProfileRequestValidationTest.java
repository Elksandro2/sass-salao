package com.cristiane.salon.models.staff.dto;

import com.cristiane.salon.models.employee.entity.CommissionScope;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.staff.enums.BrazilianState;
import com.cristiane.salon.models.staff.enums.PixKeyType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa as anotações Bean Validation de {@link StaffProfileRequest} com um {@link Validator}
 * real (não mockado) — é a única forma de pegar erros de configuração das próprias
 * anotações (regex errado, grupo errado, etc.), que um teste de unidade do service não pega.
 */
class StaffProfileRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** Uma requisição minimamente válida — cada teste desvia dela num único ponto. */
    private StaffProfileRequest valid() {
        return new StaffProfileRequest(
                "Maria", "maria@example.com", "Senha@123", "FUNCIONARIA",
                "Maria Silva", null, "111.444.777-35", LocalDate.of(1990, 1, 1), null,
                "(81) 99999-9999", null, null,
                "50000-000", "Rua A", "10", null, "Boa Vista", "Recife", BrazilianState.PE,
                PixKeyType.EMAIL, "maria@example.com",
                LocalDate.now(), null,
                RemunerationType.SALARIO_FIXO, null, new BigDecimal("2000"), null
        );
    }

    private <T> Set<ConstraintViolation<StaffProfileRequest>> violationsFor(
            UnaryOperator<StaffProfileRequest> mutation) {
        return validator.validate(mutation.apply(valid()));
    }

    @Test
    void valid_request_shouldHaveNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void cpf_whenInvalidCheckDigits_shouldFailValidation() {
        var violations = violationsFor(r -> withCpf(r, "111.444.777-36")); // dígito verificador errado
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cpf"));
    }

    @Test
    void cpf_whenAllDigitsEqual_shouldFailValidation() {
        var violations = violationsFor(r -> withCpf(r, "11111111111"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cpf"));
    }

    @Test
    void cpf_whenBlank_shouldFailValidation() {
        var violations = violationsFor(r -> withCpf(r, ""));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cpf"));
    }

    @Test
    void email_whenMalformed_shouldFailValidation() {
        StaffProfileRequest r = replaceEmail(valid(), "not-an-email");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void password_whenMissingUppercase_shouldFailValidation() {
        StaffProfileRequest r = replacePassword(valid(), "senha1234");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void password_whenMissingDigit_shouldFailValidation() {
        StaffProfileRequest r = replacePassword(valid(), "SenhaSemNumero");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void password_whenTooShort_shouldFailValidation() {
        StaffProfileRequest r = replacePassword(valid(), "Ab1");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void roleName_whenNotFuncionariaOrGerente_shouldFailValidation() {
        StaffProfileRequest r = replaceRole(valid(), "ADMIN");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("roleName"));
    }

    @Test
    void birthDate_whenInTheFuture_shouldFailValidation() {
        StaffProfileRequest r = replaceBirthDate(valid(), LocalDate.now().plusDays(1));
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("birthDate"));
    }

    @Test
    void phone_whenMalformed_shouldFailValidation() {
        StaffProfileRequest r = replacePhone(valid(), "123");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    }

    @Test
    void phone_acceptsFormatWithoutParenthesesOrDash() {
        StaffProfileRequest r = replacePhone(valid(), "81999999999");
        assertThat(validator.validate(r)).noneMatch(v -> v.getPropertyPath().toString().equals("phone"));
    }

    @Test
    void zipCode_whenMalformed_shouldFailValidation() {
        StaffProfileRequest r = replaceZipCode(valid(), "abcde-123");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("zipCode"));
    }

    @Test
    void zipCode_acceptsFormatWithoutDash() {
        StaffProfileRequest r = replaceZipCode(valid(), "50000000");
        assertThat(validator.validate(r)).noneMatch(v -> v.getPropertyPath().toString().equals("zipCode"));
    }

    @Test
    void pixKey_whenTypeSetButKeyMissing_shouldFailValidation() {
        StaffProfileRequest r = replacePix(valid(), PixKeyType.EMAIL, null);
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("pixKey"));
    }

    @Test
    void pixKey_whenKeySetButTypeMissing_shouldFailValidation() {
        StaffProfileRequest r = replacePix(valid(), null, "maria@example.com");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("pixKeyType"));
    }

    @Test
    void pixKey_whenEmailTypeButKeyIsNotAnEmail_shouldFailValidation() {
        StaffProfileRequest r = replacePix(valid(), PixKeyType.EMAIL, "not-an-email");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("pixKey"));
    }

    @Test
    void pixKey_whenCpfTypeButInvalidCheckDigits_shouldFailValidation() {
        StaffProfileRequest r = replacePix(valid(), PixKeyType.CPF, "11111111111");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("pixKey"));
    }

    @Test
    void pixKey_whenCpfTypeWithValidCheckDigits_shouldPass() {
        StaffProfileRequest r = replacePix(valid(), PixKeyType.CPF, "11144477735");
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void pixKey_whenRandomTypeAndValidUuid_shouldPass() {
        StaffProfileRequest r = replacePix(valid(), PixKeyType.ALEATORIA,
                "550e8400-e29b-41d4-a716-446655440000");
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void pixKey_whenRandomTypeButNotAUuid_shouldFailValidation() {
        StaffProfileRequest r = replacePix(valid(), PixKeyType.ALEATORIA, "not-a-uuid");
        assertThat(validator.validate(r)).anyMatch(v -> v.getPropertyPath().toString().equals("pixKey"));
    }

    @Test
    void pixKey_whenBothNull_shouldPass() {
        StaffProfileRequest r = replacePix(valid(), null, null);
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void stateUf_whenNull_shouldFailValidation() {
        StaffProfileRequest v = valid();
        StaffProfileRequest r = new StaffProfileRequest(
                v.name(), v.email(), v.password(), v.roleName(), v.fullName(), v.socialName(),
                v.cpf(), v.birthDate(), v.gender(), v.phone(), v.emergencyContactName(),
                v.emergencyContactPhone(), v.zipCode(), v.street(), v.streetNumber(), v.complement(),
                v.district(), v.city(), null, v.pixKeyType(), v.pixKey(), v.hiredAt(), v.notes(),
                v.remunerationType(), v.commissionScope(), v.remunerationValue(), v.commissionValue()
        );
        assertThat(validator.validate(r)).anyMatch(cv -> cv.getPropertyPath().toString().equals("stateUf"));
    }

    // --- helpers para reconstruir o record trocando 1 campo por vez ---

    private StaffProfileRequest withCpf(StaffProfileRequest v, String cpf) {
        return new StaffProfileRequest(v.name(), v.email(), v.password(), v.roleName(), v.fullName(),
                v.socialName(), cpf, v.birthDate(), v.gender(), v.phone(), v.emergencyContactName(),
                v.emergencyContactPhone(), v.zipCode(), v.street(), v.streetNumber(), v.complement(),
                v.district(), v.city(), v.stateUf(), v.pixKeyType(), v.pixKey(), v.hiredAt(), v.notes(),
                v.remunerationType(), v.commissionScope(), v.remunerationValue(), v.commissionValue());
    }

    private StaffProfileRequest replaceEmail(StaffProfileRequest v, String email) {
        return new StaffProfileRequest(v.name(), email, v.password(), v.roleName(), v.fullName(),
                v.socialName(), v.cpf(), v.birthDate(), v.gender(), v.phone(), v.emergencyContactName(),
                v.emergencyContactPhone(), v.zipCode(), v.street(), v.streetNumber(), v.complement(),
                v.district(), v.city(), v.stateUf(), v.pixKeyType(), v.pixKey(), v.hiredAt(), v.notes(),
                v.remunerationType(), v.commissionScope(), v.remunerationValue(), v.commissionValue());
    }

    private StaffProfileRequest replacePassword(StaffProfileRequest v, String password) {
        return new StaffProfileRequest(v.name(), v.email(), password, v.roleName(), v.fullName(),
                v.socialName(), v.cpf(), v.birthDate(), v.gender(), v.phone(), v.emergencyContactName(),
                v.emergencyContactPhone(), v.zipCode(), v.street(), v.streetNumber(), v.complement(),
                v.district(), v.city(), v.stateUf(), v.pixKeyType(), v.pixKey(), v.hiredAt(), v.notes(),
                v.remunerationType(), v.commissionScope(), v.remunerationValue(), v.commissionValue());
    }

    private StaffProfileRequest replaceRole(StaffProfileRequest v, String roleName) {
        return new StaffProfileRequest(v.name(), v.email(), v.password(), roleName, v.fullName(),
                v.socialName(), v.cpf(), v.birthDate(), v.gender(), v.phone(), v.emergencyContactName(),
                v.emergencyContactPhone(), v.zipCode(), v.street(), v.streetNumber(), v.complement(),
                v.district(), v.city(), v.stateUf(), v.pixKeyType(), v.pixKey(), v.hiredAt(), v.notes(),
                v.remunerationType(), v.commissionScope(), v.remunerationValue(), v.commissionValue());
    }

    private StaffProfileRequest replaceBirthDate(StaffProfileRequest v, LocalDate birthDate) {
        return new StaffProfileRequest(v.name(), v.email(), v.password(), v.roleName(), v.fullName(),
                v.socialName(), v.cpf(), birthDate, v.gender(), v.phone(), v.emergencyContactName(),
                v.emergencyContactPhone(), v.zipCode(), v.street(), v.streetNumber(), v.complement(),
                v.district(), v.city(), v.stateUf(), v.pixKeyType(), v.pixKey(), v.hiredAt(), v.notes(),
                v.remunerationType(), v.commissionScope(), v.remunerationValue(), v.commissionValue());
    }

    private StaffProfileRequest replacePhone(StaffProfileRequest v, String phone) {
        return new StaffProfileRequest(v.name(), v.email(), v.password(), v.roleName(), v.fullName(),
                v.socialName(), v.cpf(), v.birthDate(), v.gender(), phone, v.emergencyContactName(),
                v.emergencyContactPhone(), v.zipCode(), v.street(), v.streetNumber(), v.complement(),
                v.district(), v.city(), v.stateUf(), v.pixKeyType(), v.pixKey(), v.hiredAt(), v.notes(),
                v.remunerationType(), v.commissionScope(), v.remunerationValue(), v.commissionValue());
    }

    private StaffProfileRequest replaceZipCode(StaffProfileRequest v, String zipCode) {
        return new StaffProfileRequest(v.name(), v.email(), v.password(), v.roleName(), v.fullName(),
                v.socialName(), v.cpf(), v.birthDate(), v.gender(), v.phone(), v.emergencyContactName(),
                v.emergencyContactPhone(), zipCode, v.street(), v.streetNumber(), v.complement(),
                v.district(), v.city(), v.stateUf(), v.pixKeyType(), v.pixKey(), v.hiredAt(), v.notes(),
                v.remunerationType(), v.commissionScope(), v.remunerationValue(), v.commissionValue());
    }

    private StaffProfileRequest replacePix(StaffProfileRequest v, PixKeyType type, String key) {
        return new StaffProfileRequest(v.name(), v.email(), v.password(), v.roleName(), v.fullName(),
                v.socialName(), v.cpf(), v.birthDate(), v.gender(), v.phone(), v.emergencyContactName(),
                v.emergencyContactPhone(), v.zipCode(), v.street(), v.streetNumber(), v.complement(),
                v.district(), v.city(), v.stateUf(), type, key, v.hiredAt(), v.notes(),
                v.remunerationType(), v.commissionScope(), v.remunerationValue(), v.commissionValue());
    }
}
