package com.chaosblade.svc.reqrspproxy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.HashMap;
import java.util.Map;

/**
 * 模拟响应配置
 */
public class MockResponse {
    
    @Min(value = 100, message = "HTTP状态码不能小于100")
    @Max(value = 599, message = "HTTP状态码不能大于599")
    private Integer statusCode = 200;
    
    private Map<String, String> headers = new HashMap<>();
    
    private String body = "";
    
    private String contentType = "application/json; charset=utf-8";
    
    public MockResponse() {}
    
    public MockResponse(Integer statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }
    
    public MockResponse(Integer statusCode, String body, String contentType) {
        this.statusCode = statusCode;
        this.body = body;
        this.contentType = contentType;
    }
    
    public Integer getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers != null ? headers : new HashMap<>();
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body != null ? body : "";
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType != null ? contentType : "application/json; charset=utf-8";
    }
    
    /**
     * 添加响应头
     */
    public MockResponse addHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }
    
    /**
     * 创建 JSON 响应
     */
    public static MockResponse json(int statusCode, String jsonBody) {
        return new MockResponse(statusCode, jsonBody, "application/json; charset=utf-8");
    }
    
    /**
     * 创建文本响应
     */
    public static MockResponse text(int statusCode, String textBody) {
        return new MockResponse(statusCode, textBody, "text/plain; charset=utf-8");
    }
    
    /**
     * 创建 HTML 响应
     */
    public static MockResponse html(int statusCode, String htmlBody) {
        return new MockResponse(statusCode, htmlBody, "text/html; charset=utf-8");
    }
    
    @Override
    public String toString() {
        return "MockResponse{" +
                "statusCode=" + statusCode +
                ", headers=" + headers +
                ", body='" + body + '\'' +
                ", contentType='" + contentType + '\'' +
                '}';
    }
}
