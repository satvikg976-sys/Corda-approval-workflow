
package com.template.states;

import com.template.contracts.ApprovalContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@BelongsToContract(ApprovalContract.class)
public class ApprovalState implements LinearState {

    private final String requestId;
    private final String description;
    private final Party requester;
    private final List<Party> approvers;
    private final List<Party> approvedBy;
    private final boolean finalized;
    private final UniqueIdentifier linearId;

    public ApprovalState(
            String requestId,
            String description,
            Party requester,
            List<Party> approvers,
            List<Party> approvedBy,
            boolean finalized,
            UniqueIdentifier linearId) {

        this.requestId = requestId;
        this.description = description;
        this.requester = requester;
        this.approvers = new ArrayList<>(approvers);
        this.approvedBy = new ArrayList<>(approvedBy);
        this.finalized = finalized;
        this.linearId = linearId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getDescription() {
        return description;
    }

    public Party getRequester() {
        return requester;
    }

    public List<Party> getApprovers() {
        return approvers;
    }

    public List<Party> getApprovedBy() {
        return approvedBy;
    }

    public boolean isFinalized() {
        return finalized;
    }

    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @Override
    public List<AbstractParty> getParticipants() {
        List<AbstractParty> participants = new ArrayList<>();
        participants.add(requester);
        participants.addAll(approvers);

        return participants.stream()
                .distinct()
                .collect(Collectors.toList());
    }
}
