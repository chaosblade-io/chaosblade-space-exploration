// Package main implements inventory-svc, a gRPC service for inventory
// management.  It delegates storage to db-svc via gRPC.
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"os"

	dbpb "test-services/gen/dbpb"
	pb "test-services/gen/inventorypb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/reflection"
	"google.golang.org/grpc/status"
)

// productRecord mirrors the JSON structure stored in db-svc.
type productRecord struct {
	Name  string  `json:"name"`
	Stock int32   `json:"stock"`
	Price float64 `json:"price"`
}

// server implements the InventoryServiceServer interface.
type server struct {
	pb.UnimplementedInventoryServiceServer
	dbClient dbpb.DBServiceClient
}

// CheckStock looks up a product in db-svc and reports its current stock level.
func (s *server) CheckStock(ctx context.Context, req *pb.CheckStockRequest) (*pb.CheckStockResponse, error) {
	log.Printf("inventory-svc: CheckStock product_id=%s", req.GetProductId())

	// Fetch the product record from db-svc.
	getResp, err := s.dbClient.Get(ctx, &dbpb.GetRequest{Key: req.GetProductId()})
	if err != nil {
		log.Printf("inventory-svc: db-svc Get error: %v", err)
		return nil, status.Errorf(codes.Internal, "failed to query db-svc: %v", err)
	}

	if !getResp.GetFound() {
		log.Printf("inventory-svc: product %s not found", req.GetProductId())
		return &pb.CheckStockResponse{
			ProductId: req.GetProductId(),
			Quantity:  0,
			Available: false,
		}, nil
	}

	var product productRecord
	if err := json.Unmarshal([]byte(getResp.GetValue()), &product); err != nil {
		log.Printf("inventory-svc: failed to parse product JSON: %v", err)
		return nil, status.Errorf(codes.Internal, "failed to parse product data: %v", err)
	}

	return &pb.CheckStockResponse{
		ProductId: req.GetProductId(),
		Quantity:  product.Stock,
		Available: product.Stock > 0,
	}, nil
}

// Deduct reduces the stock of a product by the requested quantity.
func (s *server) Deduct(ctx context.Context, req *pb.DeductRequest) (*pb.DeductResponse, error) {
	log.Printf("inventory-svc: Deduct product_id=%s quantity=%d", req.GetProductId(), req.GetQuantity())

	// Fetch current stock from db-svc.
	getResp, err := s.dbClient.Get(ctx, &dbpb.GetRequest{Key: req.GetProductId()})
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to query db-svc: %v", err)
	}

	if !getResp.GetFound() {
		return &pb.DeductResponse{Success: false, Remaining: 0}, nil
	}

	var product productRecord
	if err := json.Unmarshal([]byte(getResp.GetValue()), &product); err != nil {
		return nil, status.Errorf(codes.Internal, "failed to parse product data: %v", err)
	}

	// Check if we have enough stock.
	if product.Stock < req.GetQuantity() {
		log.Printf("inventory-svc: insufficient stock for %s (have %d, need %d)",
			req.GetProductId(), product.Stock, req.GetQuantity())
		return &pb.DeductResponse{
			Success:   false,
			Remaining: product.Stock,
		}, nil
	}

	// Deduct and save back.
	product.Stock -= req.GetQuantity()
	updatedJSON, err := json.Marshal(product)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to marshal product: %v", err)
	}

	_, err = s.dbClient.Set(ctx, &dbpb.SetRequest{
		Key:   req.GetProductId(),
		Value: string(updatedJSON),
	})
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update db-svc: %v", err)
	}

	log.Printf("inventory-svc: deducted %d from %s, remaining=%d",
		req.GetQuantity(), req.GetProductId(), product.Stock)

	return &pb.DeductResponse{
		Success:   true,
		Remaining: product.Stock,
	}, nil
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8082"
	}

	dbSvcAddr := os.Getenv("DB_SVC_ADDR")
	if dbSvcAddr == "" {
		dbSvcAddr = "db-svc:8083"
	}

	// Connect to db-svc.
	dbConn, err := grpc.NewClient(dbSvcAddr,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		log.Fatalf("inventory-svc: failed to connect to db-svc at %s: %v", dbSvcAddr, err)
	}
	defer dbConn.Close()

	dbClient := dbpb.NewDBServiceClient(dbConn)

	listener, err := net.Listen("tcp", fmt.Sprintf(":%s", port))
	if err != nil {
		log.Fatalf("inventory-svc: failed to listen on port %s: %v", port, err)
	}

	grpcServer := grpc.NewServer()
	pb.RegisterInventoryServiceServer(grpcServer, &server{dbClient: dbClient})
	reflection.Register(grpcServer)

	log.Printf("inventory-svc: listening on :%s, db-svc at %s", port, dbSvcAddr)
	if err := grpcServer.Serve(listener); err != nil {
		log.Fatalf("inventory-svc: failed to serve: %v", err)
	}
}
