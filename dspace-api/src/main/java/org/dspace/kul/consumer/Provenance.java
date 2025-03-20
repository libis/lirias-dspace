package org.dspace.kul.consumer;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;

public class Provenance {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Provenance.class);

    public static void applyTo(final KULEvent event) throws Exception {
        String message = null;
        switch (event.getConsumeCaseEnum()) {
            case REDEPOSIT: {
                message = redepositCase(event);
                break;
            }
            case ADD_VIA_UI: {
                message = dspaceAddCase(event);
                break;
            }
            case DEPOSIT: {
                message = depositCase(event);
                break;
            }
            case REMOVE: {
                message = removeCase(event);
                break;
            }
            case EDIT_PERMISSION: {
                message = editBitstreamPermissionCase(event);
                break;
            }
            default: {
                log.error("provenance for this event not implemented: " + event.getConsumeCaseEnum().name());
                break;
            }
        }
        if (message != null) {
            writeMessage(event, message);
            System.out.println("Message: " + message);
        }
    }

    private static String depositCase(final KULEvent event) throws Exception {
        String message = MessageFormat.format("No. of bitstreams: {0} ", event.getBitstreams().size());
        for (final Bitstream b : event.getBitstreams()) {
            message += "- " + MessageFormat.format("{0} (ID: {1}): {2}  bytes, checksum: {3} ({4})",
                    b.getName(),
                    b.getID().toString(),
                    b.getSizeBytes(),
                    b.getChecksum(),
                    b.getChecksumAlgorithm());
            final String permissionMessage = getBitstreamPermissionText(event, b);
            if (!permissionMessage.isBlank()) {
                message += MessageFormat.format(", File permission: {0}", permissionMessage);
            }
            if (permissionMessage == "EMBARGO") {
                for (final ResourcePolicy policy : event.getServices().authorizeService.getPoliciesActionFilter(
                        event.getCtx(), b,
                        Constants.READ)) {
                    message += getPolicyDates(policy);
                }
            }
            message += " ";
        }
        message = MessageFormat.format("Submitted by {0} ({1}) on {2} - {3}",
                event.getCtx().getCurrentUser().getFullName(),
                event.getCtx().getCurrentUser().getEmail(), getDate(event), message);

        // Remove previous provenance messages
        event.getServices().itemService.removeMetadataValues(event.getCtx(), event.getItem(),
                event.getServices().itemService.getMetadata(event.getItem(), "dc", "description", "provenance",
                        Item.ANY));
        event.getServices().itemService.update(event.getCtx(), event.getItem());

        return message;
    }

    private static String getPolicyDates(final ResourcePolicy policy) {
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

    private static String dspaceAddCase(final KULEvent event) throws Exception {
        String message = MessageFormat.format("No. of bitstreams: {0} ", event.getBitstreams().size());
        for (final Bitstream b : event.getBitstreams()) {

            message += "- " + MessageFormat.format("{0} (ID: {1}): {2}",
                    b.getName(),
                    b.getID().toString(),
                    b.getSizeBytes());

            if (b != null && b.getID() == b.getID()) {
                message += MessageFormat.format("bytes, checksum: {0} ({1})",
                        b.getChecksum(),
                        b.getChecksumAlgorithm());
            }
            final String permissionMessage = getBitstreamPermissionText(event, b);
            if (!permissionMessage.isBlank()) {
                message += MessageFormat.format(", File permission: {0}", permissionMessage);
            }
            if (permissionMessage == "EMBARGO") {
                for (final ResourcePolicy policy : event.getServices().authorizeService
                        .getPoliciesActionFilter(event.getCtx(), b, Constants.READ)) {
                    message += getPolicyDates(policy);
                }
            }

            message += " ";
        }

        message = MessageFormat.format("Bitstream added by {0} ({1}) on {2} - {3}",
                event.getCtx().getCurrentUser().getFullName(),
                event.getCtx().getCurrentUser().getEmail(),
                DCDate.getCurrent().toString(),
                message);

        return message;
    }

    private static String redepositCase(final KULEvent event) throws Exception {
        String message = MessageFormat.format("No. of bitstreams: {0} ", event.getBitstreams().size());
        for (final Bitstream b : event.getBitstreams()) {

            message += "- " + MessageFormat.format("{0} (ID: {1}): {2}",
                    b.getName(),
                    b.getID().toString(),
                    b.getSizeBytes());
            if (b != null && b.getID() == b.getID()) {
                message += MessageFormat.format("bytes, checksum: {0} ({1})",
                        b.getChecksum(),
                        b.getChecksumAlgorithm());
            }
            final String permissionMessage = getBitstreamPermissionText(event, b);
            if (!permissionMessage.isBlank()) {
                message += MessageFormat.format(", File permission: {0}", permissionMessage);
            }
            if (permissionMessage == "EMBARGO") {
                for (final ResourcePolicy policy : event.getServices().authorizeService.getPoliciesActionFilter(
                        event.getCtx(), b,
                        Constants.READ)) {
                    message += getPolicyDates(policy);
                }
            }
            message += " ";
        }

        message = MessageFormat.format("Redeposited by {0} ({1}) on {2} - {3}",
                event.getCtx().getCurrentUser().getFullName(),
                event.getCtx().getCurrentUser().getEmail(),
                DCDate.getCurrent().toString(),
                message);

        return message;
    }

    private static String removeCase(final KULEvent event) throws Exception {
        String message = MessageFormat.format("Bitstream removed by {0} ({1}) on {2} ",
                event.getCtx().getCurrentUser().getFullName(),
                event.getCtx().getCurrentUser().getEmail(), DCDate.getCurrent().toString());

        message += "- " + MessageFormat.format("{0} (ID: {1}): {2}",
                event.getBitstream().getName(),
                event.getBitstream().getID().toString(),
                event.getBitstream().getSizeBytes());

        return message;
    }

    private static String editBitstreamPermissionCase(final KULEvent event) throws Exception {

        final String newPermission = getBitstreamPermissionText(event, event.getBitstream());
        final String previousPermission = getPreviousBitstreamPermissionText(event.getCtx(), event.getBitstream());

        if (!newPermission.equals(previousPermission)) {
            // If permission is first or has changed: write to bitstream metadata
            // (dc.bitstream.permissions)
            System.out.println("Writing new permission to bitstream metadata: " + newPermission
                    + " (Previous permission: " + previousPermission + ")");
            event.getServices().bitstreamService.addMetadata(event.getCtx(), event.getBitstream(), "dc", "bitstream",
                    "permissions", "en",
                    DCDate.getCurrent().toDate() + ";" + newPermission);
            event.getServices().bitstreamService.update(event.getCtx(), event.getBitstream());

            if (previousPermission != null) {
                // If permission is changing and not first: add message to item provenance
                // metadata
                final String message = MessageFormat.format(
                        "The permissions of bitstream \"{0}\" (ID: {1}) were updated on {2} by {3} ({4}) from {5} to {6}",
                        event.getBitstream().getName(),
                        event.getBitstream().getID(),
                        DCDate.getCurrent().toString(),
                        event.getCtx().getCurrentUser().getFullName(),
                        event.getCtx().getCurrentUser().getEmail(),
                        previousPermission,
                        newPermission);
                return message;
            }
        }
        return null;
    }

    private static String getDate(final KULEvent event) {
        String date = event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "date",
                "accessioned", Item.ANY);
        if (date.isBlank()) {
            date = DCDate.getCurrent().toString();
        }
        return date;
    }

    private static void writeMessage(final KULEvent event, final String message) throws Exception {
        event.getServices().itemService.addMetadata(event.getCtx(), event.getItem(), "dc", "description", "provenance",
                "en", message);
        event.getServices().itemService.update(event.getCtx(), event.getItem());
    }

    private static String getBitstreamPermissionText(final KULEvent event, final Bitstream bs) {
        try {
            final List<ResourcePolicy> resourcePolicies = event.getServices().authorizeService.getPoliciesActionFilter(
                    event.getCtx(), bs,
                    Constants.READ);
            String result = "PRIVATE";

            for (final ResourcePolicy policy : resourcePolicies) {

                final Group group = policy.getGroup();
                final Date startDate = policy.getStartDate();
                final Date now = DCDate.getCurrent().toDate();

                if (group == event.getServices().groupService.findByName(event.getCtx(), KULConsumer.ANONYMOUS_GROUP)) {
                    if (startDate == null || startDate.before(now)) {
                        return "PUBLIC";
                    } else if (startDate.after(now)) {
                        result = "EMBARGO";
                    }
                } else if (group == event.getServices().groupService.findByName(event.getCtx(),
                        KULConsumer.INTRANET_GROUP)) {
                    result = "INTRANET";
                }
            }
            return result;
        } catch (final SQLException e) {
            log.error(e);
        }
        return null;
    }

    private static String getPreviousBitstreamPermissionText(final Context ctx, final Bitstream bitstream)
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
}
