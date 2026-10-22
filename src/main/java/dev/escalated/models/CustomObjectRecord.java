package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One record of a {@link CustomObject}: its {@code data}, a JSON object keyed by schema field name. */
@Entity
@Table(name = "escalated_custom_object_records")
public class CustomObjectRecord extends BaseEntity {

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String data = "{}";

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
