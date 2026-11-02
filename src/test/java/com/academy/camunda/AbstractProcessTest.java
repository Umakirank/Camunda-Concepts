package com.academy.camunda;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;

/**
 * =============================================================================================
 *  AbstractProcessTest - how to test Camunda 7 processes with Spring Boot
 * =============================================================================================
 *
 * <h2>Strategy</h2>
 * <ul>
 *   <li>Full Spring context + real engine on an IN-MEMORY H2 database: delegates, listeners,
 *       expressions, DMN and BPMN are tested together exactly as they run in production.</li>
 *   <li>{@code job-execution.enabled=false}: the job executor is OFF, so NOTHING happens in the
 *       background. Timers and async continuations stay as jobs until the test executes them
 *       with {@code execute(job(...))}. That makes tests deterministic - the #1 rule.</li>
 *   <li>The scheduled payment worker is off; tests call {@code pollAndProcess()} themselves.</li>
 *   <li>camunda-bpm-assert gives a fluent API: {@code assertThat(pi).isWaitingAt("Task_X")},
 *       {@code hasPassed(...)}, {@code isEnded()}, {@code task()}, {@code complete(...)}.</li>
 * </ul>
 * The Spring context (and the engine) is created once and cached for all test classes that
 * share this configuration - so tests must not depend on a clean database. Always query BY
 * PROCESS INSTANCE id.
 *
 * <p>Alternative for fast unit tests without Spring: {@code @ExtendWith(ProcessEngineExtension.class)}
 * + {@code @Deployment(resources = "bpmn/x.bpmn")} + {@code Mocks.register("beanName", mock)}.</p>
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:camunda-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "camunda.bpm.job-execution.enabled=false",
    "camunda.bpm.generate-unique-process-engine-name=false",
    "academy.workers.payment.enabled=false"
})
public abstract class AbstractProcessTest {

  @Autowired protected ProcessEngine processEngine;
  @Autowired protected RuntimeService runtimeService;
  @Autowired protected TaskService taskService;
  @Autowired protected HistoryService historyService;

  @BeforeEach
  void initAssertions() {
    init(processEngine); // tell camunda-bpm-assert which engine to use
  }

  /** Reads a variable from HISTORY - works for running AND ended instances. */
  protected Object historicVariable(String processInstanceId, String name) {
    HistoricVariableInstance v = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstanceId).variableName(name).singleResult();
    return v == null ? null : v.getValue();
  }

  protected List<Task> tasks(String processInstanceId, String taskDefinitionKey) {
    return taskService.createTaskQuery().processInstanceId(processInstanceId)
        .taskDefinitionKey(taskDefinitionKey).list();
  }
}
