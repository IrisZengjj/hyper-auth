package com.keycloak.credentialserver.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keycloak.credentialserver.service.EncryptedDataProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Component
public class EncryptedUploadFilter extends OncePerRequestFilter {

    @Autowired
    private EncryptedDataProcessingService encryptedDataProcessingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        // Intercept requests to /api/upload
        if ("/api/upload".equals(requestURI) && "POST".equalsIgnoreCase(request.getMethod())) {
            MultiReadHttpServletRequestWrapper wrappedRequest = new MultiReadHttpServletRequestWrapper(request);
            String body = wrappedRequest.getBody();

            try {
                // Check if body is JSON and contains encrypted_data
                if (body != null && !body.isEmpty() && body.contains("encrypted_data")) {
                    Map<String, String> requestMap = objectMapper.readValue(body, Map.class);
                    
                    if (requestMap.containsKey("encrypted_data") && requestMap.containsKey("encrypted_key")) {
                        // Process encrypted data
                        Map<String, Object> result = encryptedDataProcessingService.processEncryptedData(requestMap);
                        
                        // Return success response directly
                        response.setContentType("application/json;charset=UTF-8");
                        response.setStatus(HttpServletResponse.SC_OK);
                        response.getWriter().write(objectMapper.writeValueAsString(result));
                        return; // Stop chain
                    }
                }
                
                // If not encrypted data, continue with wrapped request
                filterChain.doFilter(wrappedRequest, response);
                
            } catch (Exception e) {
                // Handle processing errors
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"success\":false,\"message\":\"处理加密数据失败: " + e.getMessage() + "\"}");
            }
        } else {
            // Not target endpoint, continue
            filterChain.doFilter(request, response);
        }
    }
}
