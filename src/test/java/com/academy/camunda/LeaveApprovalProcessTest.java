package com.academy.camunda;

import com.academy.camunda.config.ProcessConstants;
import com.academy.camunda.service.ProcessLifecycleService;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

/**
 * leave-approval.bpmn: DRD (FIRST + UNIQUE), link events, conditional boundary event,
 * timer cycle, terminate end event, manual task.
 */
class LeaveApprovalProcessTest extends AbstractProcessTest {

  @Autowired ProcessLifecycleService lifecycle;

  private ProcessInstance request(String type, long days, long balance) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("employeeName", "Kiran");
    vars.put("leaveType", type);
    vars.put("requestedDays", days);
    vars.put("availableBalance", balance);
    vars.put("managerId", "priya");
    return lifecycle.start(ProcessConstants.PROCESS_LEAVE, "LEAVE-" + System.nanoTime(), vars);
  }

  @Test
  void shortSickLeaveIsAutoApproved() {
    ProcessInstance pi = request("SICK", 2, 0);   // SICK is eligible even with 0 balance
    assertThat(pi).isEnded().hasPassed("Task_BookLeave", "ManualTask_UpdateRegister", "EndEvent_LeaveApproved");
    Assertions.assertThat(historicVariable(pi.getId(), "approvalLevel")).isEqualTo("AUTO");
  }

  @Test
  void normalLeaveNeedsManager() {
    ProcessInstance pi = request("ANNUAL", 5, 12);
    assertThat(pi).isWaitingAt("UserTask_ManagerApproval");
    assertThat(task("UserTask_ManagerApproval", pi)).isAssignedTo("priya");
    complete(task("UserTask_ManagerApproval", pi), withVariables("approved", true));
    assertThat(pi).isEnded().hasPassed("EndEvent_LeaveApproved");
    Assertions.assertThat(historicVariable(pi.getId(), "remainingBalance")).isEqualTo(7L);
  }

  @Test
  void longLeaveNeedsHrDirector() {
    ProcessInstance pi = request("ANNUAL", 15, 20);
    assertThat(pi).isWaitingAt("UserTask_HrDirectorApproval");
    assertThat(task("UserTask_HrDirectorApproval", pi)).hasCandidateGroup("hr-directors");
  }

  @Test
  void insufficientBalanceIsRejectedViaLinkEvent() {
    ProcessInstance pi = request("ANNUAL", 15, 10);   // eligibility=false -> FIRST rule 1 -> REJECT
    assertThat(pi).isEnded()
        .hasPassed("Event_GoToRejection1", "Event_Rejection", "Task_NotifyRejected", "EndEvent_LeaveRejected");
  }

  @Test
  void managerRejectionAlsoJumpsThroughTheLink() {
    ProcessInstance pi = request("CASUAL", 4, 12);
    complete(task("UserTask_ManagerApproval", pi), withVariables("approved", false));
    assertThat(pi).isEnded().hasPassed("Event_GoToRejection2", "EndEvent_LeaveRejected");
  }

  @Test
  void settingAVariableTriggersTheConditionalEvent() {
    ProcessInstance pi = request("ANNUAL", 5, 12);
    assertThat(pi).isWaitingAt("UserTask_ManagerApproval");

    lifecycle.setVariable(pi.getId(), "leaveCancelled", true);

    assertThat(pi).isEnded().hasPassed("Boundary_LeaveCancelled", "EndEvent_Terminated");
    Assertions.assertThat(historicVariable(pi.getId(), "leaveStatus")).isEqualTo("CANCELLED");
  }

  @Test
  void reminderTimerCycleKeepsTaskOpen() {
    ProcessInstance pi = request("ANNUAL", 5, 12);
    execute(job("Boundary_DailyReminder", pi));
    assertThat(pi).hasPassed("Task_SendReminder").isWaitingAt("UserTask_ManagerApproval");
    // R2 -> after the first firing the engine scheduled the second one
    Assertions.assertThat(managementServiceJobs(pi)).isEqualTo(1);
  }

  private long managementServiceJobs(ProcessInstance pi) {
    return processEngine.getManagementService().createJobQuery()
        .processInstanceId(pi.getId()).activityId("Boundary_DailyReminder").count();
  }
}
