# Camunda 7 Engine Academy

A runnable Spring Boot project that embeds the **Camunda 7.24 engine** and covers BPMN, DMN, FEEL and the Java API through **6 business processes**, **3 supporting processes** and **7 DMN files (11 decisions)**. Every Java class, BPMN file and DMN file has comments that explain how the concept works inside the engine and when to use it.

> Camunda 7 Community Edition reached its final release line with 7.24 (Oct 2025). Use this project to learn C7 or maintain C7 systems. Every concept here carries over to Camunda 8; the C7/C8 notes at the end list the differences.

---

## 1. Run it

```bash
# Java 17+ and Maven 3.9+
mvn spring-boot:run
```

| What | URL | Login |
|---|---|---|
| Cockpit / Tasklist / Admin | http://localhost:8080/camunda | `demo` / `demo` |
| Camunda REST API | http://localhost:8080/engine-rest | – |
| H2 console (look at ACT_* tables) | http://localhost:8080/h2-console | JDBC URL `jdbc:h2:file:./camunda-h2-db/process-engine`, `sa`/`sa` |
| Our demo API | http://localhost:8080/api/... | – |

Demo users (password `demo`): `anil` (underwriters), `john` (senior-adjusters), `mary` (medical-team), `peter` (fraud-investigators), `priya` (manager, hr), `rahul` (hr-directors), `sneha` (it-team), `kiran` (kyc-officers).

Run the tests (each process path is a test):

```bash
mvn test
```

Open any `.bpmn` / `.dmn` from `src/main/resources` in **Camunda Modeler** (Platform 7). Every element has a *Documentation* text that explains it.

---

## 2. Project map

```
src/main/java/com/academy/camunda
├── CamundaAcademyApplication.java      engine bootstrap + the service map
├── config/
│   ├── ProcessConstants.java           keys, message/signal names, error codes
│   ├── DemoDataInitializer.java        IdentityService: users & groups
│   ├── AuditProcessEnginePlugin.java   ProcessEnginePlugin + BPMN parse listener
│   └── SpringEngineEventListeners.java Spring eventing bridge (TaskEvent)
├── domain/Order.java, OrderItem.java   JSON (Spin) object variables
├── delegate/
│   ├── common/   EmailNotificationDelegate (camunda:class + field injection)
│   │             ProcessAuditListener      (execution listener)
│   ├── loan/     Validate (BpmnError), CreditBureauService (expression), Disburse (async),
│   │             UnderwriterTaskListener (task listener), SLA escalation
│   ├── order/    Prepare, Reserve/Release (compensation), Invoice, Refund, RequestShipment
│   ├── claim/    Register, Reject, NotifyCompliance, PayClaim (IO mapping), DailyReport (history)
│   ├── onboarding/ CreateRecord, ProvisionEquipment (multi-instance), event sub-process delegates
│   ├── leave/    LeaveBookingDelegate
│   └── creditcard/ CardIssuingService (bean-method expressions, transaction compensation)
├── worker/PaymentExternalTaskWorker.java  external task fetch-and-lock / complete / BPMN error / failure
├── service/
│   ├── ProcessLifecycleService.java   RuntimeService + RepositoryService
│   ├── TaskWorkService.java           TaskService + FormService
│   ├── DecisionEvaluationService.java DecisionService (+ OUTPUT ORDER emulation)
│   ├── ProcessHistoryService.java     HistoryService (audit trail)
│   └── OperationsService.java         ManagementService, incidents, suspension, modification, migration
└── web/  ProcessController, TaskController, DecisionController, OperationsController, ApiExceptionHandler

src/main/resources
├── application.yaml        (every engine setting explained)
├── bpmn/  9 process models
└── dmn/   7 decision files
src/test/java/...           7 test classes, one per process + DecisionTablesTest
```

---

## 3. The processes

| # | Process (key) | Industry | Main concepts |
|---|---|---|---|
| 1 | `loan-approval` | Banking | service task ×3 styles, business rule task (UNIQUE), exclusive gateway + default flow, error / non-interrupting timer / message boundary events, user task (candidate group, due date, priority, form), task listeners incl. timeout, execution listeners (process + sequence flow `take`), asyncBefore, retries → incident, job priority, send task |
| 2 | `order-fulfillment` | E-commerce | message start event, JSON object variable, COLLECT MAX + COLLECT MIN, expression-only task, **external task** + BPMN error from worker, parallel gateway, event-based gateway, message & timer catch, **compensation** (boundary, handlers, throw event) |
| 3 | `insurance-claim` | Insurance | receive task, interrupting timer (P7D), COLLECT → list, **call activity** (in/out mapping, business key), error + escalation boundaries on call activity, inclusive gateway split/join, **parallel multi-instance** with completion condition, Groovy **script task**, input/output mapping |
| 4 | `fraud-check` *(called)* | Insurance | COLLECT SUM scorecard, escalation throw, error end event |
| 5 | `employee-onboarding` | HR | embedded sub-process, **sequential multi-instance**, manual task, assignee expression, **event sub-processes** (non-interrupting + interrupting), **signal throw** with payload |
| 6 | `team-announcement` *(signal)* | HR | signal start event |
| 7 | `leave-approval` | HR | **DRD** (required decision), FIRST, conditional boundary event, timer cycle, link events, terminate end event |
| 8 | `credit-card-application` | Banking | ANY, RULE ORDER, **transaction sub-process**, cancel end + cancel boundary, compensation inside a transaction, asyncAfter |
| 9 | `daily-claims-report` | Insurance | timer start event (cron), HistoryService in a delegate |

### Hit policies → where to find them

| Hit policy | Decision | File |
|---|---|---|
| UNIQUE (U) | `loan-risk-assessment`, `leave-eligibility` | loan-risk-assessment.dmn, leave-approval.dmn |
| ANY (A) | `credit-card-eligibility` | credit-card-decisions.dmn |
| FIRST (F) | `leave-approval-level` (DRD root) | leave-approval.dmn |
| RULE ORDER (R) | `credit-card-offers` | credit-card-decisions.dmn |
| COLLECT (C) | `claim-required-documents` | claim-required-documents.dmn |
| COLLECT SUM (C+) | `fraud-risk-score` | fraud-risk-score.dmn |
| COLLECT MIN (C<) | `shipping-fee` | order-pricing.dmn |
| COLLECT MAX (C>) | `order-discount` | order-pricing.dmn |
| COLLECT COUNT (C#) | `matching-promotions-count` | customer-engagement.dmn |
| Literal expression | `customer-greeting` | customer-engagement.dmn |
| OUTPUT ORDER (O) / PRIORITY (P) | **not supported in Camunda 7**, emulated in `DecisionEvaluationService#offersInOutputOrder` | – |

### BPMN elements Camunda 7 does not execute
* **Complex gateway**: not supported by the C7 engine. Use an inclusive gateway plus a condition, or a multi-instance completion condition.
* **Data objects and stores**: the modeler accepts them, but the engine ignores them as documentation.

---

## 4. Try it with curl

```bash
# 1 LOAN: PAN digits = mock credit score (0800 LOW, 0700 MEDIUM, 0550 HIGH, ERROR... = outage)
curl -X POST localhost:8080/api/loans -H 'Content-Type: application/json' -d '{
  "applicantName":"Ravi","applicantPan":"ABCDE0700F","applicantEmail":"ravi@x.com",
  "annualIncome":1200000,"loanAmount":500000}'
curl "localhost:8080/api/tasks?user=anil"
curl -X POST "localhost:8080/api/tasks/<taskId>/complete?user=anil" -H 'Content-Type: application/json' \
     -d '{"approved":true,"comment":"looks good"}'
curl -X POST "localhost:8080/api/messages/LoanWithdrawn?businessKey=<LOAN-...>"

# 2 ORDER (the external worker polls every 5 s)
curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -d '{
  "orderId":"ORD-1001","customerId":"C-9","customerType":"GOLD","couponCode":null,
  "destinationZone":"METRO",
  "items":[{"sku":"PHONE","quantity":2,"unitPrice":12000,"weightKg":1.0}]}'
curl -X POST "localhost:8080/api/messages/ShipmentConfirmed?businessKey=ORD-1001"

# 3 CLAIM
curl -X POST localhost:8080/api/claims -H 'Content-Type: application/json' -d '{
  "policyNumber":"POL-1","claimType":"MOTOR","claimAmount":80000,"hasInjury":false,
  "daysSincePolicyStart":10,"previousClaims":0,"claimantEmail":"a@x.com"}'
curl -X POST "localhost:8080/api/messages/ClaimDocumentsUploaded?businessKey=<CLM-...>"

# 5 ONBOARDING  /  7 LEAVE  /  8 CREDIT CARD
curl -X POST localhost:8080/api/onboardings -H 'Content-Type: application/json' \
     -d '{"employeeName":"Asha","department":"ENGINEERING","managerId":"priya"}'
curl -X POST localhost:8080/api/leaves -H 'Content-Type: application/json' \
     -d '{"employeeName":"Kiran","leaveType":"ANNUAL","requestedDays":5,"availableBalance":12}'
curl -X PUT localhost:8080/api/process-instances/<id>/variables/leaveCancelled \
     -H 'Content-Type: application/json' -d 'true'
curl -X POST localhost:8080/api/credit-cards -H 'Content-Type: application/json' \
     -d '{"applicantName":"Meera","age":30,"monthlyIncome":120000}'

# DMN as a service
curl -X POST localhost:8080/api/decisions/leave-approval-level/evaluate -H 'Content-Type: application/json' \
     -d '{"leaveType":"ANNUAL","requestedDays":15,"availableBalance":10}'
curl "localhost:8080/api/decisions/greeting?name=Asha&type=GOLD"

# Operations
curl localhost:8080/api/ops/incidents
curl localhost:8080/api/ops/process-instances/<id>/history
curl -X POST "localhost:8080/api/ops/process-instances/<id>/move?from=Task_SendRejection&to=UserTask_Underwrite"
```

---

## 5. Suggested learning path

1. `CamundaAcademyApplication` → `application.yaml`: how the engine boots.
2. `loan-approval.bpmn` + `delegate/loan/*`: service tasks, user tasks, gateways, boundary events, async.
3. `LoanApprovalProcessTest`: how the engine moves the token step by step.
4. The DMN files in order 1→7, then `DecisionTablesTest`.
5. `order-fulfillment` (external tasks, compensation) → `insurance-claim` (call activity, multi-instance).
6. `employee-onboarding`, `leave-approval`, `credit-card-application` (event sub-processes, conditional events, transactions).
7. `service/*`: the engine API you will use in real projects. Finish with `OperationsService` (incidents, modification, migration).

---

## 6. Camunda 7 → 8 cheat sheet

| Camunda 7 (this project) | Camunda 8 |
|---|---|
| Embedded engine, relational DB, your JVM | Zeebe broker cluster, partitions, event log; you are a client |
| JavaDelegate / expressions / camunda:class | **Job workers** only (like our external task worker) |
| JUEL `${...}` in conditions | FEEL `=...` everywhere |
| `camunda:inputOutput`, call activity `camunda:in/out` | `zeebe:ioMapping`, propagate-all / mappings |
| Generated forms (`camunda:formData`) | Camunda Forms (form-js) |
| Script task (Groovy) | FEEL script task or job worker |
| Transaction sub-process / cancel events | Not supported. Model the saga with compensation and error events |
| Compensation | Supported in recent 8.x releases (check your version) |
| DMN hit policies U A F R C C+ C< C> C# | Same set (Camunda 8 runs DMN on the same FEEL-scala engine) |
| Cockpit / Tasklist / Admin | Operate / Tasklist / Identity, plus Optimize |
| Process instance migration and modification API | Available via Zeebe API / Operate |

Use the official *Camunda 7 to 8 migration* guide and the **Migration Analyzer** before you port.

---

## 7. Production checklist (short)

* Use PostgreSQL or Oracle, set `database.schema-update=false`, and run DB scripts under change control.
* Set history level and `historyTimeToLive` on every model, and schedule history cleanup.
* Enable `camunda.bpm.authorization.enabled` and secure `/engine-rest`; use SSO or LDAP for identity.
* Make every remote call **idempotent** and put async boundaries before it. Set a retry cycle for every external call.
* Monitor incidents, job backlog (`ACT_RU_JOB` due jobs) and external task backlog. Alert when they grow.
* Keep object variables small. Use the reference-not-copy pattern for big data.
* Version models (`camunda:versionTag`) and plan migration of running instances.
* Write one test per process path (see `src/test`).
