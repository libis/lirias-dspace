/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.kul.enpoint;

import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;

import org.dspace.authorize.AuthorizeException;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.handle.hdllib.trust.Permission;

/**
 * This Controller serves as an example of how & where to add local customizations to the DSpace REST API.
 * See {@link ExampleControllerIT} for the integration tests for this controller.
 */
//@RestController
//@RequestMapping("/api/kul/permissions")
public class PermissionController {

    // https://josdem.io/techtalk/spring/spring_boot_json_node/
    private final ObjectMapper mapper = new ObjectMapper();

    //@RequestMapping(method = RequestMethod.GET)
    public /*ResponseEntity<RepresentationModel<?>>*/ JsonNode patch(HttpServletRequest request,
                                                        /*@RequestBody(required = true)*/ JsonNode jsonNode)
        throws SQLException, AuthorizeException {
        //Context context = obtainContext(request);
        //bitstreamRestRepository.patchBitstreamsInBulk(context, jsonNode);
        //return ResponseEntity.noContent().build();
        System.out.println(jsonNode.get("fieldName").asBoolean());
        return mapper.valueToTree(new Permission());
    }
}
