
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

import java.util.List;
import java.util.stream.Collectors;

@InitiatingFlow
@StartableByRPC
public class FinalizeApprovalFlow extends FlowLogic<SignedTransaction> {

    private final String requestId;

    public FinalizeApprovalFlow(String requestId) {
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
                        new FlowException("Request not found."));

        ApprovalState input = stateRef.getState().getData();
        Party requester = getOurIdentity();

        if (!input.getRequester().equals(requester)) {
            throw new FlowException(
                    "Only the requester can finalize this request.");
        }

        if (input.isFinalized()) {
            throw new FlowException("Request is already finalized.");
        }

        if (!input.getApprovedBy().containsAll(input.getApprovers())) {
            throw new FlowException(
                    "All required approvers must approve first.");
        }

        ApprovalState output = new ApprovalState(
                input.getRequestId(),
                input.getDescription(),
                input.getRequester(),
                input.getApprovers(),
                input.getApprovedBy(),
                true,
                input.getLinearId()
        );

        TransactionBuilder builder = new TransactionBuilder(
                getServiceHub().getNetworkMapCache()
                        .getNotaryIdentities().get(0));

        builder.addInputState(stateRef);
        builder.addOutputState(output, ApprovalContract.ID);
        builder.addCommand(
                new Command<>(
                        new ApprovalContract.Commands.Finalize(),
                        requester.getOwningKey()
                )
        );

        builder.verify(getServiceHub());

        SignedTransaction signedTx =
                getServiceHub().signInitialTransaction(builder);

        List<FlowSession> sessions = input.getParticipants().stream()
                .filter(p -> !p.equals(requester))
                .distinct()
                .map(this::initiateFlow)
                .collect(Collectors.toList());

        return subFlow(new FinalityFlow(signedTx, sessions));
    }
}
