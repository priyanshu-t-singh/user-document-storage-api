.DEFAULT_GOAL := help
.PHONY: help setup up down reset logs seed s3-ls run start test build clean search download upload swagger

GRADLEW   ?= ./gradlew
PROFILE   ?= local
BASE_URL  ?= http://localhost:8080
API       := $(BASE_URL)/api/v1
BUCKET    ?= user-document-storage
CONTAINER ?= localstack-main

# Overridable per call, e.g. make search USER_NAME=john SEARCH_TERM=resume
USER_NAME ?= sandy
SEARCH_TERM ?= logistics
FILE ?= logistics_report.pdf

help: ## Show this help
	@echo "User Document Storage Service"
	@echo ""
	@echo "Usage: make <target> [VAR=value]"
	@echo ""
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z0-9_-]+:.*## / {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "Variables (defaults): USER_NAME=$(USER_NAME) SEARCH_TERM=$(SEARCH_TERM) FILE=$(FILE) PROFILE=$(PROFILE)"

setup: ## One-time prep (make init script executable)
	chmod +x localstack-init/init-s3.sh

up: setup ## Start LocalStack, create bucket and seed sample files
	docker compose up -d --wait localstack
	@echo "LocalStack is ready at http://localhost:4566"

down: ## Stop LocalStack (data kept)
	docker compose down

reset: ## Stop LocalStack and wipe all data (re-seeds on next 'make up')
	docker compose down -v

logs: ## Tail LocalStack logs
	docker compose logs -f localstack

seed: ## Re-run the seed script inside the running container
	docker exec $(CONTAINER) bash /etc/localstack/init/ready.d/init-s3.sh

s3-ls: ## List everything in the S3 bucket
	docker exec $(CONTAINER) awslocal s3 ls s3://$(BUCKET)/ --recursive

run: ## Run the Spring Boot app with the local profile
	$(GRADLEW) bootRun --args='--spring.profiles.active=$(PROFILE)'

start: up run ## Start LocalStack, then run the app (the one command)

test: ## Run all JUnit tests
	$(GRADLEW) test

build: ## Build the jar (skips tests)
	$(GRADLEW) clean build -x test

clean: ## Remove build output
	$(GRADLEW) clean

search: ## Search a user's files: make search USER_NAME=sandy SEARCH_TERM=logistics
	curl -s "$(API)/users/$(USER_NAME)/files?search=$(SEARCH_TERM)" | python3 -m json.tool

download: ## Download a file: make download USER_NAME=sandy FILE=logistics_report.pdf
	curl -f -OJ "$(API)/users/$(USER_NAME)/files/$(FILE)/download"

upload: ## Upload a file: make upload USER_NAME=sandy FILE=./path/to/file.txt
	curl -s -F "file=@$(FILE)" "$(API)/users/$(USER_NAME)/files"

swagger: ## Print the Swagger UI URL
	@echo "$(BASE_URL)/swagger-ui.html"
