package com.template.contracts;

import com.template.states.ApprovalState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.corda.testing.node.NodeTestUtils.ledger;

public class ContractTests {
    private final MockServices services =
            new MockServices(Arrays.asList("com.template"));

    private final TestIdentity alice = new TestIdentity(
            new CordaX500Name("Alice", "London", "GB"));
    private final TestIdentity bob = new TestIdentity(
            new CordaX500Name("Bob", "New York", "US"));

    private ApprovalState state(String id, List<net.corda.core.identity.Party> approvers) {
        return new ApprovalState(id, "Test request", alice.getParty(),
                approvers, Collections.emptyList(), false, new UniqueIdentifier());
    }

    @Test
    public void validCreateTransactionVerifies() {
        ApprovalState output = state("TEST001", Collections.singletonList(bob.getParty()));
        ledger(services, l -> {
            l.transaction(tx -> {
                tx.output(ApprovalContract.ID, output);
                tx.command(alice.getPublicKey(), new ApprovalContract.Commands.Create());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void emptyRequestIdIsRejected() {
        ApprovalState output = state(" ", Collections.singletonList(bob.getParty()));
        ledger(services, l -> {
            l.transaction(tx -> {
                tx.output(ApprovalContract.ID, output);
                tx.command(alice.getPublicKey(), new ApprovalContract.Commands.Create());
                return tx.fails();
            });
            return null;
        });
    }

    @Test
    public void requesterCannotBeApprover() {
        ApprovalState output = state("TEST002", Collections.singletonList(alice.getParty()));
        ledger(services, l -> {
            l.transaction(tx -> {
                tx.output(ApprovalContract.ID, output);
                tx.command(alice.getPublicKey(), new ApprovalContract.Commands.Create());
                return tx.fails();
            });
            return null;
        });
    }

    @Test
    public void duplicateApproversAreRejected() {
        ApprovalState output = state("TEST003",
                Arrays.asList(bob.getParty(), bob.getParty()));
        ledger(services, l -> {
            l.transaction(tx -> {
                tx.output(ApprovalContract.ID, output);
                tx.command(alice.getPublicKey(), new ApprovalContract.Commands.Create());
                return tx.fails();
            });
            return null;
        });
    }
}
