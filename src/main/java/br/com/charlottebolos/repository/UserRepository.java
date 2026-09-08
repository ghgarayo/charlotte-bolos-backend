package br.com.charlottebolos.repository;

import br.com.charlottebolos.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso aos usuarios.
 *
 * <p>Enxuto de proposito: so tem o que a autenticacao precisa hoje. Consultas de
 * listagem e filtro entram quando existir a tela que as consome.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Carrega o usuario pelo e-mail, que e o {@code username} do Spring
     * Security.
     *
     * <p>O {@code @EntityGraph} traz a {@code Person} na mesma consulta. Sem
     * ele, a associacao {@code LAZY} so seria resolvida dentro da transacao —
     * e este metodo e chamado pelo filtro de JWT, fora de qualquer transacao e
     * com {@code open-in-view} desligado, onde qualquer acesso a
     * {@code user.getPerson()} estouraria {@code LazyInitializationException}.
     *
     * @param email e-mail informado no login
     * @return o usuario, se existir
     */
    @EntityGraph(attributePaths = "person")
    Optional<User> findByEmail(String email);

    /**
     * @param email e-mail a verificar
     * @return {@code true} se ja houver usuario com este e-mail
     */
    boolean existsByEmail(String email);
}
