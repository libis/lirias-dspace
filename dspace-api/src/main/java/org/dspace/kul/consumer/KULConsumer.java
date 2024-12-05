package org.dspace.kul.consumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
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

    private Set<QueuedItem> queue = new HashSet<>();

    @Override
    public void initialize() throws Exception {
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        if (event.getEventType() == Event.ADD && event.getSubjectType() == Constants.BUNDLE) {
            final Bundle bundle = bundleService.find(ctx, event.getSubjectID());
            for (final Item item : bundle.getItems()) {
                queue.add(new QueuedItem(item.getID(), Event.ADD));
            }
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
            if (qi.getEventType() == Event.ADD) {
                final List<ResourcePolicy> policies = new ArrayList<>();
                policies.add(readForGroup(ctx, groupsMap.get(ADMINS_LOCAL_GROUP)));
                for (final Bundle bundle : item.getBundles()) {
                    for (final Bitstream bitstream : bundle.getBitstreams()) {
                        for (final Group group : groupsMap.values()) {
                            authorizeService.removeGroupPolicies(ctx, bitstream, group);
                        }
                        authorizeService.addPolicies(ctx, policies, bitstream);
                    }
                }
            }
        }

        queue.clear();
    }

    @Override
    public void finish(Context ctx) throws Exception {
    }

    private ResourcePolicy readForGroup(final Context ctx, final Group group) throws Exception {
        final ResourcePolicy rp = resourcePolicyService.create(ctx);
        rp.setAction(Constants.READ);
        rp.setGroup(group);
        return rp;
    }

}
