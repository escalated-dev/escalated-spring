package dev.escalated.services;

import dev.escalated.models.Department;
import dev.escalated.repositories.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Department> findAll() {
        return departmentRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public Department findById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));
    }

    @Transactional
    public Department create(String name, String description) {
        Department department = new Department();
        department.setName(name);
        department.setDescription(description);
        return departmentRepository.save(department);
    }

    @Transactional
    public Department update(Long id, String name, String description, boolean active) {
        Department department = findById(id);
        department.setName(name);
        department.setDescription(description);
        department.setActive(active);
        return departmentRepository.save(department);
    }

    @Transactional
    public void delete(Long id) {
        departmentRepository.deleteById(id);
    }
}
