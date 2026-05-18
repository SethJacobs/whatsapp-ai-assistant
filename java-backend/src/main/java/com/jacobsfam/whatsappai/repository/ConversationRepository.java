package com.jacobsfam.whatsappai.repository;

import com.jacobsfam.whatsappai.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    @Query("SELECT c FROM Conversation c WHERE c.phoneNumber = :phoneNumber ORDER BY c.lastMessageAt DESC")
    List<Conversation> findByPhoneNumberOrderByLastMessageAtDesc(String phoneNumber);

    Optional<Conversation> findFirstByPhoneNumberOrderByLastMessageAtDesc(String phoneNumber);

    List<Conversation> findByLastMessageAtBefore(LocalDateTime cutoff);
}
