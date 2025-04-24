package org.dspace.kul.consumer;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.core.Constants;
import java.util.Deque;
import java.util.ArrayDeque;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Date;

import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;

public class Mailing {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Mailing.class);

    private static final Services services = new Services();
    private static final String dspaceUrl = services.configurationService.getProperty("dspace.url");
    private static final String limoUrl = services.configurationService.getProperty("limo.url");
    private static final String senderEmail = services.configurationService.getProperty("phd-emails.sender");
    private static final String elementsCacheAPIUrl = services.configurationService.getProperty("elements-cache.url")
            + "/rest/";
    private static final String elementsCacheUsername = services.configurationService
            .getProperty("elements-cache.username");
    private static final String elementsCachePassword = services.configurationService
            .getProperty("elements-cache.password");

    public static void applyTo(final KULEvent event) throws Exception {
        if (event.isPhd()) {
            switch (event.getConsumeCaseEnum()) {
                case REDEPOSIT: {
                    Set<String> emailRecipients = getContributorEmails(event.getItem(),
                            List.of("author", "supervisor", "cosupervisor"));
                    if (emailRecipients.isEmpty()) {
                        System.out.println("No recipient emails found for redeposit email.");
                        return;
                    }
                    if (senderEmail == null) {
                        System.out.println("No email sender set.");
                        return;
                    }
                    Email email = Email
                            .getEmail(I18nUtil.getEmailFilename(event.getCtx().getCurrentLocale(), "redeposit"));
                    if (email == null) {
                        System.out.println("Email template not found: redeposit");
                        return;
                    }
                    emailRecipients.forEach(r -> email.addRecipient(r));
                    email.setReplyTo(senderEmail);
                    email.addArgument(getItemDspaceUrl(event.getItem()));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "contributor",
                                    "author", Item.ANY));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                    null, Item.ANY));
                    email.addArgument(getItemPermission(event));
                    email.addArgument(getGroupStartDate(event, event.getBitstream(), "anonymous"));
                    email.send();
                    break;
                }
                case ADD_VIA_UI: {
                    Set<String> emailRecipients = getContributorEmails(event.getItem(),
                            List.of("author", "supervisor", "cosupervisor"));
                    if (emailRecipients.isEmpty()) {
                        System.out.println("No recipient emails found for add bitstream via ui email.");
                        return;
                    }
                    if (senderEmail == null) {
                        System.out.println("No email sender set.");
                        return;
                    }
                    Email email = Email
                            .getEmail(I18nUtil.getEmailFilename(event.getCtx().getCurrentLocale(),
                                    "add_bitstream_via_ui"));
                    if (email == null) {
                        System.out.println("Email template not found: add_bitstream_via_ui");
                        return;
                    }
                    emailRecipients.forEach(r -> email.addRecipient(r));
                    email.setReplyTo(senderEmail);
                    email.addArgument(getItemDspaceUrl(event.getItem()));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "contributor",
                                    "author", Item.ANY));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                    null, Item.ANY));

                    Deque<String> permissionHistory = getPreviousBitstreamPermissionText(event);
                    if (permissionHistory.size() > 0) {
                        String permission = permissionHistory.pop();
                        email.addArgument(expandPermissionString(permission));
                    } else {
                        email.addArgument(null);
                    }
                    email.addArgument(getGroupStartDate(event, event.getBitstream(), "anonymous"));
                    email.send();
                    break;
                }
                case DEPOSIT: {
                    Set<String> emailRecipients = getContributorEmails(event.getItem(),
                            List.of("author", "supervisor", "cosupervisor"));
                    if (emailRecipients.isEmpty()) {
                        System.out.println("No recipient emails found for deposit email.");
                        return;
                    }
                    if (senderEmail == null) {
                        System.out.println("No email sender set.");
                        return;
                    }
                    Email email = Email
                            .getEmail(I18nUtil.getEmailFilename(event.getCtx().getCurrentLocale(), "deposit"));

                    if (email == null) {
                        System.out.println("Email template not found: deposit");
                        return;
                    }

                    emailRecipients.forEach(r -> email.addRecipient(r));
                    email.setReplyTo(senderEmail);
                    email.addArgument(getItemDspaceUrl(event.getItem()));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "contributor",
                                    "author", Item.ANY));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                    null, Item.ANY));
                    email.addArgument(getItemPermission(event));
                    email.addArgument(getGroupStartDate(event, event.getBitstreams().get(0), "anonymous"));
                    email.send();
                    break;
                }
                case REMOVE: {
                    Set<String> emailRecipients = getContributorEmails(event.getItem(),
                            List.of("author", "supervisor", "cosupervisor"));
                    if (emailRecipients.isEmpty()) {
                        System.out.println("No recipient emails found for add bitstream removal email.");
                        return;
                    }
                    if (senderEmail == null) {
                        System.out.println("No email sender set.");
                        return;
                    }
                    Email email = Email
                            .getEmail(I18nUtil.getEmailFilename(event.getCtx().getCurrentLocale(), "remove_bitstream"));
                    if (email == null) {
                        System.out.println("Email template not found: remove_bitstream");
                        return;
                    }
                    emailRecipients.forEach(r -> email.addRecipient(r));
                    email.setReplyTo(senderEmail);

                    email.addArgument(getItemDspaceUrl(event.getItem()));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "contributor",
                                    "author", Item.ANY));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                    null, Item.ANY));
                    email.send();
                    break;
                }
                case EDIT_PERMISSION: {
                    Set<String> emailRecipients = getContributorEmails(event.getItem(),
                            List.of("author", "supervisor", "cosupervisor"));
                    if (emailRecipients.isEmpty()) {
                        System.out.println("No recipient emails found for edit bitstream permission email.");
                        return;
                    }
                    if (senderEmail == null) {
                        System.out.println("No email sender set.");
                        return;
                    }
                    Email email = Email.getEmail(
                            I18nUtil.getEmailFilename(event.getCtx().getCurrentLocale(), "edit_bitstream_permission"));
                    if (email == null) {
                        System.out.println("Email template not found: edit_bitstream_permission");
                        return;
                    }
                    emailRecipients.forEach(r -> email.addRecipient(r));
                    email.setReplyTo(senderEmail);

                    email.addArgument(getItemDspaceUrl(event.getItem()));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "contributor",
                                    "author", Item.ANY));
                    email.addArgument(
                            event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                    null, Item.ANY));
                    Deque<String> permissionHistory = getPreviousBitstreamPermissionText(event);
                    if (permissionHistory.size() > 0) {
                        final String previousPermission = permissionHistory.pop();
                        email.addArgument(expandPermissionString(previousPermission));
                    } else {
                        email.addArgument(null);
                    }
                    if (permissionHistory.size() > 0) {
                        final String currentPermission = permissionHistory.pop();
                        email.addArgument(expandPermissionString(currentPermission));
                    } else {
                        email.addArgument(null);
                    }
                    email.addArgument(getGroupStartDate(event, event.getBitstream(), "anonymous"));
                    email.send();
                    break;
                }
                default: {
                    log.error("phd mailing for this event not implemented: " + event.getConsumeCaseEnum().name());
                    break;
                }

            }

        } else {
            switch (event.getConsumeCaseEnum()) {
                case REDEPOSIT:
                case ADD_VIA_UI:
                case DEPOSIT:
                case REMOVE:
                case EDIT_PERMISSION: {
                    break;
                }
                default: {
                    log.error("default mailing for this event not implemented: " + event.getConsumeCaseEnum().name());
                    break;
                }
            }
        }
    }


    private static String getItemDspaceUrl(Item item) {
        return MessageFormat.format("{0}/handle/{1}", dspaceUrl, item.getHandle());
    }

    private static String getItemLimoUrl(Item item) {
        return MessageFormat.format("{0}/handle/{1}", limoUrl, item.getHandle());
    }

    private static Set<String> getContributorEmails(Item item, List<String> contributorTypes) throws Exception {
        Set<String> uNumbers = new HashSet<>();
        for (String contributorType : contributorTypes) {
            services.itemService.getMetadata(item, "dc", "contributor", contributorType, Item.ANY).stream()
                    .forEach(m -> uNumbers.add(
                            getUnumberFromMetadata(m)));
        }
        Set<String> emails = new HashSet<>();
        for (String uNumber : uNumbers) {
            String emailAddress = getEmailAdress(uNumber);
            if (emailAddress != null) {
                emails.add(emailAddress);
            }
        }
        if (emails.isEmpty()) {
            log.error("No email addresses found for item.");
        }
        return emails;
    }

    private static String getUnumberFromMetadata(MetadataValue metadataValue) {
        String[] splitMetadata = metadataValue.getValue().toString().split("\\;");
        if (splitMetadata != null && splitMetadata.length > 1) {
            return splitMetadata[1].trim();
        } else {
            return null;
        }
    }

    private static String getEmailAdress(String uNumber) throws Exception {
        if (elementsCacheAPIUrl == null) {
            System.out.println("Elements Cache API URL not set");
            return null;
        }
        if (elementsCacheUsername == null || elementsCacheUsername.isBlank()) {
            System.out.println("Elements Cache API username not set");
            return null;
        }
        if (elementsCachePassword == null || elementsCachePassword.isBlank()) {
            System.out.println("Elements Cache API password not set");
            return null;
        }
        if (uNumber == null || uNumber.isBlank()) {
            System.out.println("No u-number to send email.");
            return null;
        }
        String result;
        String requestUrl = elementsCacheAPIUrl + "email/user/" + uNumber;
        HttpGet request = new HttpGet(requestUrl);
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(elementsCacheUsername, elementsCachePassword));
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultCredentialsProvider(provider)
                .build();
        CloseableHttpResponse httpResponse = httpClient.execute(request);
        StatusLine statusLine = httpResponse.getStatusLine();
        String responseText = EntityUtils.toString(httpResponse.getEntity());
        if (statusLine.getStatusCode() != 200) {
            System.out.println("Request failed: " + requestUrl);
            System.out.println(statusLine.getReasonPhrase());
            System.out.println(responseText);
            return null;
        }
        result = responseText.replaceAll("<[^>]*>", "");
        return result;
    }

    private static String getGroupStartDate(KULEvent event, Bitstream bitstream, String groupName) throws Exception {
        String result = "Not Applicable";
        List<ResourcePolicy> resourcePolicyList = services.authorizeService.getPoliciesActionFilter(event.getCtx(),
                bitstream, Constants.READ);
        for (ResourcePolicy resourcePolicy : resourcePolicyList) {
            if (resourcePolicy.getGroup() != null && resourcePolicy.getGroup().getName().equalsIgnoreCase(groupName)) {
                Date startDate = resourcePolicy.getStartDate();
                if (startDate != null) {
                    result = startDate.toString();
                }
            }
        }
        return result;
    }

    private static String expandPermissionString(String permission) {
        switch (permission.toLowerCase()) {
            case "embargo":
                return "Public (after an embargo of 12 months)";
            case "public":
                return "Public";
            case "intranet":
                return "Permanent embargo (intranet only)";
            default:
                return "Private (repository admins only)";
        }
    }


    private static Deque<String> getPreviousBitstreamPermissionText(KULEvent event)
            throws ParseException {
        Deque<String> permissions = new ArrayDeque<String>();
        for (final MetadataValue bitstreamMetadata : event.getBitstream().getMetadata()) {
            if (bitstreamMetadata.getMetadataField().getElement().equals("bitstream")
                    && bitstreamMetadata.getMetadataField().getQualifier().equals("permissions")) {
                final String[] temp = bitstreamMetadata.getValue().toString().split("\\;");
                if (temp.length == 2) {
                    final Date permissionDate = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy").parse(temp[0]);
                    final String permission = temp[1];
                    if (permissionDate != null) {
                        permissions.push(permission);
                    }
                    // TODO: order by date
                    // TODO: refactor (similar function in Provenance.java)
                }
            }
        }
        return permissions;
    }

    private static String getItemPermission(KULEvent event) throws Exception {
        String result = "Private (repository admins only)";
        String itemLicense = event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "rights",
                "license", Item.ANY);
        String bitstreamPermission = null;
        Deque<String> bitstreamPermissions = getPreviousBitstreamPermissionText(event);
        if (bitstreamPermissions.size() > 0) {
            bitstreamPermission = bitstreamPermissions.pop();
        }
        if (itemLicense == null || itemLicense.isBlank()) {
            result = "Public";
        } else if (itemLicense != null && itemLicense.equalsIgnoreCase("KU Leuven sets the embargo")) {
            result = "Unknown Embargo";
        } else if (bitstreamPermission != null && bitstreamPermission.equalsIgnoreCase("embargo") && itemLicense
                .equalsIgnoreCase("Public access (as soon as legally possible, verified by the OA Support Desk)")) {
            result = "Public (after an embargo of 12 months)";
        } else if (bitstreamPermission != null && bitstreamPermission.equalsIgnoreCase("public") && itemLicense
                .equalsIgnoreCase("Public access (as soon as legally possible, verified by the OA Support Desk)")) {
            result = "Public";
        } else if (bitstreamPermission != null && bitstreamPermission.equalsIgnoreCase("intranet")
                && itemLicense.equalsIgnoreCase("Permanent embargo (intranet only)")) {
            result = "Permanent embargo (intranet only)";
        }
        return result;
    }

}