package config

import (
	"fmt"
	"os"
)

// Config holds the startup configuration for the proxy agent.
// Values are read from environment variables with sensible defaults.
type Config struct {
	// ProxyTarget is the HTTP backend address to forward requests to (required).
	// Example: "http://order-svc:8081"
	ProxyTarget string

	// GRPCTarget is the gRPC backend address (host:port, no scheme).
	// If empty, gRPC support is disabled and only HTTP is proxied.
	// Example: "inventory-svc:8082"
	GRPCTarget string

	// ProxyPort is the port the proxy server listens on.
	// When GRPCTarget is set, cmux is used to multiplex HTTP and gRPC on this port.
	ProxyPort string

	// ControlPort is the port the control API server listens on.
	ControlPort string

	// SnapshotDir is the directory where request/response snapshots are persisted.
	SnapshotDir string

	// InitialMode is the mode the proxy starts in.
	// Valid values: "passthrough", "record", "replay", "intercept"
	InitialMode string
}

// Load reads configuration from environment variables.
// Returns an error if required variables are missing.
func Load() (*Config, error) {
	target := os.Getenv("PROXY_TARGET")
	if target == "" {
		return nil, fmt.Errorf("PROXY_TARGET environment variable is required")
	}

	cfg := &Config{
		ProxyTarget: target,
		GRPCTarget:  os.Getenv("GRPC_TARGET"),
		ProxyPort:   envOrDefault("PROXY_PORT", "8080"),
		ControlPort: envOrDefault("CONTROL_PORT", "9090"),
		SnapshotDir: envOrDefault("SNAPSHOT_DIR", "/data/snapshots"),
		InitialMode: envOrDefault("INITIAL_MODE", "passthrough"),
	}

	return cfg, nil
}

// envOrDefault returns the value of the environment variable named by key,
// or the provided default value if the variable is not set or empty.
func envOrDefault(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}
