package com.academy.camunda.delegate.claim;

import com.academy.camunda.config.ProcessConstants;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * =============================================================================================
 *  DailyClaimsReportDelegate - TIMER START EVENT + querying HISTORY from a delegate
 * =============================================================================================
 *
 * <h2>Used by</h2> daily-claims-report.bpmn, started every day at 07:00 by a timer start event
 * (cron "0 0 7 * * ?").
 *
 * <h2>Runtime vs. history tables</h2>
 * <ul>
 *   <li>ACT_RU_* (RuntimeService, TaskService): only what is RUNNING now. Rows are deleted when
 *       an instance ends. Small and fast - the engine's working memory.</li>
 *   <li>ACT_HI_* (HistoryService): everything that ever happened (depending on history level),
 *       including finished instances. Grows forever unless history cleanup removes rows whose
 *       historyTimeToLive expired.</li>
 * </ul>
 * Reporting must use HISTORY. For heavy analytics use Camunda Optimize or export history to a
 * data warehouse - do not run big reporting queries against the engine database at peak time.
 */
@Component
public class DailyClaimsReportDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(DailyClaimsReportDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    HistoryService history = execution.getProcessEngineServices().getHistoryService();
    RuntimeService runtime = execution.getProcessEngineServices().getRuntimeService();
    Date since = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));

    long finished = history.createHistoricProcessInstanceQuery()
        .processDefinitionKey(ProcessConstants.PROCESS_CLAIM)
        .finishedAfter(since)
        .count();
    long started = history.createHistoricProcessInstanceQuery()
        .processDefinitionKey(ProcessConstants.PROCESS_CLAIM)
        .startedAfter(since)
        .count();
    long running = runtime.createProcessInstanceQuery()
        .processDefinitionKey(ProcessConstants.PROCESS_CLAIM)
        .active()
        .count();
    long incidents = runtime.createIncidentQuery().count();

    execution.setVariable("claimsStarted24h", started);
    execution.setVariable("claimsFinished24h", finished);
    execution.setVariable("claimsRunning", running);
    LOG.info("[daily report] claims started={} finished={} running={} | open incidents (all processes)={}",
        started, finished, running, incidents);
  }
}
