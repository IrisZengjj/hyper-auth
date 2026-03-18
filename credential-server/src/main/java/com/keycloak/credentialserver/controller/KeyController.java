package com.keycloak.credentialserver.controller;

import com.keycloak.credentialserver.service.KeyManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class KeyController {

    @Autowired
    private KeyManagementService keyManagementService;

    @GetMapping("/configured-public-key")
    public String getConfiguredPublicKey() {
        return keyManagementService.getPublicKeyBase64();
    }
}
