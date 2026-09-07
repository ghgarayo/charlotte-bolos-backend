# Documentação — Charlotte Bolos

Registro das decisões tomadas na construção do projeto: o **que** foi
decidido, o **porquê**, e quais alternativas foram descartadas.

A ideia é que estes documentos sejam a fonte de verdade sobre as escolhas.
Código muda; o raciocínio por trás dele se perde se não for escrito.

## Índice

| Documento | Status | Assunto |
|---|---|---|
| [01 — Infraestrutura](01-infraestrutura.md) | ✅ Escrito | Docker, ambientes, banco, portas, variáveis |
| [02 — Proteção de branches](02-protecao-de-branches.md) | ✅ Escrito | Fluxo `develop` → `main`, rulesets do GitHub |
| 03 — Arquitetura | ⬜ A escrever | Camadas, fluxo de request, padrões de código |
| 04 — Modelo de domínio | ⬜ A escrever | Entidades, relacionamentos, invariantes |
| 05 — API | ⬜ A escrever | Endpoints, contratos, erros, versionamento |

## Convenções

- Cada documento tem uma seção **Decisões** no formato
  *contexto → decisão → motivo → alternativas descartadas*.
- Pontos ainda não resolvidos ficam em **Em aberto**, para não parecer
  que já foram decididos.
- Ao mudar uma decisão, edite o documento e registre a mudança em
  **Histórico**, em vez de apagar o que existia.
