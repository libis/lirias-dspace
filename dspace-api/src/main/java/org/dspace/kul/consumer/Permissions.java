package org.dspace.kul.consumer;

import java.util.ArrayList;
import java.util.Date;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Logger;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Constants;
import org.dspace.eperson.Group;

public class Permissions {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Permissions.class);
    public static final String RIGHTS_PUBLIC_ACCESS_VALUE = "Public access (as soon as legally possible, verified by the OA Support Desk)";
    public static final String RIGHTS_PERMANENT_EMBARGO_VALUE = "Permanent embargo (intranet only)";
    public static final String RIGHTS_NO_ACCESS_VALUE = "No access (only for strictly confidential material)";

    public static void applyTo(final KULEvent event) throws Exception {
        switch (event.getConsumeCaseEnum()) {
            case REDEPOSIT:
                try {
                    log.info("Permissions consumer: Redeposit case");
                    List<ResourcePolicy> policies = new ArrayList<>();
                    policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.ADMINS_LOCAL_GROUP)));
                    policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.INTRANET_GROUP)));

                    addPoliciesToBitstream(event, policies);

                    if (event.getBitstream() != null) {
                        log.info(
                                "Permission consumer/redeposit: Writing new permission to bitstream metadata for "
                                        + event.getBitstream().getName() + " : " + "redeposit");

                        event.getServices().bitstreamService.addMetadata(event.getCtx(), event.getBitstream(), "dc",
                                "bitstream",
                                "permissions", "en",
                                DCDate.getCurrent().toDate() + ";" + "redeposit");

                        event.getServices().bitstreamService.update(event.getCtx(), event.getBitstream());
                    } else {
                        for (final Bitstream b : event.getBitstreams()) {
                            log.info(
                                    "Permission consumer/redeposit: Writing new permission to bitstream metadata for :"
                                            + b.getName() + " : " + "redeposit");

                            event.getServices().bitstreamService.addMetadata(event.getCtx(), b, "dc",
                                    "bitstream",
                                    "permissions", "en",
                                    DCDate.getCurrent().toDate() + ";" + "redeposit");
                            event.getServices().bitstreamService.update(event.getCtx(), b);
                        }
                    }
                    break;
                } catch (Exception e) {
                    log.error("Permission consumer/redeposit: " + e);
                    break;
                }

            case DEPOSIT:
                try {
                    log.info("Permission consumer/deposit");
                    setDepositBitstreamPolicies(event);
                    break;
                } catch (Exception e) {
                    log.error("Permission consumer/deposit: " + e);
                    break;
                }

            case ADD_VIA_UI: {
                break;
            }

            case REMOVE: {
                break;
            }

            case EDIT_PERMISSION: {
                try {
                    if (event.getBitstream() != null) {
                        setRedepositBitstreamPolicies(event, event.getBitstream());
                        event.getServices().bitstreamService.update(event.getCtx(), event.getBitstream());
                    } else {
                        for (final Bitstream b : event.getBitstreams()) {
                            setRedepositBitstreamPolicies(event, b);
                            event.getServices().bitstreamService.update(event.getCtx(), b);
                        }
                    }
                    break;
                } catch (Exception e) {
                    log.error("Permission consumer/edit: " + e);
                    break;
                }
            }
            default: {
                log.error("Permission consumer: permissions for this event not implemented: "
                        + event.getConsumeCaseEnum().name());
                break;
            }
        }

    }

    private static void setAddViaUIBitstreamPolicies(final KULEvent event) throws Exception {
        String permission = "PUBLIC";
        List<ResourcePolicy> policies = new ArrayList<>();
        for (final String groupName : KULConsumer.ALL_GROUP_NAMES) {
            policies.add(readForGroup(event, event.getGroupsMap().get(groupName)));
        }
        addPoliciesToBitstream(event, policies);
        // write first permission after first three policies are added to avoid
        // unnecessary emails on deposit
        if (event.getBitstream() != null) {
            log.info(
                    "Permission consumer/add_via_ui: writing new permission to bitstream metadata for "
                            + event.getBitstream().getName() + " : " + permission);

            event.getServices().bitstreamService.addMetadata(event.getCtx(), event.getBitstream(), "dc",
                    "bitstream",
                    "permissions", "en",
                    DCDate.getCurrent().toDate() + ";" + permission);

            event.getServices().bitstreamService.update(event.getCtx(), event.getBitstream());
        } else {
            for (final Bitstream b : event.getBitstreams()) {
                log.info(
                        "Permission consumer/add_via_ui: writing new permission to bitstream metadata for :"
                                + b.getName() + " : "
                                + permission);
                event.getServices().bitstreamService.addMetadata(event.getCtx(), b, "dc",
                        "bitstream",
                        "permissions", "en",
                        DCDate.getCurrent().toDate() + ";" + permission);
                event.getServices().bitstreamService.update(event.getCtx(), b);
            }
        }

    }

    private static void setDepositBitstreamPolicies(final KULEvent event) throws Exception {
        // Default: No Access
        String permission = "PRIVATE";
        List<ResourcePolicy> policies = new ArrayList<>();
        policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.ADMINS_LOCAL_GROUP)));

        // If not no access: Permanent embargo (Intranet)
        if (RIGHTS_PERMANENT_EMBARGO_VALUE.equals(getRights(event))) {
            // else: add Intranet
            policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.INTRANET_GROUP)));
            permission = "INTRANET";
        }

        // If PhD and public:
        if (event.isPhd() && RIGHTS_PUBLIC_ACCESS_VALUE.equals(getRights(event))) {
            ResourcePolicy anonymousAccess = readForGroup(event,
                    event.getGroupsMap().get(KULConsumer.ANONYMOUS_GROUP));
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            String dateIssuedString = getDateIssued(event);
            Date dateIssued = simpleDateFormat.parse(dateIssuedString);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dateIssued);
            cal.add(Calendar.YEAR, 1);
            Date dateEmbargoEnd = cal.getTime();
            anonymousAccess.setStartDate(dateEmbargoEnd);
            policies.add(anonymousAccess);
            permission = "EMBARGO";
        }
        addPoliciesToBitstream(event, policies);

        // write first permission after first three policies are added to avoid
        // unnecessary emails on deposit
        if (event.getBitstream() != null) {
            log.info(
                    "Permission consumer/deposit: writing new permission to bitstream metadata for "
                            + event.getBitstream().getName() + " : " + permission);

            event.getServices().bitstreamService.addMetadata(event.getCtx(), event.getBitstream(), "dc",
                    "bitstream",
                    "permissions", "en",
                    DCDate.getCurrent().toDate() + ";" + permission);

            event.getServices().bitstreamService.update(event.getCtx(), event.getBitstream());
        } else {
            for (final Bitstream b : event.getBitstreams()) {
                log.info(
                        "Permission consumer/deposit: writing new permission to bitstream metadata for :"
                                + b.getName() + " : "
                                + permission);
                event.getServices().bitstreamService.addMetadata(event.getCtx(), b, "dc",
                        "bitstream",
                        "permissions", "en",
                        DCDate.getCurrent().toDate() + ";" + permission);
                event.getServices().bitstreamService.update(event.getCtx(), b);
            }
        }

    }

    private static boolean isRedeposit(final Bitstream bitstream) {
        for (final MetadataValue bitstreamMetadata : bitstream.getMetadata()) {
            if (bitstreamMetadata.getMetadataField().getElement().equals("bitstream")
                    && bitstreamMetadata.getMetadataField().getQualifier().equals("permissions")) {
                final String[] temp = bitstreamMetadata.getValue().toString().split("\\;");
                if (temp.length == 2) {
                    final String permission = temp[1];
                    if ("redeposit".equalsIgnoreCase(permission)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static ArrayList<String> getBitstreamsArray(final KULEvent event) {
        ArrayList<String> bitstreams = new ArrayList();
        if (event.getBitstream() != null) {
            bitstreams.add(event.getBitstream().getInternalId());
        } else {
            for (final Bitstream b : event.getBitstreams()) {
                bitstreams.add(b.getInternalId());
            }
        }
        return bitstreams;
    }

    private static String getPreviousPermission(final KULEvent event) {
        final ArrayList<String> newBitstreams = getBitstreamsArray(event);
        final Item item = event.getItem();
        final List<MetadataValue> metadataValues = item.getMetadata();
        String result = null;
        for (MetadataValue metadataValue : metadataValues) {
            if ("provenance".equals(metadataValue.getMetadataField().getQualifier())) {
                final String provenance = metadataValue.getValue();
                if (provenance.contains("Bitstream added") || provenance.contains("Bitstream submitted")) {
                    for (String filePermission : provenance.split(" - ")) {
                        String[] splitPermission = filePermission.split("File permission: ");
                        if (splitPermission.length == 2
                                && newBitstreams.stream().noneMatch(x -> splitPermission[0].contains(x))) {
                            result = splitPermission[1];
                        }
                    }
                } else if (provenance.contains("The permissions of bitstream")
                        && newBitstreams.stream().noneMatch(x -> provenance.contains(x))) {
                    String[] splitPermission = provenance.split(" to ");
                    result = splitPermission[1];
                }

            }
        }
        return result;
    }

    private static void setRedepositBitstreamPolicies(final KULEvent event, Bitstream bitstream) throws Exception {
        if (!isRedeposit(bitstream)) {
            return;
        }
        event.getServices().bitstreamService.clearMetadata(event.getCtx(), bitstream, "dc", "bitstream", "permissions",
                Item.ANY);
        String permission = "INTRANET";
        List<ResourcePolicy> policies = new ArrayList<>();
        policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.ADMINS_LOCAL_GROUP)));
        policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.INTRANET_GROUP)));

        addPoliciesToBitstream(event, policies);

        // write first permission after first three policies are added to avoid
        // unnecessary emails on deposit
        if (event.getBitstream() != null) {
            log.info(
                    "Permission consumer/redeposit: writing new permission to bitstream metadata for "
                            + event.getBitstream().getName() + " : " + permission);

            event.getServices().bitstreamService.addMetadata(event.getCtx(), event.getBitstream(), "dc",
                    "bitstream",
                    "permissions", "en",
                    DCDate.getCurrent().toDate() + ";" + permission);

            event.getServices().bitstreamService.update(event.getCtx(), event.getBitstream());
        } else {
            for (final Bitstream b : event.getBitstreams()) {
                log.info(
                        "Permission consumer/redeposit: writing new permission to bitstream metadata for :" +
                                b.getName() + " : " + permission);

                event.getServices().bitstreamService.addMetadata(event.getCtx(), b, "dc",
                        "bitstream",
                        "permissions", "en",
                        DCDate.getCurrent().toDate() + ";" + permission);

                event.getServices().bitstreamService.update(event.getCtx(), b);
            }
        }

    }

    private static void addPoliciesToBitstream(final KULEvent event, List<ResourcePolicy> policies) throws Exception {
        if (policies != null) {
            if (event.getBitstream() != null) {
                log.info(
                        "Permission consumer: adding policy to bitstream (" + event.getBitstream().getName() + ")");
                changeBitstreamPolicies(event, event.getBitstream(), policies);
            } else {
                for (final Bitstream b : event.getBitstreams()) {
                    log.info("Permission consumer: adding policy to bitstream (" + b + ")");
                    changeBitstreamPolicies(event, b, policies);
                }
            }
        }
    }

    private static void changeBitstreamPolicies(final KULEvent event, final Bitstream bitstream,
            final List<ResourcePolicy> toAdd) throws Exception {
        log.info("Permission consumer: removing policies");
        for (final Group group : event.getGroupsMap().values()) {
            event.getServices().authorizeService.removeGroupPolicies(event.getCtx(), bitstream, group);
        }
        if (!toAdd.isEmpty()) {
            event.getServices().authorizeService.addPolicies(event.getCtx(), toAdd, bitstream);
        }
    }

    private static ResourcePolicy readForGroup(final KULEvent event, final Group group) throws Exception {
        final ResourcePolicy rp = event.getServices().resourcePolicyService.create(event.getCtx());
        rp.setAction(Constants.READ);
        rp.setGroup(group);
        return rp;
    }

    private static String getRights(final KULEvent event) {
        return event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "rights", "license",
                Item.ANY);
    }

    private static String getDateIssued(final KULEvent event) {
        String date = event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "date",
                "issued", Item.ANY).strip();
        if (date.isBlank()) {
            date = DCDate.getCurrent().toString().substring(0, 10);
        }
        return date;
    }

}
