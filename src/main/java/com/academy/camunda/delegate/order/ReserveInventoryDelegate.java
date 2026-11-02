package com.academy.camunda.delegate.order;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * =============================================================================================
 *  ReserveInventoryDelegate - a COMPENSABLE activity (bean "reserveInventoryDelegate")
 * =============================================================================================
 *
 * <h2>Used by</h2> {@code Task_ReserveInventory}, which has a compensation boundary event
 * associated with {@code Task_ReleaseInventory} (isForCompensation="true").
 *
 * <h2>Compensation in one paragraph</h2>
 * Long-running processes cannot keep a database transaction open for hours, so "undo" must be
 * modelled as a business action: for each step that changes something outside, define the
 * step that reverses it. When a compensation event is thrown, the engine calls the handlers of
 * all SUCCESSFULLY COMPLETED compensable activities of that scope, in reverse order of
 * completion. Activities that never ran (or are still running) are not compensated.
 * This is the SAGA pattern - BPMN gives it to you declaratively.
 *
 * <p>Tip: store everything the undo step needs (the reservation id) as a variable. The
 * compensation handler sees the variables as they are at compensation time.</p>
 */
@Component
public class ReserveInventoryDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(ReserveInventoryDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    String reservationId = "RSV-" + UUID.randomUUID().toString().substring(0, 8);
    execution.setVariable("reservationId", reservationId);
    LOG.info("Stock reserved for order {} -> {}", execution.getProcessBusinessKey(), reservationId);
  }
}
