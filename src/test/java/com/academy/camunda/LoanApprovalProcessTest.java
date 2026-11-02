package com.academy.camunda;

import com.academy.camunda.config.ProcessConstants;
import com.academy.camunda.service.ProcessLifecycleService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.assertj.core.api.Assertions;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

/**
 * NOTE on assertions: BpmnAwareTests.assertThat(...) handles ProcessInstance/Task/Job; for plain
 * values we call AssertJ explicitly as Assertions.assertThat(...) because a static single import
 * of AssertJ's assertThat would shadow the BPMN ones.
 *
 * Tests every path of loan-approval.bpmn. Read them as executable documentation:
 * each test is one business scenario.
 */
class LoanApprovalProcessTest extends AbstractProcessTest {

  @Autowired ProcessLifecycleService lifecycle;
  @Autowired ManagementService managementService;

  private ProcessInstance startLoan(String pan, long income, long amount) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("applicantName", "Ravi");
    vars.put("applicantPan", pan);
    vars.put("applicantEmail", "ravi@example.com");
    vars.put("annualIncome", income);
    vars.put("loanAmount", amount);
    return lifecycle.startLoan(vars);
  }

  @Test
  void lowRiskIsApprovedWithoutHumanInteraction() {
    ProcessInstance pi = startLoan("ABCDE0800F", 1_200_000, 500_000);

    // asyncBefore on "Fetch credit score" -> the start call returned at a JOB
    assertThat(pi).isWaitingAt("Task_FetchCreditScore");
    execute(job("Task_FetchCreditScore", pi));

    // score 800, ratio 0.42 -> DMN UNIQUE -> LOW -> straight to disbursement (asyncBefore again)
    assertThat(pi).isWaitingAt("Task_DisburseLoan");
    execute(job("Task_DisburseLoan", pi));

    assertThat(pi).isEnded()
        .hasPassed("Task_AssessRisk", "Gateway_MergeApproved", "Task_SendApproval", "EndEvent_LoanApproved")
        .hasNotPassed("UserTask_Underwrite");
    Assertions.assertThat(historicVariable(pi.getId(), "lastNotification")).isEqualTo("LOAN_APPROVED");
    Assertions.assertThat(historicVariable(pi.getId(), "loanAccountNumber")).isNotNull();
  }

  @Test
  void mediumRiskGoesToUnderwriterWhoApproves() {
    ProcessInstance pi = startLoan("ABCDE0700F", 1_200_000, 500_000);
    execute(job("Task_FetchCreditScore", pi));

    assertThat(pi).isWaitingAt("UserTask_Underwrite");
    Task task = task("UserTask_Underwrite", pi);
    assertThat(task).hasCandidateGroup("underwriters");
    // the task listener copied the DMN reason into a TASK-LOCAL variable
    Assertions.assertThat(taskService.getVariableLocal(task.getId(), "riskLevel")).isEqualTo("MEDIUM");

    taskService.claim(task.getId(), "anil");
    complete(task, withVariables("approved", true, "underwriterComment", "Stable job"));

    execute(job("Task_DisburseLoan", pi));
    assertThat(pi).isEnded().hasPassed("EndEvent_LoanApproved");
  }

  @Test
  void underwriterMustCommentWhenRejecting() {
    ProcessInstance pi = startLoan("ABCDE0700F", 1_200_000, 500_000);
    execute(job("Task_FetchCreditScore", pi));
    Task task = task("UserTask_Underwrite", pi);

    // complete-listener validation aborts the completion
    assertThatThrownBy(() -> taskService.complete(task.getId(), withVariables("approved", false)))
        .hasStackTraceContaining("comment");
    assertThat(pi).isWaitingAt("UserTask_Underwrite");

    complete(task, withVariables("approved", false, "underwriterComment", "Too many EMIs"));
    assertThat(pi).isEnded().hasPassed("Task_SendRejection", "EndEvent_LoanRejected");
  }

  @Test
  void highRiskIsRejectedAutomatically() {
    ProcessInstance pi = startLoan("ABCDE0550F", 1_200_000, 500_000);
    execute(job("Task_FetchCreditScore", pi));

    assertThat(pi).isEnded().hasPassed("EndEvent_LoanRejected");
    // set by the execution listener on the sequence flow ("take" event)
    Assertions.assertThat(historicVariable(pi.getId(), "rejectionChannel")).isEqualTo("AUTOMATIC_RISK_RULE");
  }

  @Test
  void invalidApplicationIsCaughtByErrorBoundaryEvent() {
    ProcessInstance pi = startLoan("BAD-PAN", 1_200_000, 500_000);

    // BpmnError thrown synchronously during start -> caught -> ended
    assertThat(pi).isEnded().hasPassed("Boundary_InvalidApplication", "EndEvent_Invalid");
    Assertions.assertThat(historicVariable(pi.getId(), "validationErrorCode"))
        .isEqualTo(ProcessConstants.ERROR_INVALID_APPLICATION);
  }

  @Test
  void applicantCanWithdrawWhileUnderwritingIsPending() {
    ProcessInstance pi = startLoan("ABCDE0700F", 1_200_000, 500_000);
    execute(job("Task_FetchCreditScore", pi));
    assertThat(pi).isWaitingAt("UserTask_Underwrite");

    lifecycle.correlateMessage(ProcessConstants.MSG_LOAN_WITHDRAWN, pi.getBusinessKey(), null);

    assertThat(pi).isEnded().hasPassed("EndEvent_Withdrawn");
  }

  @Test
  void slaTimerEscalatesButKeepsTheTaskOpen() {
    ProcessInstance pi = startLoan("ABCDE0700F", 1_200_000, 500_000);
    execute(job("Task_FetchCreditScore", pi));

    // there are two timer jobs now: the boundary (2h) and the task-listener timeout (30 min)
    Job slaTimer = managementService.createJobQuery().processInstanceId(pi.getId())
        .activityId("Boundary_UnderwritingSla").singleResult();
    execute(slaTimer);

    assertThat(pi).hasPassed("Task_EscalateSla").isWaitingAt("UserTask_Underwrite");
    Assertions.assertThat(task("UserTask_Underwrite", pi).getPriority()).isEqualTo(100);
  }

  @Test
  void bureauOutageEndsInAnIncidentAfterRetries() {
    ProcessInstance pi = startLoan("ERROR0800F", 1_200_000, 500_000);

    // Run the failing job until its retries are exhausted (R3 => 3 attempts).
    for (int i = 0; i < 5; i++) {
      Job failing = managementService.createJobQuery().processInstanceId(pi.getId()).singleResult();
      if (failing.getRetries() == 0) {
        break;
      }
      assertThatThrownBy(() -> managementService.executeJob(failing.getId()))
          .hasStackTraceContaining("Credit bureau timeout");
    }

    Assertions.assertThat(runtimeService.createIncidentQuery().processInstanceId(pi.getId()).count()).isEqualTo(1);
    assertThat(pi).isWaitingAt("Task_FetchCreditScore"); // token is safe, waiting for a retry
  }
}
