package com.academy.camunda.service;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.Job;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * =============================================================================================
 *  OperationsService - what an operator / support engineer does (Cockpit in code)
 * =============================================================================================
 *
 * <h2>Covered</h2>
 * <ul>
 *   <li>Incidents: list, fix and retry failed jobs ({@link ManagementService})</li>
 *   <li>Execute a job now (e.g. fire a 7-day timer during a test or a demo)</li>
 *   <li>Suspend / activate an instance (frozen: no tasks can be completed, no jobs run)</li>
 *   <li>PROCESS INSTANCE MODIFICATION: move a token (repair a wrong path)</li>
 *   <li>PROCESS INSTANCE MIGRATION: move running instances to a new definition version</li>
 *   <li>Delete (cancel) an instance with a reason</li>
 * </ul>
 * All of these are also available in Cockpit (modification and migration are Enterprise-only
 * in the Cockpit UI, but the Java/REST APIs are part of Community Edition).
 */
@Service
public class OperationsService {

  private final ManagementService managementService;
  private final RuntimeService runtimeService;
  private final RepositoryService repositoryService;

  public OperationsService(ManagementService managementService, RuntimeService runtimeService,
                           RepositoryService repositoryService) {
    this.managementService = managementService;
    this.runtimeService = runtimeService;
    this.repositoryService = repositoryService;
  }

  /**
   * Incidents = "the engine needs a human". Types: failedJob (async continuation/timer threw
   * with no retries left), failedExternalTask (worker reported retries=0), plus custom types.
   */
  public List<Map<String, Object>> openIncidents() {
    return runtimeService.createIncidentQuery().orderByIncidentTimestamp().desc().list()
        .stream().map(OperationsService::incident).toList();
  }

  /**
   * "Retry" in Cockpit = give the job retries again. The job executor picks it up on its next
   * acquisition cycle; the incident is resolved automatically when retries &gt; 0.
   */
  public void retryJob(String jobId) {
    managementService.setJobRetries(jobId, 1);
  }

  /** Executes a job synchronously in the caller's thread (timers, async continuations). */
  public void executeJobNow(String jobId) {
    managementService.executeJob(jobId);
  }

  public List<Map<String, Object>> jobsOf(String processInstanceId) {
    return managementService.createJobQuery().processInstanceId(processInstanceId).list()
        .stream().map(OperationsService::job).toList();
  }

  public void suspend(String processInstanceId) {
    runtimeService.updateProcessInstanceSuspensionState()
        .byProcessInstanceId(processInstanceId).suspend();
  }

  public void activate(String processInstanceId) {
    runtimeService.updateProcessInstanceSuspensionState()
        .byProcessInstanceId(processInstanceId).activate();
  }

  /**
   * PROCESS INSTANCE MODIFICATION - e.g. a loan went to "HIGH" because of a wrong DMN rule:
   * cancel the token at the rejection task and start it again before underwriting.
   * Listeners and IO mappings run by default; {@code .execute(true, true)} skips them.
   */
  public void moveToken(String processInstanceId, String fromActivityId, String toActivityId, String reason) {
    runtimeService.createProcessInstanceModification(processInstanceId)
        .cancelAllForActivity(fromActivityId)
        .startBeforeActivity(toActivityId)
        .setAnnotation(reason)            // shown in the user operation log
        .execute();
  }

  /**
   * PROCESS INSTANCE MIGRATION - after deploying version N+1 of a process, running instances
   * stay on version N. A migration plan maps activities of the old version to the new one;
   * {@code mapEqualActivities()} maps all activities with identical ids. Run it async (batch)
   * for many instances: {@code executeAsync()}.
   */
  public int migrateAllToLatest(String processDefinitionKey) {
    List<ProcessDefinition> versions = repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(processDefinitionKey).orderByProcessDefinitionVersion().desc().list();
    if (versions.size() < 2) {
      return 0; // nothing to migrate
    }
    ProcessDefinition latest = versions.get(0);
    int migrated = 0;
    for (ProcessDefinition old : versions.subList(1, versions.size())) {
      long count = runtimeService.createProcessInstanceQuery().processDefinitionId(old.getId()).count();
      if (count == 0) {
        continue;
      }
      MigrationPlan plan = runtimeService.createMigrationPlan(old.getId(), latest.getId())
          .mapEqualActivities()
          .build();
      runtimeService.newMigration(plan)
          .processInstanceQuery(runtimeService.createProcessInstanceQuery().processDefinitionId(old.getId()))
          .execute();
      migrated += (int) count;
    }
    return migrated;
  }

  /** Cancels a running instance. History keeps it with state EXTERNALLY_TERMINATED. */
  public void cancel(String processInstanceId, String reason) {
    runtimeService.deleteProcessInstance(processInstanceId, reason);
  }

  private static Map<String, Object> incident(Incident i) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", i.getId());
    m.put("type", i.getIncidentType());
    m.put("message", i.getIncidentMessage());
    m.put("activityId", i.getActivityId());
    m.put("processInstanceId", i.getProcessInstanceId());
    m.put("jobId", i.getConfiguration()); // for failedJob incidents the configuration is the job id
    m.put("time", i.getIncidentTimestamp());
    return m;
  }

  private static Map<String, Object> job(Job j) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", j.getId());
    m.put("dueDate", j.getDuedate());
    m.put("retries", j.getRetries());
    m.put("exception", j.getExceptionMessage());
    m.put("priority", j.getPriority());
    return m;
  }
}
