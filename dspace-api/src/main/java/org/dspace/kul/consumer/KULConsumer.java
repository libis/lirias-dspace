package org.dspace.kul.consumer;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

public class KULConsumer implements Consumer {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(KULConsumer.class);
    private static final String ANONYMOUS_GROUP = "Anonymous";
    private static final String INTRANET_GROUP = "registered_users";
    private static final String ADMINS_LOCAL_GROUP = "Admins_local";
    private static final List<String> ALL_GROUP_NAMES = Arrays.asList(ANONYMOUS_GROUP, INTRANET_GROUP,
            ADMINS_LOCAL_GROUP);

    private final Set<QueuedItem> queue = new HashSet<>();
    private final Services services = new Services();

    @Override
    public void initialize() throws Exception {
        System.out.println("KUL Consumer init. ");

    }

    @Override
    public void finish(final Context ctx) throws Exception {
        System.out.println("KUL Consumer finished.");
    }

    @Override
    public void consume(final Context ctx, final Event event) throws Exception {
        if (event.getSubjectType() == Constants.ITEM && Event.INSTALL == event.getEventType()) {
            System.out.println("Item install: " + event.getSubjectID());
            queue.add(new QueuedItem(event.getSubjectID(), event.getObjectID(), event.getEventType()));
        } else if (Event.ADD == event.getEventType()
                && event.getSubjectType() == Constants.BUNDLE) {
            final Bundle bundle = services.bundleService.find(ctx, event.getSubjectID());
            System.out.println("Bundle: " + bundle);
            for (final Item item : bundle.getItems()) {
                // we listen to the ADD event only when the item is already installed
                if (item.getMetadata().stream().anyMatch(x -> x.getMetadataField().getQualifier().equals("provenance")
                        && x.getValue().startsWith("Submitted by "))) {
                    System.out.println("Item added: " + item);
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
            System.out.println("modify bitstream case");
            ((Bitstream) event.getSubject(ctx)).getBundles().stream()
                    .filter(bundle -> bundle.getName().toString().equals("ORIGINAL"))
                    .forEach(bundle -> bundle.getItems()
                            .forEach(item -> {
                                if (queue.stream().noneMatch(q -> q.getItemId().equals(item.getID()))) {
                                    queue.add(new QueuedItem(item.getID(), event.getSubjectID(), event.getEventType()));
                                }
                            }));
        } else {
            System.out.println("Unprocessed event: " + event.toString());
        }
    }

    @Override
    public void end(final Context ctx) throws Exception {
        final Map<String, Group> groupsMap = new HashMap<>();
        for (final String groupName : ALL_GROUP_NAMES) {
            groupsMap.put(groupName, services.groupService.findByName(ctx, groupName));
        }

        for (final QueuedItem qi : queue) {
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
                    System.out.println("Redeposit or add via DSpace UI case");
                    if (ctx.getCurrentUser().getEmail().equals("symplectic-elements@libis.be")) {
                        caseEnum = ConsumeCaseEnum.REDEPOSIT;
                        redepositCase(ctx, bitstream, item, bitstreams, groupsMap);
                    } else {
                        caseEnum = ConsumeCaseEnum.ADD_VIA_UI;
                        dspaceAddCase(ctx, bitstream, item, bitstreams, groupsMap);
                    }
                    break;
                case Event.INSTALL:
                    System.out.println("Deposit case");
                    caseEnum = ConsumeCaseEnum.DEPOSIT;
                    depositCase(ctx, bitstream, item, bitstreams, groupsMap);
                    break;
                case Event.DELETE_BITSTREAM:
                    System.out.println("Remove case");
                    caseEnum = ConsumeCaseEnum.REMOVE;
                    removeCase(ctx, bitstream, item, bitstreams, groupsMap);
                    break;
                case Event.MODIFY:
                    System.out.println("Edit permission case");
                    caseEnum = ConsumeCaseEnum.EDIT_PERMISSION;
                    editBitstreamPermissionCase(ctx, bitstream, item, bitstreams, groupsMap);
                    break;
                default:
                    log.error("event consume not implemented: " + qi.getEventType());
                    break;
            }
            if (caseEnum != null) {
                final boolean phd = isPhd();
                final KULEvent e = new KULEvent(ctx, bitstream, item, bitstreams, groupsMap, caseEnum, phd, services);
                final String message = Provenance.getMessage(e);
                final List<ResourcePolicy> policies = Permissions.getPolicies(e);
                //TODO
                //doUpdate(ctx, bitstream, item, bitstreams, groupsMap, message, policies);
                Mailing.notify(e);
            }
        }

        queue.clear();
    }

    private boolean isPhd() {
        //TODO
        return false;
    }

    private void depositCase(final Context ctx, final Bitstream bitstream, final Item item,
            final List<Bitstream> bitstreams,
            final Map<String, Group> groupsMap) throws Exception {

        final List<ResourcePolicy> policies = new ArrayList<>();
        policies.add(readForGroup(ctx, groupsMap.get(ADMINS_LOCAL_GROUP)));

        String message = MessageFormat.format("No. of bitstreams: {0} ", bitstreams.size());
        for (final Bitstream b : bitstreams) {
            message += "- " + MessageFormat.format("{0} (ID: {1}): {2}  bytes, checksum: {3} ({4})",
                    b.getName(),
                    b.getID().toString(),
                    b.getSizeBytes(),
                    b.getChecksum(),
                    b.getChecksumAlgorithm());
            final String permissionMessage = getBitstreamPermissionText(ctx, b);
            if (!permissionMessage.isBlank()) {
                message += MessageFormat.format(", File permission: {0}", permissionMessage);
            }
            if (permissionMessage == "EMBARGO") {
                for (final ResourcePolicy policy : services.authorizeService.getPoliciesActionFilter(ctx, b,
                        Constants.READ)) {
                    message += getPolicyDates(policy);
                }
            }
            message += " ";
        }
        message = MessageFormat.format("Submitted by {0} ({1}) on {2} - {3}", ctx.getCurrentUser().getFullName(),
                ctx.getCurrentUser().getEmail(), getDate(item), message);

        // Remove previous provenance messages
        services.itemService.removeMetadataValues(ctx, item,
                services.itemService.getMetadata(item, "dc", "description", "provenance", Item.ANY));
        services.itemService.update(ctx, item);

        doUpdate(ctx, bitstream, item, bitstreams, groupsMap, message, policies);

    }

    private String getPolicyDates(final ResourcePolicy policy) {
        final Date startDate = policy.getStartDate();
        final Date endDate = policy.getEndDate();
        String result = "";
        if (startDate != null) {
            result += MessageFormat.format(", {0}", startDate.toString());

        }
        if (endDate != null) {
            result += MessageFormat.format(" to {0}", endDate.toString());

        }
        return result;

    }

    private void dspaceAddCase(final Context ctx, final Bitstream bitstream, final Item item,
            final List<Bitstream> bitstreams,
            final Map<String, Group> groupsMap) throws Exception {

        final List<ResourcePolicy> policies = new ArrayList<>();
        policies.add(readForGroup(ctx, groupsMap.get(ADMINS_LOCAL_GROUP)));

        String message = MessageFormat.format("No. of bitstreams: {0} ", bitstreams.size());
        for (final Bitstream b : bitstreams) {

            message += "- " + MessageFormat.format("{0} (ID: {1}): {2}",
                    b.getName(),
                    b.getID().toString(),
                    b.getSizeBytes());

            if (bitstream != null && b.getID() == bitstream.getID()) {
                message += MessageFormat.format("bytes, checksum: {0} ({1})",
                        b.getChecksum(),
                        b.getChecksumAlgorithm());
            }
            final String permissionMessage = getBitstreamPermissionText(ctx, b);
            if (!permissionMessage.isBlank()) {
                message += MessageFormat.format(", File permission: {0}", permissionMessage);
            }
            if (permissionMessage == "EMBARGO") {
                for (final ResourcePolicy policy : services.authorizeService.getPoliciesActionFilter(ctx, b,
                        Constants.READ)) {
                    message += getPolicyDates(policy);
                }
            }

            message += " ";
        }

        message = MessageFormat.format("Bitstream added by {0} ({1}) on {2} - {3}",
                ctx.getCurrentUser().getFullName(),
                ctx.getCurrentUser().getEmail(),
                DCDate.getCurrent().toString(),
                message);

        doUpdate(ctx, bitstream, item, bitstreams, groupsMap, message, policies);

    }

    private void redepositCase(final Context ctx, final Bitstream bitstream, final Item item,
            final List<Bitstream> bitstreams, final Map<String, Group> groupsMap) throws Exception {

        final List<ResourcePolicy> policies = List.of();
        // policies.add(readForGroup(ctx, groupsMap.get(ADMINS_LOCAL_GROUP)));
        String message = MessageFormat.format("No. of bitstreams: {0} ", bitstreams.size());
        for (final Bitstream b : bitstreams) {

            message += "- " + MessageFormat.format("{0} (ID: {1}): {2}",
                    b.getName(),
                    b.getID().toString(),
                    b.getSizeBytes());
            if (bitstream != null && b.getID() == bitstream.getID()) {
                message += MessageFormat.format("bytes, checksum: {0} ({1})",
                        b.getChecksum(),
                        b.getChecksumAlgorithm());
            }
            final String permissionMessage = getBitstreamPermissionText(ctx, b);
            if (!permissionMessage.isBlank()) {
                message += MessageFormat.format(", File permission: {0}", permissionMessage);
            }
            if (permissionMessage == "EMBARGO") {
                for (final ResourcePolicy policy : services.authorizeService.getPoliciesActionFilter(ctx, b,
                        Constants.READ)) {
                    message += getPolicyDates(policy);
                }
            }
            message += " ";
        }

        message = MessageFormat.format("Redeposited by {0} ({1}) on {2} - {3}",
                ctx.getCurrentUser().getFullName(),
                ctx.getCurrentUser().getEmail(),
                DCDate.getCurrent().toString(),
                message);

        doUpdate(ctx, bitstream, item, bitstreams, groupsMap, message, policies);
    }

    private void removeCase(final Context ctx, final Bitstream bitstream, final Item item,
            final List<Bitstream> bitstreams,
            final Map<String, Group> groupsMap) throws Exception {
        String message = MessageFormat.format("Bitstream removed by {0} ({1}) on {2} ",
                ctx.getCurrentUser().getFullName(),
                ctx.getCurrentUser().getEmail(), DCDate.getCurrent().toString());

        message += "- " + MessageFormat.format("{0} (ID: {1}): {2}",
                bitstream.getName(),
                bitstream.getID().toString(),
                bitstream.getSizeBytes());

        final List<ResourcePolicy> policies = List.of();
        doUpdate(ctx, bitstream, item, bitstreams, groupsMap, message, policies);
    }

    private void editBitstreamPermissionCase(final Context ctx, final Bitstream bitstream, final Item item,
            final List<Bitstream> bitstreams,
            final Map<String, Group> groupsMap) throws Exception {

        final String newPermission = getBitstreamPermissionText(ctx, bitstream);
        final String previousPermission = getPreviousBitstreamPermissionText(ctx, bitstream);

        if (!newPermission.equals(previousPermission)) {
            // If permission is first or has changed: write to bitstream metadata
            // (dc.bitstream.permissions)
            System.out.println("Writing new permission to bitstream metadata: " + newPermission
                    + " (Previous permission: " + previousPermission + ")");
            services.bitstreamService.addMetadata(ctx, bitstream, "dc", "bitstream", "permissions", "en",
                    DCDate.getCurrent().toDate() + ";" + newPermission);
            services.bitstreamService.update(ctx, bitstream);

            if (previousPermission != null) {
                // If permission is changing and not first: add message to item provenance
                // metadata
                final String message = MessageFormat.format(
                        "The permissions of bitstream \"{0}\" (ID: {1}) were updated on {2} by {3} ({4}) from {5} to {6}",
                        bitstream.getName(),
                        bitstream.getID(),
                        DCDate.getCurrent().toString(),
                        ctx.getCurrentUser().getFullName(),
                        ctx.getCurrentUser().getEmail(),
                        previousPermission,
                        newPermission);
                final List<ResourcePolicy> policies = Collections.emptyList();
                doUpdate(ctx, bitstream, item, bitstreams, groupsMap, message, policies);
            }
        }
    }

    private void doUpdate(final Context ctx, final Bitstream bitstream, final Item item,
            final List<Bitstream> bitstreams,
            final Map<String, Group> groupsMap, final String message, final List<ResourcePolicy> policies)
            throws Exception {

        if (policies != null && !policies.isEmpty()) {

            System.out.println("Change policies");

            if (bitstream != null) {
                changeBitstreamPolicies(ctx, bitstream, groupsMap.values(), policies);
            } else {
                for (final Bitstream b : bitstreams) {
                    changeBitstreamPolicies(ctx, b, groupsMap.values(), policies);
                }
            }
        }
        if (message != null) {
            writeMessage(ctx, item, message);
            System.out.println("Message: " + message);
        }
    }

    private String getDate(final Item item) {
        String date = services.itemService.getMetadataFirstValue(item, "dc", "date", "accessioned", Item.ANY);
        if (date.isBlank()) {
            date = DCDate.getCurrent().toString();
        }
        return date;
    }

    private void writeMessage(final Context ctx, final Item item, final String message) throws Exception {
        services.itemService.addMetadata(ctx, item, "dc", "description", "provenance", "en", message);
        services.itemService.update(ctx, item);
    }

    private void changeBitstreamPolicies(final Context ctx, final Bitstream bitstream, final Collection<Group> toRemove,
            final List<ResourcePolicy> toAdd) throws Exception {
        for (final Group group : toRemove) {
            services.authorizeService.removeGroupPolicies(ctx, bitstream, group);
        }
        services.authorizeService.addPolicies(ctx, toAdd, bitstream);
    }

    public String getPreviousBitstreamPermissionText(final Context ctx, final Bitstream bitstream)
            throws ParseException {
        String currentPermission = null;
        Date currentPermissionDate = null;
        System.out.println("Parsing permissions in bitstream metadata");
        for (final MetadataValue bitstreamMetadata : bitstream.getMetadata()) {
            if (bitstreamMetadata.getMetadataField().getElement().equals("bitstream")
                    && bitstreamMetadata.getMetadataField().getQualifier().equals("permissions")) {
                final String[] temp = bitstreamMetadata.getValue().toString().split("\\;");
                if (temp.length == 2) {
                    final Date previousPermissionDate = (new SimpleDateFormat()).parse(temp[0]);
                    final String previousPermission = temp[1];
                    if (currentPermissionDate == null || previousPermissionDate.after(currentPermissionDate)) {
                        currentPermissionDate = previousPermissionDate;
                        currentPermission = previousPermission;
                    }
                }
            }
        }
        return currentPermission;
    }

    public String getBitstreamPermissionText(final Context ctx, final Bitstream bs) {
        try {
            final List<ResourcePolicy> resourcePolicies = services.authorizeService.getPoliciesActionFilter(ctx, bs,
                    Constants.READ);
            String result = "PRIVATE";

            for (final ResourcePolicy policy : resourcePolicies) {

                final Group group = policy.getGroup();
                final Date startDate = policy.getStartDate();
                final Date now = DCDate.getCurrent().toDate();

                if (group == services.groupService.findByName(ctx, ANONYMOUS_GROUP)) {
                    if (startDate == null || startDate.before(now)) {
                        return "PUBLIC";
                    } else if (startDate.after(now)) {
                        result = "EMBARGO";
                    }
                } else if (group == services.groupService.findByName(ctx, INTRANET_GROUP)) {
                    result = "INTRANET";
                }
            }
            return result;
        } catch (final SQLException e) {
            log.error(e);
        }
        return null;
    }

    private ResourcePolicy readForGroup(final Context ctx, final Group group) throws Exception {
        final ResourcePolicy rp = services.resourcePolicyService.create(ctx);
        rp.setAction(Constants.READ);
        rp.setGroup(group);
        return rp;
    }

}
