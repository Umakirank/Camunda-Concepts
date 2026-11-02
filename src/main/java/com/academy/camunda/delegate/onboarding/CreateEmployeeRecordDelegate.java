package com.academy.camunda.delegate.onboarding;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * =============================================================================================
 *  CreateEmployeeRecordDelegate - prepares data for a SEQUENTIAL multi-instance activity
 * =============================================================================================
 *
 * <h2>Used by</h2> {@code Task_CreateEmployeeRecord} in employee-onboarding.bpmn.
 *
 * <p>It decides which equipment a joiner gets (by department) and stores the list as
 * "equipmentList". Inside the IT sub-process {@code Task_ProvisionEquipment} loops over that list
 * one item at a time ({@code isSequential="true"}).</p>
 *
 * <p>In a real system this list would come from a DMN table ("equipment per role") - try it as
 * an exercise: COLLECT hit policy + mapDecisionResult="collectEntries".</p>
 */
@Component
public class CreateEmployeeRecordDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(CreateEmployeeRecordDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    String department = String.valueOf(execution.getVariable("department"));
    String employeeId = "EMP-" + (System.currentTimeMillis() % 100000);

    List<String> equipment = new ArrayList<>();   // ArrayList: JSON (de)serializable
    equipment.add("Laptop");
    equipment.add("ID card");
    if ("ENGINEERING".equals(department)) {
      equipment.add("External monitor");
    } else if ("SALES".equals(department)) {
      equipment.add("Mobile phone");
    }

    execution.setVariable("employeeId", employeeId);
    execution.setVariable("equipmentList", equipment);
    execution.setVariable("provisionedEquipment", new ArrayList<String>());
    LOG.info("Employee {} ({}) created, equipment: {}", employeeId, department, equipment);
  }
}
