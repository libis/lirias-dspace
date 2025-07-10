package org.dspace.kul.consumer;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.core.Constants;
import java.util.Deque;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Date;
import java.util.Locale;

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
                    System.out.println("PhD emails: Redeposit case");
                    try {
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
                        Email email = readEmailTemplate(event.getCtx().getCurrentLocale(), "redeposit");
                        if (email == null) {
                            break;
                        }
                        emailRecipients.forEach(r -> email.addRecipient(r));
                        email.setReplyTo(senderEmail);
                        email.addArgument(getItemDspaceUrl(event.getItem()));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc",
                                        "contributor",
                                        "author", Item.ANY));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                        null, Item.ANY));
                        email.addArgument(getItemPermission(event));
                        email.addArgument(getGroupStartDate(event, event.getBitstream(), "anonymous"));
                        email.sendHTML();
                        break;
                    } catch (Exception e) {
                        System.err.println("PhD emails: " + e);
                        break;
                    }
                }
                case ADD_VIA_UI: {
                    System.out.println("PhD emails: Add via UI case");
                    try {
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
                        Email email = readEmailTemplate(event.getCtx().getCurrentLocale(), "add_bitstream_via_ui");
                        if (email == null) {
                            break;
                        }
                        emailRecipients.forEach(r -> email.addRecipient(r));
                        email.setReplyTo(senderEmail);
                        email.addArgument(getItemDspaceUrl(event.getItem()));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc",
                                        "contributor",
                                        "author", Item.ANY));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                        null, Item.ANY));

                        Deque<String> permissionHistory = getPreviousBitstreamPermissionText(event,
                                event.getBitstream());
                        if (permissionHistory.size() > 0
                                && !"redeposit".equalsIgnoreCase(permissionHistory.peek())) {
                            String permission = permissionHistory.pop();
                            email.addArgument(expandPermissionString(permission));
                            if (permission.toLowerCase().contains("embargo")) {
                                email.addArgument(getGroupStartDate(event, event.getBitstream(), "anonymous"));
                            } else {
                                email.addArgument(getGroupStartDate(event, event.getBitstream(), ""));
                            }

                        } else {
                            email.addArgument(null);
                            email.addArgument(getGroupStartDate(event, event.getBitstream(), ""));
                        }
                        email.sendHTML();
                        break;
                    } catch (Exception e) {
                        System.err.println("PhD emails: " + e);
                        break;
                    }
                }
                case DEPOSIT: {
                    System.out.println("PhD emails: Deposit case");
                    try {
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
                        Email email = readEmailTemplate(event.getCtx().getCurrentLocale(), "deposit");
                        if (email == null) {
                            break;
                        }
                        emailRecipients.forEach(r -> email.addRecipient(r));
                        email.setReplyTo(senderEmail);
                        email.addArgument(getItemDspaceUrl(event.getItem()));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc",
                                        "contributor",
                                        "author", Item.ANY));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                        null, Item.ANY));
                        email.addArgument(getItemPermission(event));
                        email.addArgument(getGroupStartDate(event, event.getBitstreams().get(0), "anonymous"));
                        email.sendHTML();
                        break;
                    } catch (Exception e) {
                        System.err.println("PhD emails: " + e);
                        break;
                    }
                }
                case REMOVE: {
                    System.out.println("PhD emails: Remove case");
                    try {
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
                        Email email = readEmailTemplate(event.getCtx().getCurrentLocale(), "remove_bitstream");
                        if (email == null) {
                            break;
                        }
                        emailRecipients.forEach(r -> email.addRecipient(r));
                        email.setReplyTo(senderEmail);

                        email.addArgument(getItemDspaceUrl(event.getItem()));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc",
                                        "contributor",
                                        "author", Item.ANY));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                        null, Item.ANY));
                        email.sendHTML();
                        break;
                    } catch (Exception e) {
                        System.err.println("PhD emails: " + e);
                        break;
                    }

                }
                case EDIT_PERMISSION: {
                    System.out.println("PhD emails: Edit permission case");
                    System.out.println(event.getCtx().getCurrentUser().getGroups().toString());
                    if (!services.authorizeService.isAdmin(event.getCtx())) {
                        break;
                    }
                    try {
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
                        Email email = readEmailTemplate(event.getCtx().getCurrentLocale(), "edit_bitstream_permission");
                        if (email == null) {
                            break;
                        }
                        emailRecipients.forEach(r -> email.addRecipient(r));
                        email.setReplyTo(senderEmail);

                        email.addArgument(getItemDspaceUrl(event.getItem()));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc",
                                        "contributor",
                                        "author", Item.ANY));
                        email.addArgument(
                                event.getServices().itemService.getMetadataFirstValue(event.getItem(), "dc", "title",
                                        null, Item.ANY));
                        Deque<String> permissionHistory = getPreviousBitstreamPermissionText(event,
                                event.getBitstream());
                        if (permissionHistory.size() > 0
                                && !"redeposit".equalsIgnoreCase(permissionHistory.peek())) {
                            final String previousPermission = permissionHistory.pop();
                            email.addArgument(expandPermissionString(previousPermission));
                        } else {
                            System.out.println("No current permission found for " + event.getBitstream().getName());
                            break;
                            // do not send email when no current permission found
                        }
                        if (permissionHistory.size() > 0
                                && !"redeposit".equalsIgnoreCase(permissionHistory.peek())) {
                            final String currentPermission = permissionHistory.pop();
                            email.addArgument(expandPermissionString(currentPermission));
                            if (currentPermission.toLowerCase().contains("embargo")) {
                                email.addArgument(getGroupStartDate(event, event.getBitstream(), "anonymous"));
                            } else {
                                email.addArgument(getGroupStartDate(event, event.getBitstream(), ""));
                            }
                            
                        } else {
                            System.out.println("No previous permission found for " + event.getBitstream().getName());
                            break;
                            // do not send email when there is no previous permission
                            // because it means permissions are being set for the first time
                        }
                        email.addArgument(getGroupStartDate(event, event.getBitstream(), "anonymous"));
                        email.sendHTML();
                        break;
                    } catch (Exception e) {
                        System.err.println("PhD emails: " + e);
                        break;
                    }

                }
                default: {
                    log.error("phd mailing for this event not implemented: " + event.getConsumeCaseEnum().name());
                    break;
                }

            }
        }
        switch (event.getConsumeCaseEnum()) {
            case REDEPOSIT: {
                System.out.println("OA emails: Redeposit case");
                try {
                    if (event.getBitstream() != null) {
                        sendEmailRedepositOA(event, event.getBitstream());
                    } else {
                        for (Bitstream bitstream : event.getBitstreams()) {
                            sendEmailRedepositOA(event, bitstream);
                        }
                    }
                    break;
                } catch (Exception e) {
                    System.err.println("OA emails: " + e);
                    break;
                }

            }
            case ADD_VIA_UI: {
                System.out.println("OA emails: Add from UI case (no emails sent)");
                break;
            }
            case DEPOSIT: {
                System.out.println("OA emails: Deposit case");
                try {
                    if (event.getBitstream() != null) {
                        sendEmailDepositOA(event, event.getBitstream());
                    } else {
                        for (Bitstream bitstream : event.getBitstreams()) {
                            sendEmailDepositOA(event, bitstream);
                        }
                    }
                    break;
                } catch (Exception e) {
                    System.err.println("OA emails: " + e);
                    break;
                }

            }
            case REMOVE: {
                break;
            }
            case EDIT_PERMISSION: {
                break;
            }
            default: {
                log.error("default mailing for this event not implemented: " + event.getConsumeCaseEnum().name());
                break;
            }
        }
    }

    private static Email readEmailTemplate(Locale locale, String filename) throws Exception {
        Email email;
        try {
            email = Email.getEmail(I18nUtil.getEmailFilename(locale, filename));
        } catch (Exception e) {
            log.error(MessageFormat.format("Error loading email template: {0}", filename));
            log.error(e);
            email = null;
        }
        return email;
    }

    private static void sendEmailAddViaUI(KULEvent event, Bitstream bitstream) throws Exception {
        final HashMap<String, String> itemMetadata = getItemMetadataMap(event, event.getItem(),
                event.getBitstream());
        if (senderEmail == null) {
            System.out.println("No email sender set.");
            return;
        }
        Email email = readEmailTemplate(event.getCtx().getCurrentLocale(), "add_bitstream_via_ui_oa");
        if (email == null) {
            return;
        }
        email.addRecipient(senderEmail);
        email.setReplyTo(senderEmail);
        String[] basicMetadataFields = {
                "Title",
                "Limo URL",
                "DSpace URL",
                "Author",
                "Supervisor",
                "Cosupervisor",
                "First Depositor",
                "Filename",
                "BitstreamID",
                "Accessibility" };
        String[] metadataForOAFields = {
                "Item Access License",
                "Item Publisher License",
                "Bitstream Version",
                "Date Issued",
                "Type",
                "Item Status",
                "Naam Tijdschrift",
                "Naam Uitgever",
                "DOI",
                "Bitstream File Extension" };
        String[] logbookFields = { "Item Publisher License", "Bitstream Version", "Date Issued", "Type" };
        String basicMetadata = formatMetadataAsHTML(itemMetadata, basicMetadataFields);
        String metadataForOA = formatMetadataAsHTML(itemMetadata, metadataForOAFields);
        String logbookTable = formatMetadataAsTable(itemMetadata, logbookFields, false, "{border: 1px;}");
        email.addArgument(itemMetadata.get("Title"));
        email.addArgument(itemMetadata.get("Limo URL"));
        email.addArgument(itemMetadata.get("DSpace URL"));
        email.addArgument(basicMetadata);
        email.addArgument(metadataForOA);
        email.addArgument(logbookTable);
        email.sendHTML();

    }

    private static void sendEmailDepositOA(KULEvent event, Bitstream bitstream) throws Exception {
        final HashMap<String, String> itemMetadata = getItemMetadataMap(event, event.getItem(), bitstream);
        if (senderEmail == null) {
            System.out.println("No email sender set.");
            return;
        }
        Email email = readEmailTemplate(event.getCtx().getCurrentLocale(), "deposit_oa");
        if (email == null) {
            return;
        }
        email.addRecipient(senderEmail);
        email.setReplyTo(senderEmail);
        String[] basicMetadataFields = {
                "Title",
                "Limo URL",
                "DSpace URL",
                "Author",
                "Supervisor",
                "Cosupervisor",
                "First Depositor",
                "Filename",
                "BitstreamID",
                "Accessibility" };
        String[] metadataForOAFields = {
                "Item Access License",
                "Item Publisher License",
                "Bitstream Version",
                "Date Issued",
                "Type",
                "Item Status",
                "Naam Tijdschrift",
                "Naam Uitgever",
                "DOI",
                "Bitstream File Extension" };
        String[] logbookFields = { "Item Publisher License", "Bitstream Version", "Date Issued", "Type" };
        String basicMetadata = formatMetadataAsHTML(itemMetadata, basicMetadataFields);
        String metadataForOA = formatMetadataAsHTML(itemMetadata, metadataForOAFields);
        String logbookTable = formatMetadataAsTable(itemMetadata, logbookFields, false, "{border: 1px;}");
        email.addArgument(itemMetadata.get("Title"));
        email.addArgument(itemMetadata.get("Limo URL"));
        email.addArgument(itemMetadata.get("DSpace URL"));
        email.addArgument(basicMetadata);
        email.addArgument(metadataForOA);
        email.addArgument(logbookTable);
        email.sendHTML();
    }

    private static void sendEmailRedepositOA(KULEvent event, Bitstream bitstream) throws Exception {

        final HashMap<String, String> itemMetadata = getItemMetadataMap(event, event.getItem(), bitstream);
        if (senderEmail == null) {
            System.out.println("No email sender set.");
            return;
        }
        Email email = readEmailTemplate(event.getCtx().getCurrentLocale(), "redeposit_oa");
        if (email == null) {
            return;
        }
        email.addRecipient(senderEmail);
        email.setReplyTo(senderEmail);
        String[] basicMetadataFields = {
                "Title",
                "Limo URL",
                "DSpace URL",
                "Author",
                "Supervisor",
                "Cosupervisor",
                "First Depositor",
                "Filename",
                "BitstreamID",
                "Accessibility" };
        String[] metadataForOAFields = {
                "Item Access License",
                "Item Publisher License",
                "Bitstream Version",
                "Date Issued",
                "Type",
                "Item Status",
                "Naam Tijdschrift",
                "Naam Uitgever",
                "DOI",
                "Bitstream File Extension" };
        String[] logbookFields = { "Item Publisher License", "Bitstream Version", "Date Issued", "Type" };
        String basicMetadata = formatMetadataAsHTML(itemMetadata, basicMetadataFields);
        String metadataForOA = formatMetadataAsHTML(itemMetadata, metadataForOAFields);
        String logbookTable = formatMetadataAsTable(itemMetadata, logbookFields, false, "{border: 1px;}");

        email.addArgument(itemMetadata.get("Title"));
        email.addArgument(itemMetadata.get("Limo URL"));
        email.addArgument(itemMetadata.get("DSpace URL"));
        email.addArgument(basicMetadata);
        email.addArgument(metadataForOA);
        email.addArgument(logbookTable);
        email.sendHTML();

    }

    private static String getItemDspaceUrl(Item item) {
        return MessageFormat.format("{0}/handle/{1}", dspaceUrl, item.getHandle());
    }

    private static String getItemLimoUrl(Item item) {
        return MessageFormat.format("{0}/handle/{1}", limoUrl, item.getHandle());
    }

    private static ArrayList<String> getFormattedContributorInfo(Item item, String contributorType) throws Exception {
        HashMap<String, String> contributors = getContributorEmails(item, contributorType);
        ArrayList<String> result = new ArrayList<String>();
        for (String contributor : contributors.keySet()) {
            result.add(MessageFormat.format("{0} ({1})", contributor, contributors.get(contributor)));
        }
        return result;
    }

    private static HashMap<String, String> getContributorEmails(Item item, String contributorType) throws Exception {
        HashMap<String, String> result = new HashMap<String, String>();
        services.itemService.getMetadata(item, "dc", "contributor", contributorType, Item.ANY).stream().forEach(
                m -> {
                    final String uNumber = getUnumberFromMetadata(m);
                    String emailAddress = "";
                    try {
                        emailAddress = getEmailAdress(uNumber);
                    } catch (Exception e) {
                        System.out.println("Could not retrieve email address for " + uNumber + ": " + e.toString());

                    }
                    result.put(m.getValue().toString(), emailAddress);
                });
        return result;
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

    private static String getItemMetadataOrEmptyString(Item item, String schema, String element, String qualifier,
            String language) {
        String metadata = services.itemService.getMetadataFirstValue(item, schema, element, qualifier, Item.ANY);
        if (metadata == null) {
            metadata = "";
        }
        return metadata;

    }

    private static String getBitstreamMetadataOrEmptyString(Bitstream bs, String schema, String element,
            String qualifier,
            String language) {
        String metadata = services.bitstreamService.getMetadataFirstValue(bs, schema, element, qualifier, Item.ANY);
        if (metadata == null) {
            metadata = "";
        }
        return metadata;

    }

    private static String formatMetadataAsTable(HashMap<String, String> itemMetadata, String[] fieldsToUse,
            boolean vertical, String border) {
        String result = "";
        if (!border.isBlank()) {
            result += MessageFormat.format("<table border=\"{0}\"}>", border);
        } else {
            result += "<table>";
        }
        if (vertical == true) {
            for (String fieldName : fieldsToUse) {
                result += "<tr>";
                for (int colnum = 0; colnum < 2; colnum++) {
                    if (colnum == 0) {
                        result += MessageFormat.format("<td><b>{0}</b></td>\n", fieldName);
                    } else {
                        if (itemMetadata.containsKey(fieldName)) {
                            result += MessageFormat.format("<td>{0}</td>\n", itemMetadata.get(fieldName));
                        } else {
                            result += "<td> </td>\n";
                        }
                    }
                }
                result += "</tr>\n";
            }

        } else {
            for (int rowNum = 0; rowNum < 2; rowNum++) {
                result += "<tr>";
                for (String fieldName : fieldsToUse) {
                    if (rowNum == 0) {
                        result += MessageFormat.format("<th>{0}</th>\n", fieldName);
                    } else {
                        if (itemMetadata.containsKey(fieldName)) {
                            result += MessageFormat.format("<td>{0}</td>\n", itemMetadata.get(fieldName));
                        } else {
                            result += "<td> </td>\n";
                        }
                    }
                }
                result += "</tr>\n";
            }
            result += "<tr>\n";
        }
        result += "</table>";
        return result;

    }

    private static String formatMetadataAsHTML(HashMap<String, String> itemMetadata, String[] fieldsToUse) {
        String result = "";

        for (String fieldName : fieldsToUse) {

            for (int colnum = 0; colnum < 2; colnum++) {
                if (colnum == 0) {
                    result += MessageFormat.format("<b>{0}: </b>", fieldName);
                } else {
                    if (itemMetadata.containsKey(fieldName)) {
                        if (!(itemMetadata.get(fieldName) instanceof String)
                                || !itemMetadata.get(fieldName).startsWith("https://")) {
                            result += MessageFormat.format("{0} </br> \n", itemMetadata.get(fieldName));
                        } else {
                            result += MessageFormat.format("<a href=\"{0}\"> {0} </a> </br> \n",
                                    itemMetadata.get(fieldName));
                        }
                    } else {
                        result += "</br> \n";
                    }
                }
            }
        }

        return result;

    }

    private static Bitstream getPreviousBitstream(final KULEvent event) {
        final List<Bitstream> bitstreams = event.getBitstream() != null ? List.of(event.getBitstream())
                : event.getBitstreams();
        final List<Bundle> bundles = event.getItem().getBundles().stream()
                .filter(x -> x.getName().toString().equals("ORIGINAL")).collect(Collectors.toList());
        if (bundles.size() > 0) {
            final List<Bitstream> originalBitstreams = bundles.get(0).getBitstreams().stream()
                    .filter(x -> bitstreams.stream().noneMatch(y -> x.getID().equals(y.getID())))
                    .collect(Collectors.toList());
            if (originalBitstreams.size() > 0) {
                return originalBitstreams.get(originalBitstreams.size() - 1);
            }
        }
        return null;
    }

    private static final HashMap<String, String> getItemMetadataMap(KULEvent event, Item item, Bitstream bitstream)
            throws Exception {
        HashMap result = new HashMap<String, String>();

        final String comment = getItemMetadataOrEmptyString(item, "dc", "deposit", "comment", Item.ANY);
        result.put("Comment", comment);
        String[] splitComment = comment.split("---");
        String firstDepositorValue = "";
        String publisherLicenseValue = "";
        String licenseValue = "";
        for (String commentPart : splitComment) {
            if (commentPart.startsWith("LICENCE:")) {
                licenseValue = commentPart.replace("LICENCE:", "").strip();
            }
            if (commentPart.startsWith("PUBLISHER LICENCE:")) {
                publisherLicenseValue = commentPart.replace("PUBLISHER LICENCE:", "").strip();
            }
            if (commentPart.startsWith("FIRST DEPOSITOR:")) {
                List<String> firstDepositorName = new ArrayList<String>();
                String firstDepositorEmail = null;
                for (String firstDepositorPart : commentPart.replace("FIRST DEPOSITOR:", "").strip().split(" ")) {
                    if (!firstDepositorPart.contains("@")) {
                        firstDepositorName.add(firstDepositorPart);
                    } else {
                        firstDepositorEmail = firstDepositorPart;
                    }
                }
                String firstDepositor = String.join(" ", firstDepositorName);
                if (firstDepositorEmail != null) {
                    firstDepositor += " (" + firstDepositorEmail + ")";
                }
                firstDepositorValue = firstDepositor;
            }
        }
        result.put("First Depositor", firstDepositorValue);
        result.put("Item Publisher License", publisherLicenseValue);
        result.put("Item Access License", licenseValue);
        final String accessLicense = getItemMetadataOrEmptyString(item, "dc", "rights", "license", Item.ANY);
        if (result.get("Item Access License") == null && accessLicense != null) {
            result.put("Item Access License", accessLicense);
        } // Use dc.rights.license if LICENCE not found in comment

        result.put("Bitstream License",
                getBitstreamMetadataOrEmptyString(bitstream, "dc", "rights", "license", Item.ANY));
        result.put("Bitstream Version",
                getBitstreamMetadataOrEmptyString(bitstream, "dc", "description", null, Item.ANY));
        result.put("Accessibility", "");
        Deque<String> permissionHistory = getPreviousBitstreamPermissionText(event, bitstream);
        if (null != permissionHistory && permissionHistory.size() > 0) {
            if (!"redeposit".equalsIgnoreCase(permissionHistory.peek())) {
                result.put("Accessibility", permissionHistory.pop());
            } else {

                if (!event.isPhd()) {
                    result.put("Accessibility", "INTRANET");
                } else {
                    Bitstream previousBitstream = getPreviousBitstream(event);
                    if (previousBitstream != null) {
                        Deque<String> previousBitstreamPermission = getPreviousBitstreamPermissionText(event,
                                previousBitstream);
                        if (null != previousBitstreamPermission && previousBitstreamPermission.size() > 0) {
                            if (!"redeposit".equalsIgnoreCase(previousBitstreamPermission.peek())) {
                                result.put("Accessibility", previousBitstreamPermission.pop());
                            }
                        }
                    }
                }

            }
        }

        result.put("Item Description", getItemMetadataOrEmptyString(item, "dc", "description", null, Item.ANY));
        result.put("Date Issued", getItemMetadataOrEmptyString(item, "dc", "date", "issued", Item.ANY));
        result.put("Type", getItemMetadataOrEmptyString(item, "dc", "type", "elements",
                Item.ANY));
        result.put("Item Status", getItemMetadataOrEmptyString(item, "dc", "status", null, Item.ANY));
        result.put("Naam Tijdschrift", getItemMetadataOrEmptyString(item, "dc", "relation", "ispartofseries",
                Item.ANY));
        result.put("Naam Uitgever", getItemMetadataOrEmptyString(item, "dc", "publisher", null, Item.ANY));
        String doi = getItemMetadataOrEmptyString(item, "dc", "identifier", "doi", Item.ANY);
        if (doi != "") {
            doi = "https://doi.org/" + doi;
        }
        result.put("DOI", doi);
        result.put("Title", getItemMetadataOrEmptyString(item, "dc", "title", null, Item.ANY));
        result.put("Limo URL", getItemLimoUrl(item));
        result.put("DSpace URL", getItemDspaceUrl(item));
        result.put("Author", getFormattedContributorInfo(item, "author"));
        result.put("Supervisor", getFormattedContributorInfo(item, "supervisor"));
        result.put("Cosupervisor", getFormattedContributorInfo(item, "cosupervisor"));
        result.put("Bitstream File Format",
                getBitstreamMetadataOrEmptyString(bitstream, "dc", "format", null, Item.ANY));
        result.put("Bitstream File Extension", getFileExtension(bitstream.getName()));
        result.put("Filename", bitstream.getName());
        result.put("BitstreamID", bitstream.getID());

        return result;
    }

    private static String getFileExtension(String fileName) {
        final String result;
        String[] splitFilename = fileName.split("\\.");
        if (splitFilename != null && splitFilename.length > 1) {
            result = splitFilename[splitFilename.length - 1];
        } else {
            result = "";
        }
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
        if (permission.toLowerCase().contains("embargo")) {
            return "Public (after an embargo of 12 months)";
        }
        if (permission.toLowerCase().contains("public")) {
            return "Public";
        }
        if (permission.toLowerCase().contains("intranet")) {
            return "Permanent embargo (intranet only)";
        }
        if (permission.toLowerCase().contains("private")) {
            return "Private (repository admins only)";
        }
        return "";
    }

    private static Deque<String> getPreviousBitstreamPermissionText(KULEvent event, Bitstream bitstream)
            throws ParseException {
        Deque<String> permissions = new ArrayDeque<String>();
        for (final MetadataValue bitstreamMetadata : bitstream.getMetadata()) {
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
        Deque<String> bitstreamPermissions = null;
        if (event.getBitstream() != null) {
            bitstreamPermissions = getPreviousBitstreamPermissionText(event, event.getBitstream());
        } else {
            bitstreamPermissions = getPreviousBitstreamPermissionText(event, event.getBitstreams().get(0));
        }
        if (bitstreamPermissions.size() > 0 && "redeposit".equalsIgnoreCase(bitstreamPermissions.peek())) {
            return "Permanent embargo (intranet only)";
        }
        if (bitstreamPermissions.size() > 0 && !"redeposit".equalsIgnoreCase(bitstreamPermissions.peek())) {
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