# transactions-service

Stores USD purchase transactions and returns them converted to a foreign currency
using the [U.S. Treasury Reporting Rates of Exchange API](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/).
The exchange rate used is the most recent one on or before the purchase date, within the prior 6 months.

## Getting started

Requires **JDK 17+**. Docker is needed only for the Postgres-backed tests and `run-postgres`.

```bash
make run      # start on the dev profile (in-memory H2) at http://localhost:8080/api
make test     # unit + component tests (stubs, no Docker)
make verify   # + integration tests against Testcontainers Postgres (needs Docker)
make package  # build the runnable jar
```

Default profile is `dev` (H2). To run against local Postgres instead, copy `.env.example` to `.env` and run `make run-postgres` (starts Postgres via `docker-compose.yml`).

## API

### Create a transaction
```bash
curl -i -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{"description":"Coffee","amountInUsd":12.49,"transactionDate":"2026-03-15"}'
```
```json
{
  "id": "f151e111-1e23-4c3e-abd2-7d947b58bf75",
  "description": "test",
  "transactionDate": "2026-04-11",
  "amountInUsd": 12.50
}
```
Returns `201 Created` with a `Location` header and the stored transaction (including its `id`).

### Get a transaction converted to a currency
`country` is required; `currency` optionally narrows a country that reports more than one currency.
```bash
curl "http://localhost:8080/api/transactions/{id}?country=Jamaica"
```
```json
{
  "id": "…",
  "description": "Coffee",
  "transactionDate": "2026-03-15",
  "originalAmountInUsd": 12.49,
  "conversions": [
    {
      "country": "Jamaica",
      "currencyDescription": "Jamaica-Dollar",
      "exchangeRateUsed": 159.0,
      "convertedAmount": 1985.91,
      "recordDate": "2025-12-31"
    }
  ]
}
```

A country may report multiple currencies; each is returned in `conversions`. Any that matched but had no rate within the 6-month window are listed in an `errors` array
(not returned when empty). If the lookup resolves to a single currency with no rate in the past 6 months, the response is `422 Unprocessable Content`.

When a multi-currency lookup partially succeeds, the converted currencies appear in `conversions` and the unavailable ones in `errors`:

```json
{
  "id": "…",
  "description": "Coffee",
  "transactionDate": "2026-03-15",
  "originalAmountInUsd": 12.49,
  "conversions": [
    {
      "country": "Euro Zone",
      "currencyDescription": "Euro Zone-Euro",
      "exchangeRateUsed": 0.92,
      "convertedAmount": 11.49,
      "recordDate": "2026-01-31"
    }
  ],
  "errors": [
    {
      "country": "Euro Zone",
      "currencyDescription": "Euro Zone-Krona",
      "message": "no exchange rate within 6 months on or before 2026-03-15 (latest rate was 2025-01-31)"
    }
  ]
}
```

## Testing

Tests are layered along the testing pyramid — many fast, isolated tests at the base,
progressively fewer and broader-scoped ones toward the top:

```
        ╱  live   ╲     make live    – real Treasury API, on demand
      ╱ integration ╲   make verify  – real Postgres (Testcontainers)
    ╱   component     ╲ make test    – whole app, external deps stubbed
  ╱       unit          ╲ make test  – logic, mocks, web slice
```

| Command       | Layer       | Scope                                                                 | Needs Docker | Self-contained |
|---------------|-------------|-----------------------------------------------------------------------|:------------:|:--------------:|
| `make test`   | Unit        | Pure logic and sliced Spring — service/converter mocks, the Treasury client against `MockRestServiceServer`, controller via `@WebMvcTest`. | no  | yes |
| `make test`   | Component   | Whole app in-process (`@SpringBootTest`, H2), real HTTP web→service→JPA, with the Treasury API stubbed by WireMock. | no  | yes |
| `make verify` | Integration | Full stack against a real Postgres in a container, mirroring the prod engine (`@Tag("integration")`, Failsafe). | yes | yes |
| `make live`   | Live (E2E)  | Smoke test against the **real** Treasury API (`@Tag("live")`, excluded from the default build). | no  | no  |

`make test` runs the bottom two layers (unit + component) on every build; `make verify`
adds the integration layer; `make live` runs only the live smoke test.
