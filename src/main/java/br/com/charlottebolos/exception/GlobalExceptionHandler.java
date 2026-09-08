package br.com.charlottebolos.exception;

import br.com.charlottebolos.service.MessageService;
import br.com.charlottebolos.shared.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Traduz excecoes em respostas HTTP no envelope {@link ApiResponse}.
 *
 * <p>Sem ele, uma excecao vira a pagina de erro padrao do Spring, com formato
 * diferente do resto da API — o cliente precisaria saber ler dois formatos.
 *
 * <p><strong>Limite importante:</strong> {@code @RestControllerAdvice} so
 * alcanca o que acontece dentro do {@code DispatcherServlet}. Excecao lancada
 * num filtro de servlet passa longe daqui. Por isso o
 * {@code JwtAuthenticationFilter} trata os proprios erros de token, e a recusa
 * por falta de autenticacao fica com o entry point do {@code SecurityConfig}.
 *
 * <p>As mensagens saem do {@code MessageService}, nunca da excecao, para nao
 * vazar detalhe interno ao cliente. A excecao original vai para o log.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageService messages;

    /**
     * Recurso inexistente.
     *
     * @param e excecao capturada
     * @return 404
     */
    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * Violacao de regra de negocio sinalizada por argumento invalido.
     *
     * @param e excecao capturada
     * @return 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> handleBusinessRule(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * Falha de validacao de {@code @Valid}.
     *
     * <p>Devolve o mapa campo/mensagem no {@code data}, para o front marcar cada
     * campo em vez de exibir um aviso solto.
     *
     * @param e excecao capturada
     * @return 400 com os campos que falharam
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException e) {

        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error ->
                fieldErrors.put(((FieldError) error).getField(), error.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(messages.get("error.validation.invalid-data"), fieldErrors));
    }

    /**
     * Credenciais incorretas.
     *
     * <p>A mensagem e generica de proposito: dizer se foi o e-mail ou a senha
     * transformaria o login num verificador de quais contas existem.
     *
     * @param e excecao capturada
     * @return 401
     */
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        log.debug("Falha de autenticacao: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(messages.get("error.auth.invalid-credentials")));
    }

    /**
     * Usuario existente, porem inativo ({@code active = false}).
     *
     * @param e excecao capturada
     * @return 403
     */
    @ExceptionHandler(DisabledException.class)
    ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(messages.get("error.auth.user-disabled")));
    }

    /**
     * Autenticado, mas sem permissao — tipicamente barrado por
     * {@code @PreAuthorize}.
     *
     * @param e excecao capturada
     * @return 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(messages.get("error.access.denied")));
    }

    /**
     * Edicao concorrente detectada pelo {@code @Version} do
     * {@code BaseEntity}.
     *
     * <p>409 e nao 500: o pedido nao esta errado, so chegou depois de outra
     * alteracao. O cliente deve recarregar e tentar de novo.
     *
     * @param e excecao capturada
     * @return 409
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(messages.get("error.conflict.concurrent-update")));
    }

    /**
     * Rede de seguranca para o que nao foi previsto.
     *
     * <p>Loga o stack trace inteiro e devolve uma mensagem generica: detalhe de
     * excecao inesperada frequentemente carrega nome de classe, consulta SQL ou
     * caminho de arquivo, que nao devem chegar ao cliente.
     *
     * @param e excecao capturada
     * @return 500
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Erro nao tratado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(messages.get("error.internal")));
    }
}
