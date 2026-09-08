package br.com.charlottebolos.service;

import br.com.charlottebolos.dto.AuthDTO;

/**
 * Autenticacao de usuarios.
 *
 * <p>Por ora so login. O auto-cadastro do cliente depende de uma entidade de
 * cliente que ainda nao existe neste projeto.
 */
public interface AuthService {

    /**
     * Confere as credenciais e emite um token.
     *
     * @param request e-mail e senha
     * @return token e dados basicos do autenticado
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         se e-mail ou senha nao conferirem
     * @throws org.springframework.security.authentication.DisabledException
     *         se o usuario estiver inativo
     */
    AuthDTO.LoginResponse login(AuthDTO.LoginRequest request);
}
