package com.academy.camunda.service;

import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.VariableMap;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * =============================================================================================
 *  TaskWorkService - TaskService + FormService: everything a custom task UI needs
 * =============================================================================================
 *
 * <p>Camunda Tasklist covers most needs, but many companies build their own UI (React/Angular,
 * or Appian/Salesforce embedding Camunda tasks). This class shows the API such a UI uses.</p>
 *
 * <h2>The human task lifecycle</h2>
 * <pre>
 *  created --(claim)--> assigned --(complete)--> gone from ACT_RU_TASK, token moves on
 *     |                    |--(unclaim)--> back to the group
 *     |                    |--(delegate)--> someone else works, owner must resolve
 *     `-- deleted by an interrupting event / instance cancellation
 * </pre>
 *
 * <h2>Candidate vs. assignee</h2>
 * {@code taskCandidateUser(user)} automatically includes the user's GROUPS (resolved through the
 * IdentityService). "My tasks" in a typical inbox = assigned to me OR claimable by me:
 * {@code .or().taskAssignee(u).taskCandidateUser(u).endOr()}.
 *
 * <p>Never return {@link Task} entities directly as JSON - they are engine objects with lazy
 * fields. Map them to DTOs (here: simple maps).</p>
 */
@Service
public class TaskWorkService {

  private final TaskService taskService;
  private final FormService formService;

  public TaskWorkService(TaskService taskService, FormService formService) {
    this.taskService = taskService;
    this.formService = formService;
  }

  /** Inbox: tasks assigned to the user plus tasks of the user's candidate groups. */
  public List<Map<String, Object>> inbox(String userId) {
    return taskService.createTaskQuery()
        .or()
          .taskAssignee(userId)
          .taskCandidateUser(userId)
        .endOr()
        .active()                       // excludes tasks of suspended instances
        .orderByTaskPriority().desc()
        .orderByTaskCreateTime().asc()
        .list().stream().map(TaskWorkService::toDto).toList();
  }

  /**
   * Claim = set assignee, but fails with TaskAlreadyClaimedException if someone else was faster.
   * Use setAssignee for an admin "force assign".
   */
  public void claim(String taskId, String userId) {
    taskService.claim(taskId, userId);
  }

  /**
   * Completes a task. Variables are written to the PROCESS scope (unless a variable with that
   * name exists in a nearer scope) and the token continues synchronously until the next wait
   * state - so this call can take a while if many service tasks follow (use async to decouple).
   */
  public void complete(String taskId, Map<String, Object> variables, String comment, String userId) {
    Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
    if (task == null) {
      throw new IllegalArgumentException("Task " + taskId + " does not exist (already completed?)");
    }
    if (comment != null && !comment.isBlank()) {
      // Comments are stored in ACT_HI_COMMENT and visible in Tasklist/Cockpit history.
      taskService.createComment(taskId, task.getProcessInstanceId(), comment);
    }
    taskService.complete(taskId, ProcessLifecycleService.normalizeNumbers(variables));
  }

  /**
   * The variables a generated / Camunda form would show for this task. FormService applies
   * the form field definitions (camunda:formData) - only declared fields are returned.
   */
  public Map<String, Object> formVariables(String taskId) {
    VariableMap vars = formService.getTaskFormVariables(taskId);
    return new LinkedHashMap<>(vars);
  }

  /** "Submit form" = validate against form field constraints (required, readonly...) + complete. */
  public void submitForm(String taskId, Map<String, Object> values) {
    formService.submitTaskForm(taskId, ProcessLifecycleService.normalizeNumbers(values));
  }

  static Map<String, Object> toDto(Task t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.getId());
    m.put("name", t.getName());
    m.put("taskDefinitionKey", t.getTaskDefinitionKey());
    m.put("assignee", t.getAssignee());
    m.put("priority", t.getPriority());
    m.put("created", t.getCreateTime());
    m.put("due", t.getDueDate());
    m.put("processInstanceId", t.getProcessInstanceId());
    return m;
  }
}
