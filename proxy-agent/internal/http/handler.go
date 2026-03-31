package http

import (
	"bytes"
	"io"
	"log/slog"
	"net/http"
	"net/http/httputil"
	"net/url"
	"time"

	"proxy-agent/internal/engine"
)

// NewProxyHandler creates an http.Handler that acts as a reverse proxy with
// mode-dependent behavior (passthrough, record, replay, intercept).
func NewProxyHandler(targetURL string, eng *engine.Engine) (http.Handler, error) {
	target, err := url.Parse(targetURL)
	if err != nil {
		return nil, err
	}

	// We wrap the standard ReverseProxy inside a custom handler so we can
	// intercept requests before they are forwarded (for replay / intercept
	// modes) and capture response bodies (for record mode).
	proxy := &httputil.ReverseProxy{
		Director: func(req *http.Request) {
			req.URL.Scheme = target.Scheme
			req.URL.Host = target.Host
			req.Host = target.Host
		},
		ErrorHandler: func(w http.ResponseWriter, r *http.Request, err error) {
			slog.Error("proxy error",
				"method", r.Method,
				"path", r.URL.Path,
				"error", err,
			)
			http.Error(w, "proxy error: "+err.Error(), http.StatusBadGateway)
		},
	}

	return &proxyHandler{
		engine:   eng,
		proxy:    proxy,
		targetURL: targetURL,
	}, nil
}

// proxyHandler implements http.Handler with mode-aware request handling.
type proxyHandler struct {
	engine    *engine.Engine
	proxy     *httputil.ReverseProxy
	targetURL string
}

func (h *proxyHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	h.engine.IncrementTotal()
	mode := h.engine.GetMode()

	switch mode {
	case engine.ModePassthrough:
		h.proxy.ServeHTTP(w, r)

	case engine.ModeRecord:
		h.handleRecord(w, r)

	case engine.ModeReplay:
		h.handleReplay(w, r)

	case engine.ModeIntercept:
		h.handleIntercept(w, r)

	default:
		// Unknown mode: fall back to passthrough.
		h.proxy.ServeHTTP(w, r)
	}
}

// handleRecord forwards the request to the target and records both the
// request and response as a snapshot.
func (h *proxyHandler) handleRecord(w http.ResponseWriter, r *http.Request) {
	// Check baggage filter and snapshot limit BEFORE processing
	baggageHeader := r.Header.Get("baggage")
	if !h.engine.ShouldRecord(baggageHeader) {
		// Skip recording — just proxy the request through
		h.proxy.ServeHTTP(w, r)
		h.engine.IncrSkipped()
		return
	}

	// Read and buffer the request body so we can both forward it and record it.
	var reqBody []byte
	if r.Body != nil {
		var err error
		reqBody, err = io.ReadAll(r.Body)
		if err != nil {
			slog.Error("failed to read request body", "error", err)
			http.Error(w, "failed to read request body", http.StatusInternalServerError)
			return
		}
		r.Body = io.NopCloser(bytes.NewReader(reqBody))
	}

	// Compute signature from the original request.
	sig := engine.ComputeSignature(r.Method, r.URL.RequestURI(), r.Header, reqBody)

	// Capture request metadata.
	capturedReq := engine.CapturedRequest{
		Method:  r.Method,
		Path:    r.URL.RequestURI(),
		Headers: flattenHeaders(r.Header),
		Body:    reqBody,
	}

	// Use a ResponseRecorder to capture the response from the target.
	recorder := &responseRecorder{
		header:     make(http.Header),
		statusCode: http.StatusOK,
	}

	start := time.Now()
	h.proxy.ServeHTTP(recorder, r)
	latency := time.Since(start)

	// Build captured response.
	capturedResp := engine.CapturedResponse{
		StatusCode: recorder.statusCode,
		Headers:    flattenHeaders(recorder.header),
		Body:       recorder.body.Bytes(),
		LatencyMs:  latency.Milliseconds(),
	}

	// Store the snapshot.
	if err := h.engine.Record(sig, capturedReq, capturedResp); err != nil {
		slog.Error("failed to record snapshot", "error", err)
	}

	// Write the captured response to the original client.
	writeResponse(w, recorder)
}

// handleReplay attempts to find a previously recorded snapshot matching the
// incoming request. If found, it returns the recorded response; otherwise
// it falls back to passthrough.
func (h *proxyHandler) handleReplay(w http.ResponseWriter, r *http.Request) {
	// Read the request body for signature computation.
	var reqBody []byte
	if r.Body != nil {
		var err error
		reqBody, err = io.ReadAll(r.Body)
		if err != nil {
			slog.Error("failed to read request body", "error", err)
			http.Error(w, "failed to read request body", http.StatusInternalServerError)
			return
		}
		// Reset body for potential passthrough fallback.
		r.Body = io.NopCloser(bytes.NewReader(reqBody))
	}

	sig := engine.ComputeSignature(r.Method, r.URL.RequestURI(), r.Header, reqBody)

	snap, found := h.engine.Replay(sig)
	if !found {
		// No recorded snapshot; fall back to passthrough.
		slog.Debug("replay miss, falling back to passthrough",
			"method", r.Method,
			"path", r.URL.RequestURI(),
		)
		h.proxy.ServeHTTP(w, r)
		return
	}

	// Simulate the original latency if it was meaningful.
	if snap.Response.LatencyMs > 0 {
		time.Sleep(time.Duration(snap.Response.LatencyMs) * time.Millisecond)
	}

	// Write the recorded response.
	for k, v := range snap.Response.Headers {
		w.Header().Set(k, v)
	}
	w.WriteHeader(snap.Response.StatusCode)
	if len(snap.Response.Body) > 0 {
		_, _ = w.Write(snap.Response.Body)
	}
}

// handleIntercept checks if any intercept rule matches the request. If so,
// it returns the mock response without forwarding. Otherwise, falls back
// to passthrough.
func (h *proxyHandler) handleIntercept(w http.ResponseWriter, r *http.Request) {
	baggageHeader := r.Header.Get("baggage")
	rule, matched := h.engine.MatchRule(r.Method, r.URL.Path, baggageHeader)
	if !matched {
		// No matching rule; fall back to passthrough.
		h.proxy.ServeHTTP(w, r)
		return
	}

	// Simulate recorded latency before returning mock response.
	if rule.Response.LatencyMs > 0 {
		time.Sleep(time.Duration(rule.Response.LatencyMs) * time.Millisecond)
	}

	// Return the mock response defined by the rule.
	if rule.Response.Headers != nil {
		for k, v := range rule.Response.Headers {
			w.Header().Set(k, v)
		}
	}
	if w.Header().Get("Content-Type") == "" {
		w.Header().Set("Content-Type", "application/json")
	}
	w.WriteHeader(rule.Response.StatusCode)
	if rule.Response.Body != "" {
		_, _ = w.Write([]byte(rule.Response.Body))
	}
}

// responseRecorder captures the status code, headers, and body written by
// the reverse proxy so we can both record them and forward them to the client.
type responseRecorder struct {
	header     http.Header
	body       bytes.Buffer
	statusCode int
}

func (r *responseRecorder) Header() http.Header {
	return r.header
}

func (r *responseRecorder) Write(b []byte) (int, error) {
	return r.body.Write(b)
}

func (r *responseRecorder) WriteHeader(code int) {
	r.statusCode = code
}

// writeResponse copies a recorded response to the real http.ResponseWriter.
func writeResponse(w http.ResponseWriter, rec *responseRecorder) {
	for k, vals := range rec.header {
		for _, v := range vals {
			w.Header().Add(k, v)
		}
	}
	w.WriteHeader(rec.statusCode)
	_, _ = w.Write(rec.body.Bytes())
}

// flattenHeaders converts multi-value headers to single-value for storage.
// Only the first value of each header is kept.
func flattenHeaders(h http.Header) map[string]string {
	result := make(map[string]string, len(h))
	for k, vals := range h {
		if len(vals) > 0 {
			result[k] = vals[0]
		}
	}
	return result
}
