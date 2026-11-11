
package com.template.flows.flows;

import com.template.flows.CreateApprovalFlow;
import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.flows.FlowLogic;
import net.corda.core.transactions.SignedTransaction;

@InitiatedBy(CreateApprovalFlow.class)
public class ApprovalResponderFlow extends FlowLogic<Void> {

    private final FlowSession session;

    public ApprovalResponderFlow(FlowSession session) {
        this.session = session;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        subFlow(new ReceiveFinalityFlow(session));
        return null;
    }
}
