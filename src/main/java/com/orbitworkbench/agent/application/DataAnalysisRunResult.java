package com.orbitworkbench.agent.application;

import com.orbitworkbench.agent.domain.ModelCallRecord;
import com.orbitworkbench.ai.application.AiUsage;
import com.orbitworkbench.analysis.application.DataAnalysisRunContext;
import java.util.List;

public record DataAnalysisRunResult(
        DataAnalysisRunContext context,
        ModelCallRecord finalModelCall,
        String report,
        List<String> chartSpecs,
        String providerRequestId,
        AiUsage usage,
        Long artifactStepId
) {
}
