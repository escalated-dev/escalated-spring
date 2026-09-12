package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.CustomField;
import dev.escalated.models.CustomFieldValue;
import dev.escalated.repositories.CustomFieldRepository;
import dev.escalated.repositories.CustomFieldValueRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomFieldService {

    private final CustomFieldRepository customFieldRepository;
    private final CustomFieldValueRepository customFieldValueRepository;

    public CustomFieldService(CustomFieldRepository customFieldRepository,
                              CustomFieldValueRepository customFieldValueRepository) {
        this.customFieldRepository = customFieldRepository;
        this.customFieldValueRepository = customFieldValueRepository;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<CustomField> findActiveFields() {
        return customFieldRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public CustomField findById(Long id) {
        return customFieldRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custom field not found: " + id));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public CustomField create(String name, String fieldKey, String fieldType,
                              String description, boolean required, String options) {
        CustomField field = new CustomField();
        field.setName(name);
        field.setFieldKey(fieldKey);
        field.setFieldType(fieldType);
        field.setDescription(description);
        field.setRequired(required);
        field.setOptions(options);
        return customFieldRepository.save(field);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public CustomField update(Long id, String name, String description, boolean required, boolean active) {
        CustomField field = findById(id);
        field.setName(name);
        field.setDescription(description);
        field.setRequired(required);
        field.setActive(active);
        return customFieldRepository.save(field);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void setFieldValues(Long ticketId, Map<Long, String> fieldValues) {
        for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
            CustomFieldValue value = customFieldValueRepository
                    .findByTicketIdAndCustomFieldId(ticketId, entry.getKey())
                    .orElseGet(() -> {
                        CustomFieldValue nv = new CustomFieldValue();
                        nv.setTicket(new dev.escalated.models.Ticket());
                        nv.getTicket().setId(ticketId);
                        nv.setCustomField(new CustomField());
                        nv.getCustomField().setId(entry.getKey());
                        return nv;
                    });
            value.setValue(entry.getValue());
            customFieldValueRepository.save(value);
        }
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<CustomFieldValue> getFieldValues(Long ticketId) {
        return customFieldValueRepository.findByTicketId(ticketId);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(Long id) {
        customFieldRepository.deleteById(id);
    }
}
