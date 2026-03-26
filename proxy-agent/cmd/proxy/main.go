package main

import (
	"context"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"proxy-agent/internal/config"
	"proxy-agent/internal/control"
	"proxy-agent/internal/engine"
	grpcproxy "proxy-agent/internal/grpc"
	proxyhttp "proxy-agent/internal/http"

	"github.com/soheilhy/cmux"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})))

	cfg, err := config.Load()
	if err != nil {
		slog.Error("failed to load configuration", "error", err)
		os.Exit(1)
	}

	initialMode, err := engine.ParseMode(cfg.InitialMode)
	if err != nil {
		slog.Error("invalid initial mode", "error", err)
		os.Exit(1)
	}

	store := engine.NewInMemoryStore(cfg.SnapshotDir)
	eng := engine.New(initialMode, store)

	// Create the HTTP proxy handler.
	proxyHandler, err := proxyhttp.NewProxyHandler(cfg.ProxyTarget, eng)
	if err != nil {
		slog.Error("failed to create proxy handler", "error", err)
		os.Exit(1)
	}

	// Create the control API server (always HTTP, separate port).
	controlSrv := control.NewServer(eng)
	controlHTTP := &http.Server{
		Addr:         ":" + cfg.ControlPort,
		Handler:      controlSrv.Handler(),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
	}

	go func() {
		slog.Info("control API server starting", "port", cfg.ControlPort)
		if err := controlHTTP.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("control API server error", "error", err)
			os.Exit(1)
		}
	}()

	// Graceful shutdown setup.
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	if cfg.GRPCTarget != "" {
		// gRPC mode: use cmux to multiplex HTTP and gRPC on the same port.
		startWithCmux(ctx, cfg, eng, proxyHandler)
	} else {
		// HTTP-only mode: simple http.Server (backward compatible).
		startHTTPOnly(ctx, cfg, eng, proxyHandler)
	}

	slog.Info("shutting down servers...")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := controlHTTP.Shutdown(shutdownCtx); err != nil {
		slog.Error("control API shutdown error", "error", err)
	}
	slog.Info("servers stopped")
}

// startHTTPOnly runs the proxy as a plain HTTP server (Phase 1 behavior).
func startHTTPOnly(ctx context.Context, cfg *config.Config, eng *engine.Engine, handler http.Handler) {
	proxyHTTP := &http.Server{
		Addr:         ":" + cfg.ProxyPort,
		Handler:      handler,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
	}

	go func() {
		slog.Info("proxy server starting (HTTP only)",
			"port", cfg.ProxyPort,
			"target", cfg.ProxyTarget,
			"mode", cfg.InitialMode,
		)
		if err := proxyHTTP.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("proxy server error", "error", err)
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := proxyHTTP.Shutdown(shutdownCtx); err != nil {
		slog.Error("proxy server shutdown error", "error", err)
	}
}

// startWithCmux runs the proxy with cmux, multiplexing HTTP/1.1 and gRPC
// (HTTP/2) on the same port.
func startWithCmux(ctx context.Context, cfg *config.Config, eng *engine.Engine, httpHandler http.Handler) {
	lis, err := net.Listen("tcp", ":"+cfg.ProxyPort)
	if err != nil {
		slog.Error("failed to listen", "port", cfg.ProxyPort, "error", err)
		os.Exit(1)
	}

	m := cmux.New(lis)

	// gRPC matcher: HTTP/2 with content-type "application/grpc".
	grpcL := m.MatchWithWriters(cmux.HTTP2MatchHeaderFieldSendSettings("content-type", "application/grpc"))
	// Everything else goes to HTTP.
	httpL := m.Match(cmux.Any())

	// Start gRPC proxy server.
	grpcServer := grpcproxy.NewServer(eng, cfg.GRPCTarget)
	go func() {
		slog.Info("gRPC proxy starting",
			"port", cfg.ProxyPort,
			"target", cfg.GRPCTarget,
		)
		if err := grpcServer.Serve(grpcL); err != nil {
			slog.Error("gRPC server error", "error", err)
		}
	}()

	// Start HTTP proxy server.
	httpServer := &http.Server{
		Handler:      httpHandler,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
	}
	go func() {
		slog.Info("HTTP proxy starting (cmux)",
			"port", cfg.ProxyPort,
			"http_target", cfg.ProxyTarget,
			"grpc_target", cfg.GRPCTarget,
			"mode", cfg.InitialMode,
		)
		if err := httpServer.Serve(httpL); err != nil && err != http.ErrServerClosed {
			slog.Error("HTTP server error", "error", err)
		}
	}()

	// Start cmux multiplexer.
	go func() {
		if err := m.Serve(); err != nil {
			// cmux.Serve returns error when listener is closed, which is expected.
			slog.Debug("cmux serve ended", "error", err)
		}
	}()

	<-ctx.Done()

	// Graceful shutdown.
	grpcServer.GracefulStop()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		slog.Error("HTTP server shutdown error", "error", err)
	}
	lis.Close()
}
