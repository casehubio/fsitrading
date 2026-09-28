package io.casehub.fsitrading.app.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class OrderSemaphoreService {

    private final ConcurrentHashMap<UUID, SemaphoreTicket> tickets = new ConcurrentHashMap<>();
    private final AtomicInteger haltCounter = new AtomicInteger(0);

    public SemaphoreTicket halt(String reason, String scope, String instrument, String playbookInstanceId) {
        var ticket = new SemaphoreTicket(UUID.randomUUID(), reason, scope, instrument, playbookInstanceId, Instant.now(), false);
        tickets.put(ticket.ticketId(), ticket);
        haltCounter.incrementAndGet();
        return ticket;
    }

    public boolean release(UUID ticketId) {
        var ticket = tickets.get(ticketId);
        if (ticket == null || ticket.released()) {
            return false;
        }
        tickets.put(ticketId, new SemaphoreTicket(ticket.ticketId(), ticket.reason(), ticket.scope(),
                ticket.instrument(), ticket.playbookInstanceId(), ticket.haltedAt(), true));
        haltCounter.decrementAndGet();
        return true;
    }

    public int releaseAllForPlaybook(String playbookInstanceId) {
        int count = 0;
        for (var entry : tickets.entrySet()) {
            var ticket = entry.getValue();
            if (playbookInstanceId.equals(ticket.playbookInstanceId()) && !ticket.released()) {
                if (release(entry.getKey())) {
                    count++;
                }
            }
        }
        return count;
    }

    public boolean isHalted() {
        return haltCounter.get() > 0;
    }

    public int haltCount() {
        return haltCounter.get();
    }

    public record SemaphoreTicket(UUID ticketId, String reason, String scope,
                                  String instrument, String playbookInstanceId,
                                  Instant haltedAt, boolean released) {}
}
