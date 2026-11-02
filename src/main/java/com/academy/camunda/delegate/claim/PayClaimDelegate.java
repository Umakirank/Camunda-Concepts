package com.academy.camunda.delegate.claim;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  PayClaimDelegate - INPUT / OUTPUT VARIABLE MAPPING
 * =============================================================================================
 *
 * <h2>Used by</h2> {@code Task_PayClaim}:
 * <pre>{@code
 * <camunda:inputOutput>
 *   <camunda:inputParameter name="amount">${payoutAmount}</camunda:inputParameter>
 *   <camunda:inputParameter name="beneficiary">${claimantEmail}</camunda:inputParameter>
 *   <camunda:inputParameter name="paymentReference">${claimId}-PAY</camunda:inputParameter>
 *   <camunda:outputParameter name="paymentStatus">${paymentStatusLocal}</camunda:outputParameter>
 * </camunda:inputOutput>
 * }</pre>
 *
 * <h2>What happens internally</h2>
 * <ol>
 *   <li>On activity start the engine creates a LOCAL variable scope for this activity instance
 *       and evaluates each inputParameter into it.</li>
 *   <li>The delegate reads them with {@code getVariableLocal} - it does not need to know the
 *       process's variable names ("payoutAmount", "claimantEmail"). That makes a delegate
 *       REUSABLE in many processes: each model maps its own variables onto the delegate's
 *       contract.</li>
 *   <li>Anything set with {@code setVariableLocal} stays local and is DISCARDED when the activity
 *       ends - except what outputParameters copy to the parent scope.</li>
 * </ol>
 * Camunda 8 uses exactly this idea (input/output mappings on every element, FEEL expressions).
 */
@Component
public class PayClaimDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(PayClaimDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    Number amount = (Number) execution.getVariableLocal("amount");
    Object beneficiary = execution.getVariableLocal("beneficiary");
    Object reference = execution.getVariableLocal("paymentReference");

    LOG.info("Paying {} to {} (ref {})", amount, beneficiary, reference);

    // Local result -> mapped to "paymentStatus" by the outputParameter.
    execution.setVariableLocal("paymentStatusLocal", "PAID");
  }
}
