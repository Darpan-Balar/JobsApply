package com.jobsapply.platform.catalog.web;

import com.jobsapply.platform.catalog.CatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Skill catalog", description = "The shared skill catalog (for picking skills in a profile)")
@RestController
@RequiredArgsConstructor
class SkillController {

    private final CatalogService catalog;

    record SkillView(String key, String name) {}

    @GetMapping("/api/v1/skills")
    List<SkillView> skills() {
        return catalog.skillDisplayNames().entrySet().stream().map(e -> new SkillView(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(SkillView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
