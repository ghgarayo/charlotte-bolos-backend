package br.com.charlottebolos.model;

import br.com.charlottebolos.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseEntity implements UserDetails {

    /** Prefixo exigido pelo Spring Security para authorities tratadas como papel. */
    private static final String ROLE_PREFIX = "ROLE_";

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Papeis do usuario, no formato que o Spring Security espera.
     *
     * <p>O prefixo {@code ROLE_} nao e decorativo: {@code hasRole('ADMIN')} e
     * {@code @PreAuthorize("hasRole('ADMIN')")} procuram a authority
     * {@code ROLE_ADMIN}. Sem ele as checagens nao encontram nada e **negam
     * acesso em silencio** — falha que nao aparece em log nem em excecao.
     *
     * <p>A alternativa seria devolver {@code role.name()} puro e usar sempre
     * {@code hasAuthority}. As duas convencoes funcionam; misturar as duas, nao.
     * Aqui a escolha e o prefixo, que e o padrao do framework.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(active);
    }

    public enum Role {
        ADMIN, MANAGER, USER, CUSTOMER
    }
}