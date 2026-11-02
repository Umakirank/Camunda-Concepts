package com.academy.camunda.web;

import com.academy.camunda.service.DecisionEvaluationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * =============================================================================================
 *  DecisionController - DMN as a service
 * =============================================================================================
 * <pre>
 *  POST /api/decisions/{key}/table    body: input variables  -> any decision TABLE
 *  POST /api/decisions/{key}/evaluate body: input variables  -> any decision (incl. DRD / literal)
 *  GET  /api/decisions/greeting?name=Asha&amp;type=GOLD          -> literal expression
 *  GET  /api/decisions/offers-output-order?monthlyIncome=250000 -> OUTPUT ORDER emulation
 * </pre>
 * Examples:
 * <pre>
 *  curl -X POST localhost:8080/api/decisions/fraud-risk-score/table \
 *       -H 'Content-Type: application/json' \
 *       -d '{"claimAmount":80000,"daysSincePolicyStart":10,"previousClaims":0}'
 *  -> [{"score":70}]   (COLLECT SUM)
 *
 *  curl -X POST localhost:8080/api/decisions/leave-approval-level/evaluate \
 *       -H 'Content-Type: application/json' \
 *       -d '{"leaveType":"ANNUAL","requestedDays":15,"availableBalance":10}'
 *  -> [{"approvalLevel":"REJECT"}]   (DRD: eligibility evaluated first)
 * </pre>
 */
@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

  private final DecisionEvaluationService decisions;

  public DecisionController(DecisionEvaluationService decisions) {
    this.decisions = decisions;
  }

  @PostMapping("/{key}/table")
  public List<Map<String, Object>> table(@PathVariable String key, @RequestBody Map<String, Object> inputs) {
    return decisions.evaluateTable(key, inputs);
  }

  @PostMapping("/{key}/evaluate")
  public List<Map<String, Object>> evaluate(@PathVariable String key, @RequestBody Map<String, Object> inputs) {
    return decisions.evaluateDecision(key, inputs);
  }

  @GetMapping("/greeting")
  public Map<String, String> greeting(@RequestParam String name, @RequestParam(defaultValue = "REGULAR") String type) {
    return Map.of("greeting", decisions.greeting(name, type));
  }

  @GetMapping("/offers-output-order")
  public List<String> offers(@RequestParam long monthlyIncome) {
    return decisions.offersInOutputOrder(monthlyIncome);
  }
}
