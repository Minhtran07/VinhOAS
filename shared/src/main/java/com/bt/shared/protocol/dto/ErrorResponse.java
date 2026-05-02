package com.bt.shared.protocol.dto;

import com.bt.shared.protocol.ErrorCode;

public class ErrorResponse {

    private ErrorCode code;
    private String message;

    public ErrorResponse() { /* Gson */ }

    public ErrorResponse(ErrorCode code, String message) {
        this.code = code;
        this.message = message;
    }

    public ErrorCode getCode() { return code; }
    public void setCode(ErrorCode code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
