# Adding Custom Tools

Tools are automatically discovered and registered. Just implement the `Tool` interface and add `@Component`.

## Tool Interface

```java
public interface Tool {
    String getName();                                    // Tool identifier (e.g., "home_assistant_light")
    String getDescription();                             // What it does (shown to LLM)
    JsonNode getParametersSchema();                      // JSON Schema for parameters
    ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context);
    Set<String> getRequiredPermissions();                // Who can use it
}
```

## Example 1: Home Assistant Light Control

Create: `java-backend/src/main/java/com/jacobsfam/whatsappai/service/tools/impl/HomeAssistantLightTool.java`

```java
package com.jacobsfam.whatsappai.service.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class HomeAssistantLightTool implements Tool {

    @Value("${homeassistant.url:http://homeassistant:8123}")
    private String homeAssistantUrl;

    @Value("${homeassistant.token}")
    private String token;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "home_assistant_light";
    }

    @Override
    public String getDescription() {
        return "Control lights in Home Assistant. Can turn lights on/off or set brightness.";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode entityId = properties.putObject("entity_id");
        entityId.put("type", "string");
        entityId.put("description", "The light entity ID (e.g., 'light.living_room')");

        ObjectNode action = properties.putObject("action");
        action.put("type", "string");
        action.put("description", "Action to perform: 'on', 'off', 'toggle'");
        action.putArray("enum").add("on").add("off").add("toggle");

        ObjectNode brightness = properties.putObject("brightness");
        brightness.put("type", "integer");
        brightness.put("description", "Brightness level 0-255 (optional, only for 'on')");
        brightness.put("minimum", 0);
        brightness.put("maximum", 255);

        schema.putArray("required").add("entity_id").add("action");

        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            String entityId = (String) arguments.get("entity_id");
            String action = (String) arguments.get("action");
            Integer brightness = arguments.containsKey("brightness")
                ? ((Number) arguments.get("brightness")).intValue()
                : null;

            // Build Home Assistant API call
            String service = action.equals("toggle") ? "toggle" : "turn_" + action;
            String url = String.format("%s/api/services/light/%s", homeAssistantUrl, service);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("entity_id", entityId);
            if (brightness != null && action.equals("on")) {
                payload.put("brightness", brightness);
            }

            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                    payload.toString(),
                    MediaType.get("application/json")
                ))
                .header("Authorization", "Bearer " + token)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String brightText = brightness != null ? " at " + brightness + " brightness" : "";
                    return ToolExecutionResult.success(
                        String.format("Light %s turned %s%s", entityId, action, brightText)
                    );
                } else {
                    return ToolExecutionResult.error("Failed: " + response.message());
                }
            }
        } catch (Exception e) {
            return ToolExecutionResult.error("Error: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("homeassistant:write");
    }
}
```

**Add to `application.yml`:**
```yaml
homeassistant:
  url: http://homeassistant:8123
  token: ${HOMEASSISTANT_TOKEN}
```

**Add to `.env`:**
```bash
HOMEASSISTANT_TOKEN=your-long-lived-access-token
```

**That's it!** The tool is now available. The LLM will automatically call it when users say:
- "Turn off the living room lights"
- "Set bedroom lights to 50%"
- "Toggle kitchen light"

## Example 2: Laundry Status Tool

```java
package com.jacobsfam.whatsappai.service.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Component
public class LaundryStatusTool implements Tool {

    @Value("${laundry.backend.url:http://laundry-backend:8080}")
    private String laundryUrl;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "laundry_status";
    }

    @Override
    public String getDescription() {
        return "Check the status of washer and dryer (running, time remaining, etc.)";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties"); // No parameters needed
        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            Request request = new Request.Builder()
                .url(laundryUrl + "/api/status")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonNode status = objectMapper.readTree(body);

                    StringBuilder result = new StringBuilder();
                    result.append("🧺 Laundry Status:\n\n");

                    // Washer
                    JsonNode washer = status.path("washer");
                    result.append("Washer: ");
                    if (washer.path("running").asBoolean()) {
                        result.append("Running - ");
                        result.append(washer.path("time_remaining").asText());
                        result.append(" remaining\n");
                    } else {
                        result.append("Idle\n");
                    }

                    // Dryer
                    JsonNode dryer = status.path("dryer");
                    result.append("Dryer: ");
                    if (dryer.path("running").asBoolean()) {
                        result.append("Running - ");
                        result.append(dryer.path("time_remaining").asText());
                        result.append(" remaining\n");
                    } else {
                        result.append("Idle\n");
                    }

                    return ToolExecutionResult.success(result.toString());
                } else {
                    return ToolExecutionResult.error("Failed to get laundry status");
                }
            }
        } catch (Exception e) {
            return ToolExecutionResult.error("Error: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("laundry:read");
    }
}
```

Users can ask:
- "Is the laundry done?"
- "How much longer on the washer?"
- "Check laundry status"

## Example 3: Paperless Document Search

```java
package com.jacobsfam.whatsappai.service.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class PaperlessSearchTool implements Tool {

    @Value("${paperless.url:http://paperless-webserver:8000}")
    private String paperlessUrl;

    @Value("${paperless.token}")
    private String token;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "paperless_search";
    }

    @Override
    public String getDescription() {
        return "Search for documents in Paperless-ngx by keyword, tag, or document type.";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode query = properties.putObject("query");
        query.put("type", "string");
        query.put("description", "Search query (keywords, document name, etc.)");

        schema.putArray("required").add("query");
        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            String query = (String) arguments.get("query");

            HttpUrl url = HttpUrl.parse(paperlessUrl + "/api/documents/")
                .newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("page", "1")
                .addQueryParameter("page_size", "5")
                .build();

            Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Token " + token)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonNode results = objectMapper.readTree(body);

                    int count = results.path("count").asInt();
                    JsonNode docs = results.path("results");

                    if (count == 0) {
                        return ToolExecutionResult.success("No documents found for: " + query);
                    }

                    StringBuilder result = new StringBuilder();
                    result.append(String.format("📄 Found %d document(s):\n\n", count));

                    for (JsonNode doc : docs) {
                        String title = doc.path("title").asText();
                        String created = doc.path("created").asText();
                        int id = doc.path("id").asInt();

                        result.append(String.format("• %s\n", title));
                        result.append(String.format("  Date: %s\n", created));
                        result.append(String.format("  Link: %s/documents/%d\n\n",
                            paperlessUrl, id));
                    }

                    return ToolExecutionResult.success(result.toString());
                } else {
                    return ToolExecutionResult.error("Search failed");
                }
            }
        } catch (Exception e) {
            return ToolExecutionResult.error("Error: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("paperless:read");
    }
}
```

Users can ask:
- "Find my car insurance document"
- "Search for tax documents from 2024"
- "Show me utility bills"

## Adding Tool Permissions

When you add a new tool, give users permission:

```bash
# Via command in WhatsApp
/adduser +1234567890 Mom homeassistant:write,laundry:read,paperless:read

# Or edit the database
# Admin users already have all permissions via admin:*
```

## Tool Discovery Flow

```
1. Spring Boot starts
   ↓
2. Component scan finds all @Component classes
   ↓
3. ToolRegistry finds all beans implementing Tool interface
   ↓
4. Tools registered and available to LLM
   ↓
5. User asks: "Turn off the lights"
   ↓
6. LLM sees home_assistant_light tool
   ↓
7. LLM generates tool call with parameters
   ↓
8. ToolExecutor runs execute() method
   ↓
9. Result returned to LLM
   ↓
10. LLM formulates response: "Done! Lights are off."
```

## Tips

**1. Keep descriptions clear** - The LLM reads these to decide when to use the tool

**2. Use JSON Schema** - Helps LLM generate correct parameters

**3. Error handling** - Return `ToolExecutionResult.error()` not exceptions

**4. Permissions** - Use category:action format (e.g., "homeassistant:write")

**5. Idempotent** - Tools should be safe to retry

**6. Fast** - Tools timeout after 300 seconds, keep them responsive

**7. Logging** - Log tool execution for debugging

## Testing

```java
// Add a test tool to verify the system works
@Component
public class PingTool implements Tool {
    @Override
    public String getName() { return "ping"; }

    @Override
    public String getDescription() {
        return "Test tool that responds with 'pong'";
    }

    @Override
    public JsonNode getParametersSchema() {
        return new ObjectMapper().createObjectNode().put("type", "object");
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> args, ExecutionContext ctx) {
        return ToolExecutionResult.success("Pong! Tool system working.");
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Collections.emptySet(); // Everyone can use
    }
}
```

Ask: "ping" → LLM calls tool → "Pong! Tool system working."

## Quick Start Checklist

To add a tool:
- [ ] Create class in `service/tools/impl/`
- [ ] Implement `Tool` interface
- [ ] Add `@Component` annotation
- [ ] Define name, description, schema
- [ ] Implement `execute()` method
- [ ] Set required permissions
- [ ] Add config to `application.yml` if needed
- [ ] Restart backend: `docker compose restart whatsapp-ai-backend`
- [ ] Test by asking relevant question

That's it! The system auto-discovers and registers your tool.
