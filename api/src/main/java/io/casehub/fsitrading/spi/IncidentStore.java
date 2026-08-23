package io.casehub.fsitrading.spi;

import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentSummary;
import io.casehub.fsitrading.model.IncidentTimelineRecord;

import java.util.List;
import java.util.UUID;

public interface IncidentStore {

    void save(IncidentRecord record);

    IncidentRecord findByCaseId(UUID caseId);

    List<IncidentRecord> findRecent(int limit);

    List<IncidentRecord> findByStatus(String status);

    void updateStatus(UUID caseId, String status);

    void addTimelineEntry(UUID caseId, IncidentTimelineRecord entry);

    List<IncidentTimelineRecord> getTimeline(UUID caseId);

    IncidentSummary getSummary();
}
