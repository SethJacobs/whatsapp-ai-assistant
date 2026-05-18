package com.jacobsfam.whatsappai.model;

import lombok.Data;

/**
 * Result of tool execution.
 */
@Data
public class ToolExecutionResult {

    private boolean success;
    private String output;
    private String errorMessage;
    private boolean timeout;
    private Integer exitCode;

    public static ToolExecutionResult success(String output) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setSuccess(true);
        result.setOutput(output);
        return result;
    }

    public static ToolExecutionResult error(String errorMessage) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public static ToolExecutionResult timeout() {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setSuccess(false);
        result.setTimeout(true);
        result.setErrorMessage("Tool execution timed out");
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
