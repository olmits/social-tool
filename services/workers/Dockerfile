FROM golang:1.25 AS builder
WORKDIR /build

COPY go.mod ./
RUN go mod download

COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /workers ./cmd/workers

FROM gcr.io/distroless/static:nonroot

COPY --from=builder /workers /workers

USER nonroot:nonroot
# The subcommand (publish | poll | analytics) comes from the task definition, so that one
# image backs every worker.
ENTRYPOINT ["/workers"]
