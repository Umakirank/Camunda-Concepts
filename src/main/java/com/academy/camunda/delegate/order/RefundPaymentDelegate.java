package com.academy.camunda.delegate.order;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  RefundPaymentDelegate - COMPENSATION HANDLER of the EXTERNAL task "Charge payment"
 * =============================================================================================
 *
 * <p>Interesting detail: the original step was done by an external worker, but its
 * compensation can be a normal Java delegate (or another external task - both are fine).
 * The worker stored "paymentTransactionId" when it completed the task; we need it to refund.</p>
 */
@Component
public class RefundPaymentDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(RefundPaymentDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    LOG.warn("COMPENSATION: refunding transaction {} amount {}",
        execution.getVariable("paymentTransactionId"), execution.getVariable("payableAmount"));
    execution.setVariable("paymentRefunded", true);
  }
}
