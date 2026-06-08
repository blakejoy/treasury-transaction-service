MVN := ./mvnw

test: ## Unit + component tests (H2, no Docker, no network)
	$(MVN) test

verify: ## test + integration tests (Testcontainers Postgres; needs Docker)
	$(MVN) verify

live: ## Live smoke test against the real Treasury API
	$(MVN) test -Plive

run: ## Run the app on the dev profile (in-memory H2)
	$(MVN) spring-boot:run

run-postgres: db-up ## Run the app against local Postgres
	SPRING_PROFILES_ACTIVE=postgres $(MVN) spring-boot:run

db-up: ## Start local Postgres and wait until healthy
	docker compose up -d --wait postgres

db-down: ## Stop local Postgres (keeps data)
	docker compose stop postgres

db-reset: ## Stop local Postgres and delete its data volume
	docker compose down -v

package: ## Build the runnable jar (skips tests)
	$(MVN) -DskipTests package

docker-build: ## Build the production runtime image
	docker build -t transactions-service .

clean: ## Remove build output
	$(MVN) clean
