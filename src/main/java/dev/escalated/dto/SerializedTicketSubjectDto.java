package dev.escalated.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Serialized ticket subject for the ticket detail response and attach API.
 */
public class SerializedTicketSubjectDto {

    private final String type;
    private final String id;
    private final String role;
    private final String title;
    private final String subtitle;
    private final String url;
    private final String color;
    private final String icon;
    private final boolean missing;

    public SerializedTicketSubjectDto(String type, String id, String role, String title,
                                      String subtitle, String url, String color, String icon,
                                      boolean missing) {
        this.type = type;
        this.id = id;
        this.role = role;
        this.title = title;
        this.subtitle = subtitle;
        this.url = url;
        this.color = color;
        this.icon = icon;
        this.missing = missing;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getUrl() {
        return url;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    @JsonProperty("missing")
    public boolean isMissing() {
        return missing;
    }
}
