/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.kul.enpoint;

import java.sql.SQLException;
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
import org.dspace.eperson.Group;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;
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

    @GetMapping("/{bitstreamID}")
    public ResponseEntity<BitstreamPermission> get(HttpServletRequest request, @PathVariable UUID bitstreamID)
            throws SQLException, AuthorizeException {
        Context context = ContextUtil.obtainContext(request);
        System.out.println("GET bitstreamID: " + bitstreamID);
        System.out.println("GET isAdmin: " + authorizeService.isAdmin(context));
        if (!authorizeService.isAdmin(context)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        Bitstream bitstream = bitstreamService.find(context, bitstreamID);
        return new ResponseEntity<>(getBitstreamPermission(context, bitstream), HttpStatus.OK);
    }

    @PostMapping("/{bitstreamID}")
    public ResponseEntity<BitstreamPermission> post(HttpServletRequest request, @PathVariable UUID bitstreamID,
            @RequestBody BitstreamPermission permission)
            throws SQLException, AuthorizeException {
        Context context = ContextUtil.obtainContext(request);
        System.out.println("POST bitstream: " + bitstreamID);
        System.out.println("POST isAdmin: " + authorizeService.isAdmin(context));
        if (!authorizeService.isAdmin(context)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        Bitstream bitstream = bitstreamService.find(context, bitstreamID);
        try {
            setBitstreamPermission(context, bitstream, permission);
        } catch (Exception e) {
            System.err.println(e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(HttpStatus.OK);
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
            if (group == groupService.findByName(context, KULConsumer.ANONYMOUS_GROUP)) {
                if (startDate == null || startDate.before(now)) {
                    permission = "PUBLIC";
                } else if (startDate.after(now)) {
                    permission = "EMBARGO";
                }

            } else if (group == groupService.findByName(context,
                    KULConsumer.INTRANET_GROUP)) {
                permission = "INTRANET";
            }
        }
        result.setPermission(permission);
        if ("EMBARGO".equalsIgnoreCase(permission) && null != startDate) {
            result.setEmbargoEndDate(startDate.getDate(), startDate.getMonth(), startDate.getYear() + 1900);
        }

        return result;
    }

    private ResourcePolicy readForGroup(Context context, Bitstream bitstream, String groupName)
            throws SQLException, AuthorizeException {
        Group group = null;
        try {
            group = groupService.findByName(context, groupName);
        } catch (Exception e) {
            System.err.println(e);
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
        System.out.println("removing policies: controller");
        Group group = null;
        try {
            group = groupService.findByName(context, groupName);
        } catch (Exception e) {
            System.err.println(e);
        }
        if (null != group) {
            try {
                authorizeService.removeGroupPolicies(context, bitstream, group);
                System.out.println("Removing policy: " + group.getName());
            } catch (Exception e) {
                System.err.println(e);
            }
        } else {
            System.err.println("Group " + groupName + " not found.");
        }

    }

    private void changeBitstreamPolicies(Context context, Bitstream bitstream,
            final List<ResourcePolicy> toAdd) throws SQLException, AuthorizeException {
        for (String groupName : KULConsumer.ALL_GROUP_NAMES) {
            removePolicy(context, bitstream, groupName);
        }
        if (!toAdd.isEmpty()) {
            System.out.println("Adding policies: " + toAdd.toString());
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
        System.out.println("Setting permission to " + permission.getPermission());
        System.out.println("Embargo end date (if applicable) " + permission.getEmbargoEndDate());
        List<ResourcePolicy> policiesToAdd;

        switch (permission.getPermission()) {
            case "PRIVATE": {
                policiesToAdd = getPoliciesForGroups(context, bitstream, List.of(KULConsumer.ADMINS_LOCAL_GROUP));
                changeBitstreamPolicies(context, bitstream, policiesToAdd);
                break;
            } // remove all, add ADMINS_LOCAL_GROUP
            case "INTRANET": {
                System.out.println("case intranet");
                policiesToAdd = getPoliciesForGroups(context, bitstream,
                        List.of(KULConsumer.INTRANET_GROUP, KULConsumer.ADMINS_LOCAL_GROUP));
                changeBitstreamPolicies(context, bitstream, policiesToAdd);
                break;
            } // remove all, add INTRANET_GROUP, ADMINS_LOCAL_GROUP
            case "PUBLIC": {
                policiesToAdd = getPoliciesForGroups(context, bitstream,
                        List.of(KULConsumer.INTRANET_GROUP, KULConsumer.ADMINS_LOCAL_GROUP));
                ResourcePolicy rp = readForGroup(context, bitstream, KULConsumer.ANONYMOUS_GROUP);
                rp.setStartDate(DCDate.getCurrent().toDate());
                policiesToAdd.add(rp);
                changeBitstreamPolicies(context, bitstream, policiesToAdd);
                break;
            } // remove all, add INTRANET_GROUP, ADMINS_LOCAL_GROUP, ANONYMOUS_GROUP
              // (startDate: now)
            case "EMBARGO": {
                System.out.println("case embargo");
                policiesToAdd = getPoliciesForGroups(context, bitstream,
                        List.of(KULConsumer.INTRANET_GROUP, KULConsumer.ADMINS_LOCAL_GROUP));
                System.out.println(permission);
                System.out.println(permission.getEmbargoEndDate());
                if (null == permission.getEmbargoEndDate()) {
                    System.err.println("No end date entered for embargo.");
                    break; // no end date specified
                }
                Number endDateDay = 31; // default: last day of month
                Number endDateMonth = 12; // default: December
                if (null != permission.getEmbargoEndDate().day) {
                    endDateDay = permission.getEmbargoEndDate().day;
                } // set date if present
                if (null != permission.getEmbargoEndDate().month) {
                    endDateMonth = permission.getEmbargoEndDate().month;
                } // set month if present
                Number endDateYear = permission.getEmbargoEndDate().year;
                if (null == endDateYear) {
                    System.err.println("No end date year entered for embargo.");
                    break;
                } // no year: invalid
                ResourcePolicy rp = readForGroup(context, bitstream, KULConsumer.ANONYMOUS_GROUP);
                rp.setStartDate(new Date((int) endDateYear - 1900, (int) endDateMonth, (int) endDateDay));
                policiesToAdd.add(rp);
                changeBitstreamPolicies(context, bitstream, policiesToAdd);
                break;
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
