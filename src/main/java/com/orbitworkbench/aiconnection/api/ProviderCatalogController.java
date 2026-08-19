package com.orbitworkbench.aiconnection.api;

import com.orbitworkbench.aiconnection.api.ProviderCatalogDtos.ProviderCatalogResponse;
import com.orbitworkbench.aiconnection.application.ProviderCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProviderCatalogController {

    private final ProviderCatalogService service;

    public ProviderCatalogController(ProviderCatalogService service) {
        this.service = service;
    }

    @GetMapping("/provider-catalogs")
    public List<ProviderCatalogResponse> list() {
        return service.listEnabled();
    }
}
