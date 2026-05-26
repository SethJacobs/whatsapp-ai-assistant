package com.jacobsfam.whatsappai.service.security;

import com.jacobsfam.whatsappai.model.ChatContext;
import com.jacobsfam.whatsappai.model.entity.AllowedContact;
import com.jacobsfam.whatsappai.model.entity.AllowedGroup;
import com.jacobsfam.whatsappai.repository.AllowedContactRepository;
import com.jacobsfam.whatsappai.repository.AllowedGroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class SecurityService {

    @Autowired
    private AllowedContactRepository allowedContactRepository;

    @Autowired
    private AllowedGroupRepository allowedGroupRepository;

    @Value("${security.admin-phone:}")
    private String adminPhone;

    @Value("${security.additional-admin-phones:}")
    private String additionalAdminPhones;

    @PostConstruct
    public void initializeDefaultContacts() {
        // Collect all admin phones from env vars
        List<String> allPhones = new java.util.ArrayList<>();

        // Add primary admin phone
        if (adminPhone != null && !adminPhone.trim().isEmpty()) {
            allPhones.add(adminPhone.trim());
        }

        // Add additional admin phones (comma-separated)
        if (additionalAdminPhones != null && !additionalAdminPhones.trim().isEmpty()) {
            String[] phones = additionalAdminPhones.split(",");
            for (String phone : phones) {
                if (phone != null && !phone.trim().isEmpty()) {
                    allPhones.add(phone.trim());
                }
            }
        }

        // Initialize admin contacts from config
        if (!allPhones.isEmpty()) {
            for (String phone : allPhones) {
                // Skip placeholder values
                if (phone.equals("+12125551234")) {
                    log.warn("Skipping placeholder admin phone: {}", phone);
                    continue;
                }

                String normalized = normalizePhoneNumber(phone);
                if (!allowedContactRepository.findByPhoneNumber(normalized).isPresent()) {
                    AllowedContact contact = new AllowedContact(normalized, "Admin");
                    contact.grantAllPermissions("system");
                    contact.grantAllPermissions("docker");
                    contact.grantAllPermissions("gateway");
                    contact.grantAllPermissions("web");
                    contact.grantAllPermissions("admin"); // Grant admin permissions (includes filesystem)
                    allowedContactRepository.save(contact);
                    log.info("Created admin contact: {}", normalized);
                }
            }
        } else {
            log.warn("No admin phones configured. Use /adduser command to add authorized users.");
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
     * Alias for isAuthorized - checks if phone is in allowlist.
     */
    public boolean isPhoneAllowed(String phoneNumber) {
        return isAuthorized(phoneNumber);
    }

    /**
     * Check if a group is authorized.
     */
    public boolean isGroupAllowed(String groupId) {
        return allowedGroupRepository.findByGroupId(groupId).isPresent();
    }

    /**
     * Build chat context for authorization and permissions.
     */
    public ChatContext getChatContext(String chatId, String senderPhone) {
        // Determine if this is a group (contains @g.us)
        if (chatId.contains("@g.us")) {
            // Group chat
            Optional<AllowedGroup> groupOpt = allowedGroupRepository.findByGroupId(chatId);
            if (groupOpt.isPresent()) {
                AllowedGroup group = groupOpt.get();
                return ChatContext.builder()
                        .chatType(ChatContext.ChatType.GROUP)
                        .chatId(chatId)
                        .senderPhone(senderPhone)
                        .readOnly(group.isReadOnly())
                        .permissions(group.getPermissions())
                        .displayName(group.getGroupName())
                        .build();
            }
            return null; // Group not allowed
        } else {
            // Individual chat
            Optional<AllowedContact> contactOpt = allowedContactRepository.findByPhoneNumber(
                    normalizePhoneNumber(senderPhone));
            if (contactOpt.isPresent()) {
                AllowedContact contact = contactOpt.get();
                return ChatContext.builder()
                        .chatType(ChatContext.ChatType.INDIVIDUAL)
                        .chatId(senderPhone)
                        .senderPhone(senderPhone)
                        .readOnly(false) // Individual chats are never read-only
                        .permissions(contact.getPermissions())
                        .displayName(contact.getLabel())
                        .build();
            }
            return null; // Contact not allowed
        }
    }

    /**
     * Check if user is an admin (has admin:* permissions).
     */
    public boolean isAdmin(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        return allowedContactRepository.findByPhoneNumber(normalized)
                .map(contact -> contact.getGrantedPermissions().contains("admin:*"))
                .orElse(false);
    }

    /**
     * Add a new contact to the allowlist.
     */
    public void addContact(String phoneNumber, String label, String permissions) {
        String normalized = normalizePhoneNumber(phoneNumber);

        Optional<AllowedContact> existing = allowedContactRepository.findByPhoneNumber(normalized);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Contact already exists: " + normalized);
        }

        AllowedContact contact = new AllowedContact(normalized, label);
        contact.setPermissions(permissions);
        allowedContactRepository.save(contact);
        log.info("Added contact: {} with permissions: {}", normalized, permissions);
    }

    /**
     * Remove a contact from the allowlist.
     */
    public void removeContact(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);

        Optional<AllowedContact> contact = allowedContactRepository.findByPhoneNumber(normalized);
        if (contact.isPresent()) {
            allowedContactRepository.delete(contact.get());
            log.info("Removed contact: {}", normalized);
        } else {
            throw new IllegalArgumentException("Contact not found: " + normalized);
        }
    }

    /**
     * Add a group to the allowlist.
     */
    public void addGroup(String groupId, String groupName, boolean readOnly, String permissions) {
        Optional<AllowedGroup> existing = allowedGroupRepository.findByGroupId(groupId);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Group already exists: " + groupId);
        }

        AllowedGroup group = new AllowedGroup(groupId, groupName, readOnly);
        group.setPermissions(permissions);
        allowedGroupRepository.save(group);
        log.info("Added group: {} (read-only: {}) with permissions: {}", groupName, readOnly, permissions);
    }

    /**
     * Remove a group from the allowlist.
     */
    public void removeGroup(String groupId) {
        Optional<AllowedGroup> group = allowedGroupRepository.findByGroupId(groupId);
        if (group.isPresent()) {
            allowedGroupRepository.delete(group.get());
            log.info("Removed group: {}", groupId);
        } else {
            throw new IllegalArgumentException("Group not found: " + groupId);
        }
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
     * Strips WhatsApp suffixes (@c.us, @g.us, @lid) and ensures + prefix.
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }

        // Strip WhatsApp suffixes (@c.us for contacts, @g.us for groups, @lid for channels)
        String cleaned = phone.trim();
        if (cleaned.contains("@")) {
            cleaned = cleaned.substring(0, cleaned.indexOf("@"));
        }

        // Ensure starts with +
        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }

        return cleaned;
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
