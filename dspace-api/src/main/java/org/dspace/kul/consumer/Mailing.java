package org.dspace.kul.consumer;

import org.dspace.content.Item;

import java.util.HashSet;
import java.util.Set;
import java.util.List;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.Logger;
import org.dspace.content.MetadataValue;

public class Mailing {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Mailing.class);

    private final Services services = new Services();

    private static final String senderEmail = "";
    private static final String elementsCacheAPIUrl = "";
    private static final String elementsCacheUsername = "";
    private static final String elementsCachePassword = "";

    public static void applyTo(final KULEvent event) {
        // TODO
        if (event.isPhd()) {
            switch (event.getConsumeCaseEnum()) {
                case REDEPOSIT:
                case ADD_VIA_UI:
                case DEPOSIT:
                case REMOVE:
                case EDIT_PERMISSION: {
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


    private Set<String> getContributorEmails(Item item) throws Exception{
        Set<String> uNumbers = new HashSet<>();
        for (String contributorType : List.of("author", "supervisor", "cosupervisor")) {
            services.itemService.getMetadata(item, "dc", "contributor", contributorType, Item.ANY).stream()
                    .map(m -> uNumbers.add(
                            getUnumberFromMetadata(m)));
        }
        Set<String> emails = new HashSet<>();
        for (String uNumber : uNumbers) {
            emails.add(getEmailAdress(uNumber));
        }
        System.out.println(emails);
        return emails;
    }

    private String getUnumberFromMetadata(MetadataValue metadataValue) {
        String[] splitMetadata = metadataValue.getValue().toString().split("\\;");
        if (splitMetadata != null && splitMetadata.length > 1) {
            return splitMetadata[1].trim();
        } else {
            return null;
        }
    }

    private String getEmailAdress(String uNumber) throws Exception {
        String result;
        HttpGet request = new HttpGet(
                elementsCacheAPIUrl + "email/user/" + uNumber);
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(elementsCacheUsername, elementsCachePassword));
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultCredentialsProvider(provider)
                .build();

        String responseText = httpClient.execute(request).toString();
        result = responseText.replaceAll("<[^>]*>", "");
        return result;
    }

}