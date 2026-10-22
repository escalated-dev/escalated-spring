package dev.escalated.ui;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.CustomTicketStatus;
import dev.escalated.repositories.CustomTicketStatusRepository;
import dev.escalated.services.TicketStatuses;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The custom ticket status pages' props and writes, as escalated-laravel's
 * {@code Admin\StatusController}: {@code Escalated/Admin/Statuses/Index} and {@code Form}.
 *
 * <p>System rows (one per built-in status) keep their slug and category and cannot be deleted.
 * Changing a custom status's category does not move tickets that already have it; the next
 * assignment uses the new category.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AdminStatusUiPages {

    private final CustomTicketStatusRepository statuses;
    private final TicketStatuses ticketStatuses;

    public AdminStatusUiPages(CustomTicketStatusRepository statuses, TicketStatuses ticketStatuses) {
        this.statuses = statuses;
        this.ticketStatuses = ticketStatuses;
    }

    /** Every status, ordered by category then position, with the five categories. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> index() {
        // A host that builds its schema without the migrations still gets the built-in rows.
        ticketStatuses.ensureSystemStatuses();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CustomTicketStatus status : statuses.findAllByOrderByCategoryAscPositionAscIdAsc()) {
            rows.add(status(status));
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("statuses", rows);
        props.put("categories", TicketStatuses.CATEGORIES);
        return props;
    }

    /** The form's props: {@code categories}, and {@code status} when editing. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> form(Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (id != null) {
            props.put("status", status(find(id)));
        }
        props.put("categories", TicketStatuses.CATEGORIES);
        return props;
    }

    /** Validates as Laravel's {@code StatusController} and saves. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void save(Long id, UiInput input) {
        String label = input.requiredString("label", 255);
        String category = input.string("category");
        if (category == null) {
            throw UiInput.invalid("category", "The category field is required.");
        }
        if (!TicketStatuses.CATEGORIES.contains(category)) {
            throw UiInput.invalid("category", "The selected category is invalid.");
        }
        String color = input.requiredString("color", 20);
        Object description = input.raw("description");
        if (description != null && !(description instanceof String)) {
            throw UiInput.invalid("description", "The description field must be a string.");
        }
        Long position = input.longValue("position");
        if (position != null && (position < Integer.MIN_VALUE || position > Integer.MAX_VALUE)) {
            throw UiInput.invalid("position", "The position field must be an integer.");
        }
        Object isDefault = input.raw("is_default");
        if (isDefault != null && !(isDefault instanceof Boolean)
                && !List.of("0", "1", "true", "false").contains(String.valueOf(isDefault))) {
            throw UiInput.invalid("is_default", "The is default field must be true or false.");
        }

        CustomTicketStatus status = id == null ? new CustomTicketStatus() : find(id);
        if (status.isSystem() && !status.getCategory().equals(category)) {
            throw UiInput.invalid("category", "The category of a built-in status cannot be changed.");
        }
        if (id == null) {
            status.setSlug(ticketStatuses.uniqueSlug(label));
        }
        status.setLabel(label);
        status.setCategory(category);
        status.setColor(color);
        status.setDescription(input.string("description"));
        if (position != null) {
            status.setPosition(position.intValue());
        }
        boolean makeDefault = input.bool("is_default", id != null && status.isDefaultStatus());
        if (makeDefault) {
            for (CustomTicketStatus other : statuses.findByCategoryAndDefaultStatusTrue(category)) {
                if (!other.getId().equals(status.getId())) {
                    other.setDefaultStatus(false);
                    statuses.save(other);
                }
            }
        }
        status.setDefaultStatus(makeDefault);
        statuses.save(status);
    }

    /**
     * Deletes a custom status. Its tickets keep their built-in status and lose the pointer.
     * False, deleting nothing, for a system row.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public boolean delete(Long id) {
        CustomTicketStatus status = find(id);
        if (status.isSystem()) {
            return false;
        }
        statuses.detachFromTickets(id);
        statuses.deleteById(id);
        return true;
    }

    private static Map<String, Object> status(CustomTicketStatus status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", status.getId());
        map.put("label", status.getLabel());
        map.put("slug", status.getSlug());
        map.put("category", status.getCategory());
        map.put("color", status.getColor());
        map.put("description", status.getDescription());
        map.put("position", status.getPosition());
        map.put("is_default", status.isDefaultStatus());
        map.put("is_system", status.isSystem());
        map.put("created_at", UiPresenter.iso(status.getCreatedAt()));
        map.put("updated_at", UiPresenter.iso(status.getUpdatedAt()));
        return map;
    }

    private CustomTicketStatus find(Long id) {
        return statuses.findById(id).orElseThrow(() -> new EntityNotFoundException("Status not found"));
    }
}
