
package com.template.flows.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.FlowLogic;

@InitiatedBy(ApproveRequestFlow.class)
public class ApproveRequestResponderFlow extends FlowLogic<Void> {

    private final FlowSession session;

    public ApproveRequestResponderFlow(FlowSession session) {
        this.session = session;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        subFlow(new ReceiveFinalityFlow(session));
        return null;
    }
}
