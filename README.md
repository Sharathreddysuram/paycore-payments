# PayCore Payments — Real-Time Payments Platform (Microservices)

A real-time payments platform for a bank, decomposed into four Spring Cloud microservices:
open accounts, move money between them with idempotency guarantees, and keep a
double-entry audit ledger — all behind a JWT-secured API gateway with Eureka
service discovery.

This is the microservices evolution of the single-service
[paycore-payments-engine](../paycore-payments-engine) project. The domain logic
(accounts, idempotent transfers, double-entry ledger, JWT auth) was carried over
and split along service boundaries.

## Architecture

```
                        ┌─────────────────────────────┐
                        │      service-registry       │
                        │      (Eureka, :8761)        │
                        └──────────────┬──────────────┘
                                       │ registers / discovers
   ┌───────────────┐      ┌─────────────▼─────────────┐      ┌──────────────────┐
   │    Client     │─────▶│        api-gateway        │─────▶│  account-service │
   │               │      │  (:8080, JWT at the edge) │      │  (:8081)         │
   └───────────────┘      └─────────────┬─────────────┘      │  accounts +      │
                                        │ routes            │  balances (own   │
                                        │ /api/accounts/**  │  database)       │
                                        │ /api/transfers/** └──────────────────┘
                                        ▼                              ▲
                              ┌──────────────────┐      internal debit/credit
                              │ transfer-service │───────over HTTP──────────────┘
                              │  (:8082)         │      (lb://account-service)
                              │  transfers +     │
                              │  ledger (own DB) │
                              └──────────────────┘
```

Request flow for a transfer:

1. Client registers/logs in at the gateway (`POST /api/auth/...`) and gets a JWT.
2. Client calls `POST /api/transfers` on the gateway with the JWT.
3. The gateway's `JwtAuthFilter` validates the token, then routes to `transfer-service`
   via Eureka (`lb://transfer-service`).
4. `transfer-service` checks its idempotency store, then calls account-service's
   internal endpoints — `POST /internal/accounts/{n}/debit`, then `.../credit` —
   resolved through Eureka with a load-balanced `RestClient` (`lb://account-service`).
5. Each debit/credit is atomic inside account-service (pessimistic row lock).
   transfer-service persists the transfer row plus two ledger entries (DEBIT/CREDIT).

## Services

| Service | Port | Responsibility |
|---|---|---|
| `service-registry` | 8761 | Eureka server — service discovery for all modules |
| `api-gateway` | 8080 | Edge: JWT auth (`/api/auth/**` lives here), routing to `lb://account-service` and `lb://transfer-service` |
| `account-service` | 8081 | Accounts, balances; public `/api/accounts`, internal `/internal/accounts/{n}/debit\|credit` (pessimistic locking, 422 on insufficient funds) |
| `transfer-service` | 8082 | Transfer orchestration, idempotency keys, double-entry ledger |

## Tech stack

- Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1 (Eureka, Gateway, LoadBalancer)
- Spring Data JPA + Flyway (schema migrations, `ddl-auto: validate`)
- H2 by default; PostgreSQL via the `postgres` profile + `docker-compose.yml`
- JJWT 0.12.6 (HS256), Bean Validation, springdoc-openapi
- JUnit 5 + AssertJ + Mockito for tests

## Project structure

```
paycore-payments/
├── pom.xml                  # parent: Boot 3.2.5, Spring Cloud BOM 2023.0.1
├── docker-compose.yml       # postgres-accounts (:5432) + postgres-transfers (:5433)
├── service-registry/        # Eureka server
├── api-gateway/             # routes, JwtAuthFilter, AuthController, user store
├── account-service/         # Account entity/service, public + internal controllers
└── transfer-service/        # Transfer orchestration, AccountClient, ledger
```

Each service owns its database (database-per-service): the gateway keeps users,
account-service keeps accounts, transfer-service keeps transfers + ledger rows
(storing account *numbers* as plain strings — no cross-service foreign keys).

## How to run

Prerequisites: JDK 17, Maven 3.9+.

**1. Start the registry** (in `paycore-payments/`):

```bash
cd service-registry && mvn spring-boot:run
```

Wait until the Eureka dashboard loads at http://localhost:8761.

**2. Start account-service** (new terminal):

```bash
cd account-service && mvn spring-boot:run
```

**3. Start transfer-service** (new terminal):

```bash
cd transfer-service && mvn spring-boot:run
```

**4. Start the gateway** (new terminal):

```bash
cd api-gateway && mvn spring-boot:run
```

Give Eureka ~30 seconds to register everything (check the dashboard), then drive
the whole platform through the gateway at http://localhost:8080.

**PostgreSQL instead of H2** (optional):

```bash
docker compose up -d
cd account-service && mvn spring-boot:run -Dspring-boot.run.profiles=postgres
cd transfer-service && mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## API walkthrough (through the gateway)

Register and log in:

```bash
curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"teller1","password":"secret123","fullName":"Teller One"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"teller1","password":"secret123"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")
```

Create two accounts:

```bash
A=$(curl -s -X POST localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"ownerName":"Ada Lovelace","type":"CHECKING","currency":"USD","initialDeposit":1000.00}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['accountNumber'])")

B=$(curl -s -X POST localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alan Turing","type":"SAVINGS","currency":"USD","initialDeposit":500.00}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['accountNumber'])")
```

Transfer $250 from A to B (201 = executed):

```bash
curl -s -X POST localhost:8080/api/transfers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"pay-0001\",\"fromAccountNumber\":\"$A\",\"toAccountNumber\":\"$B\",\"amount\":250.00,\"currency\":\"USD\"}"
```

Replay the exact same request — the idempotency key returns the original result
with 200 and **no money moves twice**:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/transfers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"pay-0001\",\"fromAccountNumber\":\"$A\",\"toAccountNumber\":\"$B\",\"amount\":250.00,\"currency\":\"USD\"}"
# -> 200, same transferId as before
```

Overdraft attempt — records a `FAILED` transfer with a reason instead of a 500:

```bash
curl -s -X POST localhost:8080/api/transfers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"idempotencyKey\":\"pay-0002\",\"fromAccountNumber\":\"$B\",\"toAccountNumber\":\"$A\",\"amount\":99999.00,\"currency\":\"USD\"}"
# -> 201 {"status":"FAILED","failureReason":"Insufficient funds on account ..."}
```

Unauthenticated call — the gateway rejects it before it reaches any service:

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/accounts/$A
# -> 401
```

## Key design decisions

- **Database per service.** account-service and transfer-service each own their
  schema (separate H2/Postgres databases). transfer-service references accounts
  by number string only — no cross-service joins or foreign keys, so services
  stay independently deployable.
- **Orchestration, not distributed transactions.** transfer-service drives
  debit-then-credit over HTTP. Each leg is atomic inside account-service, but
  there is no atomicity *across* the two calls: if credit fails after a
  successful debit, the debit stands and no transfer row exists (the client can
  safely retry with the same idempotency key). This trade-off is explicit in
  `TransferService`'s class Javadoc; the production answer is the saga pattern
  with compensating actions (credit-back on failure).
- **Idempotency keys.** The client supplies one per transfer; replays return the
  original result (200) instead of moving money again. The key has a DB unique
  constraint as a backstop against race conditions.
- **Pessimistic locking in account-service.** Debit/credit load the account row
  `FOR UPDATE`, so concurrent transfers against the same account serialize
  instead of losing updates.
- **JWT at the edge.** Only the gateway validates tokens; downstream services
  stay simple. The internal debit/credit endpoints are never routed by the
  gateway — they are cluster-internal by construction.
- **Load-balanced inter-service calls.** transfer-service reaches account-service
  via `lb://account-service` on a `@LoadBalanced RestClient.Builder`, so
  instances resolve through Eureka with client-side load balancing.
- **Flyway owns the schema** (`ddl-auto: validate`) in every service, so the
  database always matches what JPA expects.

## Tests

- `account-service`: `@DataJpaTest` — account creation, debit, overdraft (balance
  unchanged), credit, missing account.
- `transfer-service`: pure Mockito unit tests — success path (verifies debit runs
  before credit, two ledger rows), idempotent replay (no downstream calls, no
  saves), insufficient funds (FAILED persisted, credit never called).

The gateway has no tests by design: WebFlux + blocking JPA makes slice tests
misleading; its contract is the routing table + auth filter above.

```bash
cd account-service && mvn test
cd transfer-service && mvn test
```

## Configuration notes

- Gateway JWT secret: `paycore.jwt.secret` (dev default in `application.yml`;
  override with the `PAYCORE_JWT_SECRET` environment variable in real deployments).
- H2 is the default for zero-dependency local runs; use `-Dspring-boot.run.profiles=postgres`
  with `docker compose up -d` for PostgreSQL.

## Future improvements

- Saga with compensating transactions (credit-back) for the debit/credit orchestration.
- Publish transfer events to Kafka so a fraud-detection service can score them in
  real time (see the sibling `sentinel-fraud-detection` project).
- Rate limiting and request tracing (Micrometer + Zipkin) at the gateway.
- Refresh-token rotation and per-user rate limits on auth endpoints.
