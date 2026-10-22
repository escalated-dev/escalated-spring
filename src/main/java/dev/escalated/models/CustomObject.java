package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * An admin-defined record type, as escalated-laravel's {@code CustomObject}: a name, a unique slug
 * and a field schema ({@code [{name, type, required?, options?}]}, stored as JSON).
 */
@Entity
@Table(name = "escalated_custom_objects")
public class CustomObject extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "fields_schema", nullable = false, columnDefinition = "TEXT")
    private String fieldsSchema = "[]";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getFieldsSchema() {
        return fieldsSchema;
    }

    public void setFieldsSchema(String fieldsSchema) {
        this.fieldsSchema = fieldsSchema;
    }
}
