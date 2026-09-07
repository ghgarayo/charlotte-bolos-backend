# Charlotte Bolos

API REST da confeitaria Charlotte Bolos.

## Stack

- Java 21
- Spring Boot 3.4
- Spring Web, Spring Data JPA, Validation
- PostgreSQL
- Flyway (versionamento e migração do banco)
- Lombok + DevTools

## Pré-requisitos

- Docker Desktop (única dependência para o fluxo abaixo)
- Opcional: JDK 21 + Maven 3.9+, se quiser compilar na máquina host

## Subir tudo com Docker (desenvolvimento)

```bash
cp .env.example .env   # opcional; sem .env valem os defaults
docker compose up
```

Isso sobe:

| Serviço | Porta | Descrição                                      |
|---------|-------|------------------------------------------------|
| `db`    | 5432  | PostgreSQL 16 com o banco `charlotte_bolos`     |
| `app`   | 8080  | A API, rodando via `mvn spring-boot:run`        |
| `app`   | 5005  | Porta de debug remoto (JDWP) da API             |

A API sobe em `http://localhost:8080`. O `app` só inicia depois que o
healthcheck do `db` passa, então não há corrida na primeira subida.

Comandos úteis:

```bash
docker compose up -d          # em background
docker compose logs -f app    # acompanhar a aplicação
docker compose restart app    # recompila e reinicia a API
docker compose down           # derruba os containers (mantém os dados)
docker compose down -v        # derruba e APAGA o volume do banco
```

### Hot reload

O projeto inteiro é montado em `/app` no container e o DevTools observa
`target/classes`. Ou seja, o restart automático dispara quando as **classes
compiladas** mudam:

- **Com JDK no host:** compile pela IDE (no IntelliJ, `Ctrl+F9`) ou rode
  `mvn compile`. Em ~2s o DevTools reinicia a aplicação dentro do container.
- **Sem JDK no host:** use `docker compose restart app`, que recompila e
  sobe de novo.

### Debug remoto

A JVM do container escuta na porta `5005`. Na IDE, crie uma configuração
*Remote JVM Debug* apontando para `localhost:5005` e conecte.

### Acessar o banco

```bash
docker compose exec db psql -U postgres -d charlotte_bolos
```

Ou conecte qualquer cliente em `localhost:5432` com `postgres`/`postgres`.

## Rodar sem Docker

Suba só o banco e rode a aplicação no host:

```bash
docker compose up -d db
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

> O perfil padrão é `prod`. Sem `-Dspring-boot.run.profiles=dev` a aplicação
> exige `DB_HOST`, `DB_USER` e `DB_PASSWORD` definidos no ambiente — e o
> arquivo `.env` **não** é lido pelo Spring, só pelo Docker Compose.

> Dica: para gerar o Maven Wrapper (`./mvnw`) e não depender do Maven
> instalado globalmente, rode uma vez (com internet): `mvn wrapper:wrapper`.

## Imagem de produção

O `Dockerfile` é multi-estágio; o estágio `runtime` empacota o jar sobre
um JRE 21 enxuto e roda com usuário não-root:

```bash
docker build --target runtime -t charlotte-bolos:latest .
```

## Configuração

### Perfis

| Arquivo | Quando vale |
|---|---|
| `application.yml` | Sempre — base compartilhada |
| `application-dev.yml` | Só com `SPRING_PROFILES_ACTIVE=dev` (o compose já define) |
| `application-prod.yml` | **Padrão**, quando nenhum perfil é declarado |

O padrão é `prod` de propósito: esquecer de declarar o ambiente resulta na
configuração restrita (credenciais obrigatórias, sem `baseline-on-migrate`),
nunca na permissiva. Desenvolvimento é sempre explícito.

Para subir com o `.env` de produção:

```bash
docker compose --env-file .env.prod up -d
```

### Variáveis

As credenciais do banco vêm de variáveis de ambiente, com defaults para
desenvolvimento local (ver `src/main/resources/application.yml`). O
`docker-compose.yml` lê essas mesmas variáveis de um `.env` na raiz
(veja `.env.example` e `.env.prod.example`):

| Variável      | Default          |
|---------------|------------------|
| `SPRING_PROFILES_ACTIVE` | `prod` (no container: `dev`) |
| `DB_HOST`     | `localhost` (no container: `db`) |
| `DB_PORT`     | `5432`           |
| `DB_NAME`     | `charlotte_bolos`|
| `DB_USER`     | `postgres`       |
| `DB_PASSWORD` | `postgres`       |

## Migrações (Flyway)

Coloque os scripts SQL em `src/main/resources/db/migration`, seguindo o
padrão de nome `V<versão>__<descrição>.sql`. Exemplo:

```
V1__create_bolo_table.sql
V2__create_cliente_table.sql
```

O Flyway roda as migrações automaticamente ao iniciar a aplicação.
Como `spring.jpa.hibernate.ddl-auto=validate`, o schema é controlado
apenas pelas migrações — o Hibernate só confere se as entidades batem
com as tabelas.

## Documentação

As decisões de projeto (arquitetura, infra, entidades) ficam em [`docs/`](docs/README.md).
Comece por [01 — Infraestrutura](docs/01-infraestrutura.md).

## Estrutura

```
src/main/java/com/charlottebolos/charlottebolos/
├── config/       # configurações (beans, CORS, etc.)
├── controller/   # endpoints REST
├── service/      # regras de negócio
├── repository/   # interfaces Spring Data JPA
├── model/        # entidades JPA
└── dto/          # objetos de transferência (request/response)
```
