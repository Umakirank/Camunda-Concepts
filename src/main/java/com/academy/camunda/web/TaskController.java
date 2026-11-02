package com.academy.camunda.web;

import com.academy.camunda.service.TaskWorkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * =============================================================================================
 *  TaskController - a minimal "custom task list" API
 * =============================================================================================
 * <pre>
 *  GET  /api/tasks?user=anil                       inbox (assigned + candidate)
 *  POST /api/tasks/{id}/claim?user=anil
 *  GET  /api/tasks/{id}/form-variables
 *  POST /api/tasks/{id}/complete?user=anil         body: {"approved":true,"comment":"ok"}
 * </pre>
 * SECURITY NOTE: in a real app the user comes from the authenticated principal (Spring
 * Security / JWT), never from a request parameter, and you check that the user may work on the
 * task (assignee or candidate) before completing it. Camunda's AuthorizationService can enforce
 * this inside the engine when {@code camunda.bpm.authorization.enabled=true} and
 * {@code identityService.setAuthentication(user, groups)} is set per request.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  private final TaskWorkService tasks;

  public TaskController(TaskWorkService tasks) {
    this.tasks = tasks;
  }

  @GetMapping
  public List<Map<String, Object>> inbox(@RequestParam String user) {
    return tasks.inbox(user);
  }

  @PostMapping("/{taskId}/claim")
  public ResponseEntity<Void> claim(@PathVariable String taskId, @RequestParam String user) {
    tasks.claim(taskId, user);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{taskId}/form-variables")
  public Map<String, Object> form(@PathVariable String taskId) {
    return tasks.formVariables(taskId);
  }

  @PostMapping("/{taskId}/complete")
  public ResponseEntity<Void> complete(@PathVariable String taskId, @RequestParam(required = false) String user,
                                       @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> variables = body == null ? new HashMap<>() : new HashMap<>(body);
    Object comment = variables.remove("comment");
    tasks.complete(taskId, variables, comment == null ? null : comment.toString(), user);
    return ResponseEntity.noContent().build();
  }
}
