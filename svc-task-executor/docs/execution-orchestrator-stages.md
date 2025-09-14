# Execution Orchestrator: Status Flow, Stages, UI Guidance, and Data Sources

This document summarizes the task-execution lifecycle implemented in `svc-task-executor` (ExecutionOrchestrator), outlines each stage, and recommends frontend visualization.

Repository file: `svc-task-executor/src/main/java/com/chaosblade/svc/taskexecutor/service/ExecutionOrchestrator.java`

## 1) Task Execution Status Flow (chronological)

Status values observed in code (exact strings), in the typical order:

1. GENERATING_CASES — set when the execution record is created
2. ANALYZING_PATTERNS — pattern analysis via proxy, includes readiness wait
3. INJECTING_AND_REPLAYING — inject per-service faults and perform replay verification
4. RULES_READY — interceptors upserted and ready
5. LOAD_TEST_BASELINE — run all test cases (baseline/single/dual) and collect metrics
6. DONE — execution completed successfully
7. FAILED — terminal error (on BusinessException or uncaught Exception)

Legacy/other statuses referenced in queries but not set in current code:
- INIT (entity default prior to first persist)
- RECORDING_READY (no longer used; recording stage was removed)

## 2) Stage-by-Stage Breakdown

Below describes operations, available data, expected duration, and checkpoints.

### Stage: GENERATING_CASES (creation)
- Trigger: POST /api/tasks/{taskId}/execute → `createOrFailIfRunning` creates `task_execution` row
- Operations:
  - Validate no running execution unless `force`
  - Resolve namespace from system
  - Set `req_def_id` from `apiDefinitionId` (fallback: `api_id`)
  - Persist `task_execution` with status=GENERATING_CASES
- Data available:
  - task_execution.id (executionId), taskId, namespace, reqDefId, requestNum (default 1), startedAt
- Duration: fast (<100ms)
- Checkpoints:
  - Execution ID returned to caller

### Stage: ANALYZING_PATTERNS
- Trigger: `run(executionId)` begins Stage1/2; status set to ANALYZING_PATTERNS
- Operations:
  - Stage1: log "Start analyzing patterns"
  - Build service list from FaultConfigQueryService (unique services with configs)
  - Stage2: call proxy `analyze(...)` (with `excution_id`) → receive `analyzeTaskId`, `recordId`
  - Poll analyzeTask until `COMPLETED` or timeout; on errors/timeout → FAILED
  - After completion: parallel wait for each service to be stable (KubernetesService)
- Data available:
  - task_execution.analyze_task_id, record_id; task_execution_log entries for Stage1/2
  - service list to analyze (in-memory; can be re-derived from fault configs)
- Duration:
  - Analysis polling: up to `waitAnalyzeTimeoutSec` (configurable)
  - Stability checks: up to ~120s per service in parallel
- Checkpoints:
  - Analyze task created; analyze completed; services stable post-analyze

### Stage: (no explicit status change) Generate Test Cases & Baggage
- Operations:
  - Generate suite: baseline + per-service single-fault + all pairs (dual)
  - Persist generated cases → `test_cases` (taskId+executionId)
  - Build and upsert baggage tokens per service → `baggage_map`
- Data available:
  - test_cases rows for this execution; baggage_map rows per service
- Duration: typically seconds (depends on number of services)
- Checkpoints:
  - test_cases persisted; baggage_map upserted

### Stage: INJECTING_AND_REPLAYING
- Trigger: set status=INJECTING_AND_REPLAYING
- Operations:
  - For each unique service (from generated cases), in parallel:
    - Inject one fault (POST fault service /api/faults/execute) → obtain `bladeName`
    - Poll fault status until `phase=Running` or timeout (30s)
    - Replay verification via ProxyClient.replay() → persist to `intercept_replay_result`
  - Recover all faults (DELETE) and wait services to be stable (parallel)
- Data available:
  - task_execution_log entries (injection start/completion)
  - intercept_replay_result rows per service
- Duration: depends on number of services; each service injection+replay typically <1–2 min
- Checkpoints:
  - Fault injected for each service; replay verified; faults recovered; services stable

### Stage: RULES_READY (Interceptors upsert)
- Trigger: build interceptor items from baggage_map + replay results; upsert via proxy; set status=RULES_READY
- Operations:
  - Build items: serviceName, path, method, resp template, baggageTokens
  - Upsert interceptors (namespace, recordId=executionId, TTL configurable)
  - Poll `getInterceptorStatus` until exists=true or timeout; then wait all target services stable again
- Data available:
  - task_execution.intercept_record_id (string, executionId)
  - Items (derived; not persisted as-is)
- Duration: seconds to minutes depending on cluster rollout
- Checkpoints:
  - Interceptors exist (ready=true); services stable after upsert

### Stage: LOAD_TEST_BASELINE (executing all cases)
- Trigger: set status=LOAD_TEST_BASELINE
- Operations:
  - Load HttpReqDef (request definition)
  - Execute all test cases in batches (`batchSize`=18), each case with `requestNum` concurrency (<=8)
  - Compute metrics: p50/p95/p99 and error rate; persist `test_result` per case
- Data available:
  - test_result rows (executionId + testCaseId + metrics + request meta)
- Duration:
  - Depends on number of cases, per-case concurrency and requestNum
- Checkpoints:
  - Per-batch completion; per-case completion with metrics

### Stage: DONE / FAILED
- DONE: set finishedAt, persist
- FAILED: set finishedAt, errorCode, errorMsg and persist

## 3) Frontend Visualization Recommendations

For a dashboard that tracks a single execution (executionId):

- Global header:
  - Execution ID, Task ID, Namespace, Status (with colored badges)
  - StartedAt / UpdatedAt / FinishedAt
  - ErrorCode / ErrorMsg when FAILED

- Progress bar with milestones:
  - GENERATING_CASES → ANALYZING_PATTERNS → INJECTING_AND_REPLAYING → RULES_READY → LOAD_TEST_BASELINE → DONE/FAILED

- Logs panel (live append):
  - Consume `task_execution_log` (ordered by ts) with level filter
  - Highlight WARN/ERROR entries

- Stage-specific panels:
  - ANALYZING_PATTERNS
    - Show service list count (to analyze)
    - Show current analyze status (polling), `analyzeTaskId` and `recordId`
    - Timeouts/Failures as warnings
  - Generate Cases & Baggage
    - Show counts: total cases generated (baseline/single/dual)
    - Show baggage_map entries count; optionally preview tokens per service
  - INJECTING_AND_REPLAYING
    - Show unique services to inject: total, completed, running
    - Per-service row: bladeName, injection status (Pending/Running/Recovered), replay status code
    - Recovery status and post-recovery stability
  - RULES_READY
    - Interceptor items summary: total items; ready state (yes/no); TTL
    - Per-service stability after upsert (success/timeout)
  - LOAD_TEST_BASELINE
    - Batch progress: current/total batches; per-batch cases count
    - Per-case metrics (p50/p95/p99/errRate) as they persist
    - Aggregated metrics (e.g., worst p95, avg errRate)

- Error states & warnings:
  - ANALYZE_TIMEOUT / ANALYZE_FAILED
  - INTERCEPTOR_NOT_READY
  - Any injection/replay failures per service

## 4) Data Sources (entities/tables/services)

- task_execution (entity: TaskExecution)
  - status, started_at, updated_at, finished_at, analyze_task_id, record_id, intercept_record_id, error_code, error_msg, request_num
- task_execution_log (entity: TaskExecutionLog)
  - Append-only logs per execution; levels: DEBUG/INFO/WARN/ERROR (0–3)
- test_cases (entity: TestCase)
  - Generated at Stage3 for the execution; case_type, faults_json, target_count
- baggage_map (entity: BaggageMap)
  - Upserted at Stage3; serviceName → baggage token values
- intercept_replay_result (entity: InterceptReplayResult)
  - Persisted at Stage4 per service; includes request/response template info
- test_result (entity: TestResult)
  - Persisted at Stage6 per case; metrics p50/p95/p99/errRate, requestUrl/method
- External services
  - ProxyClient (analyze/replay/interceptors APIs): provides analyze task id/status, replay data, interceptor readiness
  - KubernetesService: waits for service stability (used after critical changes)
  - Fault service API (`/api/faults/*`): inject/recover faults and get fault status

## 5) Timing and Configurations

- `waitAnalyzeTimeoutSec`: maximum wait for analysis completion
- `waitInterceptorReadySec`: maximum wait for interceptor readiness
- Fault running poll timeout: 30s per service
- Service stability waits: up to 120s per service (parallelized)
- Batch size for test execution: 18 (constant in code)
- Per-case concurrency: `min(8, requestNum)`

## 6) Notes & Legacy

- Recording stage has been removed (no `RECORDING_READY` in current flow)
- The `excution_id` key (typo) is passed to analyze; it’s consumed by the proxy and should remain consistent
- Status `INIT` is the entity default but is not persisted for new executions (status is set to GENERATING_CASES before first save)

