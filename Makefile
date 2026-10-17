SHELL := /bin/bash
.DEFAULT_GOAL := help
.PHONY: help up down back front compile typecheck test-back test-front check

help: ## Lista os comandos
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{ printf "  make %-12s %s\n", $$1, $$2 }'

up: ## Sobe os serviços do docker-compose
	docker compose up -d

down: ## Para os serviços
	docker compose down

back: ## API em modo dev
	cd salon-back && ./mvnw spring-boot:run

front: ## Front em http://localhost:5173
	cd salon-front && npm run dev

compile: ## Compila o back sem testes
	cd salon-back && ./mvnw -q -DskipTests compile

typecheck: ## Checa tipos do front
	cd salon-front && npx tsc -b

test-back: ## Testes do back (CLASSE=NomeDoTeste para um só)
	cd salon-back && ./mvnw -q test $(if $(CLASSE),-Dtest=$(CLASSE),)

test-front: ## Testes do front (ARQ=caminho para um só)
	cd salon-front && npx vitest run $(ARQ)

check: compile typecheck ## Verificação rápida pré-integração (sem testes)
