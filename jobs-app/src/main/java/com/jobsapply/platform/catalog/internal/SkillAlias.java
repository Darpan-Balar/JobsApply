package com.jobsapply.platform.catalog.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skill_alias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillAlias extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false, updatable = false)
    private Skill skill;

    @Column(name = "alias_normalized", nullable = false, updatable = false, length = 100)
    private String aliasNormalized;

    SkillAlias(Skill skill, String aliasNormalized) {
        this.skill = skill;
        this.aliasNormalized = aliasNormalized;
    }
}
