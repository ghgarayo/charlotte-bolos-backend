package br.com.charlottebolos.controller;

import br.com.charlottebolos.dto.AuthDTO;
import br.com.charlottebolos.service.AuthService;
import br.com.charlottebolos.service.MessageService;
import br.com.charlottebolos.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacao.
 *
 * <p>Primeiro controller do projeto. A rota ja estava reservada em
 * {@code SecurityConfig.AUTH_PATH} e liberada de autenticacao — sem isso, o
 * login exigiria estar logado.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MessageService messages;

    /**
     * Autentica e devolve um token.
     *
     * <p>Credenciais invalidas nao chegam aqui: a excecao sobe do
     * {@code AuthenticationManager} e o {@code GlobalExceptionHandler} responde
     * 401 com mensagem generica, sem revelar se foi o e-mail ou a senha que
     * falhou.
     *
     * @param request e-mail e senha, validados por {@code @Valid}
     * @return 200 com o token dentro do envelope padrao
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDTO.LoginResponse>> login(
            @Valid @RequestBody AuthDTO.LoginRequest request) {

        AuthDTO.LoginResponse response = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.ok(messages.get("success.auth.logged-in"), response));
    }
}
