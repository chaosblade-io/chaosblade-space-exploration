package control

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strings"

	"proxy-agent/internal/engine"
)

// Server is the control API server that exposes endpoints for managing the
// proxy engine (mode switching, snapshot inspection, intercept rules, etc.).
type Server struct {
	engine *engine.Engine
	mux    *http.ServeMux
}

// NewServer creates a new control API server backed by the given engine.
func NewServer(eng *engine.Engine) *Server {
	s := &Server{
		engine: eng,
		mux:    http.NewServeMux(),
	}
	s.registerRoutes()
	return s
}

// Handler returns the http.Handler for this control server.
func (s *Server) Handler() http.Handler {
	return s.mux
}

// registerRoutes sets up all control API endpoints.
func (s *Server) registerRoutes() {
	s.mux.HandleFunc("/control/health", s.handleHealth)
	s.mux.HandleFunc("/control/mode", s.handleMode)
	s.mux.HandleFunc("/control/snapshots", s.handleSnapshots)
	s.mux.HandleFunc("/control/rules", s.handleRules)
	s.mux.HandleFunc("/control/rules/", s.handleRuleByID)
	s.mux.HandleFunc("/control/stats", s.handleStats)
}

// handleHealth returns the proxy health status and current mode.
// GET /control/health
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{
		"status": "ok",
		"mode":   string(s.engine.GetMode()),
	})
}

// handleMode gets or sets the current proxy mode.
// GET  /control/mode  — returns current mode
// PUT  /control/mode  — switches mode
func (s *Server) handleMode(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		writeJSON(w, http.StatusOK, map[string]string{
			"mode": string(s.engine.GetMode()),
		})

	case http.MethodPut:
		var body struct {
			Mode string `json:"mode"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{
				"error": "invalid JSON: " + err.Error(),
			})
			return
		}

		newMode, err := engine.ParseMode(body.Mode)
		if err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{
				"error": err.Error(),
			})
			return
		}

		previous := s.engine.GetMode()
		s.engine.SetMode(newMode)

		writeJSON(w, http.StatusOK, map[string]string{
			"mode":     string(newMode),
			"previous": string(previous),
		})

	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

// handleSnapshots lists or clears all snapshots.
// GET    /control/snapshots  — returns all snapshots
// DELETE /control/snapshots  — clears all snapshots
func (s *Server) handleSnapshots(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		snapshots := s.engine.GetSnapshots()
		if snapshots == nil {
			snapshots = []*engine.Snapshot{}
		}
		writeJSON(w, http.StatusOK, snapshots)

	case http.MethodDelete:
		s.engine.Clear()
		writeJSON(w, http.StatusOK, map[string]string{
			"status": "cleared",
		})

	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

// handleRules lists or adds intercept rules.
// GET  /control/rules  — returns all rules
// POST /control/rules  — adds a new rule
func (s *Server) handleRules(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		rules := s.engine.GetRules()
		if rules == nil {
			rules = []engine.InterceptRule{}
		}
		writeJSON(w, http.StatusOK, rules)

	case http.MethodPost:
		var rule engine.InterceptRule
		if err := json.NewDecoder(r.Body).Decode(&rule); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{
				"error": "invalid JSON: " + err.Error(),
			})
			return
		}

		if rule.PathMatch == "" {
			writeJSON(w, http.StatusBadRequest, map[string]string{
				"error": "path_match is required",
			})
			return
		}
		// gRPC OK = 0, don't override to HTTP 200
		if rule.Response.StatusCode == 0 && !strings.EqualFold(rule.Method, "GRPC") {
			rule.Response.StatusCode = http.StatusOK
		}

		created := s.engine.AddRule(rule)
		writeJSON(w, http.StatusCreated, created)

	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

// handleRuleByID removes an intercept rule by ID.
// DELETE /control/rules/{id}
func (s *Server) handleRuleByID(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Extract the rule ID from the URL path: /control/rules/{id}
	id := strings.TrimPrefix(r.URL.Path, "/control/rules/")
	if id == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{
			"error": "rule ID is required",
		})
		return
	}

	s.engine.RemoveRule(id)
	writeJSON(w, http.StatusOK, map[string]string{
		"status": "removed",
		"id":     id,
	})
}

// handleStats returns proxy statistics.
// GET /control/stats
func (s *Server) handleStats(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	stats := s.engine.GetStats()
	writeJSON(w, http.StatusOK, stats)
}

// writeJSON serializes data as JSON and writes it to the response.
func writeJSON(w http.ResponseWriter, statusCode int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)

	if err := json.NewEncoder(w).Encode(data); err != nil {
		slog.Error("failed to encode JSON response", "error", err)
	}
}
