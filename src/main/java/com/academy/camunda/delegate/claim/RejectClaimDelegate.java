package com.academy.camunda.delegate.claim;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  RejectClaimDelegate - runs after an ERROR thrown by a CALLED process
 * =============================================================================================
 *
 * <h2>Path</h2>
 * fraud-check.bpmn ends in the ERROR END EVENT "FRAUD_CONFIRMED" -> the called instance is
 * finished -> the engine propagates the error to the CALL ACTIVITY in the parent ->
 * {@code Boundary_FraudConfirmed} catches it (same error code) -> this delegate.
 *
 * <p>Note: out-mappings ({@code camunda:out}) are NOT applied when the child ends with an error;
 * the call activity is interrupted. If you need data from the child in that case, pass it with
 * the error (camunda:errorMessageVariable / variables) or read it from history.</p>
 */
@Component
public class RejectClaimDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(RejectClaimDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable("claimStatus", "REJECTED_FRAUD");
    LOG.warn("Claim {} rejected: fraud confirmed", execution.getVariable("claimId"));
  }
}
