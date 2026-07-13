# Prospera API Handoff for Frontend

This document describes the current REST API surface for the finance-tracking frontend. The API is centered on authenticated, user-owned finances: accounts, cards, transactions, expenses, budgets, recurrences, summaries, forecasts, and derived alerts.

## Integration Basics

- Base path examples are relative, for example `GET /cards`.
- All endpoints require `Authorization: Bearer <jwt>` except `POST /auth/login` and `POST /auth/register`.
- Ownership comes from the JWT. Do not send or trust `userId` for new flows, even when an older DTO still exposes it.
- Money fields are decimal values. Treat them as strings or decimal-safe numbers in the frontend; avoid binary floating-point math for totals.
- Date formats:
  - `LocalDate`: `YYYY-MM-DD`.
  - `LocalDateTime`: ISO datetime such as `2026-06-18T10:30:00`.
- Soft deletes return `204 No Content` and generally set `active=false`.
- Authentication throttling returns `429 Too Many Requests` with a `Retry-After` header.
- Standard domain errors usually return `400`, `403`, or `404` with a body shaped like:

```json
{
  "timestamp": 1710000000000,
  "status": 400,
  "error": "Bad request",
  "message": "Month must be between 1 and 12",
  "path": "/budgets"
}
```

## Enums

- `AccountType`: `CHECKING`, `SAVINGS`, `CASH`, `OTHER`
- `TransactionType`: `INCOME`, `EXPENSE`, `TRANSFER_IN`, `TRANSFER_OUT`, `CARD_PAYMENT`, `ADJUSTMENT`
- `CategoryType`: `INCOME`, `EXPENSE`
- `BudgetStatus`: `UNDER_BUDGET`, `NEAR_LIMIT`, `OVER_BUDGET`
- `CardStatementStatus`: `OPEN`, `PARTIALLY_PAID`, `PAID`, `OVERPAID`
- `RecurringTargetType`: `ACCOUNT_TRANSACTION`, `CARD_EXPENSE`
- `RecurringFrequency`: `MONTHLY`, `ANNUAL`
- `RecurringClassification`: `FIXED`, `VARIABLE`
- `RecurringOccurrenceStatus`: `PENDING`, `MATERIALIZED`, `SKIPPED`
- `AlertType`: `CARD_LIMIT_NEAR`, `BUDGET_NEAR_LIMIT`, `BUDGET_EXCEEDED`, `CARD_BILL_DUE_SOON`, `CARD_BILL_OVERDUE`, `LOW_ACCOUNT_BALANCE`
- `AlertSeverity`: `WARNING`, `CRITICAL`

## Authentication

### `POST /auth/login`

Request:

```json
{
  "email": "user@example.com",
  "password": "secret"
}
```

Response:

```json
{
  "token": "jwt-token",
  "userId": 1,
  "email": "user@example.com",
  "name": "Lucas",
  "lastName": "Nunes"
}
```

Particularities:

- Store the `token` and send it as `Authorization: Bearer <token>`.
- Invalid credentials return `401` with a plain text body.
- Login is limited independently by client IP and normalized email.

### `POST /auth/register`

Request:

```json
{
  "name": "Lucas",
  "lastName": "Nunes",
  "monthLimit": 3000,
  "email": "user@example.com",
  "password": "secret"
}
```

Response: `200 OK` with an empty body.

Public registration always creates a `USER`. Re-registering an existing email returns the same empty
`200 OK` response and does not modify the existing account.

## Accounts

Account shape:

```json
{
  "id": 1,
  "name": "Checking",
  "type": "CHECKING",
  "balance": 1250.75,
  "currency": "BRL",
  "active": true
}
```

Endpoints:

- `GET /accounts`
- `POST /accounts`
- `GET /accounts/{accountId}`
- `PUT /accounts/{accountId}`
- `DELETE /accounts/{accountId}`
- `POST /accounts/{accountId}/transfers`

Create request:

```json
{
  "name": "Checking",
  "type": "CHECKING",
  "balance": 1000,
  "currency": "BRL"
}
```

Transfer request:

```json
{
  "targetAccountId": 2,
  "amount": 150,
  "occurredAt": "2026-06-18T10:30:00",
  "description": "Move to savings"
}
```

Particularities:

- Account creation with a positive opening balance creates an `ADJUSTMENT` transaction.
- `PUT /accounts/{id}` updates metadata only; it does not directly edit balance.
- `DELETE` soft-deactivates the account.
- Transfers create paired `TRANSFER_OUT` and `TRANSFER_IN` transactions and return `204`.
- Transfers require both accounts to belong to the authenticated user and source balance to be sufficient.

## Transactions

Transaction shape:

```json
{
  "id": 10,
  "type": "EXPENSE",
  "amount": 75,
  "occurredAt": "2026-06-18T10:30:00",
  "description": "Lunch",
  "accountId": 1,
  "relatedTransactionId": null,
  "categoryId": 3
}
```

Endpoints:

- `GET /transactions?month=&year=&accountId=&type=`
- `POST /transactions`
- `GET /transactions/{transactionId}`
- `DELETE /transactions/{transactionId}`

Create request:

```json
{
  "type": "EXPENSE",
  "amount": 75,
  "occurredAt": "2026-06-18T10:30:00",
  "description": "Lunch",
  "accountId": 1,
  "categoryId": 3
}
```

Particularities:

- Use this endpoint for manual `INCOME`, `EXPENSE`, and `ADJUSTMENT`.
- Do not create `TRANSFER_IN`, `TRANSFER_OUT`, or `CARD_PAYMENT` directly. Use transfer and card payment endpoints.
- `INCOME` increases account balance; `EXPENSE` decreases it.
- `ADJUSTMENT` supports signed correction and cannot be zero.
- `categoryId` is allowed only for `INCOME` and `EXPENSE`; category type must match.
- `month` and `year` filters must be used together.
- Deleting reverses balance impact. `CARD_PAYMENT` transactions cannot be deleted directly.

## Cards

Card shape:

```json
{
  "id": 1,
  "bankName": "Nubank",
  "name": "Purple",
  "network": "Mastercard",
  "lastFourDigits": "1234",
  "creditLimit": 5000,
  "closingDay": 25,
  "dueDay": 10,
  "active": true
}
```

Endpoints:

- `GET /cards`
- `POST /cards`
- `GET /cards/{cardId}`
- `PUT /cards/{cardId}`
- `DELETE /cards/{cardId}`
- `GET /cards/{cardId}/statements?month=&year=`
- `GET /cards/{cardId}/statements/current`
- `POST /cards/{cardId}/payments`
- `GET /cards/{cardId}/payments?month=&year=`
- `PUT /cards/{cardId}/payments/{paymentId}`
- `DELETE /cards/{cardId}/payments/{paymentId}`

Create/update request:

```json
{
  "bankName": "Nubank",
  "name": "Purple",
  "network": "Mastercard",
  "lastFourDigits": "1234",
  "creditLimit": 5000,
  "closingDay": 25,
  "dueDay": 10
}
```

Statement response:

```json
{
  "cardId": 1,
  "cardName": "Purple",
  "month": 6,
  "year": 2026,
  "dueDate": "2026-06-10",
  "closingDate": "2026-05-25",
  "totalAmount": 900,
  "availableLimit": 4100,
  "paidAmount": 300,
  "remainingAmount": 600,
  "status": "PARTIALLY_PAID",
  "installments": []
}
```

Payment request:

```json
{
  "accountId": 1,
  "month": 6,
  "year": 2026,
  "amount": 300,
  "paymentDate": "2026-06-08",
  "description": "Partial payment"
}
```

Payment response/list item:

```json
{
  "id": 10,
  "cardId": 2,
  "accountId": 1,
  "month": 6,
  "year": 2026,
  "amount": 300,
  "paymentDate": "2026-06-08",
  "description": "Pagamento Nubank",
  "transactionId": 55
}
```

Particularities:

- The API stores safe card metadata only. Never send full card numbers.
- `lastFourDigits`, when present, must contain exactly four numbers.
- `closingDay` and `dueDay` must be `1..31`.
- `DELETE /cards/{id}` soft-deactivates the card.
- Statements are derived from expense installments and card payments; there is no persisted statement row.
- A card payment creates a `CARD_PAYMENT` transaction and subtracts from the source account.
- Updating a card payment preserves `transactionId`, updates the related `CARD_PAYMENT` transaction, and recalculates account balance impact if `accountId` or `amount` changes.
- Deleting a card payment reverses its balance impact, removes the payment, removes its related `CARD_PAYMENT` transaction, and returns `204 No Content`.
- Card payment amount must be positive and source account must have sufficient balance.
- Overpaying a statement is allowed if account balance is sufficient; statement status becomes `OVERPAID`.
- `CARD_PAYMENT` transactions cannot be deleted directly through `DELETE /transactions/{transactionId}`; use `DELETE /cards/{cardId}/payments/{paymentId}`.

## Expenses

Expense shape:

```json
{
  "id": 12,
  "name": "Laptop",
  "amount": 2400,
  "installmentCount": 12,
  "purchaseDate": "2026-06-18T10:30:00",
  "description": "Work laptop",
  "userId": 1,
  "cardId": 1,
  "categoryId": 4
}
```

Endpoints:

- `GET /expenses?month=&year=&cardId=`
- `POST /expenses`
- `GET /expenses/{expenseId}`
- `PUT /expenses/{expenseId}`
- `DELETE /expenses/{expenseId}`

Create/update request:

```json
{
  "name": "Laptop",
  "amount": 2400,
  "installmentCount": 12,
  "purchaseDate": "2026-06-18T10:30:00",
  "description": "Work laptop",
  "cardId": 1,
  "categoryId": 4
}
```

Particularities:

- Ignore `userId` in frontend requests for new flows; ownership comes from JWT.
- `cardId` is optional. When present, it must be an active card owned by the user.
- `categoryId` is optional. When present, it must be an active `EXPENSE` category owned by the user.
- `amount` and `installmentCount` must be greater than zero.
- Card expense installments follow card closing/due-day billing rules.
- Updating an expense regenerates its installments.
- Shared expenses can include an optional `share` object when the users have an accepted connection. See `docs/expense-sharing-frontend-handoff.md` for the full connection, shared-expense, and `Acertos` contract.

Legacy compatibility endpoints:

- `GET /expenses/{userId}/total-expenses`
- `GET /expenses/{userId}/total-expenses/current-month`
- `GET /expenses/{userId}/total-expenses/any-month?month=&year=`

Frontend guidance: keep these out of new UI work. Prefer `/summary`, `/cards/{id}/statements`, `/transactions`, and `/alerts`.

## Categories

Category shape:

```json
{
  "id": 3,
  "name": "Groceries",
  "type": "EXPENSE",
  "active": true
}
```

Endpoints:

- `GET /categories`
- `POST /categories`
- `GET /categories/{categoryId}`
- `PUT /categories/{categoryId}`
- `DELETE /categories/{categoryId}`

Particularities:

- Categories are flat and user-owned.
- Active category names must be unique per user.
- `DELETE` soft-deactivates the category.
- Use `INCOME` categories only for income transactions.
- Use `EXPENSE` categories for account expenses, card expenses, and budgets.

## Budgets

Budget shape:

```json
{
  "id": 7,
  "categoryId": null,
  "month": null,
  "year": null,
  "amount": 800,
  "active": true
}
```

Endpoints:

- `GET /budgets`
- `GET /budgets?month=&year=`
- `POST /budgets`
- `GET /budgets/{budgetId}`
- `PUT /budgets/{budgetId}`
- `DELETE /budgets/{budgetId}`
- `GET /budgets/progress?month=&year=`

Progress response item:

```json
{
  "budgetId": 7,
  "categoryId": 3,
  "categoryName": "Groceries",
  "month": 6,
  "year": 2026,
  "budgetAmount": 800,
  "spentAmount": 650,
  "remainingAmount": 150,
  "percentUsed": 81.25,
  "status": "NEAR_LIMIT"
}
```

Particularities:

- `categoryId` is optional. When null, the budget is a global monthly budget.
- `month` and `year` are optional together. When both are null, the budget recurs every month.
- When `month/year` are provided to `GET /budgets`, the response includes budgets for that exact month plus recurring budgets.
- A specific month budget overrides a recurring budget for the same scope (`categoryId`, or global when `categoryId` is null).
- Category budgets are allowed only for active `EXPENSE` categories.
- One active budget per user/scope/period.
- `amount` must be greater than zero.
- Budget usage includes account `EXPENSE` transactions plus card statement installments for the budget month.
- Global budget usage sums all monthly spending, including uncategorized spending.
- In progress responses, global budgets return `categoryId: null` and `categoryName: "Monthly budget"`.
- Card payments are excluded from budget spending to avoid double-counting.
- `NEAR_LIMIT` starts at 80%.

## Recurrences

Recurring transaction shape:

```json
{
  "id": 1,
  "name": "Rent",
  "description": "Monthly rent",
  "targetType": "ACCOUNT_TRANSACTION",
  "transactionType": "EXPENSE",
  "amount": 1800,
  "frequency": "MONTHLY",
  "startDate": "2026-06-01",
  "endDate": null,
  "dayOfMonth": 5,
  "monthOfYear": null,
  "accountId": 1,
  "cardId": null,
  "categoryId": 3,
  "installmentCount": null,
  "classification": "FIXED",
  "active": true
}
```

Endpoints:

- `GET /recurrences`
- `POST /recurrences`
- `GET /recurrences/{recurrenceId}`
- `PUT /recurrences/{recurrenceId}`
- `DELETE /recurrences/{recurrenceId}`
- `GET /recurrences/occurrences?from=&to=`
- `POST /recurrences/{recurrenceId}/occurrences`
- `POST /recurrences/{recurrenceId}/occurrences/skip`

Occurrence materialize request:

```json
{
  "occurrenceDate": "2026-07-05",
  "amount": 350
}
```

Occurrence skip request:

```json
{
  "occurrenceDate": "2026-07-05"
}
```

Occurrence response:

```json
{
  "id": 10,
  "recurrenceId": 1,
  "recurrenceName": "Rent",
  "occurrenceDate": "2026-07-05",
  "amount": 1800,
  "targetType": "ACCOUNT_TRANSACTION",
  "transactionType": "EXPENSE",
  "accountId": 1,
  "cardId": null,
  "categoryId": 3,
  "classification": "FIXED",
  "status": "MATERIALIZED",
  "transactionId": 55,
  "expenseId": null
}
```

Particularities:

- Recurrences are not automatic jobs. The frontend previews and explicitly materializes or skips occurrences.
- `ACCOUNT_TRANSACTION` requires `accountId` and `transactionType` of `INCOME` or `EXPENSE`; `cardId` must be null.
- `CARD_EXPENSE` requires `cardId`; `accountId` and `transactionType` must be null.
- Card recurrences create expenses and installments; `installmentCount` defaults to `1`.
- Monthly recurrences use `dayOfMonth`; annual recurrences use `monthOfYear` plus `dayOfMonth`.
- Invalid month days are clamped to the last valid day.
- Materialization can send `amount` only when `classification` is `VARIABLE`; fixed recurrences reject custom occurrence amounts.
- Occurrence responses show the materialized occurrence amount when present; otherwise they fall back to the recurrence amount.
- Duplicate materialization for the same recurrence/date is rejected.
- Skipped occurrences cannot be materialized later in the current API.
- `DELETE` soft-deactivates the recurrence.

## Alerts

Endpoint:

- `GET /alerts?month=&year=&from=&to=`

Alert shape:

```json
{
  "key": "CARD_BILL_DUE_SOON:card:1:2026-6",
  "type": "CARD_BILL_DUE_SOON",
  "severity": "WARNING",
  "message": "Card Purple bill is due soon.",
  "resourceType": "CARD",
  "resourceId": 1,
  "amount": 600,
  "threshold": 0,
  "percentageUsed": null,
  "dueDate": "2026-06-20",
  "month": 6,
  "year": 2026
}
```

Defaults:

- `month/year`: current month/year.
- `from`: today in `America/Sao_Paulo`.
- `to`: `from + 7 days`.

Particularities:

- Alerts are derived and advisory; there is no read/resolved state.
- Critical alerts are returned before warnings.
- Card limit alert triggers at 80% of credit limit.
- Budget near-limit alert triggers at 80%; over-budget is critical.
- Due-soon card bills require a remaining amount and a due date inside the alert window.
- Overdue card bills require a remaining amount and a due date before today.
- Low account balance triggers below `100 BRL`.

## Summary and Reports

### `GET /summary/monthly?month=&year=`

Returns:

```json
{
  "month": 6,
  "year": 2026,
  "incomeTotal": 5000,
  "accountExpenseTotal": 1200,
  "cardPaymentsTotal": 900,
  "netCashFlow": 2900,
  "totalAccountBalance": 7500,
  "cardBillsTotal": 1400,
  "cardBillsRemaining": 500,
  "categoryBreakdown": [],
  "budgetProgress": []
}
```

Particularities:

- `netCashFlow` uses cash movement: income minus account expenses minus card payments.
- Card bill totals are statement totals/remaining amounts, separate from cash-flow card payments.

### `GET /summary/categories?month=&year=`

Returns categorized spending totals plus an uncategorized bucket where applicable.

### `GET /summary/trends?fromMonth=&fromYear=&toMonth=&toYear=`

Returns chronological monthly totals:

```json
{
  "month": 6,
  "year": 2026,
  "incomeTotal": 5000,
  "accountExpenseTotal": 1200,
  "cardStatementExpenseTotal": 1400,
  "netTotal": 2400
}
```

### `GET /summary/upcoming?from=&to=`

Returns upcoming recurrence occurrences and card statements due in the date range.

### `GET /summary/forecast?months=`

Defaults to `12`; accepts `1..24`.

Response:

```json
{
  "months": 12,
  "forecast": [
    {
      "month": 6,
      "year": 2026,
      "incomeTotal": 5000,
      "accountExpenseTotal": 1200,
      "cardStatementExpenseTotal": 1400,
      "cardPaymentsTotal": 900,
      "recurringIncomeTotal": 0,
      "recurringAccountExpenseTotal": 1800,
      "recurringCardExpenseTotal": 0,
      "projectedNetCashFlow": 1100,
      "projectedAccountBalance": 8600
    }
  ]
}
```

Particularities:

- Forecast does not mutate data.
- Includes real transactions/expenses plus pending recurrence occurrences.
- Avoids double-counting card payments: spending projections use statements; cash-flow projections use transactions/payments.

### `GET /summary/yearly?year=`

Returns monthly trend rows plus yearly totals.

### `GET /summary/cards?month=&year=`

Returns per-card statement totals:

```json
{
  "cardId": 1,
  "cardName": "Purple",
  "month": 6,
  "year": 2026,
  "totalAmount": 1400,
  "paidAmount": 900,
  "remainingAmount": 500,
  "status": "PARTIALLY_PAID"
}
```

### `GET /summary/fixed-variable?month=&year=`

Returns:

```json
{
  "month": 6,
  "year": 2026,
  "fixedAmount": 1800,
  "variableAmount": 350,
  "unclassifiedAmount": 900
}
```

Particularities:

- Classification comes from recurrence-generated records.
- Manual records without recurrence metadata are `unclassified`.

## Users

Endpoints:

- `GET /users` requires `ADMIN`.
- `GET /users/{userId}`
- `PUT /users/{userId}`
- `DELETE /users/{userId}`

Frontend guidance:

- Most finance screens should not depend on `/users`; use JWT-owned endpoints.
- Keep user administration separate from personal finance flows.

## User Preferences

Movement-entry defaults are managed through `GET /me/preferences` and `PUT /me/preferences`.

See `docs/user-preferences-frontend-handoff.md` for the full contract.

## Frontend Implementation Notes

- Build new screens around these primary resources: `/accounts`, `/cards`, `/transactions`, `/expenses`, `/categories`, `/budgets`, `/recurrences`, `/summary/*`, and `/alerts`.
- There are no `/wallets` endpoints; wallet code was removed from the app layer and only old database migrations remain.
- For dashboard UX, a good first load is:
  - `GET /summary/monthly?month=current&year=current`
  - `GET /alerts`
  - `GET /summary/upcoming?from=today&to=today+30`
  - `GET /accounts`
  - `GET /cards`
- For credit-card detail UX:
  - `GET /cards/{id}`
  - `GET /cards/{id}/statements/current`
  - `GET /cards/{id}/payments?month=&year=`
  - `GET /expenses?month=&year=&cardId={id}`
- For planning UX:
  - `GET /budgets/progress?month=&year=`
  - `GET /summary/categories?month=&year=`
  - `GET /summary/forecast?months=12`
  - `GET /recurrences/occurrences?from=&to=`
