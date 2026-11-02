package com.academy.camunda.delegate.creditcard;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * =============================================================================================
 *  CardIssuingService - several BPMN steps in ONE bean, called via method expressions
 * =============================================================================================
 *
 * <h2>Used by</h2> credit-card-application.bpmn, e.g.
 * {@code camunda:expression="${cardIssuingService.createCardAccount(execution)}"}.
 * "execution" is a built-in expression variable (the current DelegateExecution); "task" exists
 * in task listeners; "authenticatedUserId" holds the current user.
 *
 * <h2>The TRANSACTION SUB-PROCESS story (internal working)</h2>
 * <pre>
 *  [Create card account] -> [Reserve credit line] -> (KYC user task) -> KYC ok? -> end
 *        | compensation          | compensation                          \-> CANCEL END EVENT
 *  [Close card account]    [Release credit line]
 * </pre>
 * When the token reaches the CANCEL end event:
 * <ol>
 *   <li>All active executions inside the transaction are cancelled.</li>
 *   <li>Compensation is triggered for every completed compensable activity INSIDE the
 *       transaction, in reverse order: releaseCreditLine() then closeCardAccount().</li>
 *   <li>The CANCEL BOUNDARY event on the transaction fires and the token continues there
 *       ("Notify KYC failure").</li>
 * </ol>
 * This is a "business transaction" (BPMN semantics) - NOT a database transaction. Each step
 * may have committed long ago (the KYC task can wait days).
 *
 * <p>Design trade-off: a bean with many methods keeps related logic together (one class per
 * bounded context) instead of 5 tiny JavaDelegate classes. Both styles are fine; be
 * consistent within a team.</p>
 */
@Service
public class CardIssuingService {

  private static final Logger LOG = LoggerFactory.getLogger(CardIssuingService.class);

  public void createCardAccount(DelegateExecution execution) {
    String account = "CARD-" + UUID.randomUUID().toString().substring(0, 8);
    execution.setVariable("cardAccountId", account);
    LOG.info("Card account {} created for {}", account, execution.getVariable("applicantName"));
  }

  public void reserveCreditLine(DelegateExecution execution) {
    long income = ((Number) execution.getVariable("monthlyIncome")).longValue();
    long limit = income * 3;
    execution.setVariable("creditLimit", limit);
    LOG.info("Credit line {} reserved on {}", limit, execution.getVariable("cardAccountId"));
  }

  /** Compensation handler of createCardAccount. */
  public void closeCardAccount(DelegateExecution execution) {
    execution.setVariable("cardAccountClosed", true);
    LOG.warn("COMPENSATION: card account {} closed", execution.getVariable("cardAccountId"));
  }

  /** Compensation handler of reserveCreditLine. */
  public void releaseCreditLine(DelegateExecution execution) {
    execution.setVariable("creditLineReleased", true);
    LOG.warn("COMPENSATION: credit line {} released", execution.getVariable("creditLimit"));
  }

  /** Runs with camunda:asyncAfter="true": its effect is committed before the process continues. */
  public void dispatchCard(DelegateExecution execution) {
    execution.setVariable("cardDispatched", true);
    LOG.info("Card for account {} dispatched. Offers: {}", execution.getVariable("cardAccountId"),
        execution.getVariable("offers"));
  }
}
