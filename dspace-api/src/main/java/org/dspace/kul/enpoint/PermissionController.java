/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.kul.enpoint;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.eperson.service.GroupService;
import org.dspace.kul.consumer.KULConsumer;
import org.dspace.kul.consumer.Mailing;
import org.dspace.web.ContextUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.dspace.content.Bitstream;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.eperson.Group;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.dspace.core.Constants;

/**
 * This Controller serves as an example of how & where to add local
 * customizations to the DSpace REST API.
 * See {@link ExampleControllerIT} for the integration tests for this
 * controller.
 */
@RestController
@RequestMapping("/api/kul/permissions")
public class PermissionController {

    @Autowired
    BitstreamService bitstreamService;

    @Autowired
    ResourcePolicyService resourcePolicyService;

    @Autowired
    AuthorizeService authorizeService;

    @Autowired
    GroupService groupService;

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Mailing.class);

    @GetMapping("/{bitstreamID}")
    public ResponseEntity<BitstreamPermission> get(HttpServletRequest request, @PathVariable UUID bitstreamID)
            throws SQLException, AuthorizeException {
        Context context = ContextUtil.obtainContext(request);
        Bitstream bitstream = bitstreamService.find(context, bitstreamID);
        return new ResponseEntity<>(getBitstreamPermission(context, bitstream), HttpStatus.OK);
    }

    @PostMapping("/{bitstreamID}")
    public ResponseEntity<BitstreamPermission> post(HttpServletRequest request, @PathVariable UUID bitstreamID,
            @RequestBody BitstreamPermission permission)
            throws SQLException, AuthorizeException {
        Context context = ContextUtil.obtainContext(request);
        if (!authorizeService.isAdmin(context) ) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        Bitstream bitstream = bitstreamService.find(context, bitstreamID);
        BitstreamPermission previousPermission = getBitstreamPermission(context, bitstream);
        String newPermissionMessage = permission.getPermission();

        if ("EMBARGO".equalsIgnoreCase(permission.getPermission()) && permission.getEmbargoEndDate() != null) {
            final Calendar cal = new GregorianCalendar(permission.getEmbargoEndDate().year.intValue(),
                    permission.getEmbargoEndDate().month.intValue() - 1,
                    permission.getEmbargoEndDate().day.intValue());
            final Date embargoDate = Date.from(cal.toInstant());
            newPermissionMessage += MessageFormat.format(", {0}", embargoDate.toString());
        }
        System.out.println("Permission controller is removing previous permissions for " + bitstream.getName());
        clearAllBitstreamPermissionMetadata(context, bitstream);

        System.out.println("Permission controller is updating permission metadata for " + bitstream.getName() + " from  " + previousPermission.getPermission() + " to " + newPermissionMessage);
        addBitstreamPermissionMetadata(context, bitstream, previousPermission);
        addBitstreamPermissionMetadata(context, bitstream, permission);
        context.commit();
        try { 
            setBitstreamPermission(context, bitstream, permission);
        } catch (Exception e) {
            System.err.println(e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void clearAllBitstreamPermissionMetadata(Context context, Bitstream bitstream) throws SQLException, AuthorizeException {

        bitstreamService.clearMetadata(context, bitstream, "dc", "bitstream", "permissions", Item.ANY);
        bitstreamService.update(context, bitstream);
        
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

    private BitstreamPermission getBitstreamPermission(Context context, Bitstream bitstream) throws SQLException {
        BitstreamPermission result = new BitstreamPermission();
        final Date now = DCDate.getCurrent().toDate();

        String permission = "PRIVATE";
        Date startDate = null;
        final List<ResourcePolicy> resourcePolicies = resourcePolicyService.find(context, bitstream, Constants.READ);
        for (ResourcePolicy policy : resourcePolicies) {
            startDate = policy.getStartDate();
            final Group group = policy.getGroup();
            if (group != null && KULConsumer.ANONYMOUS_GROUP.equals(group.getName())) {
                if (startDate == null || startDate.before(now)) {
                    permission = "PUBLIC";
                    break;
                } else {
                    permission = "EMBARGO";
                    break;
                }
            } else if (group != null && KULConsumer.INTRANET_GROUP.equals(group.getName())) {
                permission = "INTRANET";
            }
        }
        result.setPermission(permission);
        if ("EMBARGO".equalsIgnoreCase(permission) && startDate != null) {
            final GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTime(startDate);
            result.setEmbargoEndDate(calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR));
        }

        return result;
    }

    private void addBitstreamPermissionMetadata(Context ctx, Bitstream bitstream, BitstreamPermission permission) throws SQLException, AuthorizeException {
        String newPermission = permission.getPermission();
        if ("EMBARGO".equalsIgnoreCase(permission.getPermission())) {
            for (final ResourcePolicy policy : authorizeService
                    .getPoliciesActionFilter(ctx, bitstream, Constants.READ)) {
                newPermission += getPolicyDates(policy);
            }
        }
        System.out.println("Permission controller - writing new permission to bitstream metadata for " + bitstream.getName() + " : " + newPermission);
        bitstreamService.addMetadata(ctx, bitstream, "dc", "bitstream",
                "permissions", "en",
                DCDate.getCurrent().toDate() + ";" + newPermission);
        bitstreamService.update(ctx, bitstream);
    }

    private ResourcePolicy readForGroup(Context context, Bitstream bitstream, String groupName)
            throws SQLException, AuthorizeException {
        Group group = null;
        try {
            group = groupService.findByName(context, groupName);
        } catch (Exception e) {
            log.error(e);
        }
        if (null != group) {
            final ResourcePolicy rp = resourcePolicyService.create(context);
            rp.setAction(Constants.READ);
            rp.setGroup(groupService.findByName(context, groupName));
            return rp;
        } else {
            return null;
        }
    }

    private void removePolicy(Context context, Bitstream bitstream, String groupName) {
        Group group = null;
        try {
            group = groupService.findByName(context, groupName);
        } catch (Exception e) {
            System.err.println(e);
        }
        if (null != group) {
            try {
                authorizeService.removeGroupPolicies(context, bitstream, group);
            } catch (Exception e) {
                System.err.println(e);
            }
        }
    }

    private void changeBitstreamPolicies(Context context, Bitstream bitstream, final List<ResourcePolicy> toAdd) throws SQLException, AuthorizeException {
        for (String groupName : KULConsumer.ALL_GROUP_NAMES) {
            removePolicy(context, bitstream, groupName);
        }
        if (!toAdd.isEmpty()) {
            try {
                authorizeService.addPolicies(context, toAdd, bitstream);
            } catch (Exception e) {
                System.err.println(e);
            }
        }
        bitstreamService.update(context, bitstream);
        context.commit();
    }

    private void setBitstreamPermission(Context context, Bitstream bitstream, BitstreamPermission permission)
            throws SQLException, AuthorizeException {
        switch (permission.getPermission()) {
            case "PRIVATE": {
                final List<ResourcePolicy> policiesToAdd = getPoliciesForGroups(context, bitstream, List.of(KULConsumer.ADMINS_LOCAL_GROUP));
                changeBitstreamPolicies(context, bitstream, policiesToAdd);
                return;
            } // remove all, add ADMINS_LOCAL_GROUP
            case "INTRANET": {
                final List<ResourcePolicy> policiesToAdd = getPoliciesForGroups(context, bitstream,
                        List.of(KULConsumer.INTRANET_GROUP, KULConsumer.ADMINS_LOCAL_GROUP));
                changeBitstreamPolicies(context, bitstream, policiesToAdd);
                return;
            } // remove all, add INTRANET_GROUP, ADMINS_LOCAL_GROUP
            case "PUBLIC": {
                final List<ResourcePolicy> policiesToAdd = getPoliciesForGroups(context, bitstream,
                        List.of(KULConsumer.INTRANET_GROUP, KULConsumer.ADMINS_LOCAL_GROUP));
                ResourcePolicy rp = readForGroup(context, bitstream, KULConsumer.ANONYMOUS_GROUP);
                rp.setStartDate(DCDate.getCurrent().toDate());
                policiesToAdd.add(rp);
                changeBitstreamPolicies(context, bitstream, policiesToAdd);
                return;
            } // remove all, add INTRANET_GROUP, ADMINS_LOCAL_GROUP, ANONYMOUS_GROUP
              // (startDate: now)
            case "EMBARGO": {
                final List<ResourcePolicy> policiesToAdd = getPoliciesForGroups(context, bitstream,
                        List.of(KULConsumer.INTRANET_GROUP, KULConsumer.ADMINS_LOCAL_GROUP));
                if (null == permission.getEmbargoEndDate()) {
                    return; // no end date specified
                }
                final ResourcePolicy rp = readForGroup(context, bitstream, KULConsumer.ANONYMOUS_GROUP);
                final Calendar calendar = new GregorianCalendar(permission.getEmbargoEndDate().year.intValue(), permission.getEmbargoEndDate().month.intValue() - 1, permission.getEmbargoEndDate().day.intValue());
                final Date startDate = Date.from(calendar.toInstant());
                rp.setStartDate(startDate);
                policiesToAdd.add(rp);
                changeBitstreamPolicies(context, bitstream, policiesToAdd);
                return;
            }
        }
    }

    private List<ResourcePolicy> getPoliciesForGroups(Context context, Bitstream bitstream, List<String> groupNames)
            throws SQLException, AuthorizeException {
        List<ResourcePolicy> result = new ArrayList<>();
        for (String groupName : groupNames) {
            ResourcePolicy policy = readForGroup(context, bitstream, groupName);
            if (null != policy) {
                result.add(policy);
            }
        }
        return result;
    }

}
