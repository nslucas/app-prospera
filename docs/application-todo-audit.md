# Coisas a fazer na aplicação

Documento criado em 2026-06-24 para registrar pendências de modelagem, campos possivelmente inúteis e dívidas técnicas observadas no backend Prospera.

## Objetivo

O backend evoluiu de uma estrutura inicial baseada em `user`, `card`, `wallet` e `expense` para um modelo mais completo com contas, transações, cartões, faturas, orçamentos, recorrências, compartilhamento de despesas e alertas derivados.

Com isso, alguns campos antigos continuam no banco ou nos DTOs, mas já não representam bem o produto atual. A ideia deste documento é separar:

- campos realmente usados;
- campos legados que parecem sobrar;
- campos perigosos por estarem expostos de forma ampla demais;
- tarefas recomendadas antes de remover algo em produção.

## Resumo executivo

| Item | Situação atual | Recomendação |
| --- | --- | --- |
| `user.role` | Ainda é usado pelo Spring Security para `ROLE_ADMIN` em `GET /users`. | Manter se existir administração; remover do cadastro público. |
| `user.month_limit` / `monthLimit` | Quase legado. Não participa do fluxo principal de orçamento, cartão, resumo ou alertas. | Remover ou transformar explicitamente em meta mensal global. |
| `wallet` | Migrado para `account` em `V11`; não há entidade ou endpoint ativo. | Criar migration futura para remover depois de validar dados reais. |
| `card.number`, `card.owner`, `card.balance` | Campos antigos da tabela `card`; a entidade atual não mapeia esses campos. | Remover em migration futura depois de confirmar que `bank_name`, `name`, `last_four_digits` e `account` cobrem os dados. |
| `ExpenseRecord.userId` | Backend ignora em criação/atualização novas e usa JWT como dono real. | Separar DTO de request e response ou remover do request. |
| `User.expenses` | Campo `@Transient` sem uso operacional visível. | Remover se nenhum serializador ou teste depender dele. |

## Auditoria dos campos citados

### `user.role`

Status: usado; o problema de cadastro público foi corrigido.

Onde aparece:

- `User.role` é um `UserRole`.
- `User.getAuthorities()` devolve `ROLE_ADMIN` e `ROLE_USER` quando o usuário é admin.
- `SecurityConfiguration` exige `hasRole("ADMIN")` para `GET /users`.
- `RegisterDTO` não aceita mais `role`.
- `AuthenticationResource.register()` sempre cria usuários com `UserRole.USER`.

Conclusão: a coluna não é inútil hoje. Ela controla ao menos uma autorização real: listar usuários.

Problema corrigido: o cadastro público não aceita nem confia em `role`; campos legados desconhecidos são ignorados.

Tarefas recomendadas:

- [x] Remover `role` de `RegisterDTO`.
- [x] Fazer todo cadastro público criar usuário com `UserRole.USER`.
- [ ] Criar um fluxo separado e protegido para promover usuário a admin, se administração continuar existindo.
- [ ] Decidir se `GET /users` é uma tela administrativa real. Se não for, remover o endpoint ou restringir melhor o uso.
- [x] Atualizar `docs/frontend-api-handoff.md`, removendo `role` do exemplo.
- [x] Adicionar teste garantindo que `POST /auth/register` nunca cria admin.

Decisão sugerida: manter `user.role`, mas não aceitar `role` em cadastro público.

## `user.month_limit` / `monthLimit`

Status: provavelmente legado.

Onde aparece:

- Coluna `month_limit` em `V1__Create-Users-Table.sql`.
- Ajuste de tipo em `V10__Credit_Card_Tracking_V1.sql`.
- `User.monthLimit`.
- `RegisterDTO.monthLimit`.
- `UserRecord.monthLimit`.
- `UserService.updateData()` ainda atualiza `monthLimit`.
- Métodos antigos em `ExpenseService`: `getSumAmountByUserId()` e `getSumAmountByUserIdInCurrentMonth()`.

O que não usa esse campo:

- Orçamentos atuais usam `Budget` por categoria, mês e ano.
- Alertas de orçamento usam `BudgetService` e limites por categoria.
- Alertas de cartão usam `Card.creditLimit`.
- Resumos mensais usam contas, transações, faturas e orçamentos.
- O frontend atual, pelo contrato documentado, deve preferir `/summary`, `/budgets`, `/cards/{id}/statements`, `/transactions` e `/alerts`.

Conclusão: `monthLimit` não parece participar do fluxo financeiro principal. Ele representa uma ideia antiga de "limite mensal do usuário", mas a aplicação atual trabalha melhor com:

- limite do cartão para crédito;
- orçamento mensal por categoria para planejamento;
- saldo de conta para disponibilidade de caixa.

Riscos de manter:

- Confusão de produto: usuário pode achar que existe um limite mensal global aplicado.
- Contrato sujo: cadastro e edição de usuário pedem um campo que o app não usa de verdade.
- Código frágil: os métodos antigos de `ExpenseService` comparam total com `user.getMonthLimit()` e podem falhar se o limite for `null`, caso voltem a ser chamados.

Opção A, recomendada: remover.

- [ ] Remover `monthLimit` de `RegisterDTO`.
- [ ] Remover `monthLimit` de `UserRecord` ou manter apenas temporariamente em response de compatibilidade.
- [ ] Remover `monthLimit` de `User`.
- [ ] Criar migration para remover `user.month_limit`.
- [ ] Remover ou reescrever os métodos antigos `getSumAmountByUserId()` e `getSumAmountByUserIdInCurrentMonth()`.
- [ ] Atualizar README, que ainda menciona "monthly spending limit".
- [ ] Atualizar docs do frontend para não orientar envio desse campo.

Opção B: reaproveitar como meta mensal global.

- [ ] Renomear o conceito no produto para algo claro, por exemplo `monthlySpendingTarget`.
- [ ] Definir exatamente o que entra na conta: transações de despesa, parcelas de cartão, pagamentos de cartão ou combinação.
- [ ] Criar endpoint de progresso global mensal.
- [ ] Criar alerta específico para meta mensal global.
- [ ] Evitar conflito com `Budget`, deixando claro que orçamento por categoria continua sendo o controle detalhado.

Decisão sugerida: remover, a menos que exista uma tela planejada de meta mensal global.

## Campos legados de cartão

Status: sobras de schema antigo.

Campos antigos na tabela `card`:

- `number`
- `owner`
- `balance`

Modelo atual:

- `Card` usa `bankName`, `name`, `network`, `lastFourDigits`, `creditLimit`, `closingDay`, `dueDay`, `active` e `userId`.
- A documentação atual diz para nunca enviar número completo do cartão.
- `V10__Credit_Card_Tracking_V1.sql` migra `owner` para `name` e `number` para `last_four_digits`.
- A entidade Java atual não mapeia `number`, `owner` ou `balance`.

Tarefas recomendadas:

- [ ] Confirmar no banco de produção se `bank_name`, `name`, `last_four_digits` e `credit_limit` estão preenchidos para cartões antigos.
- [ ] Criar backup antes de qualquer remoção.
- [ ] Criar migration para remover `card.number`, `card.owner` e `card.balance`.
- [ ] Garantir que nenhum relatório externo ainda lê essas colunas.

Decisão sugerida: remover os campos antigos em uma migration de limpeza.

## `wallet`

Status: tabela legada.

O que aconteceu:

- `V7` cria `wallet`.
- `V8` insere carteira inicial.
- `V11` cria `account` e migra dados de `wallet` para `account`.
- A aplicação atual não tem entidade, resource ou service de `Wallet`.
- `docs/frontend-api-handoff.md` já registra que não existem endpoints `/wallets`.

Tarefas recomendadas:

- [ ] Confirmar se todos os dados de `wallet` foram migrados para `account`.
- [ ] Criar migration futura para remover `wallet`.
- [ ] Remover menções antigas em README se ainda sugerirem carteira como conceito atual.

Decisão sugerida: remover depois de validação de dados reais.

## DTOs que ainda expõem detalhes internos

### `ExpenseRecord.userId`

Status: útil como resposta legada, ruim como request.

O backend já usa o usuário autenticado para criar e atualizar despesas. O `userId` enviado pelo frontend não deve ser confiado.

Tarefas recomendadas:

- [ ] Criar DTO separado para request de despesa sem `userId`.
- [ ] Manter `userId` apenas em response se o frontend ainda precisar exibir ou depurar.
- [ ] Atualizar `docs/frontend-api-handoff.md` para deixar o request sem `userId`.
- [ ] Adicionar teste garantindo que `POST /expenses` ignora qualquer `userId` enviado.

### `RegisterDTO.role` e `RegisterDTO.monthLimit`

Status: `role` removido do contrato público; a decisão sobre `monthLimit` permanece pendente.

Tarefas recomendadas:

- [ ] Criar request de cadastro só com `name`, `lastName`, `email` e `password`.
- [x] Definir `role=USER` no servidor.
- [ ] Remover `monthLimit` do cadastro, se a decisão for remover limite mensal global.

## Endpoints legados de despesa

Endpoints atuais:

- `GET /expenses/{userId}/total-expenses`
- `GET /expenses/{userId}/total-expenses/current-month`
- `GET /expenses/{userId}/total-expenses/any-month?month=&year=`

Problemas:

- Recebem `userId` na URL, enquanto os fluxos novos usam JWT como fonte de ownership.
- Estão documentados como compatibilidade legada.
- Competem conceitualmente com `/summary`, `/cards/{id}/statements`, `/budgets/progress` e `/alerts`.

Tarefas recomendadas:

- [ ] Verificar se o frontend ainda chama algum desses endpoints.
- [ ] Se não chamar, marcar como deprecated no código/docs.
- [ ] Remover em uma versão futura.
- [ ] Se precisar manter, aplicar checagem de ownership pelo JWT para evitar leitura cruzada por `userId`.

## Pequenas limpezas de código

- [ ] Remover `User.expenses`, campo `@Transient` sem uso claro.
- [ ] Remover import não usado de `JsonFormat` em `Expense.java`.
- [ ] Corrigir método `Expense.Integer(Integer id)`, que parece um setter acidental com nome errado.
- [ ] Padronizar nomes de getters/setters de `ExpenseInstallment`: `getInstallment_amount()` e `setInstallment_amount()` deveriam ser `getInstallmentAmount()` e `setInstallmentAmount()`.
- [ ] Revisar `UserRole.getRole()`: hoje o enum guarda `admin`/`user`, mas a persistência usa o nome do enum (`ADMIN`/`USER`) por `EnumType.STRING`. O campo interno pode ser desnecessário.

## Pendências de documentação

- [x] Corrigir exemplo de `POST /auth/login` em `docs/frontend-api-handoff.md`.
- [x] Corrigir exemplo de `POST /auth/register`: o DTO atual usa `name`, não `firstName`.
- [x] Remover `role` do exemplo de cadastro.
- [ ] Remover `monthLimit` dos contratos se a decisão for descartar o limite mensal global.
- [ ] Atualizar README para refletir o produto atual: contas, cartões, faturas, orçamentos por categoria, recorrências e compartilhamento.

## Ordem sugerida de execução

1. Fechar decisão de produto sobre `monthLimit`.
2. Proteger cadastro público removendo `role` do request.
3. Separar DTOs de request/response onde `userId` aparece.
4. Deprecar ou remover endpoints legados de despesas por `userId`.
5. Criar migrations de limpeza para colunas/tabelas legadas depois de validar produção.
6. Atualizar README e handoff do frontend para refletir o contrato real.

## Checklist de validação antes de remover coluna

- [ ] Buscar uso no backend com `rg`.
- [ ] Buscar uso no frontend.
- [ ] Conferir jobs, scripts, dashboards ou queries manuais fora do repo.
- [ ] Verificar dados reais em produção.
- [ ] Criar backup.
- [ ] Criar migration reversível quando possível.
- [ ] Rodar testes com `.\mvnw.cmd test`.
- [ ] Atualizar documentação no mesmo PR.

