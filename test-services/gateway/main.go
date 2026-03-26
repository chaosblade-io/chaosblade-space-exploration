// Package main implements gateway, an HTTP reverse-proxy that receives
// external requests and forwards them to order-svc over HTTP.
package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

// orderSvcAddr is the base URL of the order-svc.
var orderSvcAddr string

// httpClient is shared across requests with a reasonable timeout.
var httpClient = &http.Client{
	Timeout: 15 * time.Second,
}

// createOrder forwards POST /api/orders to order-svc.
func createOrder(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}

	targetURL := fmt.Sprintf("http://%s/api/orders", orderSvcAddr)
	log.Printf("gateway: forwarding POST /api/orders → %s", targetURL)

	// Forward the request body to order-svc.
	resp, err := httpClient.Post(targetURL, "application/json", r.Body)
	if err != nil {
		log.Printf("gateway: failed to call order-svc: %v", err)
		writeJSON(w, http.StatusBadGateway, map[string]string{
			"error": fmt.Sprintf("failed to reach order-svc: %v", err),
		})
		return
	}
	defer resp.Body.Close()

	// Relay the response from order-svc back to the client.
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(resp.StatusCode)
	if _, err := io.Copy(w, resp.Body); err != nil {
		log.Printf("gateway: failed to relay response: %v", err)
	}
}

// getOrder forwards GET /api/orders/{id} to order-svc.
func getOrder(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}

	// Extract order ID from path.
	orderID := strings.TrimPrefix(r.URL.Path, "/api/orders/")
	if orderID == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "order id is required"})
		return
	}

	targetURL := fmt.Sprintf("http://%s/api/orders/%s", orderSvcAddr, orderID)
	log.Printf("gateway: forwarding GET /api/orders/%s → %s", orderID, targetURL)

	resp, err := httpClient.Get(targetURL)
	if err != nil {
		log.Printf("gateway: failed to call order-svc: %v", err)
		writeJSON(w, http.StatusBadGateway, map[string]string{
			"error": fmt.Sprintf("failed to reach order-svc: %v", err),
		})
		return
	}
	defer resp.Body.Close()

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(resp.StatusCode)
	if _, err := io.Copy(w, resp.Body); err != nil {
		log.Printf("gateway: failed to relay response: %v", err)
	}
}

// health handles GET /health.
func health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// apiOrders routes /api/orders and /api/orders/{id}.
func apiOrders(w http.ResponseWriter, r *http.Request) {
	// /api/orders (exact) → create order
	// /api/orders/{id}   → get order
	path := strings.TrimPrefix(r.URL.Path, "/api/orders")
	if path == "" || path == "/" {
		if r.Method == http.MethodPost {
			createOrder(w, r)
			return
		}
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	// path starts with "/" and has an ID after it
	getOrder(w, r)
}

// writeJSON is a helper to write a JSON response.
func writeJSON(w http.ResponseWriter, statusCode int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	if err := json.NewEncoder(w).Encode(data); err != nil {
		log.Printf("gateway: failed to write response: %v", err)
	}
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	orderSvcAddr = os.Getenv("ORDER_SVC_ADDR")
	if orderSvcAddr == "" {
		orderSvcAddr = "order-svc:8081"
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/health", health)
	mux.HandleFunc("/api/orders", apiOrders)
	mux.HandleFunc("/api/orders/", apiOrders)

	log.Printf("gateway: listening on :%s, order-svc at %s", port, orderSvcAddr)
	if err := http.ListenAndServe(fmt.Sprintf(":%s", port), mux); err != nil {
		log.Fatalf("gateway: server error: %v", err)
	}
}
