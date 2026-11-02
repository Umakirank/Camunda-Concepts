package com.academy.camunda.delegate.common;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.Expression;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * =============================================================================================
 *  EmailNotificationDelegate - the "camunda:class" style + FIELD INJECTION
 * =============================================================================================
 *
 * <h2>Used by</h2>
 * Every send task in the project, e.g. in loan-approval.bpmn:
 * <pre>{@code
 * <bpmn:sendTask id="Task_SendApproval"
 *     camunda:class="com.academy.camunda.delegate.common.EmailNotificationDelegate">
 *   <bpmn:extensionElements>
 *     <camunda:field name="template"><camunda:string>LOAN_APPROVED</camunda:string></camunda:field>
 *   </bpmn:extensionElements>
 * </bpmn:sendTask>
 * }</pre>
 *
 * <h2>Three ways to attach Java code to a service/send task - compare them</h2>
 * <table border="1">
 *  <tr><th>Attribute</th><th>Who creates the object?</th><th>Spring injection?</th></tr>
 *  <tr><td>camunda:class</td><td>The ENGINE (reflection, via no-arg constructor). NOT a Spring
 *      bean -> no @Autowired.</td><td>No</td></tr>
 *  <tr><td>camunda:delegateExpression="${bean}"</td><td>Spring (the expression resolves a bean
 *      that implements JavaDelegate)</td><td>Yes - the recommended style</td></tr>
 *  <tr><td>camunda:expression="${bean.method(x)}"</td><td>Spring; any method, any signature;
 *      return value -> camunda:resultVariable</td><td>Yes</td></tr>
 * </table>
 *
 * <h2>Field injection</h2>
 * {@code <camunda:field name="template">} makes the engine call {@link #setTemplate(Expression)}
 * (or set a field of type Expression). Values can be fixed strings ({@code camunda:string}) or
 * expressions ({@code camunda:expression>${applicantEmail}}). Always declare the field as
 * {@link Expression} and call {@code getValue(execution)} at runtime.
 *
 * <h2>Thread-safety warning (classic interview question)</h2>
 * The engine creates ONE instance of this class per BPMN activity and reuses it for every
 * process instance, concurrently. Therefore the class must be stateless: the injected
 * Expression is immutable configuration, all runtime data comes from the {@code execution}.
 * Never store per-instance data in fields.
 *
 * <p>A real implementation would call an e-mail service/SMTP. Sending mail is NOT
 * transactional: if the engine transaction later rolls back, the mail is already gone. Use
 * asyncBefore on the send task or an outbox pattern if duplicates/ghost mails matter.</p>
 */
public class EmailNotificationDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationDelegate.class);

  /** Injected from {@code <camunda:field name="template">}. */
  private Expression template;

  public void setTemplate(Expression template) {
    this.template = template;
  }

  @Override
  public void execute(DelegateExecution execution) {
    String templateName = template != null ? String.valueOf(template.getValue(execution)) : "GENERIC";

    // Look for a recipient in the usual variable names of our processes. getVariable searches
    // the current scope and then walks up to the process instance scope.
    Object recipient = firstNonNull(execution,
        "applicantEmail", "claimantEmail", "employeeEmail", "customerEmail");

    LOG.info("[mail] template={} to={} businessKey={} activity={}",
        templateName, recipient != null ? recipient : "<unknown>",
        execution.getProcessBusinessKey(), execution.getCurrentActivityName());

    // Record what we did - visible in Cockpit / history, and asserted in the tests.
    execution.setVariable("lastNotification", templateName);
    execution.setVariable("lastNotificationAt", new Date());
  }

  private Object firstNonNull(DelegateExecution execution, String... names) {
    for (String name : names) {
      Object value = execution.getVariable(name);
      if (value != null) {
        return value;
      }
    }
    return null;
  }
}
