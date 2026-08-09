.PHONY: build test vet fmt check run docker-build

BIN := bin/workers

build:
	go build -o $(BIN) ./cmd/workers

test:
	go test ./...

vet:
	go vet ./...

fmt:
	gofmt -l -w .

check: vet test

# Requires the core API to be running (cd ../api && docker compose up -d).
run:
	CORE_API_KEY=$${CORE_API_KEY:-local-dev-api-key} go run ./cmd/workers publish

docker-build:
	docker build -t social-workers:local .
