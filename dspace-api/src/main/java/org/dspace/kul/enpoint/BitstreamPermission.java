package org.dspace.kul.enpoint;

import java.io.Serializable;

public class BitstreamPermission implements Serializable {

    public class ResponseDate {
        public Number year;
        public Number month;
        public Number day;

    }

    public static final String NAME = "bitstreampermission";
    private String permission;
    private ResponseDate embargoEndDate;

    public BitstreamPermission() {
        this.embargoEndDate = new ResponseDate();
    }

    public String getPermission() {
        return permission;
    }

    public ResponseDate getEmbargoEndDate() {
        return embargoEndDate;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public void setEmbargoEndDate(Number day, Number month, Number year) {
        this.embargoEndDate.day = day;
        this.embargoEndDate.month = month;
        this.embargoEndDate.year = year;
    }

}
