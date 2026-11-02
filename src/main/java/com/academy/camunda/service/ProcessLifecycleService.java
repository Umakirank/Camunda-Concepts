package com.academy.camunda.service;

import com.academy.camunda.config.ProcessConstants;
import com.academy.camunda.domain.Order;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.MessageCorrelationResult;
import org.camunda.bpm.engine.runtime.MessageCorrelationResultType;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * =============================================================================================
 *  ProcessLifecycleService - RuntimeService + RepositoryService in action
 * =============================================================================================
 *
 * <p>Everything an application does to DRIVE processes: start them (by key, by message), send
 * messages and signals into running instances, read and write variables, inspect deployed
 * definitions.</p>
 *
 * <h2>Transactions</h2>
 * The engine shares Spring's transaction manager. Each engine API call runs in its own
 * transaction unless a surrounding {@code @Transactional} exists - then the engine joins it.
 * That lets you save your own entity (JPA) and start a process ATOMICALLY: either both commit or
 * neither. (Annotate a method with {@code @Transactional} to try it.)
 *
 * <h2>Business key</h2>
 * A business key (order id, claim id) is the bridge between your domain and the engine: it is
 * indexed, shown in Cockpit/Tasklist, and the most common correlation criterion. It should be
 * unique per process definition (not enforced by C7).
 */
@Service
public class ProcessLifecycleService {

  private final RuntimeService runtimeService;
  private final RepositoryService repositoryService;

  public ProcessLifecycleService(RuntimeService runtimeService, RepositoryService repositoryService) {
    this.runtimeService = runtimeService;
    this.repositoryService = repositoryService;
  }

  // ======================================================================= starting instances

  /**
   * Generic start "by key" with the fluent builder. The builder can also start at a given
   * activity ({@code .startBeforeActivity("Task_X")}), skip listeners/IO mappings
   * ({@code .execute(true, true)}) or attach a tenant id.
   * The call returns when the new instance reached its first wait state (or ended).
   */
  public ProcessInstance start(String processKey, String businessKey, Map<String, Object> variables) {
    return runtimeService.createProcessInstanceByKey(processKey)
        .businessKey(businessKey)
        .setVariables(normalizeNumbers(variables))
        .execute();
  }

  public ProcessInstance startLoan(Map<String, Object> request) {
    String businessKey = "LOAN-" + System.currentTimeMillis();
    return start(ProcessConstants.PROCESS_LOAN, businessKey, request);
  }

  /**
   * Starts order-fulfillment through its MESSAGE START EVENT. We never mention the process key:
   * whoever subscribed to "OrderPlaced" is started. The Order object is passed as a typed JSON
   * object value.
   */
  public String placeOrder(Order order) {
    VariableMap variables = Variables.createVariables()
        .putValueTyped("order", Variables.objectValue(order)
            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create());

    // correlateStartMessage() only considers MESSAGE START EVENTS and returns the new instance.
    // (Alternative with the same effect: runtimeService.startProcessInstanceByMessage(name, key, vars).)
    ProcessInstance pi = runtimeService.createMessageCorrelation(ProcessConstants.MSG_ORDER_PLACED)
        .processInstanceBusinessKey(order.getOrderId())
        .setVariables(variables)
        .correlateStartMessage();
    return pi.getId();
  }

  // ======================================================================= messages & signals

  /**
   * Correlates a message to EXACTLY ONE waiting receiver (intermediate catch event, receive
   * task, boundary event, event sub-process start, or a message start event).
   * <ul>
   *   <li>0 matches  -> MismatchingMessageCorrelationException (nobody is waiting - too early
   *       or too late). Callers should treat it as "retry later" or "ignore".</li>
   *   <li>&gt;1 match -> also an exception; use {@code correlateAll()} if you mean all.</li>
   * </ul>
   * Variables are set on the process instance BEFORE the token continues.
   */
  public Map<String, Object> correlateMessage(String messageName, String businessKey, Map<String, Object> variables) {
    MessageCorrelationResult result = runtimeService.createMessageCorrelation(messageName)
        .processInstanceBusinessKey(businessKey)
        .setVariables(normalizeNumbers(variables))
        .correlateWithResult();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("resultType", result.getResultType().name()); // Execution or ProcessDefinition
    response.put("processInstanceId", result.getResultType() == MessageCorrelationResultType.Execution
        ? result.getExecution().getProcessInstanceId()
        : result.getProcessInstance().getId());
    return response;
  }

  /** Broadcasts a signal to ALL subscribers (signal catch events and signal start events). */
  public void broadcastSignal(String signalName, Map<String, Object> variables) {
    runtimeService.createSignalEvent(signalName)
        .setVariables(normalizeNumbers(variables))
        .send();
  }

  // ======================================================================= variables

  public Map<String, Object> getVariables(String processInstanceId) {
    return runtimeService.getVariables(processInstanceId);
  }

  /**
   * Setting a variable on a running instance also (re)evaluates CONDITIONAL events - that is
   * how the leave request is cancelled: setVariable(id, "leaveCancelled", true).
   */
  public void setVariable(String processInstanceId, String name, Object value) {
    runtimeService.setVariable(processInstanceId, name, value instanceof Integer i ? Long.valueOf(i) : value);
  }

  // ======================================================================= repository

  /** Latest version of every deployed process (RepositoryService = ACT_RE_PROCDEF). */
  public List<Map<String, Object>> deployedProcesses() {
    return repositoryService.createProcessDefinitionQuery()
        .latestVersion()
        .orderByProcessDefinitionKey().asc()
        .list().stream()
        .map(this::toMap)
        .toList();
  }

  private Map<String, Object> toMap(ProcessDefinition pd) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", pd.getKey());
    m.put("name", pd.getName());
    m.put("version", pd.getVersion());
    m.put("versionTag", pd.getVersionTag());
    m.put("deploymentId", pd.getDeploymentId());
    m.put("suspended", pd.isSuspended());
    return m;
  }

  // ======================================================================= helpers

  /**
   * Jackson turns JSON numbers into Integer or Long depending on size. Our forms and DMN tables
   * use "long"/"integer"/"double" types; mixing Integer and Long in the same variable across
   * instances makes Cockpit filters and generated forms confusing. We therefore normalise every
   * Integer to Long at the API boundary - a small but very practical best practice.
   */
  public static Map<String, Object> normalizeNumbers(Map<String, Object> input) {
    Map<String, Object> out = new HashMap<>();
    if (input != null) {
      input.forEach((k, v) -> out.put(k, v instanceof Integer i ? Long.valueOf(i) : v));
    }
    return out;
  }
}
