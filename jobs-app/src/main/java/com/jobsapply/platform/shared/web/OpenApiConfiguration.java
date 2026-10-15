package com.jobsapply.platform.shared.web;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.jackson.TypeNameResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI jobsPlatformOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Jobs Platform API")
                .version("v1")
                .description("Local-first job discovery and application platform. Errors use RFC 9457 problem details."));
    }

    /**
     * Schema names: nested types are prefixed with their enclosing type ({@code TrackingViews.Summary} becomes
     * {@code TrackingViewsSummary}). With plain simple names, different types called {@code Summary} or {@code Outcome}
     * would silently overwrite each other in the published contract the dashboard is generated from.
     */
    @Bean
    ModelResolver unambiguousSchemaNames() {
        return new ModelResolver(Json.mapper(), new NestedTypeNameResolver());
    }

    static final class NestedTypeNameResolver extends TypeNameResolver {

        @Override
        protected String getNameOfClass(Class<?> cls) {
            Class<?> enclosing = cls.getEnclosingClass();
            return enclosing == null ? super.getNameOfClass(cls) : getNameOfClass(enclosing) + cls.getSimpleName();
        }
    }
}
