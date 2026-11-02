package com.academy.camunda.delegate.onboarding;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  RevokeAccessDelegate - INTERRUPTING EVENT SUB-PROCESS
 * =============================================================================================
 *
 * <h2>Path</h2>
 * Message "OnboardingCancelled" -> interrupting start event of {@code EventSubProcess_Cancelled}.
 *
 * <h2>Internal working</h2>
 * The engine first CANCELS every active execution of the parent scope: open user tasks
 * (HR paperwork, IT accounts, manager intro) are deleted (task listeners get "delete"), timers
 * and subscriptions are removed, the embedded sub-process is ended. Only then does this event
 * sub-process run. When it ends, the process instance ends.
 *
 * <p>Compare: an interrupting BOUNDARY event cancels only the activity it is attached to; an
 * interrupting EVENT SUB-PROCESS cancels the whole scope it lives in. Use the event
 * sub-process when "at any point during this phase" is the business rule.</p>
 */
@Component
public class RevokeAccessDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(RevokeAccessDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable("onboardingStatus", "CANCELLED");
    LOG.warn("Onboarding of {} cancelled - revoking access to {}", execution.getVariable("employeeName"),
        execution.getVariable("provisionedEquipment"));
  }
}
