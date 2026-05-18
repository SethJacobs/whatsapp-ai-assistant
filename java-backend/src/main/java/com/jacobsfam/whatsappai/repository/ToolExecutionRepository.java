package com.jacobsfam.whatsappai.repository;

import com.jacobsfam.whatsappai.model.entity.ToolExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ToolExecutionRepository extends JpaRepository<ToolExecution, String> {

    List<ToolExecution> findBySessionIdOrderByExecutedAtDesc(String sessionId);

    List<ToolExecution> findByToolNameOrderByExecutedAtDesc(String toolName);

    List<ToolExecution> findByExecutedAtBefore(LocalDateTime cutoff);
}
