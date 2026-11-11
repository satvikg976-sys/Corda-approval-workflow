
package com.template.flows.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.FlowLogic;

@InitiatedBy(FinalizeApprovalFlow.class)
public class FinalizeApprovalResponderFlow extends FlowLogic<Void> {

    private final FlowSession session;

    public FinalizeApprovalResponderFlow(FlowSession session) {
        this.session = session;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        subFlow(new ReceiveFinalityFlow(session));
        return null;
    }
}
