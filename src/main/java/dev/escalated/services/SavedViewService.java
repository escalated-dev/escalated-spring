package dev.escalated.services;

import dev.escalated.models.SavedView;
import dev.escalated.repositories.SavedViewRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedViewService {

    private final SavedViewRepository savedViewRepository;

    public SavedViewService(SavedViewRepository savedViewRepository) {
        this.savedViewRepository = savedViewRepository;
    }

    @Transactional(readOnly = true)
    public List<SavedView> findAccessibleByAgent(Long agentId) {
        return savedViewRepository.findAccessibleByAgent(agentId);
    }

    @Transactional(readOnly = true)
    public SavedView findById(Long id) {
        return savedViewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Saved view not found: " + id));
    }

    @Transactional
    public SavedView create(String name, String filters, String sortBy, String sortDirection,
                            String columns, boolean shared, Long agentId) {
        SavedView view = new SavedView();
        view.setName(name);
        view.setFilters(filters);
        view.setSortBy(sortBy);
        view.setSortDirection(sortDirection);
        view.setColumns(columns);
        view.setShared(shared);
        if (agentId != null) {
            view.setAgent(new dev.escalated.models.AgentProfile());
            view.getAgent().setId(agentId);
        }
        return savedViewRepository.save(view);
    }

    @Transactional
    public SavedView update(Long id, String name, String filters, String sortBy,
                            String sortDirection, String columns, boolean shared) {
        SavedView view = findById(id);
        view.setName(name);
        view.setFilters(filters);
        view.setSortBy(sortBy);
        view.setSortDirection(sortDirection);
        view.setColumns(columns);
        view.setShared(shared);
        return savedViewRepository.save(view);
    }

    @Transactional
    public void delete(Long id) {
        savedViewRepository.deleteById(id);
    }
}
