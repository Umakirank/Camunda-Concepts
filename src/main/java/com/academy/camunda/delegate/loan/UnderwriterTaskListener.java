package com.academy.camunda.delegate.loan;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * =============================================================================================
 *  UnderwriterTaskListener - a TASK LISTENER (bean "underwriterTaskListener")
 * =============================================================================================
 *
 * <h2>Used by</h2>
 * {@code UserTask_Underwrite} in loan-approval.bpmn for the events create, assignment,
 * complete and timeout (a timer of 30 minutes defined INSIDE the listener).
 *
 * <h2>Task lifecycle events (C7)</h2>
 * <pre>
 *  create      - task row inserted; assignee/candidates already set from the model
 *  assignment  - assignee changed (claim, setAssignee, delegate)
 *  update      - any property/ local variable changed
 *  complete    - before the task is deleted and the token moves on
 *  delete      - task cancelled (interrupting boundary event, process deleted ...)
 *  timeout     - a timer attached to the LISTENER fired (the task stays open!)
 * </pre>
 *
 * <h2>What we do</h2>
 * <ul>
 *   <li>create: copy DMN details into TASK-LOCAL variables (riskLevel/riskReason). Local
 *       variables live only as long as the task; the generated form shows them read-only.
 *       Bump the priority for big loans (Tasklist sorts by priority).</li>
 *   <li>assignment: log who took the task (four-eyes / audit).</li>
 *   <li>complete: validate - a rejection requires a comment. Throwing here aborts the
 *       completion and the user sees the message in Tasklist.</li>
 *   <li>timeout: remind (here: log). Unlike a boundary timer, nothing in the flow changes.</li>
 * </ul>
 */
@Component
public class UnderwriterTaskListener implements TaskListener {

  private static final Logger LOG = LoggerFactory.getLogger(UnderwriterTaskListener.class);

  @Override
  public void notify(DelegateTask task) {
    switch (task.getEventName()) {
      case EVENTNAME_CREATE -> onCreate(task);
      case EVENTNAME_ASSIGNMENT -> LOG.info("Underwriting task {} assigned to {}", task.getId(), task.getAssignee());
      case EVENTNAME_COMPLETE -> onComplete(task);
      case EVENTNAME_TIMEOUT -> LOG.warn("Reminder: underwriting task {} for {} is open for 30 minutes (assignee={})",
          task.getId(), task.getVariable("applicantName"), task.getAssignee());
      default -> { }
    }
  }

  private void onCreate(DelegateTask task) {
    Object decision = task.getVariable("riskDecision");
    if (decision instanceof Map<?, ?> map) {
      task.setVariableLocal("riskLevel", String.valueOf(map.get("riskLevel")));
      task.setVariableLocal("riskReason", String.valueOf(map.get("riskReason")));
    }
    Number amount = (Number) task.getVariable("loanAmount");
    if (amount != null && amount.longValue() >= 1_000_000L) {
      task.setPriority(90); // default is 50; the BPMN sets 60
    }
    LOG.info("Underwriting task created for {} (candidate group 'underwriters')", task.getVariable("applicantName"));
  }

  private void onComplete(DelegateTask task) {
    Boolean approved = (Boolean) task.getVariable("approved");
    Object comment = task.getVariable("underwriterComment");
    if (Boolean.FALSE.equals(approved) && (comment == null || comment.toString().isBlank())) {
      // Aborts task completion: the transaction rolls back, the task stays open.
      throw new IllegalArgumentException("Please give a comment when rejecting a loan.");
    }
  }
}
