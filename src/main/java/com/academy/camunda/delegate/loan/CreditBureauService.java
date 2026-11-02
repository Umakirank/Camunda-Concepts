package com.academy.camunda.delegate.loan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * =============================================================================================
 *  CreditBureauService - a PLAIN Spring bean called from a BPMN expression
 * =============================================================================================
 *
 * <h2>Used by</h2>
 * {@code Task_FetchCreditScore} in loan-approval.bpmn:
 * <pre>{@code
 * camunda:asyncBefore="true"
 * camunda:expression="${creditBureauService.fetchCreditScore(applicantPan)}"
 * camunda:resultVariable="creditScore"
 * <camunda:failedJobRetryTimeCycle>R3/PT5S</camunda:failedJobRetryTimeCycle>
 * }</pre>
 *
 * <h2>Why is this nice?</h2>
 * The class does not know Camunda at all (no JavaDelegate, no DelegateExecution). The BPMN
 * expression passes the variable as a method argument and stores the return value in
 * "creditScore". Existing service classes can be orchestrated without writing adapters.
 *
 * <h2>asyncBefore + retries + incidents - the full story</h2>
 * <ol>
 *   <li>Because of asyncBefore, the engine does NOT call the bureau in the user's request.
 *       It commits the transaction with a JOB row (ACT_RU_JOB) and returns immediately.</li>
 *   <li>The Job Executor acquires the job (locks it), opens a NEW transaction and calls us.</li>
 *   <li>We throw (bureau down) -> rollback; the job's retries are decremented and its due date
 *       moved by the retry cycle "R3/PT5S" = 3 attempts, 5 seconds apart.</li>
 *   <li>Retries reach 0 -> an INCIDENT appears in Cockpit (red badge). The token stays safely
 *       BEFORE the task. An operator fixes the cause and clicks "Retry" (or calls
 *       managementService.setJobRetries(jobId, 1)) - see OperationsService.</li>
 * </ol>
 * This is THE pattern for calling remote systems: never lose a token, never rely on the
 * client's HTTP request staying open.
 *
 * <h2>Mock behaviour (so you can play with it)</h2>
 * <ul>
 *   <li>The 4 digits of the PAN are the score: ABCDE0800F -> 800, ABCDE0550F -> 550.</li>
 *   <li>Values outside 300..900 -> 650.</li>
 *   <li>PAN starting with "ERROR" -> simulated outage (exception) -> retries -> incident.</li>
 * </ul>
 */
@Service
public class CreditBureauService {

  private static final Logger LOG = LoggerFactory.getLogger(CreditBureauService.class);

  /**
   * Returns a primitive long on purpose: the start form declares numbers as "long", and
   * keeping one numeric type (Long) in all variables avoids surprises in forms and DMN.
   */
  public long fetchCreditScore(String pan) {
    if (pan.startsWith("ERROR")) {
      LOG.error("Credit bureau unavailable for {}", pan);
      throw new IllegalStateException("Credit bureau timeout (simulated)");
    }
    long score = Long.parseLong(pan.substring(5, 9));
    if (score < 300 || score > 900) {
      score = 650;
    }
    LOG.info("Credit score for {} = {}", pan, score);
    return score;
  }
}
