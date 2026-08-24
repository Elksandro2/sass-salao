package com.cristiane.salon.models.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Email e senha só são obrigatórios pra papéis que efetivamente fazem login (ADMIN, GERENTE,
 * FUNCIONARIA) — validado em {@code UserService.create}, não aqui, porque depende do papel
 * (roleId). Cliente cadastrado pelo salão pode ter só o nome; email é uma funcionalidade
 * desligada por feature flag nesta versão, não removida.
 */
public record UserCreateRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres")
        String name,

        @Email(message = "O formato do email é inválido")
        @Size(max = 150, message = "O email deve ter no máximo 150 caracteres")
        String email,

        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
        @Pattern(regexp = "^(?=.*\\d).*$", message = "A senha deve conter pelo menos um número")
        String password,

        @Size(max = 20, message = "O telefone não pode exceder 20 caracteres")
        String phone,

        /** Opcional no cadastro — só passa a ser exigido/validado na hora de gerar um PIX. */
        @Pattern(regexp = "^$|^\\d{11}$", message = "O CPF deve conter exatamente 11 dígitos numéricos")
        String cpf,

        Boolean active,

        @NotNull(message = "A role / papel é obrigatória")
        Long roleId
) {}