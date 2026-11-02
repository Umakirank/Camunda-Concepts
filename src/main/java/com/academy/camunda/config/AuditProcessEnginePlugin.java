package com.academy.camunda.config;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * =============================================================================================
 *  AuditProcessEnginePlugin - extending the ENGINE itself (not a single process)
 * =============================================================================================
 *
 * <h2>Process engine plugins</h2>
 * A {@code ProcessEnginePlugin} gets three callbacks while the engine is being built:
 * <ul>
 *   <li>{@code preInit(cfg)}  - before defaults are applied: register parse listeners, custom
 *       history handlers, incident handlers, job handlers, variable serializers, ...</li>
 *   <li>{@code postInit(cfg)} - after defaults: tweak already initialised components.</li>
 *   <li>{@code postProcessEngineBuild(engine)} - engine exists: e.g. create users/filters.</li>
 * </ul>
 * In Spring Boot every bean implementing {@code ProcessEnginePlugin} is registered automatically.
 * Real plugins you will meet: LDAP identity, Spin, Connect, "camunda-bpm-reactor", Prometheus
 * metrics, custom history event handlers (e.g. stream history to Kafka/Elastic).
 *
 * <h2>BPMN parse listeners - the most useful extension point</h2>
 * While a BPMN file is DEPLOYED (parsed), the engine converts each XML element into an internal
 * PVM (Process Virtual Machine) object: {@link ActivityImpl}, {@link ScopeImpl}, transitions.
 * A {@link BpmnParseListener} is called for every element and may decorate it - here we attach an
 * {@link ExecutionListener} to EVERY service task of EVERY process without touching a single
 * BPMN file. This is how companies enforce cross-cutting concerns (audit, metrics, MDC logging,
 * SLA tracking) consistently.
 *
 * <p>Note: parse listeners only affect models deployed AFTER the plugin is active (the parse
 * happens at deployment / when the definition cache is (re)filled).</p>
 */
@Component
public class AuditProcessEnginePlugin extends AbstractProcessEnginePlugin {

  private static final Logger LOG = LoggerFactory.getLogger(AuditProcessEnginePlugin.class);

  @Override
  public void preInit(ProcessEngineConfigurationImpl configuration) {
    // "Pre" parse listeners run before the engine's own listeners, "post" ones after.
    List<BpmnParseListener> listeners = configuration.getCustomPostBPMNParseListeners();
    if (listeners == null) {
      listeners = new ArrayList<>();
      configuration.setCustomPostBPMNParseListeners(listeners);
    }
    listeners.add(new ServiceTaskTimingParseListener());
    LOG.info("AuditProcessEnginePlugin registered a global BPMN parse listener");
  }

  /** Called for each &lt;serviceTask&gt; while parsing; adds the timing listener to it. */
  static class ServiceTaskTimingParseListener extends AbstractBpmnParseListener {
    private final ExecutionListener timingListener = new ServiceTaskTimingListener();

    @Override
    public void parseServiceTask(Element serviceTaskElement, ScopeImpl scope, ActivityImpl activity) {
      activity.addListener(ExecutionListener.EVENTNAME_START, timingListener);
      activity.addListener(ExecutionListener.EVENTNAME_END, timingListener);
    }
  }

  /**
   * Measures how long each service task took. Stores the start time as an execution-LOCAL
   * variable (it disappears when the activity instance ends - no pollution of the process).
   * Stateless (no fields) => thread-safe => one shared instance is fine.
   */
  static class ServiceTaskTimingListener implements ExecutionListener {
    private static final String START = "_serviceTaskStartedAt";

    @Override
    public void notify(DelegateExecution execution) {
      if (EVENTNAME_START.equals(execution.getEventName())) {
        execution.setVariableLocal(START, System.currentTimeMillis());
      } else {
        Object started = execution.getVariableLocal(START);
        if (started instanceof Long startedAt) {
          LOG.debug("[timing] {} / {} took {} ms", execution.getProcessDefinitionId(),
              execution.getCurrentActivityId(), System.currentTimeMillis() - startedAt);
        }
      }
    }
  }
}
