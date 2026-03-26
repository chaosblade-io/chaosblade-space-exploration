FROM golang:1.23-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /proxy-agent ./cmd/proxy

FROM alpine:3.19
RUN apk add --no-cache ca-certificates
COPY --from=builder /proxy-agent /usr/local/bin/proxy-agent
RUN mkdir -p /data/snapshots
EXPOSE 8080 9090
ENTRYPOINT ["proxy-agent"]
