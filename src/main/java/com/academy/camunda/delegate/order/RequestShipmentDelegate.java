package com.academy.camunda.delegate.order;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * =============================================================================================
 *  RequestShipmentDelegate - SEND TASK + the request/response pattern with MESSAGES
 * =============================================================================================
 *
 * <h2>Used by</h2> {@code Task_RequestShipment} (a send task) in order-fulfillment.bpmn.
 *
 * <h2>Send task vs. service task</h2>
 * Technically identical in C7 (both run Java code). The send task SAYS "we send a message to a
 * partner and do not wait for the answer here". The answer comes back later through the
 * event-based gateway: message "ShipmentConfirmed" or the timer.
 *
 * <h2>The race condition you must know about</h2>
 * If the carrier answered extremely fast (before this transaction committed), the engine would
 * have no message subscription yet and correlation fails ("No process definition or execution
 * matches the parameters"). Options: make the request call AFTER commit (asyncAfter on this
 * task, or an outbox), or let the partner retry. In this demo the carrier answer is a manual REST
 * call, so there is no race.
 *
 * <h2>Correlation key</h2>
 * We tell the carrier our business key (order id). It must send it back, and
 * {@code createMessageCorrelation("ShipmentConfirmed").processInstanceBusinessKey(orderId)} finds
 * exactly this instance. You can also correlate on a variable:
 * {@code .processInstanceVariableEquals("trackingRef", ref)}.
 */
@Component
public class RequestShipmentDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(RequestShipmentDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable("shipmentRequestedAt", new Date());
    LOG.info("Carrier asked to pick up order {}. Confirm with: POST /api/messages/ShipmentConfirmed?businessKey={}",
        execution.getProcessBusinessKey(), execution.getProcessBusinessKey());
  }
}
