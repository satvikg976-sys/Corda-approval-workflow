
package com.template.contracts;

import com.template.states.ApprovalState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.TypeOnlyCommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import java.util.List;

public class ApprovalContract implements Contract {

    public static final String ID =
            "com.template.contracts.ApprovalContract";

    public interface Commands extends CommandData {
        class Create extends TypeOnlyCommandData implements Commands {}
        class Approve extends TypeOnlyCommandData implements Commands {}
        class Finalize extends TypeOnlyCommandData implements Commands {}
    }

    @Override
    public void verify(LedgerTransaction tx) {
        if (tx.getCommands().size() != 1) {
            throw new IllegalArgumentException(
                    "A transaction must have exactly one command.");
        }

        CommandData command = tx.getCommand(0).getValue();

        if (command instanceof Commands.Create) {
            verifyCreate(tx);
        } else if (command instanceof Commands.Approve) {
            verifyApprove(tx);
        } else if (command instanceof Commands.Finalize) {
            verifyFinalize(tx);
        } else {
            throw new IllegalArgumentException("Unknown approval command.");
        }
    }

    private void verifyCreate(LedgerTransaction tx) {
        if (!tx.getInputs().isEmpty() || tx.getOutputs().size() != 1) {
            throw new IllegalArgumentException(
                    "Create must have no inputs and one output.");
        }

        ApprovalState output = tx.outputsOfType(ApprovalState.class).get(0);

        if (output.getRequestId() == null ||
                output.getRequestId().trim().isEmpty()) {
            throw new IllegalArgumentException("Request ID is required.");
        }

        if (output.getDescription() == null ||
                output.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required.");
        }

        if (output.getApprovers() == null ||
                output.getApprovers().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one approver is required.");
        }

        if (!output.getApprovedBy().isEmpty() || output.isFinalized()) {
            throw new IllegalArgumentException(
                    "A new request cannot already be approved or finalized.");
        }

        if (output.getApprovers().contains(output.getRequester())) {
            throw new IllegalArgumentException(
                    "The requester cannot be an approver.");
        }

        if (output.getApprovers().stream().distinct().count()
                != output.getApprovers().size()) {
            throw new IllegalArgumentException(
                    "Duplicate approvers are not allowed.");
        }
    }

    private void verifyApprove(LedgerTransaction tx) {
        if (tx.getInputs().size() != 1 || tx.getOutputs().size() != 1) {
            throw new IllegalArgumentException(
                    "Approve must consume one state and create one state.");
        }

        ApprovalState input = tx.inputsOfType(ApprovalState.class).get(0);
        ApprovalState output = tx.outputsOfType(ApprovalState.class).get(0);

        if (input.isFinalized()) {
            throw new IllegalArgumentException(
                    "A finalized request cannot be approved.");
        }

        if (output.getApprovedBy().size() != input.getApprovedBy().size() + 1) {
            throw new IllegalArgumentException(
                    "Exactly one new approval must be added.");
        }

        if (!input.getRequestId().equals(output.getRequestId()) ||
                !input.getDescription().equals(output.getDescription()) ||
                !input.getRequester().equals(output.getRequester()) ||
                !input.getApprovers().equals(output.getApprovers()) ||
                !input.getLinearId().equals(output.getLinearId()) ||
                output.isFinalized()) {
            throw new IllegalArgumentException(
                    "Request details cannot change during approval.");
        }

        if (!output.getApprovedBy().containsAll(input.getApprovedBy())) {
            throw new IllegalArgumentException(
                    "Existing approvals cannot be removed.");
        }

        List<net.corda.core.identity.Party> added =
                output.getApprovedBy().stream()
                        .filter(p -> !input.getApprovedBy().contains(p))
                        .collect(java.util.stream.Collectors.toList());

        if (added.size() != 1 ||
                !input.getApprovers().contains(added.get(0))) {
            throw new IllegalArgumentException(
                    "Only a required approver can approve.");
        }

        if (input.getApprovedBy().contains(added.get(0))) {
            throw new IllegalArgumentException("Duplicate approval.");
        }
    }

    private void verifyFinalize(LedgerTransaction tx) {
        if (tx.getInputs().size() != 1 || tx.getOutputs().size() != 1) {
            throw new IllegalArgumentException(
                    "Finalize must consume one state and create one state.");
        }

        ApprovalState input = tx.inputsOfType(ApprovalState.class).get(0);
        ApprovalState output = tx.outputsOfType(ApprovalState.class).get(0);

        if (input.isFinalized()) {
            throw new IllegalArgumentException(
                    "Request is already finalized.");
        }

        if (!input.getApprovedBy().containsAll(input.getApprovers())) {
            throw new IllegalArgumentException(
                    "All required approvals must be completed first.");
        }

        if (!output.isFinalized() ||
                !input.getRequestId().equals(output.getRequestId()) ||
                !input.getDescription().equals(output.getDescription()) ||
                !input.getRequester().equals(output.getRequester()) ||
                !input.getApprovers().equals(output.getApprovers()) ||
                !input.getApprovedBy().equals(output.getApprovedBy()) ||
                !input.getLinearId().equals(output.getLinearId())) {
            throw new IllegalArgumentException(
                    "Finalization must preserve the request and its approvals.");
        }
    }
}
