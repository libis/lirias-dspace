package org.dspace.kul.consumer;

import java.util.UUID;

public class QueuedItem {
    private UUID itemId;
    private int eventType;
    private UUID bitstreamId;

    public QueuedItem(UUID itemId, UUID bitstreamId, int eventType) {
        this.itemId = itemId;
        this.eventType = eventType;
        this.bitstreamId = bitstreamId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public int getEventType() {
        return eventType;
    }

    public UUID getBitstreamId() {
        return bitstreamId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((itemId == null) ? 0 : itemId.hashCode());
        result = prime * result + eventType;
        result = prime * result + ((bitstreamId == null) ? 0 : bitstreamId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        QueuedItem other = (QueuedItem) obj;
        if (itemId == null) {
            if (other.itemId != null)
                return false;
        } else if (!itemId.equals(other.itemId))
            return false;
        if (eventType != other.eventType)
            return false;
        if (bitstreamId == null) {
            if (other.bitstreamId != null)
                return false;
        } else if (!bitstreamId.equals(other.bitstreamId))
            return false;
        return true;
    }

    
}
