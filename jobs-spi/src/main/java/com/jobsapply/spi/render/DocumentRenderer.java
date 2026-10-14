package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.common.PluginResult;
import java.util.Set;

public interface DocumentRenderer extends Plugin {

    Set<String> supportedMediaTypes();

    PluginResult<RenderedDocument> render(ResumeDocument document, RenderOptions options);
}
