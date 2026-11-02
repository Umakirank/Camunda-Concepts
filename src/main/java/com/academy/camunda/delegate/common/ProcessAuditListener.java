package com.academy.camunda.delegate.common;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * =============================================================================================
 *  ProcessAuditListener - an EXECUTION LISTENER (bean name "processAuditListener")
 * =============================================================================================
 *
 * <h2>Used by</h2>
 * loan-approval.bpmn, on the PROCESS element:
 * <pre>{@code
 * <camunda:executionListener delegateExpression="${processAuditListener}" event="start" />
 * <camunda:executionListener delegateExpression="${processAuditListener}" event="end" />
 * }</pre>
 *
 * <h2>Execution listener vs. JavaDelegate</h2>
 * A JavaDelegate IS the work of a service task (it is visible in the diagram). An execution
 * listener is a hook on lifecycle events of ANY element and is invisible in the diagram:
 * <ul>
 *   <li>process / activity / gateway / event: {@code start}, {@code end}</li>
 *   <li>sequence flow: {@code take} (see Flow_RiskHigh in loan-approval.bpmn)</li>
 * </ul>
 * Use listeners for technical concerns (audit, metrics, setting defaults, cleanup) and NOT for
 * business steps - business steps should be visible tasks so that business people can read
 * the model ("no hidden logic").
 *
 * <p>Listeners run in the same transaction as the step that triggers them. An exception in a
 * listener fails the API call / job exactly like a failing service task.</p>
 */
@Component
public class ProcessAuditListener implements ExecutionListener {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessAuditListener.class);

  @Override
  public void notify(DelegateExecution execution) {
    String event = execution.getEventName(); // "start" or "end"
    if (EVENTNAME_START.equals(event)) {
      execution.setVariable("auditStartedAt", Instant.now().toString());
      LOG.info("[audit] START {} businessKey={} instance={}",
          execution.getProcessDefinitionId(), execution.getProcessBusinessKey(), execution.getProcessInstanceId());
    } else if (EVENTNAME_END.equals(event)) {
      Object started = execution.getVariable("auditStartedAt");
      String took = started != null
          ? Duration.between(Instant.parse(started.toString()), Instant.now()).toString()
          : "?";
      LOG.info("[audit] END   {} businessKey={} duration={} endedAt={}",
          execution.getProcessDefinitionId(), execution.getProcessBusinessKey(), took,
          execution.getCurrentActivityId());
    }
  }
}
