package com.academy.camunda.config;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * =============================================================================================
 *  DemoDataInitializer - creates users and groups with the IdentityService
 * =============================================================================================
 *
 * <h2>Why do we need users and groups?</h2>
 * User tasks are assigned in three ways in our BPMN files:
 * <ul>
 *   <li>{@code camunda:assignee="${managerId}"} - exactly one user owns the task.</li>
 *   <li>{@code camunda:candidateGroups="underwriters"} - everybody in the group SEES the task,
 *       one of them CLAIMS it (claim = become assignee), then completes it.</li>
 *   <li>{@code camunda:candidateUsers="a,b"} - same idea for an explicit user list.</li>
 * </ul>
 * The engine stores only the strings. Tasklist resolves "which groups is the logged-in user in?"
 * through the IdentityService. Here we use the engine's built-in DB identity (ACT_ID_USER,
 * ACT_ID_GROUP, ACT_ID_MEMBERSHIP). In production you usually plug in LDAP
 * ({@code LdapIdentityProviderPlugin}) or SSO (Keycloak / Azure AD via a custom plugin), and the
 * engine then reads users/groups read-only from there.
 *
 * <h2>Why ApplicationReadyEvent?</h2>
 * At that moment the process engine is fully built, so IdentityService is usable. The method is
 * idempotent (checks before creating) because the H2 database is a FILE and survives restarts.
 *
 * <h2>Id whitelist (a classic first-run error)</h2>
 * The engine validates user/group/tenant ids against {@code generalResourceWhitelistPattern},
 * default {@code [a-zA-Z0-9]+|camunda-admin}. Saving the group "fraud-investigators" therefore
 * fails with "ENGINE-... is not a valid resource identifier" unless the pattern allows hyphens -
 * see generic-properties in application.yaml ({@code [a-zA-Z0-9-]+}).
 *
 * <p>Every demo user's password is "demo". Log in to http://localhost:8080/camunda as e.g.
 * "anil" (underwriter) to see the loan underwriting tasks in Tasklist.</p>
 *
 * <p>Security note: with {@code camunda.bpm.authorization.enabled=false} (the default) any
 * logged-in user may use every web app. Enable authorization in production and grant
 * Tasklist/Cockpit application permissions explicitly via AuthorizationService or Admin.</p>
 */
@Component
public class DemoDataInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(DemoDataInitializer.class);

  private final IdentityService identityService;

  public DemoDataInitializer(IdentityService identityService) {
    this.identityService = identityService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void createDemoUsersAndGroups() {
    // group id -> human readable name. These ids are used in camunda:candidateGroups.
    Map<String, String> groups = Map.ofEntries(
        Map.entry("loan-officers", "Loan officers"),
        Map.entry("underwriters", "Underwriters"),
        Map.entry("senior-adjusters", "Senior claim adjusters"),
        Map.entry("medical-team", "Medical team"),
        Map.entry("fraud-investigators", "Fraud investigators"),
        Map.entry("hr", "Human resources"),
        Map.entry("hr-directors", "HR directors"),
        Map.entry("it-team", "IT team"),
        Map.entry("kyc-officers", "KYC officers"));
    groups.forEach(this::ensureGroup);

    // user id -> group memberships
    ensureUser("anil", "Anil", "Underwriter", "underwriters", "loan-officers");
    ensureUser("john", "John", "Adjuster", "senior-adjusters");
    ensureUser("mary", "Mary", "Adjuster", "medical-team");
    ensureUser("peter", "Peter", "Adjuster", "fraud-investigators");
    ensureUser("priya", "Priya", "Manager", "hr");
    ensureUser("rahul", "Rahul", "HR Director", "hr-directors", "hr");
    ensureUser("sneha", "Sneha", "IT Admin", "it-team");
    ensureUser("kiran", "Kiran", "KYC Officer", "kyc-officers");
    LOG.info("Demo users ready (password for everyone: 'demo').");
  }

  private void ensureGroup(String id, String name) {
    if (identityService.createGroupQuery().groupId(id).count() == 0) {
      Group group = identityService.newGroup(id);
      group.setName(name);
      group.setType("WORKFLOW"); // "SYSTEM" is reserved for camunda-admin style groups
      identityService.saveGroup(group);
    }
  }

  private void ensureUser(String id, String first, String last, String... groupIds) {
    if (identityService.createUserQuery().userId(id).count() == 0) {
      User user = identityService.newUser(id);
      user.setFirstName(first);
      user.setLastName(last);
      user.setEmail(id + "@academy.example");
      user.setPassword("demo");
      identityService.saveUser(user);
      for (String groupId : groupIds) {
        identityService.createMembership(id, groupId);
      }
    }
  }
}
