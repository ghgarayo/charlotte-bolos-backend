# Charlotte Bolos

API REST da confeitaria Charlotte Bolos.

## Stack

- Java 21
- Spring Boot 3.4
- Spring Web, Spring Data JPA, Validation
- Spring Security com autenticação por JWT (jjwt)
- PostgreSQL
- Flyway (versionamento e migração do banco)
- Lombok + DevTools
- Testcontainers nos testes (PostgreSQL real)

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

**Só em produção**, e todas **sem default** — faltando qualquer uma, a aplicação
não sobe:

| Variável      | Para quê         |
|---------------|------------------|
| `CORS_ALLOWED_ORIGINS` | Domínios do front, separados por vírgula |
| `JWT_SECRET`  | Assinatura dos tokens — **mínimo 32 caracteres** |
| `ADMIN_EMAIL` | E-mail do administrador inicial |
| `ADMIN_PASSWORD_HASH` | **Hash BCrypt** da senha dele, não a senha |

Em `dev` as quatro têm valor no `application-dev.yml`, então não precisam do
`.env`. Como gerar o hash de produção: [00 — Progresso](docs/00-progresso.md).

## Migrações (Flyway)

Os scripts ficam em `src/main/resources/db/migration`, no padrão
`V<versão>__<descrição>.sql`. As existentes:

```
V1__create_people_and_users.sql   tabelas people e users
V2__seed_admin_user.sql           administrador inicial
```

O Flyway roda as migrações automaticamente ao iniciar a aplicação.
Como `spring.jpa.hibernate.ddl-auto=validate`, o schema é controlado
apenas pelas migrações — o Hibernate só confere se as entidades batem
com as tabelas.

> Migração já aplicada **não deve ser editada**: o Flyway guarda um checksum e
> recusa o start se o arquivo mudar. Corrija com uma nova versão. (A `V1` foi
> alterada uma vez, antes de qualquer implantação; se o seu banco de dev acusar
> divergência, recrie-o com `docker compose down -v`.)

## Autenticação

Único endpoint hoje. Em `dev`, o administrador vem do seed da `V2`:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email": "admin@charlottebolos.com.br", "password": "admin123"}'
```

A resposta traz o token dentro do envelope padrão da API:

```json
{
  "success": true,
  "message": "Autenticado com sucesso",
  "data": { "token": "eyJ...", "expiresInMs": 86400000,
            "email": "admin@charlottebolos.com.br",
            "role": "ADMIN", "fullName": "Admin Charlotte" }
}
```

Use-o nas demais chamadas:

```bash
curl http://localhost:8080/api/... -H 'Authorization: Bearer eyJ...'
```

Sem token, ou com token inválido ou expirado, a API responde **401**. Autenticado
mas sem permissão, **403**. Detalhes em
[07 — Segurança](docs/07-seguranca.md).

## Documentação

As decisões de projeto (arquitetura, infra, entidades) ficam em [`docs/`](docs/README.md).
Comece por [01 — Infraestrutura](docs/01-infraestrutura.md).

## Estrutura

```
src/main/java/br/com/charlottebolos/
├── config/       # configurações (beans, CORS, segurança, JWT)
├── controller/   # endpoints REST
├── service/      # regras de negócio (interface + impl/)
├── repository/   # interfaces Spring Data JPA
├── model/        # entidades JPA
├── dto/          # objetos de transferência (request/response)
├── exception/    # GlobalExceptionHandler
└── shared/       # o que atravessa camadas (BaseEntity, ApiResponse)
```
