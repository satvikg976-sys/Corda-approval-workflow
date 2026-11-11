package com.template.contracts;

import com.template.states.ApprovalState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StateTests {

    private final TestIdentity alice = new TestIdentity(
            new CordaX500Name("Alice", "London", "GB"));
    private final TestIdentity bob = new TestIdentity(
            new CordaX500Name("Bob", "New York", "US"));

    @Test
    public void stateStoresCorrectValues() {
        ApprovalState state = new ApprovalState(
                "REQ001",
                "Test request",
                alice.getParty(),
                Collections.singletonList(bob.getParty()),
                Collections.emptyList(),
                false,
                new UniqueIdentifier());

        assertEquals("REQ001", state.getRequestId());
        assertEquals("Test request", state.getDescription());
        assertEquals(alice.getParty(), state.getRequester());
        assertEquals(Collections.singletonList(bob.getParty()), state.getApprovers());
        assertTrue(state.getApprovedBy().isEmpty());
        assertFalse(state.isFinalized());
        assertEquals(2, state.getParticipants().size());
    }
}
