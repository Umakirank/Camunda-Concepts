package com.academy.camunda;

import com.academy.camunda.config.ProcessConstants;
import com.academy.camunda.service.ProcessLifecycleService;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

/**
 * credit-card-application.bpmn: DMN ANY + RULE ORDER, transaction sub-process,
 * cancel end/boundary events, compensation inside a transaction, asyncAfter.
 */
class CreditCardApplicationProcessTest extends AbstractProcessTest {

  @Autowired ProcessLifecycleService lifecycle;

  private ProcessInstance apply(long age, long income, boolean defaults) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("applicantName", "Meera");
    vars.put("age", age);
    vars.put("monthlyIncome", income);
    vars.put("hasDefaults", defaults);
    vars.put("applicantEmail", "meera@example.com");
    return lifecycle.start(ProcessConstants.PROCESS_CREDIT_CARD, "CARD-" + System.nanoTime(), vars);
  }

  @Test
  @SuppressWarnings("unchecked")
  void eligibleApplicantPassingKycGetsCard() {
    ProcessInstance pi = apply(30, 120_000, false);

    Assertions.assertThat(runtimeService.getVariable(pi.getId(), "eligibility")).isEqualTo("ACCEPT");
    // RULE ORDER keeps the table order of the matching rules
    List<String> offers = (List<String>) runtimeService.getVariable(pi.getId(), "offers");
    Assertions.assertThat(offers).containsExactly("5% cashback on travel", "Zero joining fee");

    assertThat(pi).isWaitingAt("UserTask_KycVerification");
    complete(task("UserTask_KycVerification", pi), withVariables("kycPassed", true));

    // asyncAfter on "Dispatch card": dispatch is committed, continuation is a job
    execute(job("Task_DispatchCard", pi));
    assertThat(pi).isEnded().hasPassed("Task_DispatchCard", "EndEvent_CardIssued");
    Assertions.assertThat(historicVariable(pi.getId(), "creditLimit")).isEqualTo(360_000L);
  }

  @Test
  void failedKycCancelsTransactionAndCompensates() {
    ProcessInstance pi = apply(30, 120_000, false);
    complete(task("UserTask_KycVerification", pi), withVariables("kycPassed", false));

    assertThat(pi).isEnded()
        .hasPassed("EndEvent_TxCancelled", "Task_ReleaseCreditLine", "Task_CloseCardAccount",
            "Boundary_IssuingCancelled", "Task_NotifyKycFailed")
        .hasNotPassed("Task_DispatchCard");
    Assertions.assertThat(historicVariable(pi.getId(), "cardAccountClosed")).isEqualTo(true);
    Assertions.assertThat(historicVariable(pi.getId(), "creditLineReleased")).isEqualTo(true);
  }

  @Test
  void knockOutRulesRejectWithAnyHitPolicy() {
    // under 21 AND has defaults -> two REJECT rules match; ANY accepts because outputs are equal
    ProcessInstance pi = apply(19, 30_000, true);
    assertThat(pi).isEnded().hasPassed("Task_NotifyNotEligible", "EndEvent_NotEligible");
    Assertions.assertThat(historicVariable(pi.getId(), "eligibility")).isEqualTo("REJECT");
  }
}
