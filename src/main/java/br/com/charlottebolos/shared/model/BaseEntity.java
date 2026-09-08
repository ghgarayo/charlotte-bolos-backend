package br.com.charlottebolos.shared.model;


import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Superclasse das entidades JPA: identidade e campos de auditoria comuns.
 *
 * <p>{@code @MappedSuperclass} significa que nao existe tabela para esta
 * classe. As colunas declaradas aqui sao copiadas para a tabela de cada
 * subclasse concreta — ou seja, toda migracao do Flyway que criar uma tabela
 * herdeira precisa repetir as seis colunas.
 *
 * <p>O identificador e um {@link UUID} gerado por {@link GenerationType#UUID},
 * do lado do Hibernate e nao do banco. A chave ja existe antes do
 * {@code INSERT}, o que dispensa o round-trip para descobrir o id gerado.
 *
 * <p>Os quatro campos de auditoria sao preenchidos pelo
 * {@link AuditingEntityListener}, que so age com o auditing do Spring Data
 * ligado por {@code @EnableJpaAuditing} — feito hoje em {@code AuditingConfig}.
 * A dependencia e critica: sem essa configuracao, {@code createdAt} e
 * {@code updatedAt} ficariam nulos e todo {@code INSERT} falharia, ja que as
 * duas colunas sao {@code nullable = false}.
 *
 * <p>{@code createdBy} e {@code updatedBy} vem do {@code AuditorAware} declarado
 * na mesma classe de configuracao, que le o usuario autenticado do
 * {@code SecurityContext} e grava {@code system} quando a requisicao e
 * anonima.
 *
 * <p>O construtor sem argumentos e {@code protected} porque o JPA precisa dele
 * para instanciar a entidade por reflexao, mas o codigo de aplicacao deve usar
 * o builder da subclasse, gerado por {@code @SuperBuilder}.
 *
 * <p>Esta classe deliberadamente nao define {@code equals} e {@code hashCode}:
 * comparar por id quebra para instancias ainda nao persistidas, cujo id e nulo,
 * e cada subclasse decide o criterio que faz sentido para ela.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /** Chave primaria, gerada pelo Hibernate antes do {@code INSERT}. Imutavel. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    /**
     * Contador de lock otimista, incrementado pelo Hibernate a cada
     * {@code UPDATE}.
     *
     * <p>Duas edicoes concorrentes do mesmo registro nao se sobrescrevem em
     * silencio: a segunda encontra a versao ja alterada e falha com
     * {@code OptimisticLockingFailureException}, traduzida em HTTP 409 pelo
     * {@code GlobalExceptionHandler}.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Momento da criacao, gravado uma unica vez. Obrigatorio no banco. */
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /** Autor da criacao, vindo do {@code AuditorAware}. Opcional no banco. */
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    /** Momento da ultima alteracao, reescrito a cada {@code UPDATE}. Obrigatorio no banco. */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Autor da ultima alteracao, vindo do {@code AuditorAware}. Opcional no banco. */
    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

}

