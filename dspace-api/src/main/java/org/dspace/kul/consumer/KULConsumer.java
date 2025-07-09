package org.dspace.kul.consumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

public class KULConsumer implements Consumer {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final String ANONYMOUS_GROUP = "Anonymous";
    public static final String INTRANET_GROUP = "registered_users";
    public static final String ADMINS_LOCAL_GROUP = "Admins_local";
    public static final List<String> ALL_GROUP_NAMES = Arrays.asList(ANONYMOUS_GROUP, INTRANET_GROUP,
            ADMINS_LOCAL_GROUP);

    private final Set<QueuedItem> queue = new HashSet<>();
    private final Services services = new Services();

    @Override
    public void initialize() throws Exception {
        LOGGER.info("KUL Consumer init. ");

    }

    @Override
    public void finish(final Context ctx) throws Exception {
        LOGGER.info("KUL Consumer finished.");
    }

    @Override
    public void consume(final Context ctx, final Event event) {
        try {
            doConsume(ctx, event);
        } catch (Exception e) {
            if (null != event) {
                LOGGER.error("KUL Consumer/consume for event " + event.toString(), e);
            } else {
                LOGGER.error("KUL Consumer/consume", e);
            }
        }
    }


    private void doConsume(final Context ctx, final Event event) throws Exception {
        if (event.getSubjectType() == Constants.ITEM && Event.INSTALL == event.getEventType()) {
            LOGGER.info("KUL Consumer/consume: Item install: " + event.getSubjectID());
            queue.add(new QueuedItem(event.getSubjectID(), event.getObjectID(), event.getEventType()));
        } else if (Event.ADD == event.getEventType()
                && event.getSubjectType() == Constants.BUNDLE) {
            final Bundle bundle = services.bundleService.find(ctx, event.getSubjectID());
            LOGGER.info("Bundle: " + bundle);
            for (final Item item : bundle.getItems()) {
                // we listen to the ADD event only when the item is already installed
                if (item.getMetadata().stream().anyMatch(
                        x -> x.getMetadataField().getQualifier() != null && x.getValue() != null
                                && x.getMetadataField().getQualifier().equals("provenance")
                                && x.getValue().startsWith("Submitted by "))) {
                    LOGGER.info("Item added: " + item);
                    // event.getObjectID() is the bitstream ID
                    if (bundle.getName().equals("ORIGINAL")) {
                        queue.add(new QueuedItem(item.getID(), event.getObjectID(), event.getEventType()));
                    }
                }
            }
        } else if (Event.DELETE_BITSTREAM == event.getEventType()) {
            final String bundleName = event.getDetail();
            if (bundleName.equals("ORIGINAL")) {
                queue.add(new QueuedItem(event.getSubjectID(), event.getObjectID(), event.getEventType()));
            }
        } else if (Event.MODIFY == event.getEventType() && event.getSubjectType() == Constants.BITSTREAM) {
            LOGGER.info("KUL Consumer/consume: modify bitstream case");
            ((Bitstream) event.getSubject(ctx)).getBundles().stream()
                    .filter(bundle -> bundle.getName().toString().equals("ORIGINAL"))
                    .forEach(bundle -> bundle.getItems()
                            .forEach(item -> {
                                if (queue.stream().noneMatch(q -> q.getItemId().equals(item.getID()))) {
                                    queue.add(new QueuedItem(item.getID(), event.getSubjectID(),
                                            event.getEventType()));
                                }
                            }));
        } else {
            LOGGER.info("KUL Consumer/consume: Unprocessed event: " + event.toString());
        }
    }

    @Override
    public void end(final Context ctx) {
        try {
            doEnd(ctx);
        } catch (Exception e) {
            LOGGER.error("KUL Consumer/end", e);
            for (final QueuedItem qi : queue) {
                LOGGER.error("KUL Consumer/end: possibly unprocessed queued item -> event type: " + qi.getEventType()
                        + ", item id: " + qi.getItemId() + ", bitstream id: "
                        + qi.getBitstreamId());
            }
        }
    }

    private void doEnd(final Context ctx) throws Exception {
        final Map<String, Group> groupsMap = new HashMap<>();
        for (final String groupName : ALL_GROUP_NAMES) {
            groupsMap.put(groupName, services.groupService.findByName(ctx, groupName));
        }

        for (final QueuedItem qi : queue) {
            try {
                final Item item = services.itemService.find(ctx, qi.getItemId());
                Bitstream bitstream = null;
                if (qi.getBitstreamId() != null) {
                    bitstream = services.bitstreamService.find(ctx, qi.getBitstreamId());

                }
                final List<Bitstream> bitstreams = new ArrayList<>();
                if (qi.getItemId() != null) {
                    for (final Bundle bundle : services.itemService.getBundles(item, "ORIGINAL")) {
                        bitstreams.addAll(bundle.getBitstreams());

                    }
                }

                ConsumeCaseEnum caseEnum = null;
                switch (qi.getEventType()) {
                    case Event.ADD:
                        LOGGER.info("KUL Consumer: Redeposit or add via DSpace UI case");
                        if (ctx.getCurrentUser().getEmail().equals("symplectic-elements@libis.be")) {
                            caseEnum = ConsumeCaseEnum.REDEPOSIT;
                        } else {
                            caseEnum = ConsumeCaseEnum.ADD_VIA_UI;
                        }
                        break;
                    case Event.INSTALL:
                        LOGGER.info("KUL Consumer: Deposit case");
                        caseEnum = ConsumeCaseEnum.DEPOSIT;
                        break;
                    case Event.DELETE_BITSTREAM:
                        LOGGER.info("KUL Consumer: Remove case");
                        caseEnum = ConsumeCaseEnum.REMOVE;
                        break;
                    case Event.MODIFY:
                        LOGGER.info("KUL Consumer: Edit permission case");
                        caseEnum = ConsumeCaseEnum.EDIT_PERMISSION;
                        break;
                    default:
                        LOGGER.error("KUL Consumer: event consume not implemented: " + qi.getEventType());
                        break;
                }
                if (caseEnum != null) {
                    final boolean phd = isPhd(item);
                    final KULEvent e = new KULEvent(ctx, bitstream, item, bitstreams, groupsMap, caseEnum, phd,
                            services);
                    Permissions.applyTo(e);
                    Provenance.applyTo(e);
                    Mailing.applyTo(e);
                }
            } catch (Exception e) {
                LOGGER.error("KUL Consumer/end: failed processing queued item -> event type: " + qi.getEventType()
                        + ", item id: " + qi.getItemId() + ", bitstream id: "
                        + qi.getBitstreamId(), e);
            }
        }
        queue.clear();

    }

    private boolean isPhd(Item item) {
        if (services.itemService.getMetadata(item, "dc", "type", "elements", Item.ANY).stream()
                .anyMatch(m -> m.getValue().equals("thesis-dissertation"))) {
            return true;
        }
        return false;
    }
}
