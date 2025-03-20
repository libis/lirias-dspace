package org.dspace.kul.consumer;

import org.apache.logging.log4j.Logger;

public class Mailing {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Mailing.class);

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
}
