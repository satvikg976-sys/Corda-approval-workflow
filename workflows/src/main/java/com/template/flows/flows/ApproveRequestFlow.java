
package com.template.flows.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.contracts.ApprovalContract;
import com.template.states.ApprovalState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.flows.InitiatingFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@InitiatingFlow
@StartableByRPC
public class ApproveRequestFlow extends FlowLogic<SignedTransaction> {

    private final String requestId;

    public ApproveRequestFlow(String requestId) {
        this.requestId = requestId;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {

        List<StateAndRef<ApprovalState>> states =
                getServiceHub().getVaultService()
                        .queryBy(ApprovalState.class)
                        .getStates();

        StateAndRef<ApprovalState> stateRef = states.stream()
                .filter(s -> s.getState().getData()
                        .getRequestId().equals(requestId))
                .findFirst()
                .orElseThrow(() ->
                        new FlowException("Approval request not found."));

        ApprovalState input = stateRef.getState().getData();
        Party approver = getOurIdentity();

        if (input.isFinalized()) {
            throw new FlowException("Request is already finalized.");
        }

        if (!input.getApprovers().contains(approver)) {
            throw new FlowException(
                    "You are not an authorized approver.");
        }

        if (input.getApprovedBy().contains(approver)) {
            throw new FlowException(
                    "You have already approved this request.");
        }

        List<Party> updatedApprovals =
                new ArrayList<>(input.getApprovedBy());
        updatedApprovals.add(approver);

        ApprovalState output = new ApprovalState(
                input.getRequestId(),
                input.getDescription(),
                input.getRequester(),
                input.getApprovers(),
                updatedApprovals,
                false,
                input.getLinearId()
        );

        TransactionBuilder builder = new TransactionBuilder(
                getServiceHub().getNetworkMapCache()
                        .getNotaryIdentities().get(0));

        builder.addInputState(stateRef);
        builder.addOutputState(output, ApprovalContract.ID);
        builder.addCommand(
                new Command<>(
                        new ApprovalContract.Commands.Approve(),
                        approver.getOwningKey()
                )
        );

        builder.verify(getServiceHub());

        SignedTransaction signedTx =
                getServiceHub().signInitialTransaction(builder);

        List<FlowSession> sessions = input.getParticipants().stream()
                .filter(p -> !p.equals(approver))
                .distinct()
                .map(this::initiateFlow)
                .collect(Collectors.toList());

        return subFlow(new FinalityFlow(signedTx, sessions));
    }
}
