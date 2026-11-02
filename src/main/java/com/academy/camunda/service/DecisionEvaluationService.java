package com.academy.camunda.service;

import com.academy.camunda.config.ProcessConstants;
import org.camunda.bpm.dmn.engine.DmnDecisionResult;
import org.camunda.bpm.dmn.engine.DmnDecisionTableResult;
import org.camunda.bpm.engine.DecisionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * =============================================================================================
 *  DecisionEvaluationService - DMN WITHOUT a process (DecisionService)
 * =============================================================================================
 *
 * <h2>Three ways to evaluate a DMN decision in Camunda 7</h2>
 * <ol>
 *   <li>Business rule task in BPMN ({@code camunda:decisionRef}) - most common.</li>
 *   <li>{@link DecisionService} from Java (this class) or
 *       {@code POST /engine-rest/decision-definition/key/{key}/evaluate} - e.g. a pricing API,
 *       a validation on a web form, a rules endpoint for another system. Evaluations are
 *       stored in history (ACT_HI_DECINST) exactly like from a process.</li>
 *   <li>Standalone DMN engine ({@code DmnEngineConfiguration.createDefaultDmnEngineConfiguration()
 *       .buildEngine()}) - no database, no history; great for unit tests of rules.</li>
 * </ol>
 *
 * <h2>Reading results</h2>
 * A result is a LIST of rule results; each rule result is a MAP outputName -> value.
 * <pre>
 *   getSingleResult()   one matched rule  (exception if more)   -> Map
 *   getSingleEntry()    one rule, one output column             -> value
 *   getResultList()     all matched rules                       -> List&lt;Map&gt;
 *   collectEntries("x") column x of all matched rules           -> List
 * </pre>
 * The BPMN attribute {@code camunda:mapDecisionResult} is exactly these four
 * (singleResult / singleEntry / resultList / collectEntries).
 *
 * <h2>evaluateDecisionTableByKey vs evaluateDecisionByKey</h2>
 * The first works only for decision TABLES. The second works for any decision (table, literal
 * expression) and evaluates REQUIRED decisions of a DRD first.
 */
@Service
public class DecisionEvaluationService {

  private final DecisionService decisionService;

  public DecisionEvaluationService(DecisionService decisionService) {
    this.decisionService = decisionService;
  }

  /** Any decision table, any hit policy: returns the raw list of matched-rule outputs. */
  public List<Map<String, Object>> evaluateTable(String decisionKey, Map<String, Object> variables) {
    DmnDecisionTableResult result = decisionService
        .evaluateDecisionTableByKey(decisionKey)
        .variables(ProcessLifecycleService.normalizeNumbers(variables))
        .evaluate();
    return result.getResultList();
  }

  /**
   * Generic evaluation (tables, literal expressions, DRDs). For "leave-approval-level" the
   * engine evaluates "leave-eligibility" first - you only pass the raw inputs.
   */
  public List<Map<String, Object>> evaluateDecision(String decisionKey, Map<String, Object> variables) {
    DmnDecisionResult result = decisionService
        .evaluateDecisionByKey(decisionKey)
        .variables(ProcessLifecycleService.normalizeNumbers(variables))
        .evaluate();
    return result.getResultList();
  }

  /** Literal expression example: returns just the greeting string. */
  public String greeting(String customerName, String customerType) {
    DmnDecisionResult result = decisionService.evaluateDecisionByKey(ProcessConstants.DECISION_GREETING)
        .variables(Map.of("customerName", customerName, "customerType", customerType))
        .evaluate();
    return result.getSingleEntry();
  }

  /**
   * OUTPUT ORDER (O) emulation. The DMN spec's OUTPUT ORDER returns all hits sorted by the
   * priority of the output VALUES (order of the allowed values of the output column).
   * Camunda 7 does not support O and P (PRIORITY) - deployment fails. The portable workaround:
   * evaluate with RULE ORDER or COLLECT and sort in code by an explicit priority list.
   */
  public List<String> offersInOutputOrder(long monthlyIncome) {
    List<String> priority = List.of(           // the "allowed values" order of the output column
        "Unlimited airport lounge access",
        "5% cashback on travel",
        "Upgrade path to secured card",
        "Zero joining fee");
    List<String> offers = new ArrayList<>(decisionService
        .evaluateDecisionTableByKey(ProcessConstants.DECISION_CARD_OFFERS)
        .variables(Map.of("monthlyIncome", monthlyIncome))
        .evaluate()
        .<String>collectEntries("offer"));
    offers.sort(Comparator.comparingInt(priority::indexOf));
    return offers;
  }
}
