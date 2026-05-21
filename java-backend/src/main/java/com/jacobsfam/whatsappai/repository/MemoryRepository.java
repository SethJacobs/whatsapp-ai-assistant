package com.jacobsfam.whatsappai.repository;

import com.jacobsfam.whatsappai.model.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, Long> {
    Optional<Memory> findByKey(String key);
    List<Memory> findByKeyContainingIgnoreCase(String keyword);
    boolean existsByKey(String key);
}
