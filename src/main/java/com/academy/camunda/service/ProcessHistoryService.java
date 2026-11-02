package com.academy.camunda.service;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * =============================================================================================
 *  ProcessHistoryService - HistoryService: the audit trail
 * =============================================================================================
 *
 * <h2>History levels (camunda.bpm.history-level)</h2>
 * <pre>
 *  none      nothing
 *  activity  process + activity instances (start/end), tasks
 *  audit     + final variable values
 *  full      + every variable update, form properties, user operation log, DMN inputs/outputs,
 *              incidents, job log, external task log
 * </pre>
 * Higher levels mean more INSERTs per step - measure in load tests. "full" is great for
 * development and audit-heavy industries (banking, insurance); many high-volume systems run
 * "audit" or a custom HistoryEventHandler that streams to Elasticsearch / a data lake.
 *
 * <h2>Typical uses</h2>
 * "What happened to claim X?" (support), SLA reports, regulatory audit ("who approved this loan
 * and when?"), and debugging: the activity instance list shows exactly which path a token took.
 */
@Service
public class ProcessHistoryService {

  private final HistoryService historyService;

  public ProcessHistoryService(HistoryService historyService) {
    this.historyService = historyService;
  }

  /** Complete picture of one instance - also works after the instance has ENDED. */
  public Map<String, Object> auditTrail(String processInstanceId) {
    HistoricProcessInstance pi = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(processInstanceId).singleResult();
    if (pi == null) {
      throw new IllegalArgumentException("Unknown process instance " + processInstanceId);
    }

    List<Map<String, Object>> activities = historyService.createHistoricActivityInstanceQuery()
        .processInstanceId(processInstanceId)
        .orderByHistoricActivityInstanceStartTime().asc()
        .list().stream().map(ProcessHistoryService::activity).toList();

    Map<String, Object> variables = new LinkedHashMap<>();
    for (HistoricVariableInstance v : historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstanceId).list()) {
      variables.put(v.getName(), v.getValue());
    }

    List<Map<String, Object>> decisions = historyService.createHistoricDecisionInstanceQuery()
        .processInstanceId(processInstanceId)
        .includeInputs().includeOutputs()
        .list().stream().map(ProcessHistoryService::decision).toList();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("processDefinitionKey", pi.getProcessDefinitionKey());
    result.put("businessKey", pi.getBusinessKey());
    result.put("state", pi.getState());              // ACTIVE, COMPLETED, EXTERNALLY_TERMINATED, ...
    result.put("startTime", pi.getStartTime());
    result.put("endTime", pi.getEndTime());
    result.put("endActivityId", pi.getEndActivityId());
    result.put("activities", activities);
    result.put("variables", variables);
    result.put("decisions", decisions);
    return result;
  }

  private static Map<String, Object> activity(HistoricActivityInstance a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("activityId", a.getActivityId());
    m.put("name", a.getActivityName());
    m.put("type", a.getActivityType());
    m.put("start", a.getStartTime());
    m.put("end", a.getEndTime());
    m.put("durationMs", a.getDurationInMillis());
    m.put("assignee", a.getAssignee());
    m.put("canceled", a.isCanceled());
    return m;
  }

  private static Map<String, Object> decision(HistoricDecisionInstance d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("decisionKey", d.getDecisionDefinitionKey());
    m.put("activityId", d.getActivityId());
    m.put("inputs", d.getInputs().stream().map(i -> i.getClauseName() + "=" + i.getValue()).toList());
    m.put("outputs", d.getOutputs().stream().map(o -> o.getVariableName() + "=" + o.getValue()).toList());
    m.put("rootDecisionInstanceId", d.getRootDecisionInstanceId()); // set for required decisions of a DRD
    return m;
  }
}
