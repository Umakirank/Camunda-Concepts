package com.academy.camunda.config;

import org.camunda.bpm.spring.boot.starter.event.TaskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  SpringEngineEventListeners - the Spring "eventing bridge" of the Camunda starter
 * =============================================================================================
 *
 * <p>With {@code camunda.bpm.eventing.task=true} (application.yaml) the starter registers a
 * global task listener that re-publishes every task event (create, assignment, complete,
 * update, delete, timeout) as a Spring {@link TaskEvent}. Same for executions
 * ({@code ExecutionEvent}) and history events.</p>
 *
 * <h2>When to use what?</h2>
 * <ul>
 *   <li>BPMN task/execution listener - logic that belongs to ONE model element
 *       (see {@link com.academy.camunda.delegate.loan.UnderwriterTaskListener}).</li>
 *   <li>Parse listener plugin - cross-cutting logic for ALL elements of a type
 *       (see {@link AuditProcessEnginePlugin}).</li>
 *   <li>Spring eventing - decoupled Spring components that just want to react (notifications,
 *       metrics, push updates to a UI via WebSocket), without referencing the model.</li>
 * </ul>
 *
 * <p>IMPORTANT: the listener runs synchronously INSIDE the engine transaction. An exception here
 * rolls back the user's "complete task" call! Keep it fast and safe. If you need "after commit"
 * semantics use {@code @TransactionalEventListener}.</p>
 */
@Component
public class SpringEngineEventListeners {

  private static final Logger LOG = LoggerFactory.getLogger(SpringEngineEventListeners.class);

  @EventListener
  public void onTaskEvent(TaskEvent event) {
    LOG.info("[spring-eventing] task '{}' ({}) -> {} | assignee={} | processInstance={}",
        event.getName(), event.getTaskDefinitionKey(), event.getEventName(),
        event.getAssignee(), event.getProcessInstanceId());
  }
}
