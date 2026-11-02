package com.academy.camunda.web;

import org.camunda.bpm.engine.MismatchingMessageCorrelationException;
import org.camunda.bpm.engine.OptimisticLockingException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.TaskAlreadyClaimedException;
import org.camunda.bpm.engine.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * =============================================================================================
 *  ApiExceptionHandler - translating engine exceptions into meaningful HTTP answers
 * =============================================================================================
 *
 * <h2>The engine exceptions you will meet most</h2>
 * <table border="1">
 *  <tr><th>Exception</th><th>Meaning</th><th>HTTP</th></tr>
 *  <tr><td>MismatchingMessageCorrelationException</td><td>No (or more than one) receiver waits
 *      for that message - too early, too late, wrong business key, or wrong message NAME.</td><td>409</td></tr>
 *  <tr><td>TaskAlreadyClaimedException</td><td>Someone else claimed the task first.</td><td>409</td></tr>
 *  <tr><td>OptimisticLockingException</td><td>Two transactions changed the same execution
 *      concurrently (e.g. two messages at once). Safe to retry the request.</td><td>409</td></tr>
 *  <tr><td>NotFoundException / NullValueException</td><td>Unknown id / key.</td><td>404</td></tr>
 *  <tr><td>ProcessEngineException (generic)</td><td>Expression errors ("Unknown property used
 *      in expression"), no outgoing flow, DMN hit policy violation, deployment parse errors...</td><td>400/500</td></tr>
 * </table>
 * The original message is returned so you can learn from it - in production, return an error
 * id and log the details instead of leaking internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler({MismatchingMessageCorrelationException.class, TaskAlreadyClaimedException.class,
      OptimisticLockingException.class})
  public ResponseEntity<Map<String, String>> conflict(ProcessEngineException e) {
    return error(HttpStatus.CONFLICT, e);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, String>> notFound(ProcessEngineException e) {
    return error(HttpStatus.NOT_FOUND, e);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage())));
  }

  @ExceptionHandler(ProcessEngineException.class)
  public ResponseEntity<Map<String, String>> engine(ProcessEngineException e) {
    LOG.error("Engine error", e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, e);
  }

  private ResponseEntity<Map<String, String>> error(HttpStatus status, Exception e) {
    return ResponseEntity.status(status)
        .body(Map.of("error", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage())));
  }
}
