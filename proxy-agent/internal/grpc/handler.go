package grpcproxy

import (
	"context"
	"encoding/base64"
	"io"
	"log/slog"
	"time"

	"proxy-agent/internal/engine"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

// frame is a raw byte container that satisfies the proto.Message interface
// without actually doing protobuf deserialization. This allows transparent
// proxying of any gRPC service without needing proto definitions.
type frame struct {
	Data []byte
}

func (f *frame) Reset()         { f.Data = nil }
func (f *frame) String() string { return string(f.Data) }
func (f *frame) ProtoMessage()  {}

// rawCodec is a gRPC codec that passes through raw bytes without
// serialization/deserialization.
type rawCodec struct{}

func (rawCodec) Marshal(v interface{}) ([]byte, error) {
	f, ok := v.(*frame)
	if !ok {
		return nil, status.Errorf(codes.Internal, "rawCodec: expected *frame, got %T", v)
	}
	return f.Data, nil
}

func (rawCodec) Unmarshal(data []byte, v interface{}) error {
	f, ok := v.(*frame)
	if !ok {
		return status.Errorf(codes.Internal, "rawCodec: expected *frame, got %T", v)
	}
	f.Data = make([]byte, len(data))
	copy(f.Data, data)
	return nil
}

func (rawCodec) Name() string { return "raw" }

// NewServer creates a gRPC server that transparently proxies all methods
// through the engine's record/replay/intercept/passthrough logic.
func NewServer(eng *engine.Engine, target string) *grpc.Server {
	srv := grpc.NewServer(
		grpc.ForceServerCodec(rawCodec{}),
		grpc.UnknownServiceHandler(makeHandler(eng, target)),
	)
	return srv
}

// makeHandler returns a grpc.StreamHandler that implements the four proxy modes.
func makeHandler(eng *engine.Engine, target string) grpc.StreamHandler {
	return func(srv interface{}, serverStream grpc.ServerStream) error {
		eng.IncrementTotal()

		fullMethod, ok := grpc.MethodFromServerStream(serverStream)
		if !ok {
			return status.Error(codes.Internal, "failed to get method from stream")
		}

		// Extract incoming metadata for signature computation.
		md, _ := metadata.FromIncomingContext(serverStream.Context())
		mdMap := map[string][]string(md)

		// Receive the client request frame (raw bytes).
		reqFrame := &frame{}
		if err := serverStream.RecvMsg(reqFrame); err != nil {
			if err == io.EOF {
				reqFrame.Data = nil
			} else {
				return status.Errorf(codes.Internal, "failed to receive request: %v", err)
			}
		}

		mode := eng.GetMode()

		switch mode {
		case engine.ModePassthrough:
			return forward(target, fullMethod, md, reqFrame, serverStream)

		case engine.ModeRecord:
			return handleRecord(eng, target, fullMethod, md, mdMap, reqFrame, serverStream)

		case engine.ModeReplay:
			return handleReplay(eng, target, fullMethod, md, mdMap, reqFrame, serverStream)

		case engine.ModeIntercept:
			return handleIntercept(eng, target, fullMethod, md, mdMap, reqFrame, serverStream)

		default:
			return forward(target, fullMethod, md, reqFrame, serverStream)
		}
	}
}

// handleRecord forwards the request to the backend, records the response, and
// sends it back to the client.
func handleRecord(eng *engine.Engine, target, method string, md metadata.MD,
	mdMap map[string][]string, reqFrame *frame, serverStream grpc.ServerStream) error {

	sig := engine.ComputeGRPCSignature(method, mdMap, reqFrame.Data)

	capturedReq := engine.CapturedRequest{
		Method:  "GRPC",
		Path:    method,
		Headers: flattenMD(md),
		Body:    reqFrame.Data,
	}

	start := time.Now()
	respFrame, respMD, err := forwardAndCapture(target, method, md, reqFrame)
	latency := time.Since(start)

	if err != nil {
		slog.Error("gRPC forward error during record", "method", method, "error", err)
		return err
	}

	capturedResp := engine.CapturedResponse{
		StatusCode: 0, // gRPC OK
		Headers:    flattenMD(respMD),
		Body:       respFrame.Data,
		LatencyMs:  latency.Milliseconds(),
	}

	if storeErr := eng.Record(sig, capturedReq, capturedResp); storeErr != nil {
		slog.Error("failed to record gRPC snapshot", "error", storeErr)
	}

	// Send trailer metadata and response to client.
	if err := serverStream.SendMsg(respFrame); err != nil {
		return err
	}
	return nil
}

// handleReplay looks up a recorded snapshot and returns it. Falls back to
// passthrough if no matching snapshot is found.
func handleReplay(eng *engine.Engine, target, method string, md metadata.MD,
	mdMap map[string][]string, reqFrame *frame, serverStream grpc.ServerStream) error {

	sig := engine.ComputeGRPCSignature(method, mdMap, reqFrame.Data)

	snap, found := eng.Replay(sig)
	if !found {
		slog.Debug("gRPC replay miss, falling back to passthrough", "method", method)
		return forward(target, method, md, reqFrame, serverStream)
	}

	if snap.Response.LatencyMs > 0 {
		time.Sleep(time.Duration(snap.Response.LatencyMs) * time.Millisecond)
	}

	respFrame := &frame{Data: snap.Response.Body}
	return serverStream.SendMsg(respFrame)
}

// handleIntercept checks intercept rules. If matched, returns a gRPC error.
// Otherwise falls back to passthrough.
func handleIntercept(eng *engine.Engine, target, method string, md metadata.MD,
	mdMap map[string][]string, reqFrame *frame, serverStream grpc.ServerStream) error {

	baggageVal := ""
	if vals, ok := mdMap["baggage"]; ok && len(vals) > 0 {
		baggageVal = vals[0]
	}
	rule, matched := eng.MatchRule("GRPC", method, baggageVal)
	if !matched {
		return forward(target, method, md, reqFrame, serverStream)
	}

	// Simulate recorded latency before returning mock response.
	if rule.Response.LatencyMs > 0 {
		time.Sleep(time.Duration(rule.Response.LatencyMs) * time.Millisecond)
	}

	// Case 1: BodyBase64 is set — decode and return as successful gRPC response (protobuf bytes).
	if rule.Response.BodyBase64 != "" {
		decoded, err := base64.StdEncoding.DecodeString(rule.Response.BodyBase64)
		if err != nil {
			slog.Error("failed to decode body_base64 for gRPC intercept", "error", err)
			return status.Error(codes.Internal, "failed to decode body_base64: "+err.Error())
		}
		slog.Debug("gRPC intercept: returning Base64-decoded protobuf response", "method", method, "bytes", len(decoded))
		return serverStream.SendMsg(&frame{Data: decoded})
	}

	// Case 2: StatusCode == 0 (gRPC OK) — send Body as raw bytes.
	if rule.Response.StatusCode == 0 {
		return serverStream.SendMsg(&frame{Data: []byte(rule.Response.Body)})
	}

	// Case 3: Non-zero StatusCode — return gRPC error.
	grpcCode := codes.Code(rule.Response.StatusCode)
	return status.Error(grpcCode, rule.Response.Body)
}

// forward transparently proxies the request to the backend.
func forward(target, method string, md metadata.MD, reqFrame *frame,
	serverStream grpc.ServerStream) error {

	respFrame, respMD, err := forwardAndCapture(target, method, md, reqFrame)
	if err != nil {
		return err
	}

	_ = respMD // trailer metadata not forwarded in simple unary case
	return serverStream.SendMsg(respFrame)
}

// forwardAndCapture dials the backend, sends the request, and captures the
// response along with any trailer metadata.
func forwardAndCapture(target, method string, md metadata.MD,
	reqFrame *frame) (*frame, metadata.MD, error) {

	// Dial the backend.
	conn, err := grpc.NewClient(target,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithDefaultCallOptions(grpc.ForceCodec(rawCodec{})),
	)
	if err != nil {
		return nil, nil, status.Errorf(codes.Unavailable, "failed to connect to backend %s: %v", target, err)
	}
	defer conn.Close()

	// Create outgoing context with original metadata.
	ctx := metadata.NewOutgoingContext(context.Background(), md)

	respFrame := &frame{}
	var trailer metadata.MD
	err = conn.Invoke(ctx, method, reqFrame, respFrame, grpc.Trailer(&trailer))
	if err != nil {
		return nil, nil, err
	}

	return respFrame, trailer, nil
}

// flattenMD converts gRPC metadata to a single-value map for storage.
func flattenMD(md metadata.MD) map[string]string {
	result := make(map[string]string, len(md))
	for k, vals := range md {
		if len(vals) > 0 {
			result[k] = vals[0]
		}
	}
	return result
}
