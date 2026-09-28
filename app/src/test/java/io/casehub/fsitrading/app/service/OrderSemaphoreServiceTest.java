package io.casehub.fsitrading.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderSemaphoreServiceTest {

    private OrderSemaphoreService service;

    @BeforeEach
    void setUp() {
        service = new OrderSemaphoreService();
    }

    @Test
    void haltIncrementsCounterAndReturnsTicket() {
        assertFalse(service.isHalted());
        var ticket = service.halt("flash-crash", "ALL", null, "playbook-1");
        assertNotNull(ticket.ticketId());
        assertTrue(service.isHalted());
        assertEquals(1, service.haltCount());
    }

    @Test
    void multipleHaltsIncrementCounter() {
        service.halt("flash-crash", "ALL", null, "playbook-1");
        service.halt("risk-escalation", "ALL", null, "playbook-2");
        assertEquals(2, service.haltCount());
        assertTrue(service.isHalted());
    }

    @Test
    void releaseDecrementsCounter() {
        var ticket1 = service.halt("flash-crash", "ALL", null, "playbook-1");
        var ticket2 = service.halt("risk-escalation", "ALL", null, "playbook-2");
        assertTrue(service.release(ticket1.ticketId()));
        assertEquals(1, service.haltCount());
        assertTrue(service.isHalted());
        assertTrue(service.release(ticket2.ticketId()));
        assertEquals(0, service.haltCount());
        assertFalse(service.isHalted());
    }

    @Test
    void doubleReleaseIsNoOp() {
        var ticket = service.halt("flash-crash", "ALL", null, "playbook-1");
        assertTrue(service.release(ticket.ticketId()));
        assertFalse(service.release(ticket.ticketId()));
        assertEquals(0, service.haltCount());
    }

    @Test
    void releaseAllForPlaybookCleansUp() {
        service.halt("reason-1", "ALL", null, "playbook-1");
        service.halt("reason-2", "ALL", null, "playbook-1");
        service.halt("reason-3", "ALL", null, "playbook-2");
        assertEquals(3, service.haltCount());
        int released = service.releaseAllForPlaybook("playbook-1");
        assertEquals(2, released);
        assertEquals(1, service.haltCount());
        assertTrue(service.isHalted());
    }

    @Test
    void releaseUnknownTicketReturnsFalse() {
        assertFalse(service.release(java.util.UUID.randomUUID()));
    }
}
