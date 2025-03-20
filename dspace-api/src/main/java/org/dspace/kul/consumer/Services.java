package org.dspace.kul.consumer;

import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;

public class Services {

    public AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    public EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    public GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    public ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance()
            .getResourcePolicyService();
    public ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    public BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    public BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

}
