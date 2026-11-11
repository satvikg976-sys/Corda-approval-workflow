
package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.contracts.ApprovalContract;
import com.template.states.ApprovalState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.InitiatingFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@InitiatingFlow
@StartableByRPC
public class CreateApprovalFlow extends FlowLogic<SignedTransaction> {

    private final String requestId;
    private final String description;
    private final List<Party> approvers;

    public CreateApprovalFlow(
            String requestId,
            String description,
            List<Party> approvers) {
        this.requestId = requestId;
        this.description = description;
        this.approvers = approvers;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {

        if (approvers == null || approvers.isEmpty()) {
            throw new FlowException(
                    "At least one approver is required.");
        }

        Party notary = getServiceHub()
                .getNetworkMapCache()
                .getNotaryIdentities()
                .get(0);

        ApprovalState outputState = new ApprovalState(
                requestId,
                description,
                getOurIdentity(),
                approvers,
                new ArrayList<>(),
                false,
                new UniqueIdentifier()
        );

        Command<ApprovalContract.Commands.Create> command =
                new Command<>(
                        new ApprovalContract.Commands.Create(),
                        getOurIdentity().getOwningKey()
                );

        TransactionBuilder transactionBuilder =
                new TransactionBuilder(notary)
                        .addOutputState(
                                outputState,
                                ApprovalContract.ID)
                        .addCommand(command);

        transactionBuilder.verify(getServiceHub());

        SignedTransaction signedTransaction =
                getServiceHub()
                        .signInitialTransaction(transactionBuilder);

        List<net.corda.core.flows.FlowSession> sessions =
                approvers.stream()
                        .distinct()
                        .map(this::initiateFlow)
                        .collect(Collectors.toList());

        return subFlow(
                new FinalityFlow(signedTransaction, sessions));
    }
}
