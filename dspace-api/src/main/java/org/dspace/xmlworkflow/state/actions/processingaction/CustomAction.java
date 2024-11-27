package org.dspace.xmlworkflow.state.actions.processingaction;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.workflow.WorkflowException;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

public class CustomAction extends ProcessingAction {
    private static final String ANONYMOUS_GROUP = "Anonymous";
    private static final String INTRANET_GROUP = "registered_users";
    private static final String ADMINS_LOCAL_GROUP = "Admins_local";
    private static final List<String> ALL_GROUP_NAMES = Arrays.asList(ANONYMOUS_GROUP, INTRANET_GROUP, ADMINS_LOCAL_GROUP);

    protected AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    protected EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    protected GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    protected ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance().getResourcePolicyService();

    @Override
    public void activate(Context c, XmlWorkflowItem wf)
            throws SQLException, IOException, AuthorizeException, WorkflowException {
    }

    @Override
    public ActionResult execute(final Context ctx, final XmlWorkflowItem wfi, final Step step, final HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException, WorkflowException {
        
        final Map<String, Group> groupsMap = new HashMap<>();
        for(final String groupName : ALL_GROUP_NAMES) {
            groupsMap.put(groupName, groupService.findByName(ctx, groupName));
        }

        final ResourcePolicy rp = resourcePolicyService.create(ctx);
        rp.setAction(Constants.READ);
        rp.setGroup(groupsMap.get(ADMINS_LOCAL_GROUP));

        for(final Bundle bundle : wfi.getItem().getBundles()) {
            for(final Bitstream bitstream : bundle.getBitstreams()) {
                for(final Group group : groupsMap.values()) {
                    authorizeService.removeGroupPolicies(ctx, bitstream, group);
                }
                authorizeService.addPolicies(ctx, Arrays.asList(rp), bitstream);
            }
        }
        
        return new ActionResult(ActionResult.TYPE.TYPE_OUTCOME, ActionResult.OUTCOME_COMPLETE);
    }

    @Override
    public List<String> getOptions() {
        return Collections.emptyList();
    }
    
}
