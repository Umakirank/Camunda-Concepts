package com.academy.camunda.delegate.onboarding;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  UpdateHrRecordsDelegate - NON-INTERRUPTING EVENT SUB-PROCESS
 * =============================================================================================
 *
 * <h2>Path</h2>
 * {@code POST /api/messages/EmployeeDetailsUpdated?businessKey=EMP-...} with a body like
 * {@code {"newAddress":"Hyderabad"}} -> the message start event (isInterrupting="false") of
 * {@code EventSubProcess_DetailsUpdated} -> this delegate.
 *
 * <h2>Internal working</h2>
 * When the instance starts, the engine creates an EVENT SUBSCRIPTION (ACT_RU_EVENT_SUBSCR) for
 * every event sub-process start event of the scope. A non-interrupting start creates a NEW
 * concurrent execution each time the message arrives; the main flow is untouched. The
 * subscription stays alive until the parent scope (here: the whole process) ends - so this can
 * happen many times.
 *
 * <p>Variables sent with the message (setVariables on the correlation) are written to the
 * PROCESS scope before the sub-process starts, so we can read "newAddress" here.</p>
 */
@Component
public class UpdateHrRecordsDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(UpdateHrRecordsDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    Long updates = (Long) execution.getVariable("detailUpdates");
    execution.setVariable("detailUpdates", updates == null ? 1L : updates + 1);
    LOG.info("HR record of {} updated (newAddress={})", execution.getVariable("employeeName"),
        execution.getVariable("newAddress"));
  }
}
