package br.com.charlottebolos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Contratos de entrada e saida da autenticacao.
 *
 * <p>Agrupados numa classe so porque nascem e mudam juntos; separa-los em
 * arquivos individuais espalharia o contrato sem ganho de clareza.
 */
public final class AuthDTO {

    private AuthDTO() {
    }

    /**
     * Credenciais do login.
     *
     * @param email    e-mail cadastrado
     * @param password senha em texto puro, conferida contra o hash BCrypt
     */
    public record LoginRequest(
            @NotBlank(message = "O e-mail e obrigatorio")
            @Email(message = "E-mail invalido")
            String email,

            @NotBlank(message = "A senha e obrigatoria")
            String password
    ) {
    }

    /**
     * Resposta do login.
     *
     * <p>Devolve e-mail e papel junto do token para o front nao precisar
     * decodificar o JWT so para montar o menu. O token continua sendo a unica
     * fonte de verdade da autorizacao — o servidor o revalida a cada
     * requisicao.
     *
     * @param token       token assinado, para o cabecalho {@code Authorization}
     * @param expiresInMs validade do token em milissegundos
     * @param email       e-mail do autenticado
     * @param role        papel, sem o prefixo {@code ROLE_}
     * @param fullName    nome completo, para exibicao
     */
    public record LoginResponse(
            String token,
            long expiresInMs,
            String email,
            String role,
            String fullName
    ) {
    }
}
