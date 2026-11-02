package com.academy.camunda.delegate.order;

import com.academy.camunda.domain.Order;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  PrepareOrderDelegate - reading a JSON object variable, deriving flat DMN inputs
 * =============================================================================================
 *
 * <h2>Used by</h2> {@code Task_PrepareOrder} in order-fulfillment.bpmn.
 *
 * <h2>Two ways to read an object variable</h2>
 * <pre>{@code
 * Order order = (Order) execution.getVariable("order");            // deserialized Java object
 * ObjectValue raw = execution.getVariableTyped("order", false);   // NOT deserialized
 * raw.getValueSerialized();     // the JSON string
 * raw.getSerializationDataFormat(); // "application/json"
 * raw.getObjectTypeName();      // "com.academy.camunda.domain.Order"
 * }</pre>
 * The typed API is handy for logging, auditing or passing JSON on without ever needing the class.
 *
 * <h2>Why derive orderTotal / weightKg / customerType ...?</h2>
 * DMN input expressions and gateway conditions work best on flat primitive variables. FEEL can
 * navigate objects only if they are Java beans / maps, and every navigation means deserializing
 * the whole JSON. Flat variables are also visible and filterable in Cockpit and Optimize.
 *
 * <p>We ALWAYS set couponCode (possibly null): a DMN input expression that references a variable
 * which does not exist at all fails ("no variable found"), whereas a null value simply does not
 * match "FESTIVE20" and matches "-".</p>
 */
@Component
public class PrepareOrderDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(PrepareOrderDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    ObjectValue raw = execution.getVariableTyped("order", false);
    LOG.debug("order variable stored as {} / {}: {}", raw.getSerializationDataFormat(),
        raw.getObjectTypeName(), raw.getValueSerialized());

    Order order = (Order) execution.getVariable("order");

    execution.setVariable("orderTotal", order.totalAmount());          // Double
    execution.setVariable("weightKg", order.totalWeightKg());          // Double
    execution.setVariable("customerType", order.getCustomerType() == null ? "REGULAR" : order.getCustomerType());
    execution.setVariable("couponCode", order.getCouponCode());       // may be null - intentionally
    execution.setVariable("destinationZone", order.getDestinationZone() == null ? "URBAN" : order.getDestinationZone());
    LOG.info("Order {} prepared: total={} weight={}kg", order.getOrderId(), order.totalAmount(), order.totalWeightKg());
  }
}
