package engine

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// CapturedRequest holds the significant parts of an HTTP request for storage.
type CapturedRequest struct {
	Method  string            `json:"method"`
	Path    string            `json:"path"`
	Headers map[string]string `json:"headers"`
	Body    []byte            `json:"body,omitempty"`
}

// CapturedResponse holds the significant parts of an HTTP response for storage.
type CapturedResponse struct {
	StatusCode int               `json:"status_code"`
	Headers    map[string]string `json:"headers"`
	Body       []byte            `json:"body,omitempty"`
	LatencyMs  int64             `json:"latency_ms"`
}

// Snapshot represents a recorded request/response pair, identified by its
// computed signature so it can be looked up during replay.
type Snapshot struct {
	ID         string           `json:"id"`
	Signature  Signature        `json:"signature"`
	FaultID    string           `json:"fault_id,omitempty"`
	Request    CapturedRequest  `json:"request"`
	Response   CapturedResponse `json:"response"`
	RecordedAt time.Time        `json:"recorded_at"`
}

// SnapshotStore defines the interface for snapshot persistence.
type SnapshotStore interface {
	// Save stores a snapshot, keyed by its signature hash.
	Save(snap *Snapshot) error

	// Lookup retrieves a snapshot by signature hash. Returns nil and false if not found.
	Lookup(hash string) (*Snapshot, bool)

	// All returns every stored snapshot.
	All() []*Snapshot

	// Clear removes all stored snapshots.
	Clear()

	// Count returns the number of stored snapshots.
	Count() int
}

// InMemoryStore is a thread-safe, in-memory SnapshotStore with optional
// file persistence. Snapshots are always kept in memory for fast lookup;
// when a snapshotDir is configured, each snapshot is also written to disk
// as a JSON file for durability across restarts.
type InMemoryStore struct {
	mu          sync.RWMutex
	byHash      map[string]*Snapshot // keyed by signature hash
	ordered     []*Snapshot          // insertion order
	snapshotDir string               // empty string disables file persistence
}

// NewInMemoryStore creates a new InMemoryStore. If snapshotDir is non-empty,
// snapshots will also be persisted to that directory.
func NewInMemoryStore(snapshotDir string) *InMemoryStore {
	store := &InMemoryStore{
		byHash:      make(map[string]*Snapshot),
		snapshotDir: snapshotDir,
	}

	// Load any previously persisted snapshots from disk.
	if snapshotDir != "" {
		store.loadFromDisk()
	}

	return store
}

// Save stores a snapshot in memory and optionally persists it to disk.
func (s *InMemoryStore) Save(snap *Snapshot) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	hash := snap.Signature.Hash

	// If a snapshot with the same hash already exists, replace it.
	if existing, ok := s.byHash[hash]; ok {
		*existing = *snap
	} else {
		s.byHash[hash] = snap
		s.ordered = append(s.ordered, snap)
	}

	// Persist to disk if configured.
	if s.snapshotDir != "" {
		if err := s.writeToDisk(snap); err != nil {
			slog.Error("failed to persist snapshot to disk",
				"id", snap.ID,
				"error", err,
			)
			return fmt.Errorf("persist snapshot: %w", err)
		}
	}

	return nil
}

// Lookup finds a snapshot by its signature hash.
func (s *InMemoryStore) Lookup(hash string) (*Snapshot, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	snap, ok := s.byHash[hash]
	return snap, ok
}

// All returns all stored snapshots in insertion order.
func (s *InMemoryStore) All() []*Snapshot {
	s.mu.RLock()
	defer s.mu.RUnlock()

	result := make([]*Snapshot, len(s.ordered))
	copy(result, s.ordered)
	return result
}

// Clear removes all snapshots from memory (does not delete files from disk).
func (s *InMemoryStore) Clear() {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.byHash = make(map[string]*Snapshot)
	s.ordered = nil
}

// Count returns the number of stored snapshots.
func (s *InMemoryStore) Count() int {
	s.mu.RLock()
	defer s.mu.RUnlock()

	return len(s.ordered)
}

// writeToDisk persists a single snapshot as a JSON file. Must be called
// while the lock is held.
func (s *InMemoryStore) writeToDisk(snap *Snapshot) error {
	if err := os.MkdirAll(s.snapshotDir, 0o755); err != nil {
		return fmt.Errorf("create snapshot dir: %w", err)
	}

	filename := filepath.Join(s.snapshotDir, snap.ID+".json")
	data, err := json.MarshalIndent(snap, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal snapshot: %w", err)
	}

	if err := os.WriteFile(filename, data, 0o644); err != nil {
		return fmt.Errorf("write snapshot file: %w", err)
	}

	return nil
}

// loadFromDisk reads all JSON snapshot files from the snapshot directory
// into memory. Called once during store initialization.
func (s *InMemoryStore) loadFromDisk() {
	entries, err := os.ReadDir(s.snapshotDir)
	if err != nil {
		// Directory may not exist yet; that's fine.
		slog.Info("no existing snapshots to load",
			"dir", s.snapshotDir,
			"reason", err.Error(),
		)
		return
	}

	loaded := 0
	for _, entry := range entries {
		if entry.IsDir() || filepath.Ext(entry.Name()) != ".json" {
			continue
		}

		data, err := os.ReadFile(filepath.Join(s.snapshotDir, entry.Name()))
		if err != nil {
			slog.Warn("failed to read snapshot file",
				"file", entry.Name(),
				"error", err,
			)
			continue
		}

		var snap Snapshot
		if err := json.Unmarshal(data, &snap); err != nil {
			slog.Warn("failed to parse snapshot file",
				"file", entry.Name(),
				"error", err,
			)
			continue
		}

		s.byHash[snap.Signature.Hash] = &snap
		s.ordered = append(s.ordered, &snap)
		loaded++
	}

	if loaded > 0 {
		slog.Info("loaded snapshots from disk",
			"count", loaded,
			"dir", s.snapshotDir,
		)
	}
}
