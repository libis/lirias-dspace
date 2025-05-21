package org.dspace.kul.consumer;

import java.util.ArrayList;
import java.util.Date;

import static org.dspace.core.Constants.READ;

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
                    System.out.println("Redeposit (permissions)");
                    List<ResourcePolicy> policies = new ArrayList<>();
                    policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.ADMINS_LOCAL_GROUP)));
                    policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.INTRANET_GROUP)));

                    addPoliciesToBitstream(event, policies);

                    if (event.getBitstream() != null) {
                        System.out.println(
                                "Permission consumer writing new permission to bitstream metadata for "
                                        + event.getBitstream().getName() + " : " + "redeposit");

                        event.getServices().bitstreamService.addMetadata(event.getCtx(), event.getBitstream(), "dc",
                                "bitstream",
                                "permissions", "en",
                                DCDate.getCurrent().toDate() + ";" + "redeposit");

                        event.getServices().bitstreamService.update(event.getCtx(), event.getBitstream());
                    } else {
                        for (final Bitstream b : event.getBitstreams()) {
                            System.out.println(
                                    "Permission consumer writing new permission to bitstream metadata for :"
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
                    System.err.println("Redeposit (permissions): " + e);
                    break;
                }

            case DEPOSIT:
                try {
                    System.out.println("Deposit (permissions)");
                    setDepositBitstreamPolicies(event);
                    break;
                } catch (Exception e) {
                    System.err.println("Deposit (permissions): " + e);
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
                    System.err.println("Deposit (permissions): " + e);
                    break;
                }
            }
            default: {
                log.error("permissions for this event not implemented: " + event.getConsumeCaseEnum().name());
                break;
            }
        }

    }

    private static void setDepositBitstreamPolicies(final KULEvent event) throws Exception {
        String permission = "PRIVATE";
        List<ResourcePolicy> policies = new ArrayList<>();
        policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.ADMINS_LOCAL_GROUP)));
        if (!RIGHTS_NO_ACCESS_VALUE.equals(getRights(event))) {
            // else: add Intranet
            policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.INTRANET_GROUP)));
            permission = "INTRANET";
            // if PhD: add embargo for 1 year
            if (event.isPhd()) {
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
        }
        addPoliciesToBitstream(event, policies);
        
        // write first permission after first three policies are added to avoid
        // unnecessary emails on deposit
        if (event.getBitstream() != null) {
            System.out.println(
                    "Deposit case: Permission consumer writing new permission to bitstream metadata for "
                            + event.getBitstream().getName() + " : " + permission);

            event.getServices().bitstreamService.addMetadata(event.getCtx(), event.getBitstream(), "dc",
                    "bitstream",
                    "permissions", "en",
                    DCDate.getCurrent().toDate() + ";" + permission);

            event.getServices().bitstreamService.update(event.getCtx(), event.getBitstream());
        } else {
            for (final Bitstream b : event.getBitstreams()) {
                System.out.println(
                        "Deposit case: Permission consumer writing new permission to bitstream metadata for :"
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
            System.out.println(
                    "Redeposit case: Permission consumer writing new permission to bitstream metadata for "
                            + event.getBitstream().getName() + " : " + permission);

            event.getServices().bitstreamService.addMetadata(event.getCtx(), event.getBitstream(), "dc",
                    "bitstream",
                    "permissions", "en",
                    DCDate.getCurrent().toDate() + ";" + permission);

            event.getServices().bitstreamService.update(event.getCtx(), event.getBitstream());
        } else {
            for (final Bitstream b : event.getBitstreams()) {
                // System.out.println(
                // "Permission consumer writing new permission to bitstream metadata for :" +
                // b.getName() + " : " + permission);

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
                System.out.println("Adding policy to bitstream (" + event.getBitstream().getName() + ")");
                changeBitstreamPolicies(event, event.getBitstream(), policies);
            } else {
                for (final Bitstream b : event.getBitstreams()) {
                    System.out.println("Adding policy to bitstream (" + b + ")");
                    changeBitstreamPolicies(event, b, policies);
                }
            }
        }
    }

    private static void changeBitstreamPolicies(final KULEvent event, final Bitstream bitstream,
            final List<ResourcePolicy> toAdd) throws Exception {
        System.out.println("removing policies:  Permissions");
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
