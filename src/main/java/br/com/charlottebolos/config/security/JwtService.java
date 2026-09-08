package br.com.charlottebolos.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

/**
 * Emissao e leitura dos tokens JWT.
 *
 * <p>Assinatura HMAC-SHA (HS256) com segredo simetrico: o mesmo segredo assina e
 * confere. Serve enquanto quem emite e quem valida sao a mesma aplicacao; se um
 * dia outro servico precisar apenas validar, a troca e para um par de chaves
 * assimetrico (RS256), para nao distribuir o poder de emitir junto com o de
 * conferir.
 *
 * <p>O token nao carrega papeis. As authorities sao recarregadas do banco a cada
 * requisicao pelo {@link JwtAuthenticationFilter} — mais consultas, porem a
 * revogacao de um papel passa a valer na hora, em vez de esperar o token
 * expirar.
 */
@Service
public class JwtService {

    /**
     * HS256 exige chave de no minimo 256 bits. Como o segredo e lido como texto
     * UTF-8, sao 32 caracteres ASCII.
     */
    private static final int MINIMUM_SECRET_LENGTH = 32;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey signingKey;

    /**
     * Valida o segredo e monta a chave uma unica vez, no start.
     *
     * <p>Sem esta verificacao, um segredo curto so estouraria
     * {@code WeakKeyException} na primeira emissao de token — ou seja, no
     * primeiro login de um usuario real, ja em producao. Falhar no start
     * transforma um incidente em erro de configuracao.
     */
    @PostConstruct
    void initialize() {
        if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.jwt.secret precisa de ao menos " + MINIMUM_SECRET_LENGTH
                            + " caracteres para HS256; recebeu "
                            + (secret == null ? 0 : secret.length()));
        }
        // Os bytes UTF-8 do segredo sao a chave, sem passar por Base64. Uma
        // volta encode/decode aqui seria identidade pura e daria a impressao
        // falsa de que a propriedade precisa estar em Base64.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Emite um token para o usuario autenticado.
     *
     * @param userDetails usuario ja autenticado
     * @return token assinado, pronto para o cabecalho {@code Authorization}
     */
    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * @param token token recebido
     * @return o {@code subject}, que aqui e o e-mail do usuario
     * @throws io.jsonwebtoken.JwtException se estiver expirado, malformado ou
     *                                      com assinatura invalida
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Confere se o token pertence ao usuario e ainda vale.
     *
     * @param token       token recebido
     * @param userDetails usuario carregado do banco
     * @return {@code true} se o subject bate e o prazo nao venceu
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername()) && !isExpired(token);
    }

    /** Duracao configurada do token, em milissegundos. */
    public long getExpirationMs() {
        return expirationMs;
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).toInstant().isBefore(Instant.now());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
