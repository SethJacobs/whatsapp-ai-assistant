package com.jacobsfam.whatsappai.service.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class WebSearchTool implements Tool {

    @Autowired
    private ObjectMapper objectMapper;

    private final OkHttpClient httpClient;

    public WebSearchTool() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "Search the web for information. Returns search results with titles, URLs, and snippets. " +
               "Use this to find current information, research topics, or look up facts you don't know.";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        // Query parameter
        ObjectNode queryParam = objectMapper.createObjectNode();
        queryParam.put("type", "string");
        queryParam.put("description", "The search query");
        properties.set("query", queryParam);

        // Max results parameter (optional)
        ObjectNode maxResultsParam = objectMapper.createObjectNode();
        maxResultsParam.put("type", "integer");
        maxResultsParam.put("description", "Maximum number of results to return (default: 5)");
        properties.set("max_results", maxResultsParam);

        schema.set("properties", properties);

        // Required parameters
        schema.set("required", objectMapper.createArrayNode().add("query"));

        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            String query = (String) arguments.get("query");
            Integer maxResults = arguments.containsKey("max_results")
                ? ((Number) arguments.get("max_results")).intValue()
                : 5;

            if (query == null || query.trim().isEmpty()) {
                return ToolExecutionResult.error("Query cannot be empty");
            }

            log.info("Searching web for: {}", query);

            // Use DuckDuckGo HTML search (simple scraping approach)
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ToolExecutionResult.error("Search failed with status: " + response.code());
                }

                String html = response.body().string();

                // Parse results (simple regex-based extraction)
                StringBuilder results = new StringBuilder();
                results.append("🔍 Search results for: \"").append(query).append("\"\n\n");

                // Extract result titles and URLs using simple string parsing
                // This is a basic implementation - could be improved with proper HTML parsing
                int count = 0;
                int pos = 0;

                while (count < maxResults && pos < html.length()) {
                    // Find result title
                    int titleStart = html.indexOf("class=\"result__title\"", pos);
                    if (titleStart == -1) break;

                    int linkStart = html.indexOf("href=\"", titleStart);
                    if (linkStart == -1) break;
                    linkStart += 6; // Skip 'href="'

                    int linkEnd = html.indexOf("\"", linkStart);
                    if (linkEnd == -1) break;

                    String link = html.substring(linkStart, linkEnd);

                    // Get title text
                    int titleTextStart = html.indexOf(">", linkEnd) + 1;
                    int titleTextEnd = html.indexOf("</a>", titleTextStart);
                    if (titleTextEnd == -1) break;

                    String title = html.substring(titleTextStart, titleTextEnd)
                        .replaceAll("<[^>]+>", "")
                        .replaceAll("&amp;", "&")
                        .replaceAll("&quot;", "\"")
                        .trim();

                    // Get snippet
                    int snippetStart = html.indexOf("class=\"result__snippet\"", titleTextEnd);
                    String snippet = "";
                    if (snippetStart != -1) {
                        int snippetTextStart = html.indexOf(">", snippetStart) + 1;
                        int snippetTextEnd = html.indexOf("</", snippetTextStart);
                        if (snippetTextEnd != -1) {
                            snippet = html.substring(snippetTextStart, snippetTextEnd)
                                .replaceAll("<[^>]+>", "")
                                .replaceAll("&amp;", "&")
                                .replaceAll("&quot;", "\"")
                                .trim();
                        }
                    }

                    if (!title.isEmpty() && !link.startsWith("//duckduckgo.com")) {
                        count++;
                        results.append(count).append(". **").append(title).append("**\n");
                        results.append("   ").append(link).append("\n");
                        if (!snippet.isEmpty()) {
                            results.append("   ").append(snippet).append("\n");
                        }
                        results.append("\n");
                    }

                    pos = titleTextEnd;
                }

                if (count == 0) {
                    return ToolExecutionResult.success("No results found for: \"" + query + "\"");
                }

                return ToolExecutionResult.success(results.toString());
            }

        } catch (Exception e) {
            log.error("Error executing web_search", e);
            return ToolExecutionResult.error("Search failed: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("web:search");
    }
}
