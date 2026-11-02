package com.academy.camunda.delegate.loan;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * =============================================================================================
 *  DisburseLoanDelegate - money movement behind an async boundary (bean "disburseLoanDelegate")
 * =============================================================================================
 *
 * <h2>Used by</h2>
 * {@code Task_DisburseLoan}: {@code camunda:asyncBefore="true" camunda:jobPriority="100"}.
 *
 * <h2>Why asyncBefore here?</h2>
 * Without it, the underwriter's "complete task" click would run this delegate in the SAME
 * transaction. If the core banking call succeeded but a later step failed, the engine would
 * roll back - and the task would re-appear although the money is already gone.
 * With asyncBefore the approval is committed first; disbursement runs in its own job.
 *
 * <h2>Idempotency (very important with retries)</h2>
 * A job may run more than once (retry after a timeout where the remote call actually
 * succeeded, or job lock expiry on a slow node). External calls must be IDEMPOTENT: pass a
 * stable idempotency key - here the process instance id + activity id - so the bank can detect
 * duplicates.
 */
@Component
public class DisburseLoanDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(DisburseLoanDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    String idempotencyKey = execution.getProcessInstanceId() + ":" + execution.getCurrentActivityId();
    Number amount = (Number) execution.getVariable("loanAmount");

    // ... call core banking with idempotencyKey ...
    String account = "LN-" + Math.abs(idempotencyKey.hashCode());

    execution.setVariable("loanAccountNumber", account);
    execution.setVariable("disbursedAt", new Date());
    LOG.info("Disbursed {} to loan account {} (key {})", amount, account, idempotencyKey);
  }
}
