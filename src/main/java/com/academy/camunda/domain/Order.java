package com.academy.camunda.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * =============================================================================================
 *  Order - a complex object stored as ONE process variable ("order")
 * =============================================================================================
 *
 * <h2>How does Camunda store an object like this?</h2>
 * Process variables are typed values ({@code TypedValue}). Primitives (String, Long, Boolean,
 * Date, Double...) are stored natively in ACT_RU_VARIABLE. Everything else is an
 * <b>ObjectValue</b> that must be serialized. The serialization format is chosen by
 * {@code camunda.bpm.default-serialization-format}:
 * <ul>
 *   <li>{@code application/json} (our choice) - Camunda Spin + Jackson. Requires a no-arg
 *       constructor and getters/setters (a normal Jackson bean). The JSON is readable in
 *       Cockpit and editable there.</li>
 *   <li>{@code application/x-java-serialized-object} - Java serialization. DISABLED by default
 *       since 7.13 for security reasons (deserialization attacks) - avoid it.</li>
 * </ul>
 * The ACT_RU_VARIABLE row stores the JSON plus the Java type name (com.academy...Order), so
 * {@code execution.getVariable("order")} gives you back an Order instance.
 *
 * <h2>Best practices for object variables</h2>
 * <ul>
 *   <li>Keep them small - they are (de)serialized on every read/write and written to history
 *       on every change (history level FULL).</li>
 *   <li>Never rename/move the class without migrating data - the type name is stored.</li>
 *   <li>Prefer storing a business ID + fetching data from the owning system ("reference, not
 *       copy") when data is large or changes outside the process.</li>
 *   <li>Variables you use in gateways / DMN should be flat primitives (orderTotal, weightKg) -
 *       that is exactly what {@link com.academy.camunda.delegate.order.PrepareOrderDelegate} does.</li>
 *   <li>Camunda 8 note: C8 variables are always JSON documents - designing with JSON in C7
 *       makes migration much easier.</li>
 * </ul>
 */
public class Order {

  private String orderId;
  private String customerId;
  private String customerType;      // REGULAR | SILVER | GOLD  -> DMN order-discount
  private String couponCode;        // optional                 -> DMN order-discount
  private String destinationZone;   // METRO | URBAN | REMOTE   -> DMN shipping-fee
  private List<OrderItem> items = new ArrayList<>();
  private String invoiceNumber;     // filled by GenerateInvoiceDelegate

  public Order() {
    // required by Jackson
  }

  public double totalAmount() {
    return items.stream().mapToDouble(i -> i.getUnitPrice() * i.getQuantity()).sum();
  }

  public double totalWeightKg() {
    return items.stream().mapToDouble(i -> i.getWeightKg() * i.getQuantity()).sum();
  }

  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public String getCustomerId() { return customerId; }
  public void setCustomerId(String customerId) { this.customerId = customerId; }
  public String getCustomerType() { return customerType; }
  public void setCustomerType(String customerType) { this.customerType = customerType; }
  public String getCouponCode() { return couponCode; }
  public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
  public String getDestinationZone() { return destinationZone; }
  public void setDestinationZone(String destinationZone) { this.destinationZone = destinationZone; }
  public List<OrderItem> getItems() { return items; }
  public void setItems(List<OrderItem> items) { this.items = items; }
  public String getInvoiceNumber() { return invoiceNumber; }
  public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
}
