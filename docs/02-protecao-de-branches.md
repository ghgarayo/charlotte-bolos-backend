# 02 — Proteção de branches no GitHub

> Última atualização: 2026-09-07 · Status: a aplicar (configuração manual no GitHub)

Repositório: `ghgarayo/charlotte-bolos-backend` (público, default branch `main`).

## Por que

O fluxo é `main` = produção, `develop` = desenvolvimento/homologação. Hoje isso
é só uma convenção: qualquer `git push origin main` a contorna, e um
`push --force` reescreve o histórico de produção sem deixar rastro.

Proteger `main` transforma a convenção em regra — o único caminho para produção
passa a ser um Pull Request vindo de `develop`.

Como o repositório é **público**, a proteção de branch está disponível no plano
gratuito. Isso não é acidente: no plano Free do GitHub, "Protected branches" só
vale em repositório público — fechar o repositório passaria a exigir Pro ou
Team (~US$ 4/mês). Ver **D13**.

## Qual mecanismo usar

O GitHub tem dois sistemas que fazem a mesma coisa:

| | Rulesets | Branch protection rules (clássico) |
|---|---|---|
| Onde | Settings → Rules → Rulesets | Settings → Branches |
| Status | Atual, é onde o GitHub evolui | Legado, ainda funciona |
| Vantagens | Várias regras por branch, modo "Evaluate" para testar sem bloquear, histórico de alterações |  Interface mais simples |

**Use Rulesets.** O passo a passo clássico está no fim como alternativa.

## Passo a passo — `main`

**Settings → Rules → Rulesets → New ruleset → New branch ruleset**

### 1. Identificação

- **Ruleset Name:** `protect-main`
- **Enforcement status:** `Active`

> Dica: se quiser testar antes de bloquear de verdade, use `Evaluate`. Ele
> registra o que *teria* sido barrado sem impedir ninguém. Depois volte em
> `Active`.

### 2. Bypass list

**Deixe vazia.**

Esse é o ponto que mais engana. Sendo o dono do repositório, você tem permissão
de bypass por padrão em várias situações — ou seja, a regra vale para todo mundo
menos justamente para quem mais faz push. Com a lista vazia, ela vale para você
também.

### 3. Target branches

**Add target → Include default branch** (que é `main`).

Alternativa equivalente: **Add target → Include by pattern** → `main`.

### 4. Regras a marcar

| Regra | Marcar | Por quê |
|---|---|---|
| **Restrict deletions** | ✅ | Impede apagar `main`. Já vem marcada. |
| **Block force pushes** | ✅ | Impede reescrever o histórico de produção. Já vem marcada. |
| **Require a pull request before merging** | ✅ | O ponto central: nada entra em `main` sem PR. |
| ↳ Required approvals | **0** | Ver o aviso abaixo. |
| ↳ Require all comments resolved | ✅ | Não deixa mergear com discussão pendente. |
| **Require linear history** | ✅ | Histórico legível; força squash ou rebase. |
| **Require status checks to pass** | ⬜ | **Só depois que houver CI** — ver aviso. |
| **Require signed commits** | ⬜ | Bom, mas exige configurar GPG/SSH signing antes. |
| **Restrict creations / updates** | ⬜ | Redundante aqui: o PR já cobre. |

> ### ⚠️ Required approvals com um único desenvolvedor
>
> **O GitHub não permite aprovar o próprio Pull Request.** Se você marcar
> "Required approvals: 1" trabalhando sozinho, cria um deadlock: o PR de
> `develop` para `main` nunca poderá ser mergeado, porque não existe outra
> pessoa para aprovar.
>
> Mantenha **0** enquanto for só você. O PR continua obrigatório — o que muda é
> que você mesmo pode mergeá-lo depois de revisar o diff. Suba para 1 quando
> houver uma segunda pessoa no projeto.

> ### ⚠️ Status checks antes de existir CI
>
> "Require status checks to pass" exige que você **nomeie** os checks. Marcar
> essa regra sem ter CI configurada deixa a lista vazia e não protege nada; e
> nomear um check que não existe trava todos os PRs esperando por algo que
> nunca vai reportar.
>
> Deixe desmarcada. Volte aqui quando o item de CI/CD
> ([01 — Infraestrutura](01-infraestrutura.md), *Em aberto*) estiver resolvido.

### 5. Create

Botão **Create** no fim da página.

## Secret scanning e push protection

Consequência direta de o repositório ser público (D13): um `.env` commitado por
engano fica exposto para sempre. Remover do histórico **não** desfaz o
vazamento — a credencial precisa ser rotacionada.

O GitHub oferece de graça em repositórios públicos duas camadas contra isso:

**Settings → (seção de segurança) → Secret scanning**

| Recurso | O que faz |
|---|---|
| **Secret scanning** | Varre o histórico e alerta sobre credenciais já commitadas |
| **Push protection** | **Bloqueia o push** que contém um segredo reconhecido, antes de entrar |

Push protection é a que importa: age *antes* do vazamento, não depois. Ative as
duas.

Isso não substitui o `.gitignore` — ele já cobre `.env*` e é a primeira linha.
Push protection reconhece formatos conhecidos (tokens de nuvem, chaves de API);
uma senha de banco como `postgres` não tem formato reconhecível e passa. As duas
camadas se somam, nenhuma basta sozinha.

## Regra para `develop`

Vale uma segunda ruleset, mais frouxa. `develop` recebe trabalho o tempo todo;
travá-la demais só cria atrito.

**New ruleset → New branch ruleset**

- **Ruleset Name:** `protect-develop`
- **Enforcement status:** `Active`
- **Target branches:** Include by pattern → `develop`
- Regras:

| Regra | Marcar |
|---|---|
| Restrict deletions | ✅ |
| Block force pushes | ✅ |
| Require a pull request before merging | ⬜ |

Ou seja: pode dar push direto em `develop`, mas ninguém apaga nem reescreve.

## Como fica o fluxo depois

```
feature/x ──► develop ──[PR obrigatório]──► main
              push direto                   só via PR
              permitido                     sem force push
                                            sem delete
```

Na prática, o `develop → main` passa a ser:

```bash
# abre o PR pelo site, ou:
# https://github.com/ghgarayo/charlotte-bolos-backend/compare/main...develop
```

Merge pela interface do GitHub. O push direto vai falhar assim:

```
! [remote rejected] main -> main (protected branch hook declined)
```

Se isso acontecer, o erro está certo — o commit deve ir para `develop` e subir
por PR.

## Verificando que funcionou

Depois de criar a ruleset, teste de fato (é rápido e evita descobrir que a
proteção não pegou justo no dia em que ela importava):

```bash
git checkout main
git commit --allow-empty -m "teste de protecao"
git push origin main        # deve ser REJEITADO
git reset --hard origin/main
git checkout develop
```

Se o push passar, quase certamente a **bypass list** não está vazia.

## Alternativa: branch protection clássico

Se preferir a interface antiga:

**Settings → Branches → Add branch protection rule**

- **Branch name pattern:** `main`
- ✅ Require a pull request before merging (deixe *Require approvals* desmarcado
  enquanto for um dev só)
- ✅ Require conversation resolution before merging
- ✅ Require linear history
- ✅ **Do not allow bypassing the above settings** ← equivale à bypass list vazia
- ⬜ Allow force pushes (deixe desmarcado)
- ⬜ Allow deletions (deixe desmarcado)

Não use os dois sistemas na mesma branch ao mesmo tempo: as regras se somam e
fica difícil entender de onde veio um bloqueio.

## Decisões

### D11 — Proteção via ruleset, sem exigir aprovação por ora

**Contexto:** `main` representa produção, mas está aberta a push direto e force
push. Ao mesmo tempo, o projeto tem um único desenvolvedor.

**Decisão:** ruleset em `main` exigindo PR, bloqueando force push e deleção,
com **Required approvals = 0** e sem status checks.

**Motivo:** captura o ganho real (nada entra em produção fora de um PR, o
histórico não é reescrito) sem criar o deadlock de aprovação que o GitHub impõe
a quem trabalha sozinho.

**Descartado:** exigir 1 aprovação agora — impossível de satisfazer com um só
desenvolvedor.

**Descartado:** exigir status checks agora — não há CI que os reporte.

**A revisitar:** aprovações quando entrar a segunda pessoa; status checks quando
a CI existir.

### D12 — Agente de IA não é o aprovador de registro

**Contexto:** com um único desenvolvedor, `Required approvals: 1` trava o fluxo.
Surgiu a ideia de usar um agente de IA como segundo aprovador para satisfazer o
gate.

**Decisão:** não. O agente pode **comentar** e pode **reprovar** via status
check, mas não aprova.

**Motivo:** o gate de aprovação existe para registrar que uma segunda parte
*responsável* olhou a mudança. Um agente não assume consequência — o selo de
"revisado" passa a afirmar algo falso, o que é pior que não ter gate. Em
repositório público há o agravante de que o agente leria título, descrição e
diff escritos por qualquer pessoa: uma superfície de prompt injection ligada
direto ao portão de produção. O próprio GitHub mantém o setting *Allow GitHub
Actions to create and approve pull requests* **desligado por padrão** em contas
pessoais.

**Inversão adotada:** o agente bloqueia, não libera. Um review de IA como check
obrigatório pode impedir um merge ruim, mas não autorizar um. Falso positivo
custa minutos; falso negativo não vira aprovação silenciosa.

**Exceção aceita:** Dependabot com auto-merge de patch de segurança após testes
verdes — ali o julgamento está nos testes e no escopo estreito da mudança.

### D13 — Repositório público

**Contexto:** proteção de branch no plano Free do GitHub só existe em
repositório público. Em privado exige Pro/Team (~US$ 4/mês).

**Decisão:** manter o repositório público.

**Motivo:** preserva a proteção de `main` sem custo, num projeto que não tem
segredo comercial no código.

**Custo aceito:** qualquer credencial commitada por engano fica exposta de forma
**permanente**. Reescrever o histórico não desfaz o vazamento — a credencial
tem de ser rotacionada.

**Controles compensatórios:** `.gitignore` cobrindo `.env*` (já feito) e secret
scanning + push protection ativos (ver seção acima).

**A revisitar:** se o repositório precisar ser fechado, ou paga-se o plano, ou a
proteção cai para um `pre-push` hook local — guarda-corpo contra push acidental,
contornável com `--no-verify` e válido só no clone onde foi configurado.

## Histórico

| Data | Mudança |
|---|---|
| 2026-09-07 | Versão inicial |
| 2026-09-07 | D12 (agente de IA não aprova PR) e D13 (repositório público); seção de secret scanning |
