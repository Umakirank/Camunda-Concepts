package com.academy.camunda.delegate.order;

import com.academy.camunda.domain.Order;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * =============================================================================================
 *  GenerateInvoiceDelegate - UPDATING an object variable (and the "lost update" trap)
 * =============================================================================================
 *
 * <h2>Used by</h2> {@code Task_GenerateInvoice}, one branch of the parallel gateway.
 *
 * <h2>Golden rule</h2>
 * {@code getVariable} returns a deserialized COPY. Changing the copy changes nothing in the
 * database until you call {@code setVariable} again. (C7 does have "implicit update" detection
 * for object values read in the same command, but being explicit is clearer and safer.)
 *
 * <h2>Explicit typed write</h2>
 * {@code Variables.objectValue(order).serializationDataFormat(JSON)} pins the format for this
 * single variable regardless of the engine default - useful when a few variables must stay JSON
 * while others use a different format.
 *
 * <h2>Concurrency note</h2>
 * Two parallel branches that write the SAME variable in the same transaction are fine; in two
 * different transactions (async) the second commit fails with an OptimisticLockingException
 * and is retried by the job executor. Design parallel branches to write different variables
 * where possible.
 */
@Component
public class GenerateInvoiceDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(GenerateInvoiceDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    Order order = (Order) execution.getVariable("order");
    String invoiceNumber = "INV-" + order.getOrderId();
    order.setInvoiceNumber(invoiceNumber);

    ObjectValue updated = Variables.objectValue(order)
        .serializationDataFormat(Variables.SerializationDataFormats.JSON)
        .create();
    execution.setVariable("order", updated);
    execution.setVariable("invoiceNumber", invoiceNumber);
    LOG.info("Invoice {} created", invoiceNumber);
  }
}
