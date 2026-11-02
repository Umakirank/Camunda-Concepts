package com.academy.camunda;

import com.academy.camunda.config.ProcessConstants;
import com.academy.camunda.service.DecisionEvaluationService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * Every hit policy supported by Camunda 7, evaluated through the DecisionService.
 * Treat this class as a cheat sheet: input -> which rules match -> what comes back.
 */
class DecisionTablesTest extends AbstractProcessTest {

  @Autowired DecisionEvaluationService decisions;

  @Test
  void unique_exactlyOneRuleMatches() {
    List<Map<String, Object>> r = decisions.evaluateTable(ProcessConstants.DECISION_LOAN_RISK,
        Map.of("creditScore", 700, "loanAmount", 500_000, "annualIncome", 1_200_000));
    Assertions.assertThat(r).hasSize(1);
    Assertions.assertThat(r.get(0)).containsEntry("riskLevel", "MEDIUM");
  }

  @Test
  void any_multipleMatchesWithEqualOutputAreAllowed() {
    List<Map<String, Object>> r = decisions.evaluateTable(ProcessConstants.DECISION_CARD_ELIGIBILITY,
        Map.of("age", 19, "monthlyIncome", 10_000, "hasDefaults", true)); // 3 REJECT rules match
    Assertions.assertThat(r).hasSize(1).first().isEqualTo(Map.of("eligibility", "REJECT"));
  }

  @Test
  void first_ruleOrderIsPriority_andDrdEvaluatesRequiredDecisionFirst() {
    // 1 day of SICK leave: rule 2 (AUTO) and the catch-all both match -> FIRST returns AUTO
    List<Map<String, Object>> r = decisions.evaluateDecision(ProcessConstants.DECISION_LEAVE_LEVEL,
        Map.of("leaveType", "SICK", "requestedDays", 1, "availableBalance", 0));
    Assertions.assertThat(r.get(0)).containsEntry("approvalLevel", "AUTO");
  }

  @Test
  void ruleOrder_returnsAllMatchesInTableOrder() {
    List<Map<String, Object>> r = decisions.evaluateTable(ProcessConstants.DECISION_CARD_OFFERS,
        Map.of("monthlyIncome", 250_000));
    Assertions.assertThat(r).extracting(m -> m.get("offer"))
        .containsExactly("Unlimited airport lounge access", "5% cashback on travel", "Zero joining fee");
  }

  @Test
  void outputOrder_isEmulatedInJava() {
    Assertions.assertThat(decisions.offersInOutputOrder(30_000))
        .containsExactly("Upgrade path to secured card", "Zero joining fee");
  }

  @Test
  void collect_returnsAListOfAllMatches() {
    List<Map<String, Object>> r = decisions.evaluateTable(ProcessConstants.DECISION_CLAIM_DOCUMENTS,
        Map.of("claimType", "HEALTH", "claimAmount", 5_000, "hasInjury", true));
    Assertions.assertThat(r).extracting(m -> m.get("document"))
        .containsExactlyInAnyOrder("Signed claim form", "Hospital bills and discharge summary", "Medical report");
  }

  @Test
  void collectSum_addsUpPoints() {
    List<Map<String, Object>> r = decisions.evaluateTable(ProcessConstants.DECISION_FRAUD_SCORE,
        Map.of("claimAmount", 80_000, "daysSincePolicyStart", 10, "previousClaims", 4));
    Assertions.assertThat(r.get(0).get("score")).isEqualTo(0 + 30 + 40 + 25);
  }

  @Test
  void collectMax_and_collectMin() {
    Assertions.assertThat(decisions.evaluateTable(ProcessConstants.DECISION_ORDER_DISCOUNT,
            Map.of("customerType", "SILVER", "orderTotal", 6_000.0, "couponCode", "NONE"))
        .get(0).get("discountPercent")).isEqualTo(7);            // max(0, 5, 7)
    Assertions.assertThat(decisions.evaluateTable(ProcessConstants.DECISION_SHIPPING_FEE,
            Map.of("destinationZone", "URBAN", "weightKg", 12.0))
        .get(0).get("shippingFee")).isEqualTo(120.0);            // min(250, 120)
  }

  @Test
  void collectCount_countsMatchingRules() {
    List<Map<String, Object>> r = decisions.evaluateTable(ProcessConstants.DECISION_PROMOTIONS_COUNT,
        Map.of("customerType", "GOLD", "orderTotal", 12_000.0));
    Assertions.assertThat(r.get(0).get("promotion")).isEqualTo(3); // express + points + gift wrap
  }

  @Test
  void literalExpression_buildsAString() {
    Assertions.assertThat(decisions.greeting("Asha", "GOLD"))
        .isEqualTo("Hello Asha, thank you for being a Gold member!");
  }
}
