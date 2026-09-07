# 03 — CI/CD

> Última atualização: 2026-09-07 · Status: CI especificada e pronta para aplicar · CD bloqueada pela decisão de hospedagem

## Escopo deste documento

| Parte | Status | Motivo |
|---|---|---|
| **CI** (build + testes) | Especificada por completo | Não depende de nada externo |
| **CD** (deploy) | Só o desenho | Depende de *onde* a aplicação roda — item 1 do *Em aberto* de [01 — Infraestrutura](01-infraestrutura.md) |

A CI pode ser aplicada hoje. A CD só ganha forma final quando a hospedagem for
escolhida; até lá, o que está aqui é o formato do pipeline e o que muda conforme
a opção.

---

## Pré-requisito: os testes precisam de um banco

> ✅ **Resolvido em 2026-09-07.** `mvn verify` passa no host e dentro do
> container. Esta seção fica como registro do problema e da causa.

Estado anterior, verificado num ambiente sem as variáveis de banco (ou seja,
exatamente o que um runner de CI oferece):

```
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
[ERROR] CharlotteBolosApplicationTests.contextLoads » IllegalState Failed to load ApplicationContext
Caused by: java.net.UnknownHostException: ${DB_HOST}
```

Por quê: `CharlotteBolosApplicationTests` usa `@SpringBootTest`, que sobe o
contexto inteiro — incluindo DataSource e Flyway. Sem `SPRING_PROFILES_ACTIVE`,
vale o padrão `prod` (D9), que **de propósito** não tem credenciais default.

Não é um bug do perfil: é o perfil funcionando. O que falta é dizer aos testes
onde fica o banco *deles*.

### Solução: Testcontainers

Como o projeto já assume Docker como única dependência (D1), Testcontainers cai
naturalmente: cada execução sobe um Postgres real, roda as migrações do Flyway
contra ele e derruba no fim. Funciona igual na máquina do dev e no runner.

Isso importa mais do que parece aqui: com `ddl-auto: validate` e o schema
inteiro vindo de migrações Flyway, testar contra um banco em memória (H2)
validaria um schema que **não é** o de produção. Migração com sintaxe específica
de Postgres passaria a quebrar só no deploy.

**1. Dependências no `pom.xml`** (o `spring-boot-starter-parent` já gerencia as
versões):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

**2. Configuração de teste** em
`src/test/java/br/com/charlottebolos/TestcontainersConfiguration.java`:

```java
package br.com.charlottebolos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    // A mesma versao do docker-compose.yml: o teste roda contra o banco de verdade.
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16");
    }
}
```

**3. Importar no teste:**

```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CharlotteBolosApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

`@ServiceConnection` injeta host, porta e credenciais do container direto no
contexto, com precedência sobre `spring.datasource.*`. Ou seja: os placeholders
sem valor do perfil `prod` deixam de importar nos testes, e não é preciso um
perfil `test` só para isso.

### Duas armadilhas de ambiente encontradas na implementação

Fazer o Testcontainers funcionar exigiu dois ajustes que não são óbvios. Ambos
foram diagnosticados na marra; ficam registrados para não custarem o mesmo
tempo de novo.

#### 1. Docker 29 recusa a versão de API do docker-java (HTTP 400)

Sintoma: `Could not find a valid Docker environment`, com o daemon respondendo
**400** e um payload vazio — enquanto o `docker` CLI funcionava normalmente
contra o mesmo daemon.

A causa saiu de um teste direto no socket, sem Java no meio:

```
GET /info        -> 200
GET /v1.32/info  -> 400      <- o default do docker-java
GET /v1.41/info  -> 400
GET /v1.44/info  -> 200      <- minima aceita pelo engine 29.1.3
GET /v1.52/info  -> 200
```

O docker-java embutido no Testcontainers 1.20.4 pede uma versão de API anterior
a 1.44, que engines Docker 29+ recusam. Subir o Testcontainers para 1.21.3
(docker-java 3.4.2) **não** resolveu.

Solução aplicada — a propriedade `api.version` no JVM de teste, via Surefire:

```xml
<properties>
    <docker.api.version>1.44</docker.api.version>
</properties>
...
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <api.version>${docker.api.version}</api.version>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

> **Duas coisas que não funcionam**, e custaram tempo:
> - a variável de ambiente `DOCKER_API_VERSION` é ignorada nesse caminho;
> - `MAVEN_OPTS=-Dapi.version=1.44` também não serve — o Surefire **não
>   propaga** as system properties do JVM do Maven para o JVM de teste. Só
>   `systemPropertyVariables` (ou `-D` na linha de comando) chega lá.
>
> `1.44` é suportada por qualquer engine a partir do Docker 25.0, então serve
> tanto na máquina do dev quanto no runner. Está numa property justamente para
> ser ajustável se um dia um ambiente exigir outra.

#### 2. Rodando os testes de dentro do container de dev

O `docker-compose.yml` monta `/var/run/docker.sock` no serviço `app`, para que o
Testcontainers alcance o daemon do host. Os containers de teste nascem então
**irmãos** do `app`, não filhos: as portas ficam publicadas no host, e o
endereço para alcançá-las é o host — não o gateway da rede do compose.

Sem isso: `Could not connect to Ryuk at 172.17.0.1:...`.

Por isso o compose define:

```yaml
TESTCONTAINERS_HOST_OVERRIDE: host.docker.internal
```

Essa variável é **específica de rodar dentro do container**. Na CI os testes
rodam direto no runner, e ela não deve existir — por isso mora no compose, e não
no `pom.xml`.

> ⚠️ Montar o socket do Docker dá ao container acesso equivalente a root no
> daemon do host. É prática comum em ambiente de desenvolvimento com
> Testcontainers, mas é uma escolha consciente — não faça isso numa imagem que
> vá para produção.

### Estado verificado

| Onde | Comando | Resultado |
|---|---|---|
| Host (JDK 25 + Maven 3.9.8) | `mvn verify` | ✅ `Tests run: 1, Failures: 0, Errors: 0` |
| Dentro do container | `docker compose exec app mvn verify` | ✅ idem |

Container `postgres:16` sobe em ~1,6 s nos dois casos.

### Alternativa: service container do GitHub Actions

Postgres como serviço no workflow, com os testes apontando para `localhost:5432`
via variáveis de ambiente. Mais simples, porém:

- não funciona na máquina do dev sem o compose de pé;
- a configuração do banco de teste vive no YAML do CI, não no projeto.

Descartada por isso — ver D14.

---

## Parte 1 — CI

### O que ela faz

Em todo PR para `develop` ou `main`, e em todo push para `develop`: compila,
roda os testes e reporta. Nada mais — sem publicar artefato, sem deploy.

### `.github/workflows/ci.yml`

```yaml
name: CI

on:
  pull_request:
    branches: [main, develop]
  push:
    branches: [develop]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Configura JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build e testes
        run: mvn -B verify
```

Notas de desenho:

| Escolha | Por quê |
|---|---|
| `cache: maven` | O `setup-java` cacheia o `~/.m2`; sem isso cada run rebaixa tudo |
| `mvn -B verify` | `verify` inclui `test`; `-B` (batch) tira o output interativo |
| Sem `services:` | O Testcontainers cuida do Postgres; o runner já tem Docker |
| Roda em PR **e** push para `develop` | O PR protege `main`; o push mantém o histórico de `develop` verde |

### Custo

Repositório público → **Actions é gratuita e ilimitada**. Isso é uma
consequência boa de D13; em repositório privado, o plano Free daria 2.000
minutos/mês.

### Tornar o check obrigatório

Isso fecha o item que ficou pendente em [02 — Proteção de branches](02-protecao-de-branches.md)
(D11): a regra *Require status checks to pass* não pôde ser marcada porque não
havia check para exigir.

Depois que a CI rodar **pelo menos uma vez** (o GitHub só oferece checks que já
reportaram):

1. Settings → Rules → Rulesets → `protect-main`
2. Marcar **Require status checks to pass**
3. Buscar e adicionar **`build-and-test`** (o nome do job)
4. Marcar também *Require branches to be up to date before merging*

A partir daí, `develop → main` só mergeia com o build verde.

---

## Parte 2 — CD (desenho, pendente de decisão)

### O princípio que já está estabelecido

Da discussão de branches: **o mesmo commit viaja de `develop` para `main`**. A
CD segue disso — *build once, deploy many*. A imagem é construída uma vez,
etiquetada com o SHA, e é **essa** imagem que vai para produção. Não se
reconstrói no deploy: reconstruir é entregar algo que ninguém testou.

```
merge em main ──► build da imagem ──► push GHCR ──► [aprovação] ──► deploy
                  (target: runtime)   :sha + :latest
```

### Registro de imagens: GHCR

O `ghcr.io` (GitHub Container Registry) é gratuito para repositórios públicos e
autentica com o `GITHUB_TOKEN` que a Action já tem — sem segredo adicional.

### `.github/workflows/cd.yml` (esqueleto)

```yaml
name: CD

on:
  push:
    branches: [main]

permissions:
  contents: read
  packages: write

jobs:
  build-image:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - uses: docker/build-push-action@v6
        with:
          context: .
          target: runtime          # o estagio enxuto do Dockerfile (D2)
          push: true
          tags: |
            ghcr.io/ghgarayo/charlotte-bolos-backend:latest
            ghcr.io/ghgarayo/charlotte-bolos-backend:${{ github.sha }}

  deploy:
    needs: build-image
    runs-on: ubuntu-latest
    environment: production        # gate de aprovacao manual
    steps:
      - name: Deploy
        run: echo "PENDENTE: depende da hospedagem escolhida"
```

O `target: runtime` é o motivo de o `Dockerfile` ter sido feito multi-estágio lá
em D2 — a imagem publicada carrega só JRE + jar, com usuário não-root.

### Gate de aprovação: GitHub Environments

Settings → Environments → New environment → `production` → **Required
reviewers**.

Com isso o job `deploy` **pausa** e espera aprovação humana explícita antes de
tocar produção. É gratuito em repositório público.

Vale notar como isso resolve, no lugar certo, a questão levantada em D12: a
autorização para produção é um ato humano registrado, separado da automação que
constrói e testa. O pipeline pode fazer todo o trabalho; quem libera é você.

### Migrações no deploy — o risco a tratar

O Flyway roda **no start da aplicação**. Isso significa que o deploy aplica
migrações automaticamente, e uma migração ruim derruba a subida do container.

Consequências a considerar quando a hospedagem existir:

- Migração precisa ser compatível com a versão anterior da aplicação, porque
  durante o deploy as duas convivem por alguns segundos (evitar `DROP COLUMN` no
  mesmo release que para de usá-la — faça em dois passos).
- Backup antes do deploy (item de *Em aberto* do doc 01).
- `baseline-on-migrate: false` em prod (D9) é o que faz um estado inesperado
  falhar em vez de ser adotado silenciosamente.

### O que muda conforme a hospedagem

| Opção | Passo de deploy | Banco | Observação |
|---|---|---|---|
| **Railway / Render** | Deploy hook (webhook) ou CLI | Postgres gerenciado incluso | Menor esforço; secrets e backup pela plataforma |
| **Fly.io** | `flyctl deploy` com token | Postgres gerenciado | Bom controle, custo baixo |
| **VPS + compose** | SSH + `docker compose pull && up -d` | Container ou gerenciado | Mais barato e mais trabalho: backup, TLS e segredos por sua conta |
| **Cloud Run / ECS** | CLI do provedor | Cloud SQL / RDS | Escala bem; mais configuração inicial |

Para o porte deste projeto, as duas primeiras linhas são as candidatas óbvias.

---

## Decisões

### D14 — Testcontainers para o banco de testes

**Contexto:** `@SpringBootTest` sobe o contexto inteiro e precisa de um banco.
Com o perfil padrão `prod` (D9), sem credenciais, os testes falham em runner
limpo — verificado.

**Decisão:** Testcontainers com `@ServiceConnection`, subindo `postgres:16` por
execução.

**Motivo:** o schema vem inteiro de migrações Flyway com `ddl-auto: validate`.
Testar contra H2 validaria um schema diferente do de produção, e migração com
sintaxe de Postgres só quebraria no deploy. Além disso o comportamento é o mesmo
na máquina do dev e no CI, coerente com D1 (Docker como única dependência).

**Descartado:** service container do GitHub Actions — joga a configuração do
banco de teste para dentro do YAML de CI e não funciona localmente.

**Descartado:** H2 em memória — mais rápido, mas testaria outro banco.

**Custo aceito:** cada execução de teste sobe um container; alguns segundos a
mais por run (~1,6 s medidos).

**Custo não previsto, descoberto na implementação:** duas incompatibilidades de
ambiente — versão da API do Docker e rede em Docker-out-of-Docker — documentadas
na seção acima. Resolvidas, mas não eram custo zero.

### D15 — CI só valida; publicar e implantar é a CD

**Contexto:** seria possível um único workflow fazendo tudo.

**Decisão:** `ci.yml` (PR e push em `develop`) apenas compila e testa;
`cd.yml` (push em `main`) constrói a imagem e implanta.

**Motivo:** a CI roda a cada PR e precisa ser rápida e sem efeito colateral —
ela não deve ter permissão de escrita em registro nem em produção. Separar
mantém `packages: write` fora do workflow que executa código de PR.

### D16 — Imagem etiquetada pelo SHA, construída uma vez

**Contexto:** é comum reconstruir a imagem no momento do deploy.

**Decisão:** a imagem é construída uma vez no merge para `main`, etiquetada com
`:latest` e `:${{ github.sha }}`, e o deploy consome essa imagem.

**Motivo:** *build once, deploy many* — o artefato que vai para produção é o
mesmo que passou pelos testes. Reconstruir entrega algo que ninguém validou. A
tag por SHA também torna o rollback trivial: reimplantar a tag anterior.

### D17 — Autorização de deploy via GitHub Environment

**Contexto:** falta um ponto onde a ida para produção seja um ato humano
explícito.

**Decisão:** job de deploy vinculado ao Environment `production` com *required
reviewers*.

**Motivo:** separa "a automação preparou tudo" de "alguém autorizou". É o mesmo
raciocínio de D12 — o pipeline faz o trabalho, a autorização continua sendo
humana e fica registrada. Gratuito em repositório público.

---

## Ordem de execução sugerida

1. Aplicar Testcontainers e confirmar `mvn verify` verde (D14)
2. Criar `.github/workflows/ci.yml` e abrir um PR para ver a CI rodar
3. Registrar `build-and-test` como check obrigatório na ruleset de `main`
4. **Decidir a hospedagem** — sem isso a CD não sai do papel
5. Criar o Environment `production` com aprovação
6. Criar `.github/workflows/cd.yml` com o passo de deploy real

## Em aberto

1. **Hospedagem** — bloqueia os passos 4 a 6 acima.
2. **Backup antes do deploy** — depende da hospedagem.
3. **Estratégia de rollback** — a tag por SHA torna possível, mas falta definir
   o que fazer com migrações já aplicadas (elas não voltam sozinhas).
4. **Ambiente de homologação** — se existir, `develop` também implanta, e a CD
   ganha um segundo Environment sem aprovação obrigatória.
5. **Cobertura de testes** — hoje há um único teste de contexto. Um gate de
   cobertura só faz sentido depois que houver o que cobrir.

## Histórico

| Data | Mudança |
|---|---|
| 2026-09-07 | Versão inicial: CI especificada, CD desenhada, D14–D17 |
| 2026-09-07 | Testcontainers implementado e verificado; registradas as duas armadilhas de ambiente (API do Docker 29 e rede em DooD) |
