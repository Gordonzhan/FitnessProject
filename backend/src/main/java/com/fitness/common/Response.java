package com.fitness.common;

import lombok.Data;

@Data
public class Response {
    private int code;
    private String message;
    private Object data;
    
    public Response(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    public static Response success() {
        return new Response(200, "success", null);
    }
    
    public static Response success(Object data) {
        return new Response(200, "success", data);
    }
    
    public static Response error(String message) {
        return new Response(400, message, null);
    }
    
    public static Response error(int code, String message) {
        return new Response(code, message, null);
    }
}
