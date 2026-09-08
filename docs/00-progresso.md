# 00 — Progresso

> Última atualização: 2026-09-07 · Status do build: 🟢 **passando** (11 testes)

Panorama de onde o projeto está. Diferente dos demais documentos, este não
registra decisões — registra **estado**. Deve ser reescrito, não acumulado.

---

## Estado do build

`mvn test` passa: 11 testes, em três classes.

| Classe | Testes | Cobre |
|---|---|---|
| `CharlotteBolosApplicationTests` | 1 | Contexto sobe no perfil `prod` |
| `AdminSeedTest` | 4 | Seed do admin (`V2`) e perfil `dev` inteiro |
| `AuthLoginTest` | 6 | Login de ponta a ponta por HTTP |

---

## Panorama por área

| Área | Estado | Observação |
|---|---|---|
| Infra Docker | ✅ | Compose com app + Postgres, healthcheck, volume de cache |
| Perfis (`dev`/`prod`) | ✅ | `prod` é o padrão; credenciais sem default fora de dev |
| Proteção de branches | ✅ | Ruleset `protect-main` ativo; `develop` sem ruleset |
| CI | ⬜ | **Especificada, não aplicada** — `.github/workflows/` não existe |
| CD | ⬜ | Só o desenho; depende da hospedagem — ver [03](03-ci-cd.md) |
| Testes com banco real | ✅ | Testcontainers sobe Postgres 16 |
| `BaseEntity` + auditoria | ✅ | Auditoria e lock otimista (`@Version`) |
| Entidades `User` / `Person` | ✅ | Validadas contra o schema pelo Hibernate |
| Migrações Flyway | ✅ | `V1` cria as tabelas; `V2` cria o admin |
| Admin inicial | ✅ | Roda em todos os ambientes — credencial por placeholder |
| Fundação de segurança | ✅ | Stateless, CORS, BCrypt — ver [07](07-seguranca.md) |
| **Autenticação (JWT)** | ✅ | `POST /api/auth/login` emitindo token; filtro validando |
| Tratamento de erros | ✅ | `GlobalExceptionHandler` sobre o envelope `ApiResponse` |
| i18n (`MessageService`) | ✅ | `messages.properties` em português |
| Camada de API | 🟡 | Só `AuthController`; nenhum endpoint de domínio |
| Refresh token | ⬜ | Token expira em 24h e exige novo login |
| OpenAPI / Swagger | ⬜ | Sem springdoc — ver "O que ainda não existe" |

---

## O modelo de domínio, como está

```
Person (people)                     User (users)
├─ name, surname       ◄────1:1────  ├─ person_id (FK, unique, not null)
├─ document (unique)                 ├─ email (unique, not null)
├─ phone1, phone2                    ├─ password (hash BCrypt)
├─ date_of_birth                     ├─ role (enum string, CHECK)
└─ user (mappedBy, cascade ALL)      └─ active (default true)

ambas estendem BaseEntity:
  id UUID · version · created_at/by · updated_at/by
```

A separação de `Person` (dados da pessoa) e `User` (credencial de acesso) deixa
espaço para uma pessoa existir sem login — um cliente cadastrado que nunca criou
conta. `Person` é o lado inverso e cascateia para `User`.

`User` implementa `UserDetails` diretamente, então a entidade JPA é também o
objeto que o Spring Security consome. **Essa escolha ainda não está registrada
como decisão**; vale um D no [07](07-seguranca.md), porque tem contrapartida
(acopla o modelo de persistência ao framework de segurança).

---

## O fluxo de autenticação

```
POST /api/auth/login  ──►  AuthController
                             └─► AuthService
                                   └─► AuthenticationManager
                                         └─► UserDetailsServiceImpl ─► banco
                                         └─► PasswordEncoder (BCrypt)
                                   └─► JwtService.generateToken()
                           ◄── ApiResponse<LoginResponse> { token, ... }

requisições seguintes
  Authorization: Bearer <token>
    └─► JwtAuthenticationFilter ─► JwtService ─► UserDetailsServiceImpl
          └─► SecurityContext preenchido, ou segue sem autenticar (401)
```

As authorities são recarregadas do banco a cada requisição, em vez de virem
dentro do token. Custa consultas, mas revogar um papel passa a valer na hora.

---

## Achados anteriores — todos resolvidos

Os quatro achados da versão anterior deste documento foram corrigidos:

| Achado | Correção |
|---|---|
| Authorities sem prefixo `ROLE_` | `User.getAuthorities()` agora prefixa; o usuário em memória de `dev` que divergia foi removido |
| `MessageService` sem arquivo de mensagens | `messages.properties` criado e `spring.messages` configurado |
| `@Builder` ignorando campos de `BaseEntity` | `User` e `Person` passaram a `@SuperBuilder` |
| `User.getPassword()` redundante | Removido; o `@Getter` do Lombok já o gera |

Nenhum achado novo em aberto.

---

## O que ainda não existe

- **Endpoints de domínio.** A API só expõe o login. Produtos, pedidos e
  clientes ainda não têm entidade nem rota.
- **Refresh token.** O token dura 24h; expirado, só resta novo login.
- **Troca de senha.** A `V2` é versionada e roda uma vez só, então mudar
  `ADMIN_PASSWORD_HASH` depois não altera a senha de quem já existe. Sem
  endpoint de troca, a senha inicial do admin é permanente.
- **OpenAPI/Swagger.** O projeto de origem tem springdoc. Deixado de fora por
  ora: com um único endpoint, documentaria quase nada. Ao adicionar, lembrar de
  liberar `/swagger-ui/**` e `/api-docs/**` no `SecurityConfig`, senão a
  documentação fica atrás do `authenticated()`.
- **Testes de regra de negócio.** Os 11 existentes cobrem contexto, seed e
  autenticação; não há regra de domínio para testar ainda.

---

## Próximos passos sugeridos

1. **Aplicar a CI.** O `03-ci-cd.md` traz o `ci.yml` pronto para copiar, e o
   arquivo nunca foi criado. Com 11 testes cobrindo autenticação, é agora que a
   CI começa a valer alguma coisa — e ela destrava o *Require status checks* que
   ficou pendente na **D11**.
2. **Primeiro endpoint de domínio**, para o `ApiResponse` e o
   `GlobalExceptionHandler` saírem do uso exclusivo do login.
3. **Endpoint de troca de senha**, que hoje é a única forma de a senha inicial
   do admin deixar de valer.
4. **Registrar as decisões** que só existem no código: `User` como
   `UserDetails`, separação `Person`/`User`, e o enum `Role` com quatro papéis.
5. **Refresh token**, quando o incômodo de relogar a cada 24h aparecer.

---

## Alcance da verificação

Importante para não superestimar a confiança no que existe:

| O que | Verificado? |
|---|---|
| Compilação | ✅ `mvn clean compile` passa |
| Contexto da aplicação | ✅ sobe nos perfis `prod` e `dev` |
| Migrações `V1` e `V2` | ✅ Hibernate valida as entidades contra o schema criado |
| Seed do admin | ✅ linha, papel, vínculo e hash conferidos no banco |
| Login com credencial válida | ✅ HTTP 200 com token, contra Postgres real |
| Senha errada e e-mail inexistente | ✅ ambos 401, com a **mesma** mensagem |
| Rota protegida sem token | ✅ 401 (e não o 403 padrão do Spring) |
| Token malformado | ✅ 401 (e não 500) |
| Validação de corpo inválido | ✅ 400 com o mapa de campos |
| Auditoria gravando via JPA | ⬜ o seed entra por SQL puro; nenhum `INSERT` passou pelo `AuditingEntityListener` |
| Lock otimista (`@Version`) | ⬜ nenhum `UPDATE` concorrente foi exercitado |
| CORS na prática | ⬜ nenhuma requisição com `Origin` de outro domínio |
| Token expirado | ⬜ testado malformado, não expirado — exigiria manipular o relógio |

A autenticação está verificada **por execução HTTP contra um Postgres real**. O
que segue sem exercício é o que não tem caminho para ser exercitado ainda:
auditoria e lock otimista precisam de escrita pelo JPA, que só virá com o
primeiro endpoint de domínio.

---

## Operação: o administrador inicial

A migração `V2` cria o admin em **todos** os ambientes, produção inclusive. A
credencial não está versionada: chega por placeholder do Flyway.

| Perfil | E-mail | Senha |
|---|---|---|
| `dev` | `admin@charlottebolos.com.br` | `admin123`, fixa em `application-dev.yml` |
| `prod` | `${ADMIN_EMAIL}` | `${ADMIN_PASSWORD_HASH}`, **sem default** |

Faltando qualquer uma das duas variáveis em produção, a migração falha e a
aplicação não sobe — mesma postura do datasource (**D9**) e do CORS (**D21**).

`ADMIN_PASSWORD_HASH` recebe o **hash BCrypt**, não a senha. Para gerá-lo com o
mesmo encoder que a aplicação usa na conferência:

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt

cat > Hash.java <<'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class Hash {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode(args[0]));
    }
}
EOF

java -cp "$(cat cp.txt)" Hash.java 'a-senha-forte-aqui'
rm cp.txt Hash.java
```

Copie a saída (`$2a$10$...`) para `ADMIN_PASSWORD_HASH`. Ela contém `$`, então
use **aspas simples** ao exportar em shell, ou o valor é interpretado como
variável.

Como a `V2` é versionada, ela roda uma vez só: mudar a variável depois não
altera a senha de quem já foi criado.

---

## Variáveis de ambiente de produção

Todas sem default: faltando qualquer uma, a aplicação não sobe.

| Variável | Para quê |
|---|---|
| `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Conexão com o banco |
| `CORS_ALLOWED_ORIGINS` | Domínios do front, separados por vírgula |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD_HASH` | Administrador inicial (`V2`) |
| `JWT_SECRET` | Assinatura dos tokens — **mínimo 32 caracteres** |

O `JwtService` valida o tamanho do segredo no start. Sem isso, um segredo curto
só estouraria `WeakKeyException` no primeiro login de um usuário real, já em
produção.

Modelo completo em `.env.prod.example`.

---

## Estado do repositório

Trabalho **não commitado** no working tree. Último commit: `8c227f2`.

> ⚠️ A `V1` foi alterada depois de já ter rodado (ganhou `version` e os
> `DEFAULT`). Se você tem um banco de desenvolvimento com ela aplicada, o Flyway
> vai acusar divergência de checksum no próximo start. Recrie o banco:
> `docker compose down -v && docker compose up -d`.

## Histórico

| Data | Mudança |
|---|---|
| 2026-09-07 | Versão inicial, após a entrada de `User` e `Person` |
| 2026-09-07 | Migração `V1` (tabelas) e `V2` (admin inicial em todos os ambientes); build volta a passar, com 5 testes |
| 2026-09-07 | Login com JWT, `GlobalExceptionHandler`, `messages.properties`, `@Version`; os quatro achados anteriores corrigidos; 11 testes |
