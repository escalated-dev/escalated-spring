package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.CannedResponse;
import dev.escalated.repositories.CannedResponseRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CannedResponseService {

    private final CannedResponseRepository cannedResponseRepository;

    public CannedResponseService(CannedResponseRepository cannedResponseRepository) {
        this.cannedResponseRepository = cannedResponseRepository;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<CannedResponse> findAll() {
        return cannedResponseRepository.findAll();
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<CannedResponse> findAccessibleByAgent(Long agentId) {
        return cannedResponseRepository.findAccessibleByAgent(agentId);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public CannedResponse findById(Long id) {
        return cannedResponseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Canned response not found: " + id));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public CannedResponse create(String title, String content, String shortcut, String category,
                                 boolean shared, Long agentId) {
        CannedResponse cr = new CannedResponse();
        cr.setTitle(title);
        cr.setContent(content);
        cr.setShortcut(shortcut);
        cr.setCategory(category);
        cr.setShared(shared);
        cr.setCreatedByAgentId(agentId);
        return cannedResponseRepository.save(cr);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public CannedResponse update(Long id, String title, String content, String shortcut, String category) {
        CannedResponse cr = findById(id);
        cr.setTitle(title);
        cr.setContent(content);
        cr.setShortcut(shortcut);
        cr.setCategory(category);
        return cannedResponseRepository.save(cr);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(Long id) {
        cannedResponseRepository.deleteById(id);
    }
}
