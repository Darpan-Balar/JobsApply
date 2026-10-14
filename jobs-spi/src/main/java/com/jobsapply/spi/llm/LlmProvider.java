package com.jobsapply.spi.llm;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.common.PluginResult;

public interface LlmProvider extends Plugin {

    PluginResult<LlmResponse> complete(LlmRequest request);
}
