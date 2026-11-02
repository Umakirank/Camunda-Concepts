package com.academy.camunda.delegate.loan;

import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  UnderwriterSlaEscalationDelegate - reacting to a NON-interrupting timer
 * =============================================================================================
 *
 * <h2>Used by</h2>
 * {@code Task_EscalateSla}, reached from {@code Boundary_UnderwritingSla} (timer PT2H,
 * cancelActivity="false") on the underwriting user task.
 *
 * <h2>Internal working of a boundary timer</h2>
 * When the user task is entered, the engine inserts a TIMER JOB with due date now+2h. The job
 * executor fires it. Because the boundary is non-interrupting, the engine creates a NEW
 * concurrent execution that runs this delegate while the user task stays active. If the task
 * is completed earlier, the timer job is deleted automatically.
 *
 * <h2>Using engine services inside a delegate</h2>
 * {@code execution.getProcessEngineServices()} gives access to all services. Here we raise the
 * priority of the still-open task. Constructor-injecting {@link TaskService} works too
 * (this is a Spring bean) - both run in the same transaction.
 */
@Component
public class UnderwriterSlaEscalationDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(UnderwriterSlaEscalationDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    TaskService taskService = execution.getProcessEngineServices().getTaskService();
    for (Task task : taskService.createTaskQuery()
        .processInstanceId(execution.getProcessInstanceId())
        .taskDefinitionKey("UserTask_Underwrite")
        .list()) {
      taskService.setPriority(task.getId(), 100);
      taskService.createComment(task.getId(), execution.getProcessInstanceId(),
          "SLA breached - escalated to team lead");
    }
    execution.setVariable("slaEscalated", true);
    LOG.warn("Underwriting SLA breached for {} - escalated", execution.getProcessBusinessKey());
  }
}
