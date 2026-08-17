package io.casehub.fsitrading.app.deliberation;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Unremovable
public class DeliberationEventCaptor {

    private final List<Object> events = new ArrayList<>();

    void onStarted(@Observes DeliberationStartedEvent e) { events.add(e); }
    void onCompleted(@Observes DeliberationCompletedEvent e) { events.add(e); }
    void onFailed(@Observes DeliberationFailedEvent e) { events.add(e); }

    public List<Object> getEvents() { return events; }

    public void clear() { events.clear(); }
}
