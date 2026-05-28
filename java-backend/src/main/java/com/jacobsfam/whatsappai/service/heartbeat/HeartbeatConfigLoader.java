package com.jacobsfam.whatsappai.service.heartbeat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jacobsfam.whatsappai.model.heartbeat.HeartbeatConfig;
import com.jacobsfam.whatsappai.model.heartbeat.HeartbeatTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and parses HEARTBEAT.md configuration file.
 * Supports hot-reloading when file changes.
 */
@Service
@Slf4j
public class HeartbeatConfigLoader {

    @Value("${heartbeat.config-file:/home/pi/ezra-heartbeat/HEARTBEAT.md}")
    String configFilePath;  // package-private for testing

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private HeartbeatConfig cachedConfig;
    private FileTime lastModified;

    // Pattern for parsing interval strings like "30m", "2h", "1d"
    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^(\\d+)([mhd])$");

    @PostConstruct
    public void init() {
        try {
            this.cachedConfig = loadConfig();
            log.info("Loaded heartbeat config with {} tasks",
                cachedConfig != null ? cachedConfig.getTasks().size() : 0);
        } catch (Exception e) {
            log.error("Failed to load initial heartbeat config", e);
            // Create empty config so system can still start
            this.cachedConfig = new HeartbeatConfig();
            this.cachedConfig.setEnabled(false);
        }
    }

    /**
     * Get current config, reloading if file has changed.
     */
    public HeartbeatConfig getConfig() {
        Path path = Paths.get(configFilePath);

        if (!Files.exists(path)) {
            log.warn("Heartbeat config file not found: {}", configFilePath);
            return cachedConfig != null ? cachedConfig : new HeartbeatConfig();
        }

        try {
            FileTime currentModified = Files.getLastModifiedTime(path);

            // Reload if file changed
            if (lastModified == null || !currentModified.equals(lastModified)) {
                log.info("Heartbeat config file changed, reloading...");
                cachedConfig = loadConfig();
                lastModified = currentModified;
            }
        } catch (IOException e) {
            log.warn("Could not check heartbeat config modification time", e);
        }

        return cachedConfig;
    }

    /**
     * Load and parse HEARTBEAT.md file.
     */
    private HeartbeatConfig loadConfig() throws IOException {
        Path path = Paths.get(configFilePath);

        if (!Files.exists(path)) {
            log.warn("Heartbeat config file does not exist: {}", configFilePath);
            HeartbeatConfig emptyConfig = new HeartbeatConfig();
            emptyConfig.setEnabled(false);
            return emptyConfig;
        }

        String content = Files.readString(path);

        // Split YAML frontmatter from markdown content
        // Format: ---\nYAML\n---\nMarkdown
        String[] parts = content.split("---\n", 3);

        if (parts.length < 3) {
            throw new IllegalArgumentException(
                "Invalid HEARTBEAT.md format. Expected YAML frontmatter between --- markers");
        }

        String yamlContent = parts[1].trim();
        String markdownContent = parts[2].trim();

        // Parse YAML frontmatter
        HeartbeatConfig config = yamlMapper.readValue(yamlContent, HeartbeatConfig.class);

        // Set markdown instructions
        config.setInstructions(markdownContent);

        // Parse intervals for each task
        for (HeartbeatTask task : config.getTasks()) {
            if (task.getIntervalString() != null) {
                task.setInterval(parseInterval(task.getIntervalString()));
            }
        }

        log.info("Loaded heartbeat config: {} tasks, active hours: {}, timezone: {}",
            config.getTasks().size(),
            config.getActiveHours(),
            config.getTimezone());

        return config;
    }

    /**
     * Parse interval string like "30m", "2h", "1d" into Duration.
     */
    public Duration parseInterval(String intervalString) {
        if (intervalString == null || intervalString.isBlank()) {
            throw new IllegalArgumentException("Interval string cannot be null or empty");
        }

        Matcher matcher = INTERVAL_PATTERN.matcher(intervalString.toLowerCase().trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid interval format: " + intervalString +
                ". Expected format: <number><unit> (e.g., 30m, 2h, 1d)");
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
        };
    }

    /**
     * Reload config from disk (useful for manual refresh).
     */
    public void reload() {
        try {
            cachedConfig = loadConfig();
            Path path = Paths.get(configFilePath);
            lastModified = Files.getLastModifiedTime(path);
            log.info("Manually reloaded heartbeat config");
        } catch (IOException e) {
            log.error("Failed to reload heartbeat config", e);
        }
    }
}
