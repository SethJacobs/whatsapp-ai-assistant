package com.jacobsfam.whatsappai.service.security;

import com.jacobsfam.whatsappai.model.entity.AllowedContact;
import com.jacobsfam.whatsappai.repository.AllowedContactRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class SecurityService {

    @Autowired
    private AllowedContactRepository allowedContactRepository;

    @Value("${security.allowed-phones}")
    private List<String> defaultAllowedPhones;

    @PostConstruct
    public void initializeDefaultContacts() {
        // Initialize admin contacts from config
        for (String phone : defaultAllowedPhones) {
            if (!allowedContactRepository.findByPhoneNumber(phone).isPresent()) {
                AllowedContact contact = new AllowedContact(phone, "Admin");
                contact.grantAllPermissions("system");
                contact.grantAllPermissions("docker");
                contact.grantAllPermissions("gateway");
                allowedContactRepository.save(contact);
                log.info("Created admin contact: {}", phone);
            }
        }
    }

    /**
     * Check if phone number is authorized to use the assistant.
     */
    public boolean isAuthorized(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        return allowedContactRepository.findByPhoneNumber(normalized)
                .map(AllowedContact::isEnabled)
                .orElse(false);
    }

    /**
     * Check if user has all required permissions.
     */
    public boolean hasPermissions(String phoneNumber, Set<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return true; // No permissions required
        }

        String normalized = normalizePhoneNumber(phoneNumber);
        return allowedContactRepository.findByPhoneNumber(normalized)
                .map(contact -> {
                    Set<String> granted = contact.getGrantedPermissions();
                    return requiredPermissions.stream().allMatch(perm ->
                            granted.contains(perm) ||
                            granted.contains(getCategory(perm) + ":*")
                    );
                })
                .orElse(false);
    }

    /**
     * Normalize phone number to E.164 format.
     * For now, just trim whitespace. In production, use libphonenumber.
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }
        // Simple normalization - trim and ensure starts with +
        String trimmed = phone.trim();
        if (!trimmed.startsWith("+")) {
            trimmed = "+" + trimmed;
        }
        return trimmed;
    }

    /**
     * Extract category from permission (e.g., "docker:read" → "docker").
     */
    private String getCategory(String permission) {
        if (permission.contains(":")) {
            return permission.split(":")[0];
        }
        return permission;
    }
}
