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
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.GroupService;
import org.dspace.kul.consumer.KULConsumer;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.services.model.Request;
import org.dspace.web.ContextUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;
import org.dspace.content.Bitstream;
import org.dspace.content.DCDate;
import org.dspace.eperson.Group;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        System.out.println("bitstream: " + bitstreamID);
        System.out.println("isadmin: " + authorizeService.isAdmin(context));
        Bitstream bitstream = bitstreamService.find(context, bitstreamID);
        return new ResponseEntity<>(getBitstreamPermission(context, bitstream), HttpStatus.OK);
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
            result.setEmbargoEndDate(startDate.getDate(), startDate.getMonth(), startDate.getYear());
        }

        return result;
    }

}
