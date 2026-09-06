.PHONY: build test vet fmt check run-publish run-poll docker-build

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

run-publish:
	CORE_API_KEY=$${CORE_API_KEY:-local-dev-api-key} go run ./cmd/workers publish

run-poll:
	CORE_API_KEY=local-dev-api-key go run ./cmd/workers poll

docker-build:
	docker build -t social-workers:local .
