package org.dspace.kul.consumer;

import java.util.UUID;

public class QueuedItem {
    private UUID subjectId;
    private int eventType;

    public QueuedItem(UUID subjectId, int eventType) {
        this.subjectId = subjectId;
        this.eventType = eventType;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public int getEventType() {
        return eventType;
    }

    public void setEventType(int eventType) {
        this.eventType = eventType;
    }
}
