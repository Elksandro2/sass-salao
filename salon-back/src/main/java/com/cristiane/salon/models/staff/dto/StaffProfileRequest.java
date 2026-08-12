package com.cristiane.salon.models.staff.dto;

import com.cristiane.salon.models.staff.enums.BrazilianState;
import com.cristiane.salon.models.staff.enums.Gender;
import com.cristiane.salon.models.staff.enums.PixKeyType;
import com.cristiane.salon.models.staff.validation.ValidCpf;
import com.cristiane.salon.models.staff.validation.ValidPixKey;
import com.cristiane.salon.models.employee.entity.CommissionScope;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cadastro completo de um membro da equipe. O papel escolhido determina quais campos são
 * obrigatórios — as regras específicas por papel ficam nas strategies (ver
 * {@code StaffRoleStrategy}), aqui ficam só as validações que valem para todo mundo.
 */
@ValidPixKey
public record StaffProfileRequest(

        // --- Acesso ---

        @NotBlank(message = "O nome de exibição é obrigatório")
        @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres")
        String name,

        @NotBlank(message = "O email é obrigatório")
        @Email(message = "O formato do email é inválido")
        @Size(max = 150, message = "O email deve ter no máximo 150 caracteres")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, max = 100, message = "A senha deve ter entre 8 e 100 caracteres")
        @Pattern(regexp = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).*$",
                message = "A senha deve conter ao menos uma letra maiúscula, uma minúscula e um número")
        String password,

        @NotBlank(message = "O papel é obrigatório")
        @Pattern(regexp = "^(FUNCIONARIA|GERENTE_DE_ATENDIMENTO)$",
                message = "O papel deve ser FUNCIONARIA ou GERENTE_DE_ATENDIMENTO")
        String roleName,

        // --- Dados pessoais ---

        @NotBlank(message = "O nome completo é obrigatório")
        @Size(min = 3, max = 150, message = "O nome completo deve ter entre 3 e 150 caracteres")
        String fullName,

        @Size(max = 150, message = "O nome social deve ter no máximo 150 caracteres")
        String socialName,

        @NotBlank(message = "O CPF é obrigatório")
        @ValidCpf
        String cpf,

        @NotNull(message = "A data de nascimento é obrigatória")
        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate birthDate,

        Gender gender,

        // --- Contato ---

        @NotBlank(message = "O telefone é obrigatório")
        @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}$",
                message = "Telefone inválido (use o formato (81) 99999-9999)")
        String phone,

        @Size(max = 150, message = "O nome do contato de emergência deve ter no máximo 150 caracteres")
        String emergencyContactName,

        @Pattern(regexp = "^$|^\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}$",
                message = "Telefone de emergência inválido (use o formato (81) 99999-9999)")
        String emergencyContactPhone,

        // --- Endereço ---

        @NotBlank(message = "O CEP é obrigatório")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido (use o formato 50000-000)")
        String zipCode,

        @NotBlank(message = "O logradouro é obrigatório")
        @Size(max = 200, message = "O logradouro deve ter no máximo 200 caracteres")
        String street,

        @NotBlank(message = "O número é obrigatório")
        @Size(max = 20, message = "O número deve ter no máximo 20 caracteres")
        String streetNumber,

        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres")
        String complement,

        @NotBlank(message = "O bairro é obrigatório")
        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres")
        String district,

        @NotBlank(message = "A cidade é obrigatória")
        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres")
        String city,

        @NotNull(message = "O estado (UF) é obrigatório")
        BrazilianState stateUf,

        // --- PIX (opcional, validado em conjunto por @ValidPixKey) ---

        PixKeyType pixKeyType,
        @Size(max = 150, message = "A chave PIX deve ter no máximo 150 caracteres")
        String pixKey,

        // --- Metadados ---

        @PastOrPresent(message = "A data de admissão não pode estar no futuro")
        LocalDate hiredAt,

        @Size(max = 2000, message = "As observações devem ter no máximo 2000 caracteres")
        String notes,

        // --- Remuneração: exigida só para FUNCIONARIA (ver FuncionariaStrategy) ---

        RemunerationType remunerationType,
        CommissionScope commissionScope,

        @DecimalMin(value = "0.0", message = "O valor de remuneração não pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "Valor de remuneração inválido")
        BigDecimal remunerationValue,

        @DecimalMin(value = "0.0", message = "O valor de comissão não pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "Valor de comissão inválido")
        BigDecimal commissionValue
) {
    /** CPF só com dígitos — usado para cifrar e para gerar o hash de duplicidade. */
    public String cpfDigitsOnly() {
        return cpf == null ? null : cpf.replaceAll("\\D", "");
    }

    /** CEP normalizado no formato 00000-000. */
    public String normalizedZipCode() {
        if (zipCode == null) {
            return null;
        }
        String digits = zipCode.replaceAll("\\D", "");
        return digits.length() == 8 ? digits.substring(0, 5) + "-" + digits.substring(5) : zipCode;
    }
}
