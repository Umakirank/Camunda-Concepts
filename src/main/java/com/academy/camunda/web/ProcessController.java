package com.academy.camunda.web;

import com.academy.camunda.config.ProcessConstants;
import com.academy.camunda.domain.Order;
import com.academy.camunda.service.ProcessLifecycleService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * =============================================================================================
 *  ProcessController - our own business-friendly REST API in front of the engine
 * =============================================================================================
 *
 * <h2>Why not just use /engine-rest?</h2>
 * The Camunda REST API is generic and powerful (it can delete instances, change variables of
 * anything...). Exposing it directly to front-ends or partners couples them to engine details
 * (process keys, variable types) and is a security risk. Best practice: a thin, domain-specific
 * API ("POST /api/loans") that validates input and calls the engine Java API; keep /engine-rest
 * for Cockpit/Tasklist and for internal tools, protected by authentication.
 *
 * <h2>Endpoints (see README for curl examples)</h2>
 * <pre>
 *  POST /api/loans | /api/orders | /api/claims | /api/onboardings | /api/leaves | /api/credit-cards
 *  POST /api/messages/{messageName}?businessKey=...   body: variables (optional)
 *  POST /api/signals/{signalName}                     body: variables (optional)
 *  GET  /api/process-instances/{id}/variables
 *  PUT  /api/process-instances/{id}/variables/{name}  body: JSON value
 *  GET  /api/process-definitions
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class ProcessController {

  private final ProcessLifecycleService lifecycle;

  public ProcessController(ProcessLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @PostMapping("/loans")
  public Map<String, Object> applyForLoan(@RequestBody Map<String, Object> request) {
    return describe(lifecycle.startLoan(request));
  }

  @PostMapping("/orders")
  public Map<String, Object> placeOrder(@RequestBody Order order) {
    String id = lifecycle.placeOrder(order);
    return Map.of("processInstanceId", id, "businessKey", order.getOrderId());
  }

  @PostMapping("/claims")
  public Map<String, Object> fileClaim(@RequestBody Map<String, Object> request) {
    return describe(lifecycle.start(ProcessConstants.PROCESS_CLAIM, null, request));
  }

  @PostMapping("/onboardings")
  public Map<String, Object> startOnboarding(@RequestBody Map<String, Object> request) {
    String key = "EMP-" + String.valueOf(request.getOrDefault("employeeName", "new")).replace(' ', '-').toUpperCase();
    return describe(lifecycle.start(ProcessConstants.PROCESS_ONBOARDING, key, request));
  }

  @PostMapping("/leaves")
  public Map<String, Object> requestLeave(@RequestBody Map<String, Object> request) {
    Map<String, Object> vars = new HashMap<>(request);
    vars.putIfAbsent("managerId", "priya");
    vars.putIfAbsent("availableBalance", 12);
    return describe(lifecycle.start(ProcessConstants.PROCESS_LEAVE, "LEAVE-" + System.currentTimeMillis(), vars));
  }

  @PostMapping("/credit-cards")
  public Map<String, Object> applyForCard(@RequestBody Map<String, Object> request) {
    Map<String, Object> vars = new HashMap<>(request);
    vars.putIfAbsent("hasDefaults", false);
    return describe(lifecycle.start(ProcessConstants.PROCESS_CREDIT_CARD, "CARD-" + System.currentTimeMillis(), vars));
  }

  @PostMapping("/messages/{messageName}")
  public Map<String, Object> correlate(@PathVariable String messageName, @RequestParam String businessKey,
                                       @RequestBody(required = false) Map<String, Object> variables) {
    return lifecycle.correlateMessage(messageName, businessKey, variables);
  }

  @PostMapping("/signals/{signalName}")
  public ResponseEntity<Void> signal(@PathVariable String signalName,
                                     @RequestBody(required = false) Map<String, Object> variables) {
    lifecycle.broadcastSignal(signalName, variables);
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/process-instances/{id}/variables")
  public Map<String, Object> variables(@PathVariable String id) {
    return lifecycle.getVariables(id);
  }

  @PutMapping("/process-instances/{id}/variables/{name}")
  public ResponseEntity<Void> setVariable(@PathVariable String id, @PathVariable String name, @RequestBody Object value) {
    lifecycle.setVariable(id, name, value);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/process-definitions")
  public List<Map<String, Object>> definitions() {
    return lifecycle.deployedProcesses();
  }

  private Map<String, Object> describe(ProcessInstance pi) {
    Map<String, Object> m = new HashMap<>();
    m.put("processInstanceId", pi.getId());
    m.put("businessKey", pi.getBusinessKey());
    m.put("ended", pi.isEnded()); // true if the instance already finished synchronously
    return m;
  }
}
