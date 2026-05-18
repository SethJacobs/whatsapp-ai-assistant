package com.jacobsfam.whatsappai.service.tools;

import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

@Service
@Slf4j
public class ToolExecutor {

    private final ExecutorService executorService;
    private final SecurityService securityService;
    private final Duration defaultTimeout;

    @Autowired
    public ToolExecutor(
            SecurityService securityService,
            @Value("${tools.execution-timeout-seconds:300}") int timeoutSeconds) {
        this.securityService = securityService;
        this.defaultTimeout = Duration.ofSeconds(timeoutSeconds);
        this.executorService = Executors.newFixedThreadPool(
                4,
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "tool-executor-" + counter++);
                        thread.setUncaughtExceptionHandler((t, e) ->
                                log.error("Uncaught exception in tool execution thread", e));
                        return thread;
                    }
                }
        );
    }

    /**
     * Execute tool with default timeout.
     */
    public ToolExecutionResult execute(
            Tool tool,
            Map<String, Object> arguments,
            ExecutionContext context) {
        return executeWithTimeout(tool, arguments, context, defaultTimeout);
    }

    /**
     * Execute tool with custom timeout.
     */
    public ToolExecutionResult executeWithTimeout(
            Tool tool,
            Map<String, Object> arguments,
            ExecutionContext context,
            Duration timeout) {

        log.info("Executing tool: {} for user: {}", tool.getName(), context.getUserPhone());

        // Security check
        if (!securityService.hasPermissions(context.getUserPhone(), tool.getRequiredPermissions())) {
            log.warn("Permission denied for tool: {} user: {}", tool.getName(), context.getUserPhone());
            return ToolExecutionResult.error("Permission denied: insufficient privileges");
        }

        // Execute with timeout
        Future<ToolExecutionResult> future = executorService.submit(() -> {
            try {
                return tool.execute(arguments, context);
            } catch (Exception e) {
                log.error("Tool execution failed: " + tool.getName(), e);
                return ToolExecutionResult.error(e.getMessage());
            }
        });

        try {
            ToolExecutionResult result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Tool {} executed successfully: {}", tool.getName(), result.isSuccess());
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Tool execution timeout: {}", tool.getName());
            return ToolExecutionResult.timeout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolExecutionResult.error("Execution interrupted");
        } catch (ExecutionException e) {
            log.error("Tool execution exception: " + tool.getName(), e.getCause());
            return ToolExecutionResult.error(e.getCause().getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down tool executor");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
