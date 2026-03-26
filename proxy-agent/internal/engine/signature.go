package engine

import (
	"crypto/sha256"
	"fmt"
	"net/http"
	"sort"
	"strings"
)

// Signature represents a unique fingerprint for an HTTP request.
// It is used to match recorded snapshots during replay.
type Signature struct {
	// Hash is the SHA256 hex digest of the request's significant components.
	Hash string `json:"hash"`

	// Method is the HTTP method (GET, POST, etc.).
	Method string `json:"method"`

	// Path is the request URL path (including query string if present).
	Path string `json:"path"`
}

// volatileHeaders are headers excluded from signature computation because
// they change across requests and don't represent meaningful request identity.
var volatileHeaders = map[string]bool{
	"date":              true,
	"x-request-id":      true,
	"x-b3-traceid":      true,
	"x-b3-spanid":       true,
	"x-b3-parentspanid": true,
	"x-b3-sampled":      true,
	"authorization":     true,
	"cookie":            true,
	"user-agent":        true,
	"accept-encoding":   true,
	"connection":        true,
	"keep-alive":        true,
	"te":                true,
	"transfer-encoding": true,
	"upgrade":           true,
}

// VolatileHeaders is exported so the gRPC handler can reuse the same exclusion
// list for metadata filtering.
var VolatileHeaders = volatileHeaders

// ComputeSignature produces a deterministic Signature from the significant
// parts of an HTTP request: method, path, selected headers, and body.
func ComputeSignature(method, path string, headers http.Header, body []byte) Signature {
	h := sha256.New()

	// Write method and path into the hash.
	h.Write([]byte(method))
	h.Write([]byte(path))

	// Collect and sort significant headers for deterministic hashing.
	var significantHeaders []string
	for name, values := range headers {
		lower := strings.ToLower(name)
		if volatileHeaders[lower] {
			continue
		}
		for _, v := range values {
			significantHeaders = append(significantHeaders, lower+"="+v)
		}
	}
	sort.Strings(significantHeaders)

	for _, sh := range significantHeaders {
		h.Write([]byte(sh))
	}

	// Write body into the hash.
	if len(body) > 0 {
		h.Write(body)
	}

	return Signature{
		Hash:   fmt.Sprintf("%x", h.Sum(nil)),
		Method: method,
		Path:   path,
	}
}

// ComputeGRPCSignature produces a deterministic Signature for a gRPC call.
// The method is the full gRPC method string (e.g., "/package.Service/Method").
// mdPairs are key-value pairs from gRPC metadata (flattened).
// payload is the raw protobuf bytes (not deserialized).
func ComputeGRPCSignature(method string, mdPairs map[string][]string, payload []byte) Signature {
	h := sha256.New()

	// Prefix with "grpc" to avoid collisions with HTTP signatures.
	h.Write([]byte("grpc"))
	h.Write([]byte(method))

	// Collect and sort significant metadata for deterministic hashing.
	var significantMD []string
	for k, vals := range mdPairs {
		lower := strings.ToLower(k)
		// Skip volatile headers, pseudo-headers, and gRPC internal metadata.
		if volatileHeaders[lower] {
			continue
		}
		if strings.HasPrefix(lower, ":") {
			continue
		}
		if strings.HasPrefix(lower, "grpc-") {
			continue
		}
		if lower == "traceparent" || lower == "tracestate" {
			continue
		}
		for _, v := range vals {
			significantMD = append(significantMD, lower+"="+v)
		}
	}
	sort.Strings(significantMD)

	for _, s := range significantMD {
		h.Write([]byte(s))
	}

	if len(payload) > 0 {
		h.Write(payload)
	}

	return Signature{
		Hash:   fmt.Sprintf("%x", h.Sum(nil)),
		Method: "GRPC",
		Path:   method,
	}
}
