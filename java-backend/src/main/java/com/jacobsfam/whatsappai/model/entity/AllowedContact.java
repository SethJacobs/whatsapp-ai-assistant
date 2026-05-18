package com.jacobsfam.whatsappai.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "allowed_contacts")
@Data
@NoArgsConstructor
public class AllowedContact {

    @Id
    private String phoneNumber; // E.164 format: +12125551234

    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "contact_permissions",
            joinColumns = @JoinColumn(name = "phone_number"))
    @Column(name = "permission")
    private Set<String> grantedPermissions = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    public AllowedContact(String phoneNumber, String displayName) {
        this.phoneNumber = phoneNumber;
        this.displayName = displayName;
    }

    public void grantPermission(String permission) {
        this.grantedPermissions.add(permission);
    }

    public void grantAllPermissions(String category) {
        this.grantedPermissions.add(category + ":*");
    }
}
