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
 * employee-onboarding.bpmn: embedded sub-process, sequential multi-instance, manual task,
 * event sub-processes (interrupting / non-interrupting), signal throw -> team-announcement.
 */
class EmployeeOnboardingProcessTest extends AbstractProcessTest {

  @Autowired ProcessLifecycleService lifecycle;

  private ProcessInstance start(String name) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("employeeName", name);
    vars.put("employeeEmail", name.toLowerCase() + "@example.com");
    vars.put("department", "ENGINEERING");
    vars.put("managerId", "priya");
    return lifecycle.start(ProcessConstants.PROCESS_ONBOARDING, "EMP-" + System.nanoTime(), vars);
  }

  @Test
  @SuppressWarnings("unchecked")
  void fullOnboardingBroadcastsSignal() {
    long announcementsBefore = historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey(ProcessConstants.PROCESS_TEAM_ANNOUNCEMENT).count();

    ProcessInstance pi = start("Asha");

    // sequential multi-instance provisioned 3 items one after another; manual task passed through
    List<String> provisioned = (List<String>) runtimeService.getVariable(pi.getId(), "provisionedEquipment");
    Assertions.assertThat(provisioned).containsExactly("Laptop", "ID card", "External monitor");
    assertThat(pi).hasPassed("ManualTask_WelcomeKit")
        .isWaitingAt("UserTask_CreateAccounts", "UserTask_HrPaperwork");

    // non-interrupting event sub-process: fires, main flow untouched
    lifecycle.correlateMessage(ProcessConstants.MSG_EMPLOYEE_DETAILS_UPDATED, pi.getBusinessKey(),
        Map.of("newAddress", "Hyderabad"));
    assertThat(pi).hasPassed("Task_UpdateHrRecords").isWaitingAt("UserTask_CreateAccounts", "UserTask_HrPaperwork");

    complete(task("UserTask_CreateAccounts", pi), withVariables("corporateEmail", "asha@corp.example"));
    complete(task("UserTask_HrPaperwork", pi), withVariables("contractSigned", true));

    assertThat(pi).isWaitingAt("UserTask_ManagerIntro");
    assertThat(task("UserTask_ManagerIntro", pi)).isAssignedTo("priya");
    complete(task("UserTask_ManagerIntro", pi));

    assertThat(pi).isEnded().hasPassed("Event_EmployeeOnboarded", "EndEvent_Onboarded");
    // the signal started a NEW instance of team-announcement (signal start event)
    Assertions.assertThat(historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey(ProcessConstants.PROCESS_TEAM_ANNOUNCEMENT).count()).isEqualTo(announcementsBefore + 1);
  }

  @Test
  void cancellationInterruptsEverything() {
    ProcessInstance pi = start("Vikram");
    assertThat(pi).isWaitingAt("UserTask_CreateAccounts", "UserTask_HrPaperwork");

    lifecycle.correlateMessage(ProcessConstants.MSG_ONBOARDING_CANCELLED, pi.getBusinessKey(), null);

    assertThat(pi).isEnded().hasPassed("Task_RevokeAccess", "EndEvent_Cancelled")
        .hasNotPassed("UserTask_ManagerIntro");
    Assertions.assertThat(taskService.createTaskQuery().processInstanceId(pi.getId()).count()).isZero();
  }
}
