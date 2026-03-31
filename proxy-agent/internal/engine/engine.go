package engine

import (
	"fmt"
	"log/slog"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// Mode represents the operating mode of the proxy engine.
type Mode string

const (
	ModePassthrough Mode = "passthrough"
	ModeRecord      Mode = "record"
	ModeReplay      Mode = "replay"
	ModeIntercept   Mode = "intercept"
)

// ParseMode converts a string to a Mode, returning an error for invalid values.
func ParseMode(s string) (Mode, error) {
	switch Mode(strings.ToLower(s)) {
	case ModePassthrough:
		return ModePassthrough, nil
	case ModeRecord:
		return ModeRecord, nil
	case ModeReplay:
		return ModeReplay, nil
	case ModeIntercept:
		return ModeIntercept, nil
	default:
		return "", fmt.Errorf("invalid mode: %q (valid: passthrough, record, replay, intercept)", s)
	}
}

// MockResponse defines the response to return when an intercept rule matches.
type MockResponse struct {
	StatusCode int               `json:"status_code"`
	Headers    map[string]string `json:"headers,omitempty"`
	Body       string            `json:"body"`
	BodyBase64 string            `json:"body_base64,omitempty"` // Base64-encoded binary body (for gRPC protobuf responses)
	LatencyMs  int64             `json:"latency_ms,omitempty"`  // Replay delay in milliseconds (simulate recorded latency)
}

// InterceptRule defines a rule for matching requests and returning mock responses.
type InterceptRule struct {
	ID           string       `json:"id"`
	PathMatch    string       `json:"path_match"`              // exact or prefix match
	Method       string       `json:"method"`                  // HTTP method or "*" for any
	BaggageMatch string       `json:"baggage_match,omitempty"` // if set, only match when baggage header contains this token
	Response     MockResponse `json:"response"`
}

// Stats holds counters for proxy activity.
type Stats struct {
	Mode               Mode  `json:"mode"`
	SnapshotCount      int   `json:"snapshot_count"`
	RuleCount          int   `json:"rule_count"`
	RequestsTotal      int64 `json:"requests_total"`
	RequestsRecorded   int64 `json:"requests_recorded"`
	RequestsReplayed   int64 `json:"requests_replayed"`
	RequestsIntercepted int64 `json:"requests_intercepted"`
}

// Engine is the core component that manages proxy mode, intercept rules,
// snapshot storage, and request counters. It is safe for concurrent use.
type Engine struct {
	mu    sync.RWMutex
	mode  Mode
	rules []InterceptRule
	store SnapshotStore

	// Recording filters
	baggageFilter string // if set, only record requests containing this baggage token
	maxSnapshots  int    // max snapshots to store (0 = unlimited)

	// Atomic counters for stats (no lock needed).
	requestsTotal       atomic.Int64
	requestsRecorded    atomic.Int64
	requestsReplayed    atomic.Int64
	requestsIntercepted atomic.Int64
	requestsSkipped     atomic.Int64 // skipped due to filter/limit

	ruleCounter int // monotonically increasing ID for rules
}

// New creates a new Engine with the given initial mode and snapshot store.
func New(initialMode Mode, store SnapshotStore) *Engine {
	return &Engine{
		mode:  initialMode,
		store: store,
	}
}

// SetRecordingFilter sets the baggage filter and max snapshots for recording mode.
func (e *Engine) SetRecordingFilter(baggageFilter string, maxSnapshots int) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.baggageFilter = baggageFilter
	e.maxSnapshots = maxSnapshots
	slog.Info("recording filter updated", "baggageFilter", baggageFilter, "maxSnapshots", maxSnapshots)
}

// GetBaggageFilter returns the current baggage filter.
func (e *Engine) GetBaggageFilter() string {
	e.mu.RLock()
	defer e.mu.RUnlock()
	return e.baggageFilter
}

// ShouldRecord checks if a request should be recorded based on baggage filter and snapshot limit.
func (e *Engine) ShouldRecord(baggageHeader string) bool {
	e.mu.RLock()
	defer e.mu.RUnlock()

	// Check max snapshots limit
	if e.maxSnapshots > 0 && e.store.Count() >= e.maxSnapshots {
		return false
	}

	// Check baggage filter
	if e.baggageFilter != "" {
		if baggageHeader == "" || !strings.Contains(baggageHeader, e.baggageFilter) {
			return false
		}
	}

	return true
}

// IncrSkipped increments the skipped requests counter.
func (e *Engine) IncrSkipped() {
	e.requestsSkipped.Add(1)
}

// SetMode changes the current operating mode.
func (e *Engine) SetMode(mode Mode) {
	e.mu.Lock()
	defer e.mu.Unlock()
	slog.Info("mode changed", "from", string(e.mode), "to", string(mode))
	e.mode = mode
}

// GetMode returns the current operating mode.
func (e *Engine) GetMode() Mode {
	e.mu.RLock()
	defer e.mu.RUnlock()
	return e.mode
}

// Record stores a request/response snapshot and increments counters.
func (e *Engine) Record(sig Signature, req CapturedRequest, resp CapturedResponse) error {
	snap := &Snapshot{
		ID:         fmt.Sprintf("snap-%s-%d", sig.Hash[:12], time.Now().UnixMilli()),
		Signature:  sig,
		Request:    req,
		Response:   resp,
		RecordedAt: time.Now().UTC(),
	}

	if err := e.store.Save(snap); err != nil {
		return fmt.Errorf("save snapshot: %w", err)
	}

	e.requestsRecorded.Add(1)
	slog.Debug("recorded snapshot",
		"id", snap.ID,
		"method", sig.Method,
		"path", sig.Path,
	)
	return nil
}

// Replay looks up a snapshot by signature hash. Returns the snapshot and true
// if found, nil and false otherwise. Increments the replay counter on hit.
func (e *Engine) Replay(sig Signature) (*Snapshot, bool) {
	snap, found := e.store.Lookup(sig.Hash)
	if found {
		e.requestsReplayed.Add(1)
		slog.Debug("replayed snapshot",
			"id", snap.ID,
			"method", sig.Method,
			"path", sig.Path,
		)
	}
	return snap, found
}

// MatchRule checks if any intercept rule matches the given method, path, and
// baggage header. Rules are checked in order; the first match wins.
// When a rule has a non-empty BaggageMatch, the rule only triggers if the
// baggage header contains that exact token (comma-separated).
func (e *Engine) MatchRule(method, path, baggageHeader string) (*InterceptRule, bool) {
	e.mu.RLock()
	defer e.mu.RUnlock()

	for i := range e.rules {
		rule := &e.rules[i]

		// Check method match: "*" matches any method.
		if rule.Method != "*" && !strings.EqualFold(rule.Method, method) {
			continue
		}

		// Check path match: exact match or prefix match.
		if path != rule.PathMatch && !strings.HasPrefix(path, rule.PathMatch) {
			continue
		}

		// Check baggage match (if specified).
		if rule.BaggageMatch != "" && !containsBaggageToken(baggageHeader, rule.BaggageMatch) {
			continue
		}

		e.requestsIntercepted.Add(1)
		slog.Debug("intercepted request",
			"rule_id", rule.ID,
			"method", method,
			"path", path,
			"baggage_match", rule.BaggageMatch,
		)
		return rule, true
	}

	return nil, false
}

// containsBaggageToken checks if a comma-separated baggage header contains a
// specific token (exact match per segment after trimming whitespace).
func containsBaggageToken(header, token string) bool {
	if header == "" {
		return false
	}
	for _, t := range strings.Split(header, ",") {
		if strings.TrimSpace(t) == token {
			return true
		}
	}
	return false
}

// AddRule adds an intercept rule. If the rule has no ID, one is generated.
func (e *Engine) AddRule(rule InterceptRule) InterceptRule {
	e.mu.Lock()
	defer e.mu.Unlock()

	if rule.ID == "" {
		e.ruleCounter++
		rule.ID = fmt.Sprintf("rule-%d", e.ruleCounter)
	}

	e.rules = append(e.rules, rule)
	slog.Info("added intercept rule",
		"id", rule.ID,
		"path_match", rule.PathMatch,
		"method", rule.Method,
	)
	return rule
}

// RemoveRule removes an intercept rule by ID.
func (e *Engine) RemoveRule(id string) {
	e.mu.Lock()
	defer e.mu.Unlock()

	for i, rule := range e.rules {
		if rule.ID == id {
			e.rules = append(e.rules[:i], e.rules[i+1:]...)
			slog.Info("removed intercept rule", "id", id)
			return
		}
	}
}

// GetRules returns a copy of all intercept rules.
func (e *Engine) GetRules() []InterceptRule {
	e.mu.RLock()
	defer e.mu.RUnlock()

	result := make([]InterceptRule, len(e.rules))
	copy(result, e.rules)
	return result
}

// GetSnapshots returns all stored snapshots.
func (e *Engine) GetSnapshots() []*Snapshot {
	return e.store.All()
}

// GetStats returns current statistics.
func (e *Engine) GetStats() Stats {
	return Stats{
		Mode:                e.GetMode(),
		SnapshotCount:       e.store.Count(),
		RuleCount:           len(e.GetRules()),
		RequestsTotal:       e.requestsTotal.Load(),
		RequestsRecorded:    e.requestsRecorded.Load(),
		RequestsReplayed:    e.requestsReplayed.Load(),
		RequestsIntercepted: e.requestsIntercepted.Load(),
	}
}

// IncrementTotal increments the total request counter. Called by the HTTP
// handler for every incoming request.
func (e *Engine) IncrementTotal() {
	e.requestsTotal.Add(1)
}

// Clear removes all snapshots and intercept rules, and resets counters.
func (e *Engine) Clear() {
	e.mu.Lock()
	defer e.mu.Unlock()

	e.store.Clear()
	e.rules = nil
	e.requestsTotal.Store(0)
	e.requestsRecorded.Store(0)
	e.requestsReplayed.Store(0)
	e.requestsIntercepted.Store(0)
	slog.Info("engine cleared: all snapshots, rules, and counters reset")
}
