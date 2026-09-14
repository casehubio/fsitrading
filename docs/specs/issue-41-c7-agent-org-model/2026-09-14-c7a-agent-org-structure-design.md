# C7a: Trading Desk Agent Org Structure — Design Spec

Define the fsitrading agent hierarchy using eidos org model (#150).
Map 13 response agents into 4 teams with tiered escalation.

---

## 1. Org Structure

```
Trading Desk (root)
├─ Emergency Response Team
│  ├─ emergencyHaltAgent (lead)
│  ├─ closePositionsAgent
│  ├─ haltAndWaitAgent
│  ├─ liquidationAgent
│  └─ exposureCloserAgent
├─ Risk Management Team
│  ├─ positionReducerAgent (lead)
│  ├─ hedgeAgent
│  └─ adjustLimitsAgent
├─ Analysis Team
│  ├─ sentimentAnalyserAgent (lead)
│  ├─ reEvaluatorAgent
│  └─ monitorAgent
└─ Operations Team
   ├─ verifyAgent (lead)
   └─ alertOncallAgent
```

## 2. Relationships

**Cross-team escalation:**
- Analysis ESCALATES_TO Risk Management (scope: incident-respond)
- Risk Management ESCALATES_TO Emergency Response (scope: incident-respond)

**Supervision:**
- Operations SUPERVISES Analysis (verifyAgent checks analysis outcomes)
- Operations SUPERVISES Risk Management
- Operations SUPERVISES Emergency Response

**Within-team:**
- Each team's lead agent: other members REPORTS_TO the lead

## 3. YAML Definition

File: `app/src/main/resources/fsitrading/org-structure.yaml`

```yaml
organization:
  units:
    - unitId: trading-desk
      name: Trading Desk
      kind: tiered-escalation

    - unitId: emergency-response
      name: Emergency Response Team
      kind: team
      members:
        - agentId: emergencyHaltAgent
          role: lead
        - agentId: closePositionsAgent
          role: responder
        - agentId: haltAndWaitAgent
          role: responder
        - agentId: liquidationAgent
          role: responder
        - agentId: exposureCloserAgent
          role: responder
      capabilities:
        - name: emergency-halt
        - name: position-close
        - name: forced-liquidation
      goals:
        - name: stop-loss-containment
          priority: PRIMARY

    - unitId: risk-management
      name: Risk Management Team
      kind: team
      members:
        - agentId: positionReducerAgent
          role: lead
        - agentId: hedgeAgent
          role: specialist
        - agentId: adjustLimitsAgent
          role: specialist
      capabilities:
        - name: exposure-reduction
        - name: hedging
        - name: limit-adjustment
      goals:
        - name: risk-mitigation
          priority: PRIMARY

    - unitId: analysis
      name: Analysis Team
      kind: team
      members:
        - agentId: sentimentAnalyserAgent
          role: lead
        - agentId: reEvaluatorAgent
          role: analyst
        - agentId: monitorAgent
          role: analyst
      capabilities:
        - name: sentiment-analysis
        - name: strategy-evaluation
        - name: market-monitoring
      goals:
        - name: situation-assessment
          priority: PRIMARY

    - unitId: operations
      name: Operations Team
      kind: team
      members:
        - agentId: verifyAgent
          role: lead
        - agentId: alertOncallAgent
          role: coordinator
      capabilities:
        - name: outcome-verification
        - name: human-escalation
      goals:
        - name: response-assurance
          priority: PRIMARY

  relationships:
    # Cross-team escalation chain
    - sourceUnitId: analysis
      targetUnitId: risk-management
      kind: ESCALATES_TO
      scope:
        capabilityName: incident-respond

    - sourceUnitId: risk-management
      targetUnitId: emergency-response
      kind: ESCALATES_TO
      scope:
        capabilityName: incident-respond

    # Operations supervises all teams
    - sourceUnitId: operations
      targetUnitId: analysis
      kind: SUPERVISES

    - sourceUnitId: operations
      targetUnitId: risk-management
      kind: SUPERVISES

    - sourceUnitId: operations
      targetUnitId: emergency-response
      kind: SUPERVISES

    # Within-team reporting
    - sourceAgentId: closePositionsAgent
      targetAgentId: emergencyHaltAgent
      kind: REPORTS_TO

    - sourceAgentId: haltAndWaitAgent
      targetAgentId: emergencyHaltAgent
      kind: REPORTS_TO

    - sourceAgentId: liquidationAgent
      targetAgentId: emergencyHaltAgent
      kind: REPORTS_TO

    - sourceAgentId: exposureCloserAgent
      targetAgentId: emergencyHaltAgent
      kind: REPORTS_TO

    - sourceAgentId: hedgeAgent
      targetAgentId: positionReducerAgent
      kind: REPORTS_TO

    - sourceAgentId: adjustLimitsAgent
      targetAgentId: positionReducerAgent
      kind: REPORTS_TO

    - sourceAgentId: reEvaluatorAgent
      targetAgentId: sentimentAnalyserAgent
      kind: REPORTS_TO

    - sourceAgentId: monitorAgent
      targetAgentId: sentimentAnalyserAgent
      kind: REPORTS_TO

    - sourceAgentId: alertOncallAgent
      targetAgentId: verifyAgent
      kind: REPORTS_TO
```

## 4. Loading

In `OvernightIncidentCaseHub.augment()`:

```java
@Inject OrgRegistry orgRegistry;

@Override
protected void augment(CaseDefinition definition) {
    // existing agent + CBR wiring...

    // Load org structure from YAML
    OrgYamlRegistrar.loadAndRegister(
        "fsitrading/org-structure.yaml", orgRegistry);
}
```

If `OrgYamlRegistrar` doesn't exist yet, use the builder API as fallback
and file an eidos issue for the YAML registrar.

## 5. Integration Points

The org structure enables:
- **Routing awareness** — prefer same-team agents when multiple candidates match
- **Escalation paths** — `orgRegistry.escalationPath(agentId)` returns the chain
- **Supervision queries** — `orgRegistry.supervisors(agentId)` for oversight gates
- **org-diagram UI** — renders the hierarchy (C7d, #45)
- **Decision narratives** — explain team context: "Emergency Response Team's lead agent responded"

## 6. Testing

- Verify YAML loads without errors
- Verify `orgRegistry.escalationPath("monitorAgent")` returns Analysis → Risk → Emergency
- Verify `orgRegistry.supervisors("positionReducerAgent")` returns Operations
- Verify `orgRegistry.membersOf("emergency-response")` returns 5 agents

## References

- eidos #150 — OrgStructure API, OrgRegistry, RelationshipKind
- eidos YAML archetypes — tiered-escalation pattern
- OvernightIncidentCaseDescriptor — 13 agent definitions
- IncidentSeverityDescriptor — HTN decomposition (implicit team grouping)
- decisions.md D1-D3
