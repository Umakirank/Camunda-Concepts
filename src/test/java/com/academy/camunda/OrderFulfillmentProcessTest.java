package com.academy.camunda;

import com.academy.camunda.config.ProcessConstants;
import com.academy.camunda.domain.Order;
import com.academy.camunda.domain.OrderItem;
import com.academy.camunda.service.ProcessLifecycleService;
import com.academy.camunda.worker.PaymentExternalTaskWorker;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

/**
 * order-fulfillment.bpmn: message start, DMN COLLECT MAX/MIN, external task worker,
 * parallel gateway, event-based gateway, compensation.
 */
class OrderFulfillmentProcessTest extends AbstractProcessTest {

  @Autowired ProcessLifecycleService lifecycle;
  @Autowired PaymentExternalTaskWorker paymentWorker;

  private ProcessInstance placeOrder(String customerType, String coupon, List<OrderItem> items) {
    Order order = new Order();
    order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 6));
    order.setCustomerId("C-1");
    order.setCustomerType(customerType);
    order.setCouponCode(coupon);
    order.setDestinationZone("METRO");
    order.setItems(new ArrayList<>(items));
    String id = lifecycle.placeOrder(order);
    return runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult();
  }

  private static List<OrderItem> normalBasket() {
    // 2 x 12,000 + 1 x 1,000 = 25,000 INR, 3 kg
    return List.of(new OrderItem("PHONE", 2, 12_000, 1.0), new OrderItem("CASE", 1, 1_000, 1.0));
  }

  @Test
  void happyPathWithShipmentConfirmation() {
    ProcessInstance pi = placeOrder("GOLD", null, normalBasket());

    // DMN: GOLD (10) vs >=5000 (7) vs >=20000 (12) -> COLLECT MAX = 12
    Assertions.assertThat(runtimeService.getVariable(pi.getId(), "discountPercent")).isEqualTo(12);
    // DMN: national 250 / metro<=5kg 60 / regional 120 -> COLLECT MIN = 60
    Assertions.assertThat(runtimeService.getVariable(pi.getId(), "shippingFee")).isEqualTo(60.0);
    // JUEL expression task: 25000 * 88 / 100 + 60
    Assertions.assertThat(((Number) runtimeService.getVariable(pi.getId(), "payableAmount")).doubleValue()).isEqualTo(22_060.0);

    // the token waits at the EXTERNAL task until a worker completes it
    assertThat(pi).isWaitingAt("Task_ChargePayment");
    Assertions.assertThat(paymentWorker.pollAndProcess()).isGreaterThanOrEqualTo(1);

    // parallel branches ran synchronously, now waiting at the event-based gateway
    assertThat(pi).hasPassed("Task_ReserveInventory", "Task_GenerateInvoice", "Task_RequestShipment")
        .isWaitingAt("Gateway_WaitForShipment");

    lifecycle.correlateMessage(ProcessConstants.MSG_SHIPMENT_CONFIRMED, pi.getBusinessKey(), null);

    assertThat(pi).isEnded().hasPassed("EndEvent_OrderShipped").hasNotPassed("Task_RefundPayment");
    Order stored = (Order) historicVariable(pi.getId(), "order");
    Assertions.assertThat(stored.getInvoiceNumber()).startsWith("INV-");
  }

  @Test
  void carrierTimeoutCompensatesPaymentAndReservation() {
    ProcessInstance pi = placeOrder("REGULAR", "FESTIVE20", normalBasket());
    Assertions.assertThat(runtimeService.getVariable(pi.getId(), "discountPercent")).isEqualTo(20);
    paymentWorker.pollAndProcess();
    assertThat(pi).isWaitingAt("Gateway_WaitForShipment");

    // fire the 1-hour timer now
    execute(job("Event_ShipmentTimeout", pi));

    assertThat(pi).isEnded()
        .hasPassed("Event_CompensateOrder", "Task_RefundPayment", "Task_ReleaseInventory", "EndEvent_OrderCancelled");
    Assertions.assertThat(historicVariable(pi.getId(), "paymentRefunded")).isEqualTo(true);
    Assertions.assertThat(historicVariable(pi.getId(), "inventoryReleased")).isEqualTo(true);
  }

  @Test
  void workerDeclinesLargePaymentWithBpmnError() {
    ProcessInstance pi = placeOrder("REGULAR", null, List.of(new OrderItem("CAR", 1, 600_000, 900)));
    paymentWorker.pollAndProcess();

    assertThat(pi).isEnded().hasPassed("Boundary_PaymentDeclined", "EndEvent_PaymentDeclined")
        .hasNotPassed("Gateway_ForkFulfilment");
    Assertions.assertThat(historicVariable(pi.getId(), "declineReason")).isEqualTo("LIMIT_EXCEEDED");
  }
}
