package org.dspace.kul.consumer;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.core.Constants;
import org.dspace.eperson.Group;

public class Permissions {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Permissions.class);

    public static void applyTo(final KULEvent event) throws Exception {
        final List<ResourcePolicy> policies = new ArrayList<>();
        switch (event.getConsumeCaseEnum()) {
            case REDEPOSIT:
            case ADD_VIA_UI: {
                policies.add(readForGroup(event, event.getGroupsMap().get(KULConsumer.ADMINS_LOCAL_GROUP)));
                break;
            }
            case DEPOSIT:
            case REMOVE:
            case EDIT_PERMISSION: {
                break;
            }
            default: {
                log.error("permissions for this event not implemented: " + event.getConsumeCaseEnum().name());
                break;
            }
        }
        if (policies != null && !policies.isEmpty()) {
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
        event.getServices().authorizeService.addPolicies(event.getCtx(), toAdd, bitstream);
    }

    private static ResourcePolicy readForGroup(final KULEvent event, final Group group) throws Exception {
        final ResourcePolicy rp = event.getServices().resourcePolicyService.create(event.getCtx());
        rp.setAction(Constants.READ);
        rp.setGroup(group);
        return rp;
    }
}
