package com.academy.camunda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * =============================================================================================
 *  ENTRY POINT - Camunda 7 Engine Academy
 * =============================================================================================
 *
 * <h2>What happens when you run main()?</h2>
 * <ol>
 *   <li>Spring Boot starts and creates a DataSource (H2) and a transaction manager.</li>
 *   <li>The Camunda starter ({@code camunda-bpm-spring-boot-starter}) sees them and builds a
 *       {@code SpringProcessEngineConfiguration}. Every {@code camunda.bpm.*} property from
 *       application.yaml and every {@code ProcessEnginePlugin} bean (see
 *       {@link com.academy.camunda.config.AuditProcessEnginePlugin}) is applied to it.</li>
 *   <li>The engine is built: it checks/creates the database schema (ACT_RE_*, ACT_RU_*, ACT_HI_*,
 *       ACT_ID_*, ACT_GE_* tables).</li>
 *   <li>Auto-deployment scans the classpath for *.bpmn and *.dmn files and deploys them in ONE
 *       deployment. Unchanged files are skipped (no new version).</li>
 *   <li>The Job Executor thread pool starts. From now on timers and async continuations run.</li>
 *   <li>The web apps (Cockpit / Tasklist / Admin) and the REST API (/engine-rest) are mounted.</li>
 * </ol>
 *
 * <h2>The engine's mental model (memorise this - it is asked in every interview)</h2>
 * <pre>
 *   ProcessEngine
 *    |-- RepositoryService   deployments, process/decision definitions (ACT_RE_*)
 *    |-- RuntimeService      start instances, correlate messages, signals, variables (ACT_RU_*)
 *    |-- TaskService         human tasks: query, claim, complete, comments
 *    |-- FormService         form metadata, submit start/task forms
 *    |-- HistoryService      what happened (ACT_HI_*)
 *    |-- ManagementService   jobs, incidents, DB metadata, retries
 *    |-- IdentityService     users, groups, tenants (ACT_ID_*)
 *    |-- AuthorizationService fine grained permissions
 *    |-- DecisionService     evaluate DMN decisions directly
 *    |-- ExternalTaskService fetch-and-lock work items (pull model)
 *    `-- FilterService       saved Tasklist filters
 * </pre>
 * The engine is <b>passive</b>: it only does work when an API call arrives (a thread from
 * your controller) or when the Job Executor picks up a job. It always runs the process
 * <b>synchronously until the next wait state</b> (user task, receive task, message/timer
 * catch event, external task, async continuation), commits the DB transaction and returns.
 *
 * <p>{@code @EnableScheduling} is only needed for our home-made external task poller
 * ({@link com.academy.camunda.worker.PaymentExternalTaskWorker}).</p>
 *
 * <p>Note: we do NOT use {@code @EnableProcessApplication}. That annotation switches the app to
 * "process application" mode (requires META-INF/processes.xml, enables PostDeployEvent and
 * deployment-aware job execution in shared engines). For a single embedded engine plain
 * auto-deployment is simpler.</p>
 */
@SpringBootApplication
@EnableScheduling
public class CamundaAcademyApplication {

  public static void main(String[] args) {
    SpringApplication.run(CamundaAcademyApplication.class, args);
  }
}
