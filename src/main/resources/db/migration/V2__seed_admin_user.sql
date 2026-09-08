-- V2 - Usuario administrador inicial.
--
-- Roda em TODOS os ambientes, producao inclusive. E a conta que permite o
-- primeiro login depois de um deploy num banco vazio.
--
-- A credencial NAO esta neste arquivo. E-mail e hash chegam por placeholder do
-- Flyway, resolvidos por perfil:
--
--   dev  - valores fixos em application-dev.yml (admin123, descartavel)
--   prod - variaveis de ambiente ADMIN_EMAIL e ADMIN_PASSWORD_HASH, sem default
--
-- Motivo: o repositorio e publico (decisao D13 em
-- docs/02-protecao-de-branches.md). Uma senha versionada aqui seria a senha do
-- administrador de producao, legivel por qualquer pessoa, para sempre. O
-- placeholder mantem o insert rodando em prod sem que o segredo entre no git.
--
-- Como gerar o hash de producao: ver docs/00-progresso.md.
--
-- O checksum do Flyway e calculado sobre o arquivo bruto, antes da substituicao
-- dos placeholders, entao ambientes com valores diferentes nao divergem.
--
-- Migracao versionada: roda uma unica vez. Trocar a senha depois e operacao da
-- aplicacao, nao de nova migracao.

-- version, created_at e updated_at vem dos DEFAULT declarados na V1.
INSERT INTO people (id, name, surname, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-000000000001',
        'Admin',
        'Charlotte',
        'system',
        'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, person_id, email, password, role, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000001',
        '${admin_email}',
        '${admin_password_hash}',
        'ADMIN',
        'system',
        'system')
ON CONFLICT (email) DO NOTHING;
