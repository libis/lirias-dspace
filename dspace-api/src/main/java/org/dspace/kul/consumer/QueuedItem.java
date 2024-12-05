package org.dspace.kul.consumer;

import java.util.UUID;

public class QueuedItem {
    private UUID itemId;
    private int eventType;

    public QueuedItem(UUID itemId, int eventType) {
        this.itemId = itemId;
        this.eventType = eventType;
    }

    public UUID getItemId() {
        return itemId;
    }

    public int getEventType() {
        return eventType;
    }
}
