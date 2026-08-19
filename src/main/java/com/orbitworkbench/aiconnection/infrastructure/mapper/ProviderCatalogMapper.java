package com.orbitworkbench.aiconnection.infrastructure.mapper;

import com.orbitworkbench.aiconnection.domain.ProviderCatalogRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ProviderCatalogMapper {

    List<ProviderCatalogRecord> findEnabled();

    ProviderCatalogRecord findEnabledByCode(@Param("providerCode") String providerCode);
}
