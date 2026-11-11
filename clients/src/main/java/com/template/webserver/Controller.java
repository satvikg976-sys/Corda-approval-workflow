
package com.template.webserver;

import com.template.flows.CreateApprovalFlow;
import com.template.flows.flows.ApproveRequestFlow;
import com.template.flows.flows.FinalizeApprovalFlow;
import com.template.states.ApprovalState;

import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.transactions.SignedTransaction;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class Controller {

    private final NodeRPCConnection rpc;

    public Controller(NodeRPCConnection rpc) {
        this.rpc = rpc;
    }

    private final Map<String, String> partyNames = Map.of(
            "PartyB", "O=PartyB,L=New York,C=US",
            "PartyC", "O=PartyC,L=Singapore,C=SG",
            "PartyD", "O=PartyD,L=Paris,C=FR"
    );

    @GetMapping("/health")
    public String health() {
        return "Corda Approval API is running";
    }

    // Create approval request through PartyA
    @PostMapping("/requests")
    public Map<String, Object> createRequest(
            @RequestBody Map<String, Object> body) throws Exception {

        String requestId = (String) body.get("requestId");
        String description = (String) body.get("description");

        if (requestId == null || requestId.isBlank()
                || description == null || description.isBlank()
                || !(body.get("approvers") instanceof List<?>)) {
            throw new IllegalArgumentException(
                    "Valid requestId, description and approvers are required");
        }

        List<String> approverNames =
                (List<String>) body.get("approvers");

        if (approverNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one approver is required");
        }

        CordaRPCOps partyA = rpc.getProxy("PartyA");

        // Prevent duplicate request IDs
        List<StateAndRef<ApprovalState>> existingStates =
                partyA.vaultQuery(ApprovalState.class).getStates();

        boolean idExists = existingStates.stream()
                .map(s -> s.getState().getData())
                .anyMatch(s -> s.getRequestId()
                        .equalsIgnoreCase(requestId));

        if (idExists) {
            throw new IllegalArgumentException(
                    "Request ID already exists: " + requestId);
        }

        List<Party> approvers = new ArrayList<>();

        for (String name : approverNames) {
            String x500Name = partyNames.get(name);

            if (x500Name == null) {
                throw new IllegalArgumentException(
                        "Invalid approver: " + name);
            }

            Party party = partyA.wellKnownPartyFromX500Name(
                    CordaX500Name.parse(x500Name));

            if (party == null) {
                throw new IllegalArgumentException(
                        name + " is not available on the network");
            }

            if (approvers.contains(party)) {
                throw new IllegalArgumentException(
                        "Duplicate approver: " + name);
            }

            approvers.add(party);
        }

        SignedTransaction transaction =
                partyA.startFlowDynamic(
                        CreateApprovalFlow.class,
                        requestId,
                        description,
                        approvers
                ).getReturnValue().get();

        return Map.of(
                "message", "Approval request created",
                "requestId", requestId,
                "transactionId", transaction.getId().toString()
        );
    }

    // Approve through the selected approver's node
    @PostMapping("/requests/{id}/approve")
    public Map<String, Object> approveRequest(
            @PathVariable String id,
            @RequestBody Map<String, String> body) throws Exception {

        String approver = body.get("approver");

        if (approver == null || !partyNames.containsKey(approver)) {
            throw new IllegalArgumentException(
                    "Approver must be PartyB, PartyC or PartyD");
        }

        CordaRPCOps partyA = rpc.getProxy("PartyA");

        List<StateAndRef<ApprovalState>> states =
                partyA.vaultQuery(ApprovalState.class).getStates();

        List<ApprovalState> matchingRequests = new ArrayList<>();

        for (StateAndRef<ApprovalState> state : states) {
            ApprovalState data = state.getState().getData();

            if (data.getRequestId().equalsIgnoreCase(id)) {
                matchingRequests.add(data);
            }
        }

        if (matchingRequests.isEmpty()) {
            throw new IllegalArgumentException(
                    "Request not found: " + id);
        }

        if (matchingRequests.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple records have this request ID. " +
                    "Approval is blocked to protect existing records: " + id);
        }

        ApprovalState request = matchingRequests.get(0);

        if (request.isFinalized()) {
            throw new IllegalArgumentException(
                    "Request is already finalized: " + id);
        }

        String approverX500 = partyNames.get(approver);

       boolean authorized = request.getApprovers().stream()
        .anyMatch(p -> p.getName()
                .equals(CordaX500Name.parse(approverX500)));

        if (!authorized) {
            throw new IllegalArgumentException(
                    approver + " is not an approver for " + id);
        }

boolean alreadyApproved = request.getApprovedBy().stream()
        .anyMatch(p -> p.getName()
                .equals(CordaX500Name.parse(approverX500)));
        if (alreadyApproved) {
            throw new IllegalArgumentException(
                    approver + " has already approved " + id);
        }

        CordaRPCOps approverProxy = rpc.getProxy(approver);

        SignedTransaction transaction =
                approverProxy.startFlowDynamic(
                        ApproveRequestFlow.class,
                        id
                ).getReturnValue().get();

        return Map.of(
                "message", "Request approved",
                "requestId", id,
                "approvedBy", approver,
                "transactionId", transaction.getId().toString()
        );
    }

    // Finalize through PartyA
    @PostMapping("/requests/{id}/finalize")
    public Map<String, Object> finalizeRequest(
            @PathVariable String id) throws Exception {

        CordaRPCOps partyA = rpc.getProxy("PartyA");

        List<StateAndRef<ApprovalState>> states =
                partyA.vaultQuery(ApprovalState.class).getStates();

        List<ApprovalState> matchingRequests = new ArrayList<>();

        for (StateAndRef<ApprovalState> state : states) {
            ApprovalState data = state.getState().getData();

            if (data.getRequestId().equalsIgnoreCase(id)) {
                matchingRequests.add(data);
            }
        }

        if (matchingRequests.size() != 1) {
            throw new IllegalArgumentException(
                    matchingRequests.isEmpty()
                    ? "Request not found: " + id
                    : "Multiple records have this request ID. " +
                      "Finalization is blocked: " + id);
        }

        if (matchingRequests.get(0).isFinalized()) {
            throw new IllegalArgumentException(
                    "Request is already finalized: " + id);
        }

        SignedTransaction transaction =
                partyA.startFlowDynamic(
                        FinalizeApprovalFlow.class,
                        id
                ).getReturnValue().get();

        return Map.of(
                "message", "Request finalized",
                "requestId", id,
                "transactionId", transaction.getId().toString()
        );
    }

    // Retrieve requests
    @GetMapping("/requests")
    public List<Map<String, Object>> getRequests() {

        CordaRPCOps partyA = rpc.getProxy("PartyA");

        List<StateAndRef<ApprovalState>> states =
                partyA.vaultQuery(ApprovalState.class).getStates();

        List<Map<String, Object>> result = new ArrayList<>();

        for (StateAndRef<ApprovalState> stateAndRef : states) {

            ApprovalState state =
                    stateAndRef.getState().getData();

            Map<String, Object> request = new LinkedHashMap<>();

            request.put("requestId", state.getRequestId());
            request.put("description", state.getDescription());
            request.put("requester",
                    state.getRequester().getName().toString());

            request.put("approvers",
                    state.getApprovers().stream()
                            .map(p -> p.getName().toString())
                            .toList());

            request.put("approvedBy",
                    state.getApprovedBy().stream()
                            .map(p -> p.getName().toString())
                            .toList());

            request.put("finalized", state.isFinalized());

            result.add(request);
        }

        return result;

    }
        @org.springframework.web.bind.annotation.ExceptionHandler(
            IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>>
    handleIllegalArgument(IllegalArgumentException ex) {

        return org.springframework.http.ResponseEntity
                .badRequest()
                .body(Map.of("error", ex.getMessage()));
    }
}
