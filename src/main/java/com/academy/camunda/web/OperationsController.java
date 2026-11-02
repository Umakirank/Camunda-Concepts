package com.academy.camunda.web;

import com.academy.camunda.service.OperationsService;
import com.academy.camunda.service.ProcessHistoryService;
import com.academy.camunda.worker.PaymentExternalTaskWorker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * =============================================================================================
 *  OperationsController - support / admin endpoints (protect them in production!)
 * =============================================================================================
 * <pre>
 *  GET  /api/ops/incidents
 *  POST /api/ops/jobs/{jobId}/retry
 *  POST /api/ops/jobs/{jobId}/execute
 *  GET  /api/ops/process-instances/{id}/jobs
 *  GET  /api/ops/process-instances/{id}/history
 *  POST /api/ops/process-instances/{id}/suspend | /activate
 *  POST /api/ops/process-instances/{id}/move?from=Task_A&amp;to=Task_B&amp;reason=...
 *  POST /api/ops/process-definitions/{key}/migrate-to-latest
 *  DELETE /api/ops/process-instances/{id}?reason=...
 *  POST /api/ops/workers/payment/poll       (trigger the external task worker once)
 * </pre>
 */
@RestController
@RequestMapping("/api/ops")
public class OperationsController {

  private final OperationsService ops;
  private final ProcessHistoryService history;
  private final PaymentExternalTaskWorker paymentWorker;

  public OperationsController(OperationsService ops, ProcessHistoryService history,
                              PaymentExternalTaskWorker paymentWorker) {
    this.ops = ops;
    this.history = history;
    this.paymentWorker = paymentWorker;
  }

  @GetMapping("/incidents")
  public List<Map<String, Object>> incidents() {
    return ops.openIncidents();
  }

  @PostMapping("/jobs/{jobId}/retry")
  public ResponseEntity<Void> retry(@PathVariable String jobId) {
    ops.retryJob(jobId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/jobs/{jobId}/execute")
  public ResponseEntity<Void> execute(@PathVariable String jobId) {
    ops.executeJobNow(jobId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/process-instances/{id}/jobs")
  public List<Map<String, Object>> jobs(@PathVariable String id) {
    return ops.jobsOf(id);
  }

  @GetMapping("/process-instances/{id}/history")
  public Map<String, Object> history(@PathVariable String id) {
    return history.auditTrail(id);
  }

  @PostMapping("/process-instances/{id}/suspend")
  public ResponseEntity<Void> suspend(@PathVariable String id) {
    ops.suspend(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/process-instances/{id}/activate")
  public ResponseEntity<Void> activate(@PathVariable String id) {
    ops.activate(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/process-instances/{id}/move")
  public ResponseEntity<Void> move(@PathVariable String id, @RequestParam String from, @RequestParam String to,
                                   @RequestParam(defaultValue = "manual repair") String reason) {
    ops.moveToken(id, from, to, reason);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/process-definitions/{key}/migrate-to-latest")
  public Map<String, Object> migrate(@PathVariable String key) {
    return Map.of("migratedInstances", ops.migrateAllToLatest(key));
  }

  @DeleteMapping("/process-instances/{id}")
  public ResponseEntity<Void> cancel(@PathVariable String id, @RequestParam(defaultValue = "cancelled by operator") String reason) {
    ops.cancel(id, reason);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/workers/payment/poll")
  public Map<String, Object> pollPayments() {
    return Map.of("processedTasks", paymentWorker.pollAndProcess());
  }
}
