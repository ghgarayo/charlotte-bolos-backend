# 07 — Segurança e autenticação

> Última atualização: 2026-09-07 · Status: ✅ autenticação por JWT em operação

Cobre Spring Security, JWT, CORS e a auditoria de quem altera os dados.

## Onde estamos

A autenticação funciona de ponta a ponta: o administrador criado pela migração
`V2` faz login em `POST /api/auth/login`, recebe um token e o usa nas
requisições seguintes. Verificado por `AuthLoginTest`, que exercita HTTP real
contra um Postgres do Testcontainers.

Falta o que depende de haver domínio: nenhum endpoint além do login, e portanto
nenhuma regra de autorização por papel em uso — o `@EnableMethodSecurity` está
ligado mas ainda não protege nada.

## O que está configurado

Tudo em `config/security/SecurityConfig.java`, salvo indicação contrária.

| Item | Estado | Observação |
|---|---|---|
| `spring-boot-starter-security` | ✅ | No `pom.xml` |
| `spring-security-test` | ✅ | Escopo `test` |
| Sessão | `STATELESS` | Nenhuma `HttpSession` é criada |
| CSRF | Desligado | Ver **D19** |
| Autorização | `anyRequest().authenticated()` | `/api/auth/**` liberado |
| Autenticação | **JWT** | `POST /api/auth/login` emite; `JwtAuthenticationFilter` valida |
| Senhas | BCrypt | `PasswordEncoder` publicado como bean |
| `AuthenticationManager` | ✅ publicado | Consumido pelo `AuthServiceImpl` no login |
| Usuários | Do banco | `UserDetailsServiceImpl` sobre `UserRepository` |
| Recusa sem token | **401** | `HttpStatusEntryPoint`, não o 403 padrão |
| CORS | ✅ ligado na cadeia | Origens por propriedade — ver **D21** |
| Auditoria | ✅ | `config/auditing/AuditingConfig.java` — ver **D22** |
| Tratamento de erros | ✅ | `GlobalExceptionHandler` sobre `ApiResponse` |
| Papéis | `ROLE_` + enum | Prefixo obrigatório — ver **D23** |

### A propriedade de CORS

`app.cors.allowed-origins`, lista separada por vírgula:

| Perfil | Valor |
|---|---|
| `dev` | `localhost:4200` (Angular), `localhost:3000` (CRA/Next), `localhost:5173` (Vite) |
| `prod` | `${CORS_ALLOWED_ORIGINS}`, **sem default** |
| teste | Injetada em `@SpringBootTest(properties = ...)` |

O teste precisa injetar o valor porque roda no perfil `prod`. O datasource
escapa disso porque o `@ServiceConnection` do Testcontainers tem precedência
sobre os placeholders; um `@Value` não tem equivalente e aborta o start.

## Decisões

### D18 — Fundação de segurança antes de modelar o usuário

**Contexto:** o Spring Security foi pedido antes de existir decisão sobre a
entidade de usuário e sobre como ela se liga ao cliente da confeitaria.

**Decisão:** entregar a fundação stateless — filtros, encoder,
`AuthenticationManager`, CORS — e deixar `UserDetailsService`, entidade,
migração, login e filtro de JWT para quando a modelagem estiver definida.

**Motivo:** a fundação não depende do formato do usuário e destrava o
desenvolvimento agora. Já a modelagem prejulgada custaria caro: uma entidade
`User` escolhida no chute vira migração aplicada, e desfazer migração é mais
trabalhoso do que escrevê-la depois com a decisão tomada.

**Descartado:** JWT completo agora — exigiria inventar o modelo de usuário, que
é justamente a decisão em aberto.

**Descartado:** liberar tudo com `permitAll` — deixaria o projeto sem postura de
segurança e o `SecurityContext` sempre anônimo, tornando a auditoria inútil.

### D19 — Sessão stateless e CSRF desligado

**Contexto:** API REST consumida por um front separado, com autenticação por
token planejada.

**Decisão:** `SessionCreationPolicy.STATELESS` e `csrf` desabilitado.

**Motivo:** CSRF protege contra credencial que o navegador anexa sozinho —
cookie de sessão. Um token enviado explicitamente em `Authorization` não é
anexado automaticamente, então o ataque não se aplica. Sem sessão, a API também
escala horizontalmente sem estado compartilhado.

**A revisitar:** se o refresh token passar a viajar em cookie, CSRF volta a ser
relevante e esta decisão precisa ser reaberta. O mesmo vale para
`allowCredentials(true)`, hoje ligado no CORS (ver **Em aberto**).

### D20 — Fora de `dev`, ninguém autentica *(superada pela D24)*

**Contexto:** sem tabela de usuário, não há como autenticar de verdade. Sem
nenhum `UserDetailsService`, `AuthenticationProvider` ou `AuthenticationManager`
no contexto, o Spring Boot cria um usuário `user` com senha aleatória impressa
no log do startup.

**Decisão:** publicar um `InMemoryUserDetailsManager` **vazio** em todo perfil
que não seja `dev`.

**Motivo:** mesma lógica do perfil padrão ser `prod` (**D9**) — o esquecimento
deve levar à configuração restrita, nunca à permissiva. Um repositório vazio diz
"ninguém entra" de forma explícita, em vez de deixar o `AuthenticationManager`
montado sem fonte de usuário e falhar só na primeira tentativa de login, com
erro obscuro.

**Observação:** não é este bean que suprime o usuário de senha aleatória do
Boot — o `AuthenticationManager` publicado logo acima já faz a autoconfiguração
recuar. Os dois papéis são distintos e foi fácil confundi-los.

**Superada em 2026-09-07:** os dois `InMemoryUserDetailsManager` — o usuário
`dev`/`dev` e o repositório vazio — foram removidos quando o
`UserDetailsServiceImpl` sobre JPA entrou (**D24**). Os usuários agora vêm do
banco em todos os perfis, e o admin da `V2` é quem serve ao desenvolvimento.
Fica registrado porque explica por que o projeto passou um tempo sem conseguir
autenticar em produção de propósito.

### D21 — Origens de CORS por propriedade, sem default em produção

**Contexto:** as origens autorizadas variam por ambiente e não são segredo.

**Decisão:** `app.cors.allowed-origins` por perfil; em `prod`, vinda de
`CORS_ALLOWED_ORIGINS` sem valor default.

**Motivo:** segue a regra do **D9** — o que varia e é público vai no
`application-<perfil>.yml`; o que varia por implantação vem de variável de
ambiente. Um default aqui seria um default de segurança, e o erro passaria
despercebido.

**Ganho sobre o datasource:** diferente do caso descrito no **D10**, esta falha
é limpa. O `@Value` aborta o start com *"Could not resolve placeholder"*, em vez
de manter o placeholder literal e falhar depois na conexão.

### D22 — Auditoria derivada do `SecurityContext`

**Contexto:** `BaseEntity` tem `created_by` e `updated_by`, e as colunas de data
são `nullable = false`.

**Decisão:** `@EnableJpaAuditing` com um `AuditorAware<String>` que lê o usuário
autenticado do `SecurityContext` e grava `system` quando a requisição é anônima.

**Motivo:** amarra a auditoria à autenticação real em vez de exigir que cada
serviço passe o autor à mão — o que seria esquecido em algum ponto.

**Dependência crítica:** sem `@EnableJpaAuditing`, `created_at` e `updated_at`
ficam nulos e **todo `INSERT` falha**, porque as colunas são obrigatórias. A
anotação não é opcional; é pré-requisito de qualquer entidade que estenda
`BaseEntity`.

### D23 — Papéis com prefixo `ROLE_`

**Contexto:** `User.getAuthorities()` devolvia `SimpleGrantedAuthority(role.name())`,
ou seja `"ADMIN"`. O Spring Security procura `"ROLE_ADMIN"` quando a verificação
usa `hasRole`. Pior, o usuário em memória de `dev` era criado com `.roles("ADMIN")`,
que **gera** o prefixo — os dois ambientes produziam nomes diferentes.

**Decisão:** prefixar `ROLE_` em `getAuthorities()` e usar `hasRole` nas
verificações.

**Motivo:** as duas convenções funcionam isoladamente — prefixo + `hasRole`, ou
sem prefixo + `hasAuthority`. Misturá-las não. A falha era do tipo pior: sem
exceção e sem log, apenas acesso negado em silêncio. O prefixo é o padrão do
framework, então é o caminho com menos surpresa para quem chegar depois.

**Descartado:** manter sem prefixo e padronizar `hasAuthority` — funciona, mas
contraria a expectativa de qualquer pessoa acostumada ao Spring Security.

### D24 — Usuários vindos do banco, em todos os perfis

**Contexto:** com `User`, `Person` e a migração `V2` no lugar, os
`InMemoryUserDetailsManager` da **D20** deixaram de fazer sentido.

**Decisão:** um único `UserDetailsServiceImpl` sobre `UserRepository`, sem
variação por perfil. Em `dev` o administrador do seed é a credencial de trabalho.

**Motivo:** elimina a divergência entre ambientes que causou o problema da
**D23**. O caminho de autenticação exercitado em desenvolvimento passa a ser
exatamente o de produção — só muda a senha.

**Detalhe de implementação:** `findByEmail` usa `@EntityGraph` para trazer a
`Person` na mesma consulta. Sem isso, o acesso a `user.getPerson()` estouraria
`LazyInitializationException`, já que o filtro de JWT roda fora de transação e
`open-in-view` está desligado.

### D25 — JWT sem papéis no token

**Contexto:** é comum embutir as authorities como claim, poupando uma consulta
por requisição.

**Decisão:** o token carrega apenas o `subject` (e-mail). As authorities são
recarregadas do banco a cada requisição.

**Motivo:** revogar um papel passa a valer na hora. Com os papéis no token, um
usuário rebaixado continuaria com os poderes antigos até o token expirar — 24h,
na configuração atual.

**Custo aceito:** uma consulta por requisição autenticada. Se virar gargalo, a
saída é cache no `UserDetailsService`, não claims no token.

**A revisitar:** ao introduzir refresh token, decidir onde ele trafega. Se for
cookie, a **D19** (CSRF desligado) precisa ser reaberta.

## Em aberto

- **`User` implementa `UserDetails`.** A entidade JPA é também o objeto do
  Spring Security. Funciona e evita conversão, mas acopla persistência a
  framework de segurança — um `record` intermediário desacoplaria. Herdado do
  projeto de origem, ainda não decidido de forma consciente.
- **Refresh token.** Onde trafega (cabeçalho ou cookie) reabre a **D19**.
- **`allowCredentials(true)` no CORS.** Serve para o navegador enviar cookies
  automaticamente. Com JWT em `Authorization` não é necessário; só passa a ser
  se o refresh vier em cookie. Está ligado hoje — confirmar se é a intenção.
- **Portas do front em `dev`.** As três configuradas são um chute abrangente.
  Reduzir à porta real quando o front for escolhido.
- **Troca de senha.** A `V2` roda uma vez só, então a senha inicial do admin é
  permanente até existir endpoint de troca.
- **Rotação do `JWT_SECRET`.** Trocar o segredo invalida todos os tokens em
  circulação de uma vez. Sem estratégia definida.

## Histórico

| Data | Mudança |
|---|---|
| 2026-09-07 | Versão inicial: fundação stateless, CORS e auditoria (D18–D22) |
| 2026-09-07 | Autenticação por JWT (D23–D25); D20 superada; `GlobalExceptionHandler` e prefixo `ROLE_` |
