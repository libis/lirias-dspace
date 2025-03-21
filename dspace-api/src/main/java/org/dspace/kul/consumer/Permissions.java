package org.dspace.kul.consumer;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.eperson.Group;

public class Permissions {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Permissions.class);
    public static final String RIGHTS_PUBLIC_ACCESS_VALUE = "Public access (as soon as legally possible, verified by the OA Support Desk)";
    public static final String RIGHTS_PERMANENT_EMBARGO_VALUE = "Permanent embargo (intranet only)";
    public static final String RIGHTS_NO_ACCESS_VALUE = "No access (only for strictly confidential material)";

    public static void applyTo(final KULEvent event) throws Exception {
        List<ResourcePolicy> policies = null;
        switch (event.getConsumeCaseEnum()) {
            case REDEPOSIT:
            case DEPOSIT: {
                policies = new ArrayList<>();
                policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.ADMINS_LOCAL_GROUP)));
                if (!RIGHTS_NO_ACCESS_VALUE.equals(getRights(event))) {
                    policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.INTRANET_GROUP)));
                }
                break;
            }
            case ADD_VIA_UI:
            case REMOVE:
            case EDIT_PERMISSION: {
                break;
            }
            default: {
                log.error("permissions for this event not implemented: " + event.getConsumeCaseEnum().name());
                break;
            }
        }
        if (policies != null) {
            if (event.getBitstream() != null) {
                changeBitstreamPolicies(event, event.getBitstream(), policies);
            } else {
                for (final Bitstream b : event.getBitstreams()) {
                    changeBitstreamPolicies(event, b, policies);
                }
            }
        }
    }

    private static void changeBitstreamPolicies(final KULEvent event, final Bitstream bitstream,
            final List<ResourcePolicy> toAdd) throws Exception {
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
        return event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "rights", "license", Item.ANY);
    }
}
