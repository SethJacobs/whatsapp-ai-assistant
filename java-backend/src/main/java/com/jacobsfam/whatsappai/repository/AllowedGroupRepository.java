package com.jacobsfam.whatsappai.repository;

import com.jacobsfam.whatsappai.model.entity.AllowedGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AllowedGroupRepository extends JpaRepository<AllowedGroup, Long> {
    Optional<AllowedGroup> findByGroupId(String groupId);
    boolean existsByGroupId(String groupId);
}
