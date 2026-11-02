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
 *  ProvisionEquipmentDelegate - inside a MULTI-INSTANCE loop
 * =============================================================================================
 *
 * <h2>Used by</h2>
 * <pre>{@code
 * <bpmn:serviceTask id="Task_ProvisionEquipment" camunda:delegateExpression="${provisionEquipmentDelegate}">
 *   <bpmn:multiInstanceLoopCharacteristics isSequential="true"
 *        camunda:collection="${equipmentList}" camunda:elementVariable="equipmentItem" />
 * </bpmn:serviceTask>
 * }</pre>
 *
 * <h2>Variables the engine provides in every instance</h2>
 * <ul>
 *   <li>{@code equipmentItem} - the current element (local to the instance)</li>
 *   <li>{@code loopCounter} - 0-based index of the instance (local)</li>
 *   <li>{@code nrOfInstances} - total number of instances (on the multi-instance body)</li>
 *   <li>{@code nrOfActiveInstances} - running now (always 1 when sequential)</li>
 *   <li>{@code nrOfCompletedInstances} - finished so far</li>
 * </ul>
 * Use them in a {@code completionCondition}, e.g. {@code ${nrOfCompletedInstances >= 2}} as in
 * the parallel adjuster review of insurance-claim.bpmn.
 *
 * <h2>Sequential vs. parallel</h2>
 * Sequential = one after the other (order matters, or a shared resource). Parallel = all at
 * once (independent reviewers). With PARALLEL multi-instance plus async, writing to the SAME
 * variable from each instance causes OptimisticLockingExceptions - collect results per instance
 * (local variables) or use sequential, like here where we append to a shared list safely.
 */
@Component
public class ProvisionEquipmentDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(ProvisionEquipmentDelegate.class);

  @Override
  @SuppressWarnings("unchecked")
  public void execute(DelegateExecution execution) {
    String item = (String) execution.getVariable("equipmentItem");
    Integer index = (Integer) execution.getVariable("loopCounter");
    Integer total = (Integer) execution.getVariable("nrOfInstances");

    List<String> done = (List<String>) execution.getVariable("provisionedEquipment");
    List<String> updated = done == null ? new ArrayList<>() : new ArrayList<>(done);
    updated.add(item);
    execution.setVariable("provisionedEquipment", updated); // write back explicitly

    LOG.info("Provisioned {} ({}/{}) for {}", item, index + 1, total, execution.getVariable("employeeName"));
  }
}
