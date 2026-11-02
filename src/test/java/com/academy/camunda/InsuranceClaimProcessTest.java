package com.academy.camunda;

import com.academy.camunda.config.ProcessConstants;
import com.academy.camunda.service.ProcessLifecycleService;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

/**
 * insurance-claim.bpmn + fraud-check.bpmn: receive task, DMN COLLECT / COLLECT SUM, call
 * activity, inclusive gateway, parallel multi-instance, Groovy script, IO mapping,
 * error and escalation across process boundaries.
 */
class InsuranceClaimProcessTest extends AbstractProcessTest {

  @Autowired ProcessLifecycleService lifecycle;

  private ProcessInstance fileClaim(long amount, boolean injury, long daysSincePolicyStart) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("policyNumber", "POL-77");
    vars.put("claimType", "MOTOR");
    vars.put("claimAmount", amount);
    vars.put("hasInjury", injury);
    vars.put("daysSincePolicyStart", daysSincePolicyStart);
    vars.put("previousClaims", 0L);
    vars.put("claimantEmail", "asha@example.com");
    return lifecycle.start(ProcessConstants.PROCESS_CLAIM, "CLAIM-" + System.nanoTime(), vars);
  }

  @Test
  @SuppressWarnings("unchecked")
  void cleanClaimWithAllAssessmentsIsPaid() {
    ProcessInstance pi = fileClaim(150_000, true, 400);

    // DMN COLLECT -> list of documents
    List<String> docs = (List<String>) runtimeService.getVariable(pi.getId(), "requiredDocuments");
    Assertions.assertThat(docs).contains("Signed claim form", "Police FIR copy", "Vehicle repair estimate",
        "Independent surveyor report", "Medical report");

    assertThat(pi).isWaitingAt("Task_AwaitDocuments");               // receive task
    lifecycle.correlateMessage(ProcessConstants.MSG_CLAIM_DOCUMENTS, pi.getBusinessKey(), null);

    // fraud score = 30 (amount > 50k) -> below 70 -> called process finished immediately
    assertThat(pi).hasPassed("CallActivity_FraudCheck");
    Assertions.assertThat(runtimeService.getVariable(pi.getId(), "fraudScore")).isEqualTo(30);

    // inclusive gateway: amount > 1 lakh AND injury AND always -> 3 branches
    assertThat(pi).isWaitingAt("UserTask_SeniorApproval", "UserTask_MedicalAssessment", "UserTask_AdjusterReview");
    complete(task("UserTask_SeniorApproval", pi), withVariables("seniorApproved", true));
    complete(task("UserTask_MedicalAssessment", pi), withVariables("medicalNotes", "minor"));

    // parallel multi-instance: one task per adjuster, 2 of 3 are enough
    List<Task> reviews = tasks(pi.getId(), "UserTask_AdjusterReview");
    Assertions.assertThat(reviews).extracting(Task::getAssignee).containsExactlyInAnyOrder("john", "mary", "peter");
    taskService.complete(reviews.get(0).getId(), withVariables("recommendedPayout", 140_000L));
    taskService.complete(reviews.get(1).getId(), withVariables("recommendedPayout", 145_000L));

    // completionCondition met -> third task cancelled -> join -> script -> async pay
    assertThat(pi).hasPassed("Task_CalculatePayout").isWaitingAt("Task_PayClaim");
    Assertions.assertThat(runtimeService.getVariable(pi.getId(), "payoutAmount")).isEqualTo(145_000L);

    execute(job("Task_PayClaim", pi));
    assertThat(pi).isEnded().hasPassed("EndEvent_ClaimPaid");
    Assertions.assertThat(historicVariable(pi.getId(), "paymentStatus")).isEqualTo("PAID");
  }

  @Test
  void confirmedFraudRejectsClaimThroughErrorEndEvent() {
    ProcessInstance pi = fileClaim(80_000, false, 10);      // 30 + 40 = 70 -> investigation
    lifecycle.correlateMessage(ProcessConstants.MSG_CLAIM_DOCUMENTS, pi.getBusinessKey(), null);

    assertThat(pi).isWaitingAt("CallActivity_FraudCheck");
    ProcessInstance child = calledProcessInstance(ProcessConstants.PROCESS_FRAUD, pi);
    assertThat(child).isWaitingAt("UserTask_InvestigateFraud");
    Assertions.assertThat(child.getBusinessKey()).isEqualTo(pi.getBusinessKey()); // propagated

    complete(task("UserTask_InvestigateFraud", child), withVariables("investigationOutcome", "CONFIRMED"));

    assertThat(child).isEnded().hasPassed("EndEvent_FraudConfirmed");
    assertThat(pi).isEnded().hasPassed("Boundary_FraudConfirmed", "EndEvent_ClaimRejected");
  }

  @Test
  void suspectedFraudEscalatesButClaimContinues() {
    ProcessInstance pi = fileClaim(80_000, false, 10);
    lifecycle.correlateMessage(ProcessConstants.MSG_CLAIM_DOCUMENTS, pi.getBusinessKey(), null);
    ProcessInstance child = calledProcessInstance(ProcessConstants.PROCESS_FRAUD, pi);

    complete(task("UserTask_InvestigateFraud", child), withVariables("investigationOutcome", "SUSPECTED"));

    // non-interrupting escalation boundary fired AND the normal flow continued
    assertThat(pi).hasPassed("Boundary_FraudSuspected", "Task_NotifyCompliance", "CallActivity_FraudCheck")
        .isWaitingAt("UserTask_AdjusterReview");
    Assertions.assertThat(runtimeService.getVariable(pi.getId(), "investigationOutcome")).isEqualTo("SUSPECTED");
  }

  @Test
  void claimIsClosedWhenDocumentsNeverArrive() {
    ProcessInstance pi = fileClaim(20_000, false, 400);
    execute(job("Boundary_DocumentsTimeout", pi));            // the P7D timer
    assertThat(pi).isEnded().hasPassed("Task_CloseIncomplete", "EndEvent_ClosedIncomplete");
  }
}
