package dev.escalated.contracts;

import java.util.List;
import java.util.Map;

/**
 * A source the admin Import wizard can import from, as escalated-laravel's
 * {@code Contracts\ImportAdapter}. Escalated ships none: any Spring bean implementing this
 * interface is an import source -- a host's or a plugin library's Zendesk or Freshdesk adapter,
 * say.
 *
 * <p>{@link #extract} returns records already normalized ({@code source_id}, {@code title},
 * {@code requester_source_id}, {@code tag_source_ids}, {@code ticket_source_id}, ...), as
 * Laravel's adapters do.
 */
public interface ImportAdapter {

    /** Unique slug, e.g. "zendesk". */
    String name();

    /** Human-readable name, e.g. "Zendesk". */
    String displayName();

    /** The credential form: {@code name}, {@code label}, {@code type} (text, password or url) and {@code help}. */
    List<Map<String, Object>> credentialFields();

    /** Validate credentials with a test call. An exception's message is shown to the admin. */
    boolean testConnection(Map<String, String> credentials);

    /** Importable entity types, in dependency order. */
    List<String> entityTypes();

    /** Default mappings for an entity type: source field to Escalated field. */
    Map<String, String> defaultFieldMappings(String entityType);

    /** Source fields as the Mapping screen lists them: {@code name}, {@code label}, {@code escalated_options}. */
    List<Map<String, Object>> availableSourceFields(String entityType, Map<String, String> credentials);

    /** One batch of records; a null {@link ExtractResult#cursor()} means the type is exhausted. */
    ExtractResult extract(String entityType, Map<String, String> credentials, String cursor);

    /** One batch from {@link #extract}. */
    record ExtractResult(List<Map<String, Object>> records, String cursor, Integer totalCount) {
        public boolean exhausted() {
            return cursor == null;
        }
    }
}
