package org.dspace.kul.consumer;

import java.util.List;
import java.util.Map;

import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.eperson.Group;

public class KULEvent {
    private final Context ctx;
    private final Bitstream bitstream;
    private final Item item;
    private final List<Bitstream> bitstreams;
    private final Map<String, Group> groupsMap;
    private final ConsumeCaseEnum consumeCaseEnum;
    private final boolean phd;
    private final Services services;

    public KULEvent(final Context ctx, final Bitstream bitstream, final Item item, final List<Bitstream> bitstreams,
            final Map<String, Group> groupsMap, final ConsumeCaseEnum consumeCaseEnum, final boolean phd, final Services services) {
        this.ctx = ctx;
        this.bitstream = bitstream;
        this.item = item;
        this.bitstreams = bitstreams;
        this.groupsMap = groupsMap;
        this.consumeCaseEnum = consumeCaseEnum;
        this.phd = phd;
        this.services = services;
    }

    public Context getCtx() {
        return ctx;
    }
    public Bitstream getBitstream() {
        return bitstream;
    }
    public Item getItem() {
        return item;
    }
    public List<Bitstream> getBitstreams() {
        return bitstreams;
    }
    public Map<String, Group> getGroupsMap() {
        return groupsMap;
    }
    public ConsumeCaseEnum getConsumeCaseEnum() {
        return consumeCaseEnum;
    }
    public boolean isPhd() {
        return phd;
    }
    public Services getServices() {
        return services;
    }
}
