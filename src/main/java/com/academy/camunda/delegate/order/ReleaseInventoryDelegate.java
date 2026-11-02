package com.academy.camunda.delegate.order;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  ReleaseInventoryDelegate - COMPENSATION HANDLER of "Reserve inventory"
 * =============================================================================================
 *
 * <p>Only executed when compensation is triggered (Event_CompensateOrder after the 1-hour
 * shipping timeout). A compensation handler has no incoming/outgoing sequence flows; it is
 * connected to the compensation boundary event by an ASSOCIATION.</p>
 *
 * <p>Compensation handlers should be idempotent and must not throw business errors - there is
 * nobody left to catch them. A technical exception rolls back like any other step.</p>
 */
@Component
public class ReleaseInventoryDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(ReleaseInventoryDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    LOG.warn("COMPENSATION: releasing reservation {}", execution.getVariable("reservationId"));
    execution.setVariable("inventoryReleased", true);
  }
}
