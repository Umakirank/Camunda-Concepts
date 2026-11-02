package com.academy.camunda.worker;

import com.academy.camunda.config.ProcessConstants;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * =============================================================================================
 *  PaymentExternalTaskWorker - the EXTERNAL TASK pattern (pull instead of push)
 * =============================================================================================
 *
 * <h2>Used by</h2>
 * {@code Task_ChargePayment}: {@code camunda:type="external" camunda:topic="payment-charge"}.
 *
 * <h2>How external tasks work internally</h2>
 * <ol>
 *   <li>The token reaches the task. The engine inserts a row into ACT_RU_EXT_TASK (topic,
 *       priority, retries=null) and the transaction commits. The token WAITS - nothing is
 *       called.</li>
 *   <li>A worker asks "give me up to N tasks of topic X and lock them for me for T ms"
 *       ({@code fetchAndLock}). Locking guarantees that only one worker processes a task.
 *       The lock EXPIRES after T ms - a crashed worker never blocks a task forever.</li>
 *   <li>The worker does the job and reports back with exactly one of:
 *     <ul>
 *       <li>{@code complete(id, workerId, variables)} - token moves on</li>
 *       <li>{@code handleBpmnError(id, workerId, code, msg, vars)} - business error ->
 *           boundary error event "PAYMENT_DECLINED"</li>
 *       <li>{@code handleFailure(id, workerId, msg, details, retries, retryTimeout)} - technical
 *           failure; when retries reaches 0 an incident is created</li>
 *       <li>{@code extendLock(...)} - "I need more time"</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>Why external tasks?</h2>
 * <ul>
 *   <li>Workers can be written in ANY language (Node, Python, .NET via REST:
 *       POST /engine-rest/external-task/fetchAndLock).</li>
 *   <li>Workers scale and deploy independently of the engine (microservices).</li>
 *   <li>The engine never blocks a thread on a slow remote system.</li>
 *   <li>This is exactly the model of Camunda 8 job workers -> easiest migration path.</li>
 * </ul>
 *
 * <h2>About this implementation</h2>
 * Inside the same JVM we can use the Java {@link ExternalTaskService} directly. In a separate
 * microservice you would use the official {@code camunda-external-task-client} (Java) or
 * {@code camunda-external-task-client-js}, which do long polling over REST. Polling every 5 s
 * here is only for the demo. Tests disable the scheduler ({@code academy.workers.payment.enabled=false})
 * and call {@link #pollAndProcess()} directly - deterministic tests.
 *
 * <p>Mock rule: amounts above 5,00,000 are declined (business error).</p>
 */
@Component
public class PaymentExternalTaskWorker {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentExternalTaskWorker.class);
  private static final String WORKER_ID = "payment-worker-" + UUID.randomUUID().toString().substring(0, 6);
  private static final long LOCK_DURATION_MS = 30_000;

  private final ExternalTaskService externalTaskService;
  private final boolean enabled;

  public PaymentExternalTaskWorker(ExternalTaskService externalTaskService,
                                   @Value("${academy.workers.payment.enabled:true}") boolean enabled) {
    this.externalTaskService = externalTaskService;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelay = 5000, initialDelay = 10000)
  public void scheduledPoll() {
    if (enabled) {
      pollAndProcess();
    }
  }

  /** Fetches, locks and processes up to 10 payment tasks. Returns how many were handled. */
  public int pollAndProcess() {
    List<LockedExternalTask> tasks = externalTaskService
        .fetchAndLock(10, WORKER_ID, true)              // true = highest camunda:taskPriority first
        .topic(ProcessConstants.TOPIC_PAYMENT, LOCK_DURATION_MS)
        .variables("payableAmount", "orderTotal")       // only fetch what you need!
        .execute();

    for (LockedExternalTask task : tasks) {
      try {
        double amount = ((Number) task.getVariables().get("payableAmount")).doubleValue();

        if (amount > 500_000) {
          LOG.warn("Payment declined for {} (amount {})", task.getBusinessKey(), amount);
          externalTaskService.handleBpmnError(task.getId(), WORKER_ID, ProcessConstants.ERROR_PAYMENT_DECLINED,
              "Card limit exceeded", Map.of("declineReason", "LIMIT_EXCEEDED"));
          continue;
        }

        String txId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        externalTaskService.complete(task.getId(), WORKER_ID, Map.of("paymentTransactionId", txId));
        LOG.info("Payment {} captured for order {}", txId, task.getBusinessKey());

      } catch (Exception e) {
        // Technical failure: count down retries. First failure: retries is null -> start at 3.
        int retriesLeft = task.getRetries() == null ? 3 : task.getRetries() - 1;
        externalTaskService.handleFailure(task.getId(), WORKER_ID, e.getMessage(),
            e.toString(), retriesLeft, 10_000L);
        LOG.error("Payment task {} failed, retries left {}", task.getId(), retriesLeft, e);
      }
    }
    return tasks.size();
  }
}
