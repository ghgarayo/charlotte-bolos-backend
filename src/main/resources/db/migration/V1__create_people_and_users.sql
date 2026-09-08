-- V1 - Tabelas de pessoa e usuario.
--
-- Espelha br.com.charlottebolos.model.Person e User, que estendem BaseEntity.
-- O Hibernate roda com ddl-auto=validate (application.yml): qualquer divergencia
-- entre estas colunas e as entidades derruba a aplicacao no start, com
-- "Schema-validation: missing table/column".
--
-- As seis colunas de BaseEntity se repetem nas duas tabelas porque
-- @MappedSuperclass nao cria tabela propria - as colunas sao copiadas para cada
-- entidade concreta.
--
-- Todas elas tem DEFAULT no banco. O Hibernate sempre informa os valores, entao
-- os defaults nao mudam nada pelo caminho do JPA; eles existem para o SQL puro
-- de migracao e seed, que de outro modo teria de repetir NOW() e 0 em cada
-- INSERT.

CREATE TABLE people (
    id            UUID         PRIMARY KEY,

    name          VARCHAR(100) NOT NULL,
    surname       VARCHAR(100) NOT NULL,
    -- UNIQUE aceitando nulo: o Postgres nao considera dois NULL iguais, entao
    -- varias pessoas podem existir sem documento sem violar a restricao.
    document      VARCHAR(20)  UNIQUE,
    phone1        VARCHAR(20),
    phone2        VARCHAR(20),
    date_of_birth DATE,

    -- Lock otimista (@Version). O Hibernate incrementa a cada UPDATE e recusa
    -- gravacao sobre uma versao ja alterada por outra transacao.
    version       BIGINT       NOT NULL DEFAULT 0,

    -- Auditoria (BaseEntity), preenchida pelo AuditingEntityListener.
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100)
);

CREATE TABLE users (
    id         UUID         PRIMARY KEY,

    -- UNIQUE espelha o @OneToOne: uma pessoa tem no maximo um usuario.
    -- ON DELETE CASCADE acompanha o cascade = ALL + orphanRemoval declarado em
    -- Person.user, para que apagar a pessoa pelo banco nao deixe usuario orfao.
    person_id  UUID         NOT NULL UNIQUE REFERENCES people (id) ON DELETE CASCADE,

    email      VARCHAR(150) NOT NULL UNIQUE,
    -- Sem length na entidade, entao o Hibernate assume o default de 255.
    -- Um hash BCrypt ocupa 60 caracteres; a folga cobre troca de algoritmo.
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,

    version    BIGINT       NOT NULL DEFAULT 0,

    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100),

    -- O enum e persistido como texto (@Enumerated(EnumType.STRING)), o que sem
    -- esta restricao aceitaria qualquer string numa coluna que decide
    -- autorizacao. Custo: incluir um novo papel exige nova migracao.
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'MANAGER', 'USER', 'CUSTOMER'))
);
