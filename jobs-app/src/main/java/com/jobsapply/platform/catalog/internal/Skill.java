package com.jobsapply.platform.catalog.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Aggregate root for a skill and its aliases. */
@Entity
@Table(name = "skill")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Skill extends BaseEntity {

    @Column(name = "key", nullable = false, updatable = false, length = 63)
    private String key;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "category", length = 63)
    private String category;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SkillAlias> aliases = new HashSet<>();

    public Set<String> aliasNames() {
        return aliases.stream().map(SkillAlias::getAliasNormalized).collect(Collectors.toUnmodifiableSet());
    }

    public boolean update(String displayName, String category) {
        if (Objects.equals(this.displayName, displayName) && Objects.equals(this.category, category)) {
            return false;
        }
        this.displayName = displayName;
        this.category = category;
        return true;
    }

    /** Removes aliases not in {@code keep}. @return whether any alias was removed */
    public boolean retainAliases(Set<String> keep) {
        return aliases.removeIf(alias -> !keep.contains(alias.getAliasNormalized()));
    }

    /** Adds missing aliases (already normalized). @return whether any alias was added */
    public boolean addAliases(Set<String> normalized) {
        Set<String> present = aliasNames();
        boolean added = false;
        for (String alias : normalized) {
            if (!present.contains(alias)) {
                aliases.add(new SkillAlias(this, alias));
                added = true;
            }
        }
        return added;
    }
}
