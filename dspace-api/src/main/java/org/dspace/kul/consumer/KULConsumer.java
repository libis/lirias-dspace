package org.dspace.kul.consumer;

import java.sql.SQLException;
import java.text.MessageFormat;
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
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

public class KULConsumer implements Consumer {

    private static final String ANONYMOUS_GROUP = "Anonymous";
    private static final String INTRANET_GROUP = "registered_users";
    private static final String ADMINS_LOCAL_GROUP = "Admins_local";
    private static final List<String> ALL_GROUP_NAMES = Arrays.asList(ANONYMOUS_GROUP, INTRANET_GROUP,
            ADMINS_LOCAL_GROUP);

    protected AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    protected EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    protected GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    protected ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance()
            .getResourcePolicyService();
    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    protected BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    protected BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    private Set<QueuedItem> queue = new HashSet<>();
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(KULConsumer.class);

    @Override
    public void initialize() throws Exception {
        System.out.println("KUL Consumer init. ");

    }

    @Override
    public void finish(Context ctx) throws Exception {
        System.out.println("KUL Consumer finished.");
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        if (event.getSubjectType() == Constants.ITEM && Event.INSTALL == event.getEventType()) {
            System.out.println("Item install: " + event.getSubjectID());
            queue.add(new QueuedItem(event.getSubjectID(), event.getObjectID(), event.getEventType()));
        } else if (Event.ADD == event.getEventType()
                && event.getSubjectType() == Constants.BUNDLE) {
            final Bundle bundle = bundleService.find(ctx, event.getSubjectID());
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
            final Bundle bundle = bundleService.find(ctx, event.getSubjectID());
            queue.add(new QueuedItem(event.getSubjectID(), event.getObjectID(), event.getEventType()));
        } else if (Event.MODIFY == event.getEventType() && event.getSubjectType() == Constants.BITSTREAM) {
            System.out.println("modify bitstream case");
            ((Bitstream) event.getSubject(ctx)).getBundles().forEach(bundle -> bundle.getItems()
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
    public void end(Context ctx) throws Exception {
        final Map<String, Group> groupsMap = new HashMap<>();
        for (final String groupName : ALL_GROUP_NAMES) {
            groupsMap.put(groupName, groupService.findByName(ctx, groupName));
        }

        for (final QueuedItem qi : queue) {
            final Item item = itemService.find(ctx, qi.getItemId());
            Bitstream bitstream = null;
            if (qi.getBitstreamId() != null) {
                bitstream = bitstreamService.find(ctx, qi.getBitstreamId());

            }
            List<Bitstream> bitstreams = new ArrayList<>();
            if (qi.getItemId() != null) {
                for (Bundle bundle : itemService.getBundles(item, "ORIGINAL")) {
                    bitstreams.addAll(bundle.getBitstreams());

                }
            }
            System.out.println("Item: " + item);
            System.out.println("Bitstream: " + bitstream);
            System.out.println("Bitstreams: " + bitstreams);
            System.out.println("Groupsmap: " + groupsMap);

            switch (qi.getEventType()) {

                case Event.ADD:
                    System.out.println("Redeposit or add via DSpace UI case");
                    if (ctx.getCurrentUser().getEmail().equals("symplectic-elements@libis.be")) {
                        redepositCase(ctx, bitstream, item, bitstreams, groupsMap);
                    } else {
                        dspaceAddCase(ctx, bitstream, item, bitstreams, groupsMap);
                    }
                    break;
                case Event.INSTALL:
                    System.out.println("Deposit case");
                    depositCase(ctx, bitstream, item, bitstreams, groupsMap);
                    break;
                case Event.DELETE_BITSTREAM:
                    System.out.println("Remove case");
                    removeCase(ctx, bitstream, item, bitstreams, groupsMap);
                    break;
                case Event.MODIFY:
                    System.out.println("Edit permission case");
                    editBitstreamPermissionCase(ctx, bitstream, item, bitstreams, groupsMap);
                    break;
                default:
                    log.error("event consume not implemented: " + qi.getEventType());
                    break;
            }
        }

        queue.clear();
    }

    private void depositCase(final Context ctx, final Bitstream bitstream, final Item item,
            final List<Bitstream> bitstreams,
            final Map<String, Group> groupsMap) throws Exception {

        final List<ResourcePolicy> policies = new ArrayList<>();
        policies.add(readForGroup(ctx, groupsMap.get(ADMINS_LOCAL_GROUP)));

        String message = MessageFormat.format("No. of bitstreams: {0} ", bitstreams.size());
        for (Bitstream b : bitstreams) {
            message += "- " + MessageFormat.format("{0} (ID: {1}): {2}  bytes, checksum: {3} ({4})",
                    b.getName(),
                    b.getID().toString(),
                    b.getSizeBytes(),
                    b.getChecksum(),
                    b.getChecksumAlgorithm());
            String permissionMessage = getBitstreamPermissionText(ctx, b);
            if (!permissionMessage.isBlank()) {
                message += MessageFormat.format(", File permission: {0}", permissionMessage);
            }
            message += " ";
        }
        message = MessageFormat.format("Submitted by {0} ({1}) on {2} - {3}", ctx.getCurrentUser().getFullName(),
                ctx.getCurrentUser().getEmail(), getDate(item), message);

        // Remove previous provenance messages
        itemService.removeMetadataValues(ctx, item,
                itemService.getMetadata(item, "dc", "description", "provenance", Item.ANY));
        itemService.update(ctx, item);

        doUpdate(ctx, bitstream, item, bitstreams, groupsMap, message, policies);

    }

    private void dspaceAddCase(final Context ctx, final Bitstream bitstream, final Item item,
            final List<Bitstream> bitstreams,
            final Map<String, Group> groupsMap) throws Exception {

        final List<ResourcePolicy> policies = new ArrayList<>();
        policies.add(readForGroup(ctx, groupsMap.get(ADMINS_LOCAL_GROUP)));

        String message = MessageFormat.format("No. of bitstreams: {0} ", bitstreams.size());
        for (Bitstream b : bitstreams) {

            message += "- " + MessageFormat.format("{0} (ID: {1}): {2}",
                    b.getName(),
                    b.getID().toString(),
                    b.getSizeBytes());

            if (bitstream != null && b.getID() == bitstream.getID()) {
                message += MessageFormat.format("bytes, checksum: {0} ({1})",
                        b.getChecksum(),
                        b.getChecksumAlgorithm());
            }
            String permissionMessage = getBitstreamPermissionText(ctx, b);
            if (!permissionMessage.isBlank()) {
                message += MessageFormat.format(", File permission: {0}", permissionMessage);
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
        for (Bitstream b : bitstreams) {

            message += "- " + MessageFormat.format("{0} (ID: {1}): {2}",
                    b.getName(),
                    b.getID().toString(),
                    b.getSizeBytes());
            if (bitstream != null && b.getID() == bitstream.getID()) {
                message += MessageFormat.format("bytes, checksum: {0} ({1})",
                        b.getChecksum(),
                        b.getChecksumAlgorithm());
            }
            String permissionMessage = getBitstreamPermissionText(ctx, b);
            if (!permissionMessage.isBlank()) {
                message += MessageFormat.format(", File permission: {0}", permissionMessage);
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

        if (previousPermission == null || !newPermission.equals(previousPermission)) {
            // If permission is first or has changed: write to bitstream metadata
            // (dc.bitstream.permissions)
            System.out.println("Writing new permission to bitstream metadata: " + newPermission);
            bitstreamService.addMetadata(ctx, bitstream, "dc", "bitstream", "permissions", "en",
                    DCDate.getCurrent().toDate() + ";" + newPermission);
            bitstreamService.update(ctx, bitstream);
        }

        if (!newPermission.equals(previousPermission)) {
            // If permission has changed: add message to item provenance metadata
            String message = MessageFormat.format(
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

    private void doUpdate(final Context ctx, final Bitstream bitstream, final Item item,
            final List<Bitstream> bitstreams,
            final Map<String, Group> groupsMap, final String message, final List<ResourcePolicy> policies)
            throws Exception {

        System.out.println("Do update context: " + ctx);
        System.out.println("Do update policies: " + policies);
        System.out.println("Do update bitstream: " + bitstream);
        System.out.println("Do update bitstreams: " + bitstreams);
        System.out.println("Do update groupsMap: " + groupsMap.values());

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
        String date = itemService.getMetadataFirstValue(item, "dc", "date", "accessioned", Item.ANY);
        if (date.isBlank()) {
            date = DCDate.getCurrent().toString();
        }
        return date;
    }

    private void writeMessage(final Context ctx, final Item item, final String message) throws Exception {
        itemService.addMetadata(ctx, item, "dc", "description", "provenance", "en", message);
        itemService.update(ctx, item);
    }

    private void changeBitstreamPolicies(Context ctx, Bitstream bitstream, Collection<Group> toRemove,
            List<ResourcePolicy> toAdd) throws Exception {
        for (final Group group : toRemove) {
            authorizeService.removeGroupPolicies(ctx, bitstream, group);
        }
        authorizeService.addPolicies(ctx, toAdd, bitstream);
    }

    public String getPreviousBitstreamPermissionText(Context ctx, Bitstream bitstream) {
        String currentPermission = null;
        Date currentPermissionDate = null;
        System.out.println("Parsing permissions in bitstream metadata");
        for (MetadataValue bitstreamMetadata : bitstream.getMetadata()) {
            if (bitstreamMetadata.getMetadataField().getElement().equals("bitstream")
                    && bitstreamMetadata.getMetadataField().getQualifier().equals("permissions")) {
                String[] temp = bitstreamMetadata.getValue().toString().split("\\;");
                if (temp.length == 2) {
                    Date previousPermissionDate = new Date(temp[0]);
                    String previousPermission = temp[1];
                    if (currentPermissionDate == null || previousPermissionDate.after(currentPermissionDate)) {
                        currentPermissionDate = previousPermissionDate;
                        currentPermission = previousPermission;
                    }
                }
                System.out.println("Latest permission found: " + currentPermissionDate + " " +
                        currentPermission);
            }
        }
        return currentPermission;
    }

    public String getBitstreamPermissionText(Context ctx, Bitstream bs) {
        try {
            List<ResourcePolicy> resourcePolicies = authorizeService.getPoliciesActionFilter(ctx, bs, Constants.READ);
            String result = "PRIVATE";

            for (ResourcePolicy policy : resourcePolicies) {
                Group group = policy.getGroup();
                Date startDate = policy.getStartDate();
                Date endDate = policy.getEndDate();
                Date now = DCDate.getCurrent().toDate();

                if (group == groupService.findByName(ctx, ANONYMOUS_GROUP)) {
                    if (startDate == null || startDate.before(now)) {
                        return "PUBLIC";
                    } else if (startDate.after(now)) {
                        result = MessageFormat.format("EMBARGO, {0}", startDate);
                        if (policy.getEndDate() != null) {
                            result += MessageFormat.format(" to {0}", endDate);
                        }
                    }
                } else if (group == groupService.findByName(ctx, INTRANET_GROUP)) {
                    result = "INTRANET";
                }
            }
            return result;
        } catch (SQLException e) {
            log.error(e);
        }
        return null;
    }

    private ResourcePolicy readForGroup(final Context ctx, final Group group) throws Exception {
        final ResourcePolicy rp = resourcePolicyService.create(ctx);
        rp.setAction(Constants.READ);
        rp.setGroup(group);
        return rp;
    }

}
