package dev.escalated.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores a flat metadata map as JSON text, so the column is plain TEXT on
 * every database Escalated supports.
 */
@Converter
public class JsonMetadataConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Metadata cannot be stored as JSON", ex);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(column, MAP);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored metadata is not JSON", ex);
        }
    }
}
