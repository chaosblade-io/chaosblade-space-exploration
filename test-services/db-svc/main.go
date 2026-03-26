// Package main implements db-svc, a gRPC service that simulates a key-value
// database backed by an in-memory map.  It is pre-populated with sample
// product inventory data.
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"os"
	"sync"

	pb "test-services/gen/dbpb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"
)

// Product represents a product record stored as a JSON value.
type Product struct {
	Name  string  `json:"name"`
	Stock int     `json:"stock"`
	Price float64 `json:"price"`
}

// server implements the DBServiceServer interface.
type server struct {
	pb.UnimplementedDBServiceServer

	mu   sync.RWMutex
	data map[string]string // key → JSON-encoded value
}

// newServer creates a server pre-populated with sample product data.
func newServer() *server {
	s := &server{
		data: make(map[string]string),
	}

	// Pre-populate sample products.
	products := map[string]Product{
		"product-001": {Name: "Laptop", Stock: 100, Price: 999.99},
		"product-002": {Name: "Phone", Stock: 50, Price: 599.99},
		"product-003": {Name: "Tablet", Stock: 0, Price: 399.99},
	}

	for key, prod := range products {
		val, err := json.Marshal(prod)
		if err != nil {
			log.Fatalf("failed to marshal product %s: %v", key, err)
		}
		s.data[key] = string(val)
	}

	log.Printf("db-svc: pre-populated %d products", len(products))
	return s
}

// Get retrieves a value by key from the in-memory store.
func (s *server) Get(ctx context.Context, req *pb.GetRequest) (*pb.GetResponse, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	value, found := s.data[req.GetKey()]
	log.Printf("db-svc: Get key=%q found=%v", req.GetKey(), found)
	return &pb.GetResponse{
		Value: value,
		Found: found,
	}, nil
}

// Set stores a value by key in the in-memory store.
func (s *server) Set(ctx context.Context, req *pb.SetRequest) (*pb.SetResponse, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.data[req.GetKey()] = req.GetValue()
	log.Printf("db-svc: Set key=%q value=%s", req.GetKey(), req.GetValue())
	return &pb.SetResponse{Success: true}, nil
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8083"
	}

	listener, err := net.Listen("tcp", fmt.Sprintf(":%s", port))
	if err != nil {
		log.Fatalf("db-svc: failed to listen on port %s: %v", port, err)
	}

	grpcServer := grpc.NewServer()
	pb.RegisterDBServiceServer(grpcServer, newServer())
	reflection.Register(grpcServer)

	log.Printf("db-svc: listening on :%s", port)
	if err := grpcServer.Serve(listener); err != nil {
		log.Fatalf("db-svc: failed to serve: %v", err)
	}
}
