package com.jacobsfam.whatsappai.repository;

import com.jacobsfam.whatsappai.model.entity.AllowedContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AllowedContactRepository extends JpaRepository<AllowedContact, String> {

    Optional<AllowedContact> findByPhoneNumber(String phoneNumber);

    List<AllowedContact> findByEnabled(boolean enabled);
}
