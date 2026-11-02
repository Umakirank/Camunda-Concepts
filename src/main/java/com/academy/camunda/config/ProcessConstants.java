package com.academy.camunda.config;

/**
 * =============================================================================================
 *  ProcessConstants - one place for every "magic string" shared by BPMN files and Java code
 * =============================================================================================
 *
 * <p>BPMN/DMN models and Java code are coupled ONLY through strings: process keys, message
 * names, signal names, error codes, topic names and variable names. A typo is not a compile
 * error - it is a runtime error ("Cannot correlate message", "Unknown property used in
 * expression"). Centralising them is the cheapest protection against that class of bugs.</p>
 *
 * <p>Interview tip: "How do you keep BPMN and code in sync?" -> constants class, unit tests
 * per process (see src/test), and naming conventions (ids like Task_*, Gateway_*, Event_*).</p>
 */
public final class ProcessConstants {

  private ProcessConstants() {
  }

  // ---------------------------------------------------------------- process definition keys
  /** = the {@code id} attribute of {@code <bpmn:process>}. Starting "by key" always uses the latest version. */
  public static final String PROCESS_LOAN = "loan-approval";
  public static final String PROCESS_ORDER = "order-fulfillment";
  public static final String PROCESS_CLAIM = "insurance-claim";
  public static final String PROCESS_FRAUD = "fraud-check";
  public static final String PROCESS_ONBOARDING = "employee-onboarding";
  public static final String PROCESS_TEAM_ANNOUNCEMENT = "team-announcement";
  public static final String PROCESS_LEAVE = "leave-approval";
  public static final String PROCESS_CREDIT_CARD = "credit-card-application";
  public static final String PROCESS_DAILY_REPORT = "daily-claims-report";

  // ---------------------------------------------------------------- message names (the "name", not the id!)
  public static final String MSG_ORDER_PLACED = "OrderPlaced";
  public static final String MSG_SHIPMENT_CONFIRMED = "ShipmentConfirmed";
  public static final String MSG_LOAN_WITHDRAWN = "LoanWithdrawn";
  public static final String MSG_CLAIM_DOCUMENTS = "ClaimDocumentsUploaded";
  public static final String MSG_EMPLOYEE_DETAILS_UPDATED = "EmployeeDetailsUpdated";
  public static final String MSG_ONBOARDING_CANCELLED = "OnboardingCancelled";

  // ---------------------------------------------------------------- signals, errors, topics
  public static final String SIGNAL_EMPLOYEE_ONBOARDED = "EmployeeOnboarded";
  public static final String ERROR_INVALID_APPLICATION = "INVALID_APPLICATION";
  public static final String ERROR_PAYMENT_DECLINED = "PAYMENT_DECLINED";
  public static final String TOPIC_PAYMENT = "payment-charge";

  // ---------------------------------------------------------------- decision keys (= decision id in the DMN)
  public static final String DECISION_LOAN_RISK = "loan-risk-assessment";
  public static final String DECISION_ORDER_DISCOUNT = "order-discount";
  public static final String DECISION_SHIPPING_FEE = "shipping-fee";
  public static final String DECISION_CLAIM_DOCUMENTS = "claim-required-documents";
  public static final String DECISION_FRAUD_SCORE = "fraud-risk-score";
  public static final String DECISION_LEAVE_LEVEL = "leave-approval-level";
  public static final String DECISION_LEAVE_ELIGIBILITY = "leave-eligibility";
  public static final String DECISION_CARD_ELIGIBILITY = "credit-card-eligibility";
  public static final String DECISION_CARD_OFFERS = "credit-card-offers";
  public static final String DECISION_PROMOTIONS_COUNT = "matching-promotions-count";
  public static final String DECISION_GREETING = "customer-greeting";
}
