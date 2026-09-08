# 01 — Infraestrutura

> Última atualização: 2026-09-07 · Status: implementado e validado localmente

## Visão geral

Todo o ambiente de desenvolvimento roda em Docker. A única dependência
obrigatória na máquina do desenvolvedor é o **Docker Desktop** — JDK e Maven
são opcionais, usados apenas por quem quiser compilar no host para ganhar
hot reload instantâneo.

```
┌───────────────── docker compose (rede: charlotte-bolos_default) ─────────┐
│                                                                           │
│   ┌────────────────────────┐            ┌─────────────────────────────┐  │
│   │ app                    │            │ db                          │  │
│   │ charlotte-bolos-app    │            │ charlotte-bolos-db          │  │
│   │                        │   JDBC     │                             │  │
│   │ maven:3.9-temurin-21   │ ─────────► │ postgres:16                 │  │
│   │ mvn spring-boot:run    │  db:5432   │                             │  │
│   │ Spring Boot 3.4        │            │ healthcheck: pg_isready     │  │
│   └───────┬────────┬───────┘            └──────────────┬──────────────┘  │
│           │        │                                   │                  │
│      8080 │   5005 │ (JDWP)                            │ 5432             │
└───────────┼────────┼───────────────────────────────────┼─────────────────┘
            ▼        ▼                                   ▼
     localhost:8080  localhost:5005              localhost:5432

  bind mount  ./ (projeto inteiro)  ──►  /app          [app]
  volume      maven_repo            ──►  /root/.m2     [app]
  volume      charlotte_bolos_data  ──►  /var/lib/postgresql/data   [db]
```

## Componentes

| Serviço | Imagem | Container | Portas (host) |
|---|---|---|---|
| `app` | build local, estágio `dev` | `charlotte-bolos-app` | `8080` (API), `5005` (debug JDWP) |
| `db` | `postgres:16` | `charlotte-bolos-db` | `5432` |

Volumes nomeados:

| Volume | Montado em | Para quê |
|---|---|---|
| `charlotte_bolos_data` | `db:/var/lib/postgresql/data` | Dados do Postgres sobrevivem a `docker compose down` |
| `maven_repo` | `app:/root/.m2` | Cache de dependências entre restarts, fora do projeto |

## Arquivos

| Arquivo | Papel |
|---|---|
| `Dockerfile` | Multi-estágio: `deps` → `dev` / `build` → `runtime` |
| `docker-compose.yml` | Orquestra `app` + `db` para desenvolvimento |
| `.dockerignore` | Mantém `target/`, `.git/`, `.idea/` fora do contexto de build |
| `.env.example` | Modelo do `.env` de desenvolvimento, lido pelo compose |
| `.env.prod.example` | Modelo do `.env.prod` — nunca versionar o preenchido |
| `application.yml` | Config base + perfil padrão (`prod`) |
| `application-dev.yml` | Conveniências de desenvolvimento |
| `application-prod.yml` | Credenciais sem default (falha em vez de herdar dev) |

### Estágios do Dockerfile

| Estágio | Base | Para quê |
|---|---|---|
| `deps` | `maven:3.9-eclipse-temurin-21` | `mvn dependency:go-offline` — camada cacheada, invalidada só quando o `pom.xml` muda |
| `dev` | `deps` | Roda `mvn spring-boot:run`; é o alvo usado pelo compose |
| `build` | `deps` | `mvn package -DskipTests` — gera o jar |
| `runtime` | `eclipse-temurin:21-jre` | Só o jar + JRE, usuário não-root `spring` (uid 1001) |

## Variáveis de ambiente

Defaults definidos em `src/main/resources/application.yml`; o compose lê os
mesmos nomes de um `.env` na raiz (opcional — sem ele valem os defaults).

> **Spring Boot não lê arquivos `.env`.** Quem lê é o Docker Compose, que
> injeta as variáveis no container. Rodando `mvn spring-boot:run` direto no
> host, o `.env` é ignorado por completo.

| Variável | Default | No container `app` |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | `dev` (definido pelo compose) |
| `DB_HOST` | `localhost` | `db` (nome do serviço na rede do compose) |
| `DB_PORT` | `5432` | `5432` |
| `DB_NAME` | `charlotte_bolos` | idem |
| `DB_USER` | `postgres` | idem |
| `DB_PASSWORD` | `postgres` | idem |
| `APP_PORT` | `8080` | porta publicada no host |
| `DEBUG_PORT` | `5005` | porta de debug publicada no host |

Quatro outras existem **apenas no perfil `prod`** e não têm default algum — em
`dev` os valores vêm do `application-dev.yml`, então não passam pelo `.env`:

| Variável | Para quê |
|---|---|
| `CORS_ALLOWED_ORIGINS` | Domínios do front autorizados no navegador |
| `JWT_SECRET` | Assinatura dos tokens; mínimo 32 caracteres |
| `ADMIN_EMAIL` | E-mail do administrador criado pela migração `V2` |
| `ADMIN_PASSWORD_HASH` | Hash BCrypt da senha dele |

Elas seguem a mesma postura do **D9** — faltando qualquer uma, a aplicação não
sobe. A diferença em relação ao datasource descrito no **D10** é o modo da
falha: estas abortam o start com mensagem clara (`Could not resolve placeholder`,
ou erro do Flyway), em vez de virar um `UnknownHostException` na primeira
conexão. Ver [07 — Segurança](07-seguranca.md) e
[00 — Progresso](00-progresso.md).

## Decisões

### D1 — Aplicação roda em container, não só o banco

**Contexto:** o compose original subia apenas o Postgres; a API dependia de
JDK 21 e Maven instalados no host.

**Decisão:** containerizar também a aplicação, com `docker compose up`
subindo o ambiente inteiro.

**Motivo:** um único pré-requisito (Docker) e paridade de versão de JDK entre
máquinas. Onboarding vira um comando.

**Descartado:** manter só o banco em Docker — mais simples, mas cada
desenvolvedor precisa acertar a versão do JDK por conta.

### D2 — Dockerfile multi-estágio com um estágio `dev` dedicado

**Contexto:** desenvolvimento e produção têm necessidades opostas — dev quer
Maven, fontes e recompilação; produção quer imagem mínima e imutável.

**Decisão:** um só `Dockerfile` com os estágios `deps`, `dev`, `build` e
`runtime`. O compose aponta para `target: dev`.

**Motivo:** evita dois arquivos divergindo com o tempo, e `deps` é
compartilhado — as dependências são baixadas uma vez só.

**Descartado:** `Dockerfile` + `Dockerfile.dev` separados.

### D3 — Bind mount do projeto inteiro (`.:/app`)

**Contexto:** o hot reload do Spring DevTools observa `target/classes`, não os
`.java`. Alguém precisa recompilar.

**Decisão:** montar o diretório do projeto inteiro, incluindo `target/`.

**Motivo:** atende os dois perfis de desenvolvedor sem configuração extra —
com JDK no host, o `Ctrl+F9` da IDE escreve em `./target/classes` e o DevTools
reinicia sozinho; sem JDK, `docker compose restart app` recompila dentro do
container.

**Descartado:** montar só `./src` — mais limpo, mas quebra o hot reload via
IDE, já que o `target/` ficaria invisível para o host.

**Custo aceito:** o `target/` é compartilhado entre host e container. Se o
projeto passar a ser compilado nos dois lugares alternadamente e algo ficar
estranho, `docker compose down` + `mvn clean` resolve.

### D4 — `depends_on` com `condition: service_healthy`

**Contexto:** o `app` sobe mais rápido que o Postgres aceita conexões, e o
Flyway roda logo no start.

**Decisão:** healthcheck `pg_isready` no `db` e `depends_on` condicional no `app`.

**Motivo:** elimina a falha de corrida na primeira subida, sem script de
`wait-for-it` nem retry manual.

### D5 — Cache do Maven em volume nomeado

**Contexto:** sem cache, todo restart do container rebaixaria as dependências.

**Decisão:** volume `maven_repo` montado em `/root/.m2`.

**Motivo:** o volume é inicializado a partir do conteúdo da imagem (o estágio
`deps` já populou `/root/.m2`), então o primeiro start já vem quente. Fica
fora do bind mount para não criar um `.m2` dentro do projeto.

### D6 — Porta de debug (JDWP) exposta em desenvolvimento

**Contexto:** com a JVM dentro do container, o debugger da IDE não alcança o
processo.

**Decisão:** subir com `-agentlib:jdwp=...,address=*:5005` e publicar a `5005`.

**Motivo:** permite *Remote JVM Debug* apontando para `localhost:5005`.

**Atenção:** `suspend=n`, então a aplicação não trava esperando o debugger.
Isso é configuração **de desenvolvimento** — o estágio `runtime` não expõe JDWP.

### D7 — Sem Maven Wrapper

**Contexto:** o projeto não tem `mvnw`.

**Decisão:** o Maven vem da imagem base (`maven:3.9-eclipse-temurin-21`); não
foi gerado wrapper.

**Motivo:** dentro do Docker o wrapper é redundante. Continua útil para quem
roda no host — `mvn wrapper:wrapper` gera quando for necessário.

### D8 — Credenciais fracas por padrão, isoladas em `.env`

**Contexto:** `postgres/postgres` é conveniente em dev e inaceitável fora dele.

**Decisão:** defaults fracos, sobrescrevíveis por um `.env` (já no
`.gitignore`), com `.env.example` versionado.

**Motivo:** zero atrito local sem induzir o mesmo valor em outros ambientes.

**Desdobramento:** formalizado em D9 — o perfil `prod` remove os defaults, de
modo que produção não consegue herdar as credenciais de desenvolvimento.

### D9 — Perfis do Spring, com `prod` como padrão

**Contexto:** com um `.env` para dev e outro para produção, surgiu a dúvida de
se ainda era preciso um arquivo de configuração do Spring. É preciso, porque os
dois resolvem problemas diferentes: o `.env` guarda **valores**; o
`application.yml` guarda a **estrutura** — quais propriedades existem e como se
ligam às variáveis. Além disso há muita configuração que não é segredo nem
varia por máquina (`ddl-auto`, `open-in-view`, `flyway.locations`) e que deve
estar versionada.

**Decisão:** três arquivos YAML e o perfil **`prod` como padrão**:

| Arquivo | Conteúdo |
|---|---|
| `application.yml` | Base compartilhada + `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:prod}` |
| `application-dev.yml` | `show-sql`, `format_sql`, `baseline-on-migrate: true`, log de binds |
| `application-prod.yml` | Credenciais **sem default** |

Critério de onde cada coisa mora: *varia por ambiente e é secreto* → `.env`;
*varia por ambiente e é público* → `application-<perfil>.yml`; *não varia* →
`application.yml`.

**Motivo do padrão ser `prod`:** esquecer de declarar o ambiente deve resultar
na configuração **restrita**, nunca na permissiva. Um deploy sem
`SPRING_PROFILES_ACTIVE` roda com credenciais obrigatórias e sem
`baseline-on-migrate` — falha em vez de subir frouxo. Dev é sempre explícito, e
o `docker-compose.yml` declara `SPRING_PROFILES_ACTIVE=dev`.

**Descartado:** `application.properties` — é o mesmo mecanismo do `.yml` em
outro formato. Manter os dois cria duas fontes de verdade, e o `.properties`
vence nos conflitos, o que rende bugs difíceis de enxergar.

**Descartado:** não ter perfil padrão (base conservador, ambos opt-in). Só
inverte o problema: o esquecimento passa a ser silencioso em vez de barulhento.

### D10 — Falha por credencial ausente: fica na camada de deploy

**Contexto:** a intenção em D9 era que uma variável faltando derrubasse a
aplicação com erro claro. Ao testar, o comportamento real foi outro: o Spring
**não aborta** ao encontrar um placeholder sem valor durante o binding de
`@ConfigurationProperties` — ele mantém o texto literal. O que se vê é
`UnknownHostException: ${DB_HOST}` na hora de conectar.

**Decisão:** aceitar o comportamento no Spring e colocar a validação com
mensagem clara na camada de deploy, quando ela for definida. O Docker Compose
já oferece o operador adequado:

```yaml
DB_PASSWORD: ${DB_PASSWORD:?defina DB_PASSWORD no .env.prod}
```

**Motivo:** a propriedade de segurança que importa — **nunca herdar
`postgres/postgres` em produção** — já está garantida e foi verificada. O que
falta é qualidade de mensagem, e ela sai mais barata e mais cedo no compose
(falha antes de subir o container) do que em código Java.

**Descartado:** um bean de validação em Java só para checar variáveis. Adiciona
código de infraestrutura ao domínio e falha mais tarde que o compose.

## Validação

Executado e verificado em 2026-09-07 (Windows 11, Docker 29.1.3,
Compose v2.40.3):

- `docker compose build app` — BUILD SUCCESS
- `docker compose up -d` — `db` fica *healthy* antes de o `app` iniciar
- App conecta no Postgres 16.11; Flyway executa (avisa `No migrations found`,
  esperado — `db/migration` só tem `.gitkeep`); Tomcat sobe na 8080
- `curl localhost:8080` → **404** (correto: ainda não há controllers)
- JDWP escutando na 5005
- Hot reload: alteração recompilada → DevTools reinicia em **2,2 s**

Perfis (D9/D10), verificados na mesma data:

- Via compose → `The following 1 profile is active: "dev"`
- Sem `SPRING_PROFILES_ACTIVE` → `"prod"` (padrão aplicado)
- Perfil `prod` **com** credenciais → sobe normalmente
- Perfil `prod` **sem** credenciais → não sobe; falha em
  `UnknownHostException: ${DB_HOST}`, sem cair no default de desenvolvimento

## Em aberto

Nada além do ambiente local foi definido. A decidir:

1. **Hospedagem** — onde a API e o banco rodam fora da máquina do dev
   (VPS, Railway/Render, cloud gerenciada). Define quase todo o resto.
2. **CI/CD** — especificado em [03 — CI/CD](03-ci-cd.md); a CI está pronta
   para aplicar, a CD depende do item 1.
3. **Ambientes** — haverá homologação, ou só local e produção?
4. **Gestão de segredos** — a separação `.env` / `.env.prod` está feita (D9),
   mas arquivo em disco não escala para produção; falta escolher o mecanismo
   definitivo, que depende do item 1.
5. **Compose de produção** — não existe. É onde entra a validação de variáveis
   obrigatórias descrita em D10.
6. **Backup do banco** — política de dump e retenção.
7. **Observabilidade** — Spring Actuator (healthcheck, métricas) e agregação
   de logs. Hoje não há dependência de Actuator no `pom.xml`.
8. **Healthcheck do `app`** — o compose só faz healthcheck do `db`; depende
   do item 7.

## Histórico

| Data | Mudança |
|---|---|
| 2026-09-07 | Versão inicial: aplicação containerizada, healthcheck, cache do Maven, debug remoto |
| 2026-09-07 | Perfis do Spring (D9) com `prod` como padrão; `.env.prod.example`; validação de variáveis delegada ao deploy (D10) |
