package com.academy.camunda.delegate.onboarding;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  TeamAnnouncementDelegate - runs in a process started by a SIGNAL
 * =============================================================================================
 *
 * <h2>Path</h2>
 * employee-onboarding.bpmn throws signal "EmployeeOnboarded" with
 * {@code <camunda:in source="employeeName" target="employeeName"/>} (payload) ->
 * team-announcement.bpmn has a SIGNAL START EVENT -> a new instance starts with those variables.
 *
 * <h2>Message vs. Signal</h2>
 * <ul>
 *   <li>Message: point-to-point, exactly ONE receiver must match (otherwise correlate() throws
 *       MismatchingMessageCorrelationException). Can carry a business key.</li>
 *   <li>Signal: broadcast. 0..N receivers - every subscription in every instance and every
 *       signal start event fires. No receiver = no error.</li>
 * </ul>
 * Signals across ALL tenants/processes are powerful but dangerous: a careless signal name can
 * trigger unrelated processes. Prefer messages unless you really mean "everyone".
 */
@Component
public class TeamAnnouncementDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(TeamAnnouncementDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    String text = "Please welcome " + execution.getVariable("employeeName")
        + " to the " + execution.getVariable("department") + " team!";
    execution.setVariable("announcement", text);
    LOG.info("[announcement] {}", text);
  }
}
