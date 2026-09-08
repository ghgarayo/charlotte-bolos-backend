package br.com.charlottebolos.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envelope padrao das respostas da API.
 *
 * <p>Uniformiza sucesso e erro num unico formato, para que o cliente leia
 * sempre {@code success} antes de olhar {@code data}, em vez de inferir o
 * resultado pela combinacao de status HTTP e formato do corpo.
 *
 * <p>{@code @JsonInclude(NON_NULL)} omite os campos nulos na serializacao: um
 * sucesso sem mensagem sai como {@code {"success":true,"data":...}}, sem a
 * chave {@code message} vazia no payload.
 *
 * <p>Prefira as fabricas {@link #ok(Object)} e {@link #error(String)} ao
 * builder — elas garantem que {@code success} corresponda a intencao. O builder
 * gerado pelo Lombok continua acessivel e nao impede montar um envelope
 * incoerente, como {@code success=false} sem mensagem alguma.
 *
 * @param <T> tipo do payload devolvido em {@code data}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    /** Resultado da operacao. E o unico campo sempre presente no JSON. */
    private boolean success;

    /** Texto destinado ao usuario final, ja resolvido no idioma da requisicao. */
    private String message;

    /** Corpo util da resposta. Ausente do JSON quando nulo. */
    private T data;

    /**
     * Resposta de sucesso sem mensagem, para quando o payload se explica
     * sozinho.
     *
     * @param data payload; se nulo, a chave {@code data} some do JSON
     * @param <T>  tipo do payload
     * @return envelope com {@code success=true}
     */
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    /**
     * Resposta de sucesso com mensagem para o usuario.
     *
     * @param message texto ja resolvido — traduza com {@code MessageService}
     *                antes de passar aqui, esta classe nao faz i18n
     * @param data    payload; se nulo, a chave {@code data} some do JSON
     * @param <T>     tipo do payload
     * @return envelope com {@code success=true}
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder().success(true).message(message).data(data).build();
    }

    /**
     * Resposta de erro sem detalhamento.
     *
     * @param message descricao do erro, ja resolvida no idioma da requisicao
     * @param <T>     tipo do payload, inferido pelo contexto de uso
     * @return envelope com {@code success=false} e sem {@code data}
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder().success(false).message(message).build();
    }

    /**
     * Resposta de erro com detalhamento — util para devolver, por exemplo, a
     * lista de campos que falharam na validacao junto da mensagem geral.
     *
     * @param message descricao do erro, ja resolvida no idioma da requisicao
     * @param data    detalhamento da falha
     * @param <T>     tipo do detalhamento
     * @return envelope com {@code success=false}
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return ApiResponse.<T>builder().success(false).message(message).data(data).build();
    }
}
