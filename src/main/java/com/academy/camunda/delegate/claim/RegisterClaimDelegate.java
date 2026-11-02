package com.academy.camunda.delegate.claim;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * =============================================================================================
 *  RegisterClaimDelegate - defaults, IDs and a COLLECTION for multi-instance
 * =============================================================================================
 *
 * <h2>Used by</h2> {@code Task_RegisterClaim} in insurance-claim.bpmn.
 *
 * <h2>1. Defensive defaults</h2>
 * Gateway conditions such as {@code ${hasInjury}} and DMN inputs fail if the variable does not
 * exist ("Unknown property used in expression: ${hasInjury}"). A claim started through the REST
 * API may omit optional fields, so we set safe defaults right at the start.
 *
 * <h2>2. The multi-instance collection</h2>
 * {@code UserTask_AdjusterReview} has
 * {@code camunda:collection="${adjusters}" camunda:elementVariable="adjuster"}. The engine reads
 * the list when the activity starts and creates one task per element, putting the element into a
 * LOCAL variable "adjuster" of each instance (used by {@code camunda:assignee="${adjuster}"}).
 *
 * <p>Gotcha: we store a {@code java.util.ArrayList}, NOT {@code List.of(...)}. The list is
 * serialized to JSON together with its class name; {@code List.of} creates an internal JDK class
 * (ImmutableCollections$ListN) that Jackson cannot instantiate on deserialization.</p>
 */
@Component
public class RegisterClaimDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(RegisterClaimDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    String claimId = "CLM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    execution.setVariable("claimId", claimId);
    if (execution.getProcessBusinessKey() == null) {
      execution.setProcessBusinessKey(claimId); // business key can even be set later
    }

    defaultIfMissing(execution, "claimType", "MOTOR");
    defaultIfMissing(execution, "hasInjury", false);
    defaultIfMissing(execution, "daysSincePolicyStart", 365L);
    defaultIfMissing(execution, "previousClaims", 0L);

    execution.setVariable("adjusters", new ArrayList<>(Arrays.asList("john", "mary", "peter")));
    LOG.info("Claim {} registered for policy {}", claimId, execution.getVariable("policyNumber"));
  }

  private void defaultIfMissing(DelegateExecution execution, String name, Object value) {
    if (execution.getVariable(name) == null) {
      execution.setVariable(name, value);
    }
  }
}
