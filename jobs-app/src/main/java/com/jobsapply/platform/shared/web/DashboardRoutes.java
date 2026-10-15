package com.jobsapply.platform.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the dashboard (jobs-web, on the classpath under {@code static/}): its client-side routes all load
 * {@code index.html}, which is never cached, while the content-hashed files under {@code /assets/} are cached for a year.
 * The dashboard asks for sign-in itself; the API enforces it.
 */
@Controller
class DashboardRoutes {

    @GetMapping({"/", "/jobs", "/jobs/**", "/applications", "/applications/**", "/matches", "/profiles", "/profiles/**", "/tracking", "/account", "/admin"})
    ModelAndView dashboard(HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noCache().getHeaderValue());
        return new ModelAndView("forward:/index.html");
    }

    @Configuration(proxyBeanMethods = false)
    static class Assets implements WebMvcConfigurer {

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/assets/**")
                    .addResourceLocations("classpath:/static/assets/")
                    .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        }
    }
}
