package com.orbitworkbench.aiconnection.infrastructure.mapper;

import com.orbitworkbench.aiconnection.domain.ModelProfileRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ModelProfileMapper {

    void insert(ModelProfileRecord profile);

    ModelProfileRecord findPrimaryByConnection(@Param("connectionId") Long connectionId);

    List<ModelProfileRecord> findEnabledByConnection(@Param("connectionId") Long connectionId);

    int update(ModelProfileRecord profile);
}
