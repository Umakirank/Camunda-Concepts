package com.academy.camunda.delegate.loan;

import com.academy.camunda.config.ProcessConstants;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.regex.Pattern;

/**
 * =============================================================================================
 *  ValidateLoanApplicationDelegate - JavaDelegate + BPMN ERROR  (bean "validateLoanApplicationDelegate")
 * =============================================================================================
 *
 * <h2>Used by</h2>
 * Service task {@code Task_ValidateApplication} in loan-approval.bpmn:
 * {@code camunda:delegateExpression="${validateLoanApplicationDelegate}"}.
 *
 * <h2>How the engine calls it (internal working)</h2>
 * <ol>
 *   <li>A token arrives at the service task. The engine evaluates the delegate expression,
 *       which asks Spring's ApplicationContext for the bean named "validateLoanApplicationDelegate".</li>
 *   <li>It calls {@link #execute(DelegateExecution)} on the CURRENT thread, inside the CURRENT
 *       database transaction (the transaction of whoever called runtimeService.start...).</li>
 *   <li>Normal return  -> the token leaves via the outgoing sequence flow.</li>
 *   <li>{@link BpmnError} -> a BUSINESS error: the engine looks for a matching error boundary
 *       event / error event sub-process (by error code) and continues there. No rollback.</li>
 *   <li>Any other exception -> a TECHNICAL error: the whole transaction rolls back up to the
 *       last wait state / async boundary. The caller gets the exception (or, in a job, the job
 *       is retried and finally an incident is created).</li>
 * </ol>
 *
 * <h2>BpmnError vs. Exception - the rule of thumb</h2>
 * "Can the business model a reaction to it?" -> BpmnError (invalid input, credit declined,
 * out of stock). "Is something broken that a retry/operator fixes?" -> exception (DB down,
 * timeout, NullPointerException).
 *
 * <p>If NO boundary event catches the BpmnError the engine ends the instance silently
 * (C7 default) - turn on {@code enableExceptionsAfterUnhandledBpmnError} to get an exception.</p>
 */
@Component
public class ValidateLoanApplicationDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(ValidateLoanApplicationDelegate.class);

  /** Indian PAN format: 5 letters, 4 digits, 1 letter (e.g. ABCDE1234F). */
  private static final Pattern PAN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");

  @Override
  public void execute(DelegateExecution execution) {
    // getVariable returns Object; numbers coming from forms are Long, from JSON may be Integer
    // -> always go through Number to be type-tolerant.
    String pan = (String) execution.getVariable("applicantPan");
    Number income = (Number) execution.getVariable("annualIncome");
    Number amount = (Number) execution.getVariable("loanAmount");

    if (pan == null || !PAN.matcher(pan).matches()) {
      throw invalid("PAN '" + pan + "' is not in the format ABCDE1234F");
    }
    if (income == null || income.longValue() <= 0) {
      throw invalid("Annual income must be positive");
    }
    if (amount == null || amount.longValue() <= 0 || amount.longValue() > 50_000_000L) {
      throw invalid("Loan amount must be between 1 and 5 crore");
    }

    execution.setVariable("applicationValidatedAt", new Date());
    LOG.info("Loan application {} is valid", execution.getProcessBusinessKey());
  }

  private BpmnError invalid(String message) {
    LOG.warn("Invalid loan application: {}", message);
    // errorCode MUST equal bpmn:error/@errorCode referenced by the boundary event.
    // The message is stored in camunda:errorMessageVariable="validationErrorMessage".
    return new BpmnError(ProcessConstants.ERROR_INVALID_APPLICATION, message);
  }
}
