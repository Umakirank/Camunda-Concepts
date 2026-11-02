package com.academy.camunda.domain;

/**
 * One line of an {@link Order}. A plain Jackson bean (no-arg constructor + getters/setters) so
 * it can live inside the JSON-serialized "order" process variable.
 *
 * <p>Note on {@code double} for money: fine for a learning project. In real systems use
 * {@code BigDecimal} (Jackson serializes it as a JSON number; when you need it in a gateway
 * condition, expose a primitive helper variable).</p>
 */
public class OrderItem {

  private String sku;
  private int quantity;
  private double unitPrice;
  private double weightKg;

  public OrderItem() {
  }

  public OrderItem(String sku, int quantity, double unitPrice, double weightKg) {
    this.sku = sku;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.weightKg = weightKg;
  }

  public String getSku() { return sku; }
  public void setSku(String sku) { this.sku = sku; }
  public int getQuantity() { return quantity; }
  public void setQuantity(int quantity) { this.quantity = quantity; }
  public double getUnitPrice() { return unitPrice; }
  public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
  public double getWeightKg() { return weightKg; }
  public void setWeightKg(double weightKg) { this.weightKg = weightKg; }
}
