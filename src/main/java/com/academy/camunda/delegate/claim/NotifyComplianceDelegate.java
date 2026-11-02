package com.academy.camunda.delegate.claim;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  NotifyComplianceDelegate - reacting to an ESCALATION from a called process
 * =============================================================================================
 *
 * <h2>Escalation vs. Error (interview favourite)</h2>
 * <ul>
 *   <li>ERROR: "this cannot continue". Always interrupting at the catching side, the throwing
 *       scope ends. Typically caught by an error boundary event.</li>
 *   <li>ESCALATION: "someone higher up should know / act". Can be caught by an
 *       INTERRUPTING or NON-INTERRUPTING boundary / event sub-process. The child process
 *       may simply continue after throwing it (intermediate throw event).</li>
 * </ul>
 * Here the fraud investigator marks a case "SUSPECTED": the child escalates and continues; the
 * parent's non-interrupting boundary on the call activity starts this parallel branch while the
 * call activity keeps running.
 */
@Component
public class NotifyComplianceDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(NotifyComplianceDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable("complianceNotified", true);
    LOG.warn("Compliance informed about suspicious claim {}", execution.getVariable("claimId"));
  }
}
