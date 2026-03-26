// Package main implements order-svc, an HTTP service that handles order
// creation and retrieval.  It calls inventory-svc via gRPC to check stock
// and deduct inventory.
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	invpb "test-services/gen/inventorypb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// Order represents an order stored in memory.
type Order struct {
	ID        string    `json:"id"`
	ProductID string    `json:"product_id"`
	Quantity  int32     `json:"quantity"`
	Status    string    `json:"status"`
	Message   string    `json:"message,omitempty"`
	CreatedAt time.Time `json:"created_at"`
}

// CreateOrderRequest is the JSON body for POST /api/orders.
type CreateOrderRequest struct {
	ProductID string `json:"product_id"`
	Quantity  int32  `json:"quantity"`
}

// orderStore holds orders in memory.
type orderStore struct {
	mu     sync.RWMutex
	orders map[string]*Order
	nextID atomic.Int64
}

func newOrderStore() *orderStore {
	return &orderStore{
		orders: make(map[string]*Order),
	}
}

func (s *orderStore) save(order *Order) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.orders[order.ID] = order
}

func (s *orderStore) get(id string) (*Order, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	o, ok := s.orders[id]
	return o, ok
}

func (s *orderStore) generateID() string {
	n := s.nextID.Add(1)
	return fmt.Sprintf("order-%03d", n)
}

// handler holds dependencies for HTTP handlers.
type handler struct {
	store    *orderStore
	invConn  *grpc.ClientConn
	invCli   invpb.InventoryServiceClient
}

// createOrder handles POST /api/orders.
func (h *handler) createOrder(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, `{"error":"method not allowed"}`, http.StatusMethodNotAllowed)
		return
	}

	var req CreateOrderRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid request body"})
		return
	}

	if req.ProductID == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "product_id is required"})
		return
	}
	if req.Quantity <= 0 {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "quantity must be positive"})
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()

	// Step 1: Check stock via inventory-svc.
	log.Printf("order-svc: checking stock for product_id=%s", req.ProductID)
	checkResp, err := h.invCli.CheckStock(ctx, &invpb.CheckStockRequest{
		ProductId: req.ProductID,
	})
	if err != nil {
		log.Printf("order-svc: CheckStock error: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{
			"error": fmt.Sprintf("failed to check stock: %v", err),
		})
		return
	}

	if !checkResp.GetAvailable() {
		order := &Order{
			ID:        h.store.generateID(),
			ProductID: req.ProductID,
			Quantity:  req.Quantity,
			Status:    "rejected",
			Message:   fmt.Sprintf("product %s is out of stock (available: %d)", req.ProductID, checkResp.GetQuantity()),
			CreatedAt: time.Now(),
		}
		h.store.save(order)
		log.Printf("order-svc: order %s rejected — out of stock", order.ID)
		writeJSON(w, http.StatusConflict, order)
		return
	}

	if checkResp.GetQuantity() < req.Quantity {
		order := &Order{
			ID:        h.store.generateID(),
			ProductID: req.ProductID,
			Quantity:  req.Quantity,
			Status:    "rejected",
			Message:   fmt.Sprintf("insufficient stock for %s (available: %d, requested: %d)", req.ProductID, checkResp.GetQuantity(), req.Quantity),
			CreatedAt: time.Now(),
		}
		h.store.save(order)
		log.Printf("order-svc: order %s rejected — insufficient stock", order.ID)
		writeJSON(w, http.StatusConflict, order)
		return
	}

	// Step 2: Deduct inventory via inventory-svc.
	log.Printf("order-svc: deducting %d units of product_id=%s", req.Quantity, req.ProductID)
	deductResp, err := h.invCli.Deduct(ctx, &invpb.DeductRequest{
		ProductId: req.ProductID,
		Quantity:  req.Quantity,
	})
	if err != nil {
		log.Printf("order-svc: Deduct error: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{
			"error": fmt.Sprintf("failed to deduct inventory: %v", err),
		})
		return
	}

	if !deductResp.GetSuccess() {
		order := &Order{
			ID:        h.store.generateID(),
			ProductID: req.ProductID,
			Quantity:  req.Quantity,
			Status:    "rejected",
			Message:   "deduction failed at inventory service",
			CreatedAt: time.Now(),
		}
		h.store.save(order)
		writeJSON(w, http.StatusConflict, order)
		return
	}

	// Success — create the order.
	order := &Order{
		ID:        h.store.generateID(),
		ProductID: req.ProductID,
		Quantity:  req.Quantity,
		Status:    "confirmed",
		Message:   fmt.Sprintf("order confirmed, remaining stock: %d", deductResp.GetRemaining()),
		CreatedAt: time.Now(),
	}
	h.store.save(order)

	log.Printf("order-svc: order %s confirmed for %s qty=%d", order.ID, req.ProductID, req.Quantity)
	writeJSON(w, http.StatusCreated, order)
}

// getOrder handles GET /api/orders/{id}.
func (h *handler) getOrder(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, `{"error":"method not allowed"}`, http.StatusMethodNotAllowed)
		return
	}

	// Extract order ID from the URL path: /api/orders/{id}
	path := strings.TrimPrefix(r.URL.Path, "/api/orders/")
	orderID := strings.TrimSpace(path)

	if orderID == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "order id is required"})
		return
	}

	order, found := h.store.get(orderID)
	if !found {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "order not found"})
		return
	}

	writeJSON(w, http.StatusOK, order)
}

// health handles GET /health.
func health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// writeJSON is a helper to write a JSON response.
func writeJSON(w http.ResponseWriter, statusCode int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	if err := json.NewEncoder(w).Encode(data); err != nil {
		log.Printf("order-svc: failed to write response: %v", err)
	}
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8081"
	}

	inventorySvcAddr := os.Getenv("INVENTORY_SVC_ADDR")
	if inventorySvcAddr == "" {
		inventorySvcAddr = "inventory-svc:8082"
	}

	// Connect to inventory-svc via gRPC.
	invConn, err := grpc.NewClient(inventorySvcAddr,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		log.Fatalf("order-svc: failed to connect to inventory-svc at %s: %v", inventorySvcAddr, err)
	}
	defer invConn.Close()

	invClient := invpb.NewInventoryServiceClient(invConn)

	h := &handler{
		store:   newOrderStore(),
		invConn: invConn,
		invCli:  invClient,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/health", health)
	mux.HandleFunc("/api/orders", h.createOrder)
	mux.HandleFunc("/api/orders/", h.getOrder)

	log.Printf("order-svc: listening on :%s, inventory-svc at %s", port, inventorySvcAddr)
	if err := http.ListenAndServe(fmt.Sprintf(":%s", port), mux); err != nil {
		log.Fatalf("order-svc: server error: %v", err)
	}
}
