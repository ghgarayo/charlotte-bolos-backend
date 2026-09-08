package br.com.charlottebolos.service.impl;

import br.com.charlottebolos.config.security.JwtService;
import br.com.charlottebolos.dto.AuthDTO;
import br.com.charlottebolos.model.User;
import br.com.charlottebolos.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementacao do login.
 *
 * <p>Quem confere a senha e o {@link AuthenticationManager}, nao esta classe:
 * ele delega ao {@code DaoAuthenticationProvider}, que carrega o usuario pelo
 * {@code UserDetailsService} e compara o hash pelo {@code PasswordEncoder}. Alem
 * de evitar codigo de comparacao escrito a mao, isso mantem os efeitos de
 * {@code isEnabled()} e companhia — um usuario inativo e recusado com
 * {@code DisabledException} sem que este metodo precise verificar nada.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /**
     * {@inheritDoc}
     *
     * <p>Transacao somente leitura porque o {@code UserDetailsService} busca a
     * {@code Person} associada; sem ela o acesso a associacao aconteceria fora
     * de contexto de persistencia.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = (User) authentication.getPrincipal();

        return new AuthDTO.LoginResponse(
                jwtService.generateToken(user),
                jwtService.getExpirationMs(),
                user.getEmail(),
                user.getRole().name(),
                user.getPerson().getFullName());
    }
}
