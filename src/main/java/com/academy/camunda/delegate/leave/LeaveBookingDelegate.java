package com.academy.camunda.delegate.leave;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  LeaveBookingDelegate - final booking step of leave-approval.bpmn
 * =============================================================================================
 *
 * <p>Reached from two paths merged by {@code Gateway_MergeApproved}: the DMN said "AUTO", or a
 * human approved. The delegate does not care which - one of the benefits of explicit merge
 * gateways is that downstream steps have a single, clear entry.</p>
 *
 * <p>Note the DMN DRD: "eligible" and "eligibilityReason" were computed by the REQUIRED decision
 * leave-eligibility but are NOT process variables - only the root decision's result
 * (approvalLevel) is returned to the process. Look at Cockpit -> Decisions to see both
 * evaluations with their inputs/outputs.</p>
 */
@Component
public class LeaveBookingDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(LeaveBookingDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    String type = (String) execution.getVariable("leaveType");
    long requested = ((Number) execution.getVariable("requestedDays")).longValue();
    long balance = ((Number) execution.getVariable("availableBalance")).longValue();
    long remaining = "SICK".equals(type) ? balance : balance - requested;

    execution.setVariable("leaveStatus", "APPROVED");
    execution.setVariable("remainingBalance", remaining);
    LOG.info("{} days of {} leave booked for {} (remaining balance {})", requested, type,
        execution.getVariable("employeeName"), remaining);
  }
}
