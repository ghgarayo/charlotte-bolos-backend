package br.com.charlottebolos.service.impl;

import br.com.charlottebolos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carrega o usuario do banco para o Spring Security.
 *
 * <p>E a peca que faltava para fechar o circuito da autenticacao: ate agora o
 * {@code SecurityConfig} publicava um repositorio em memoria vazio fora de
 * {@code dev}, entao ninguem conseguia entrar mesmo com a linha do
 * administrador criada pela migracao {@code V2}.
 *
 * <p>Como {@code User} implementa {@code UserDetails}, nao ha conversao: a
 * propria entidade e devolvida ao framework.
 *
 * <p>A mensagem de erro cita o e-mail, mas isso nao vaza informacao ao cliente:
 * o {@code AuthenticationManager} converte
 * {@link UsernameNotFoundException} em {@code BadCredentialsException}, e o
 * {@code GlobalExceptionHandler} responde apenas "Credenciais invalidas". Assim
 * o log ajuda a diagnosticar sem que a API revele quais e-mails existem.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario nao encontrado: " + email));
    }
}
