package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.jobsapply.platform.employer.BindingSpec;
import com.jobsapply.platform.employer.BindingView;
import com.jobsapply.platform.employer.EmployerQuery;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.EmployerSort;
import com.jobsapply.platform.employer.EmployerSpec;
import com.jobsapply.platform.employer.EmployerView;
import com.jobsapply.platform.shared.error.ConcurrencyConflictException;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.model.PageResult;
import com.jobsapply.platform.shared.model.UpsertOutcome;
import com.jobsapply.platform.source.JobSourceSpec;
import com.jobsapply.platform.source.SourceService;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.TestJobSourceAdapter;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class EmployerRegistryIT {

    @Autowired EmployerService employers;
    @Autowired SourceService sources;

    String sourceA;
    String sourceB;
    String tag;

    @BeforeEach
    void setUp() {
        sourceA = uniqueKey("src-a");
        sourceB = uniqueKey("src-b");
        sources.upsert(new JobSourceSpec(sourceA, TestJobSourceAdapter.ID, "Source A", true, Map.of("requestTimeout", "PT5S")));
        sources.upsert(new JobSourceSpec(sourceB, "not-installed-adapter", "Source B", false, Map.of()));
        tag = uniqueKey("tag");
    }

    private EmployerSpec spec(String key, String name, int priority) {
        return EmployerSpec.builder().key(key).displayName(name).tags(List.of(tag)).discoveryPriority(priority).enabled(true).build();
    }

    @Test
    void createsWithBindingsAndNormalizedName() {
        String key = uniqueKey("acme");
        EmployerView created = employers.create(spec(key, "Acme, Inc.", 70), List.of(BindingSpec.of(sourceA, key)));

        assertThat(created.normalizedName()).isEqualTo("acme");
        assertThat(created.bindings()).singleElement().satisfies(b -> {
            assertThat(b.sourceKey()).isEqualTo(sourceA);
            assertThat(b.adapterId()).isEqualTo(TestJobSourceAdapter.ID);
            assertThat(b.enabled()).isTrue();
        });
        assertThat(employers.get(key)).isEqualTo(created);
    }

    @Test
    void duplicateKeyIsRejected() {
        String key = uniqueKey("dup");
        employers.create(spec(key, "Dup", 50), List.of());
        assertThatThrownBy(() -> employers.create(spec(key, "Dup again", 50), List.of()))
                .isInstanceOf(DomainRuleViolationException.class).hasMessage("Employer already exists: " + key);
    }

    @Test
    void providerIdentifierBelongsToOneEmployerWithAClearError() {
        String ref = uniqueKey("board");
        String owner = uniqueKey("owner");
        employers.create(spec(owner, "Owner", 50), List.of(BindingSpec.of(sourceA, ref)));
        String other = uniqueKey("other");
        employers.create(spec(other, "Other", 50), List.of());

        assertThatThrownBy(() -> employers.putBinding(other, BindingSpec.of(sourceA, ref)))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessage("'" + ref + "' on " + sourceA + " is already bound to employer '" + owner + "'");
    }

    @Test
    void invalidSpecIsRejectedBeforeTheDatabase() {
        EmployerSpec bad = spec(uniqueKey("bad"), "Bad", 50).toBuilder().website("ftp://nope").build();
        assertThatThrownBy(() -> employers.create(bad, List.of())).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void upsertIsIdempotentAndDisablesBindingsNoLongerDeclared() {
        String key = uniqueKey("idem");
        List<BindingSpec> both = List.of(BindingSpec.of(sourceA, key + "-a"), BindingSpec.of(sourceB, key + "-b"));

        assertThat(employers.upsert(spec(key, "Idem", 60), both)).isEqualTo(UpsertOutcome.CREATED);
        assertThat(employers.upsert(spec(key, "Idem", 60), both)).isEqualTo(UpsertOutcome.UNCHANGED);
        assertThat(employers.upsert(spec(key, "Idem", 60), List.of(both.getFirst()))).isEqualTo(UpsertOutcome.UPDATED);

        assertThat(employers.get(key).bindings()).extracting(BindingView::sourceKey, BindingView::enabled)
                .containsExactlyInAnyOrder(tuple(sourceA, true), tuple(sourceB, false));
        assertThat(employers.upsert(spec(key, "Idem", 60), List.of(both.getFirst()))).isEqualTo(UpsertOutcome.UNCHANGED);
    }

    @Test
    void metadataIsKeptWhenNotSpecified() {
        String key = uniqueKey("meta");
        EmployerSpec withMetadata = spec(key, "Meta", 50).toBuilder().metadata(Map.of("ats", "greenhouse", "size", 120)).build();
        employers.upsert(withMetadata, List.of());
        assertThat(employers.upsert(withMetadata, List.of()))
                .as("same metadata re-read from jsonb (re-formatted by PostgreSQL) is not a change").isEqualTo(UpsertOutcome.UNCHANGED);
        assertThat(employers.upsert(spec(key, "Meta", 50), List.of())).isEqualTo(UpsertOutcome.UNCHANGED);
        assertThat(employers.get(key).metadata()).containsEntry("ats", "greenhouse");
        employers.upsert(spec(key, "Meta", 50).toBuilder().metadata(Map.of()).build(), List.of());
        assertThat(employers.get(key).metadata()).isEmpty();
    }

    @Test
    void updateRequiresTheCurrentVersion() {
        String key = uniqueKey("ver");
        EmployerView created = employers.create(spec(key, "Version", 50), List.of());

        EmployerView updated = employers.update(key, spec(key, "Version 2", 55), created.version());
        assertThat(updated.displayName()).isEqualTo("Version 2");
        assertThat(updated.version()).isGreaterThan(created.version());

        assertThatThrownBy(() -> employers.update(key, spec(key, "Stale", 50), created.version()))
                .isInstanceOf(ConcurrencyConflictException.class);
        assertThatThrownBy(() -> employers.update(key, spec(uniqueKey("renamed"), "x", 50), updated.version()))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("cannot be changed");
    }

    @Test
    void searchFiltersCombine() {
        String a = uniqueKey("alpha");
        String b = uniqueKey("beta");
        String c = uniqueKey("gamma");
        employers.create(spec(a, "Alpha 100% Labs", 90).toBuilder().industry("Fintech").build(), List.of(BindingSpec.of(sourceA, a)));
        employers.create(spec(b, "Beta", 40).toBuilder().industry("fintech").build(), List.of(BindingSpec.of(sourceB, b)));
        employers.create(spec(c, "Gamma", 70).toBuilder().enabled(false).build(), List.of());

        assertThat(keys(EmployerQuery.builder().tag(tag).build())).containsExactly(a, c, b);
        assertThat(keys(EmployerQuery.builder().tag(tag).minPriority(60).build())).containsExactly(a, c);
        assertThat(keys(EmployerQuery.builder().tag(tag).industry("FINTECH").build())).containsExactly(a, b);
        assertThat(keys(EmployerQuery.builder().tag(tag).sourceKey(sourceA).build())).containsExactly(a);
        assertThat(keys(EmployerQuery.builder().tag(tag).adapterId(TestJobSourceAdapter.ID).build())).containsExactly(a);
        assertThat(keys(EmployerQuery.builder().tag(tag).adapterId("not-installed-adapter").sourceKey(sourceA).build())).isEmpty();
        assertThat(keys(EmployerQuery.builder().tag(tag).sourceKey("no-such-source").build())).isEmpty();
        assertThat(keys(EmployerQuery.builder().tag(tag).enabled(false).build())).containsExactly(c);
        assertThat(keys(EmployerQuery.builder().tag(tag).text("100%").build())).as("LIKE wildcards are escaped").containsExactly(a);
        assertThat(keys(EmployerQuery.builder().tag(tag).text("_").build())).isEmpty();
    }

    @Test
    void archivedEmployersAreHiddenUnlessRequested() {
        String key = uniqueKey("arch");
        employers.create(spec(key, "Archived", 50), List.of());
        employers.archive(key);

        assertThat(keys(EmployerQuery.builder().tag(tag).build())).doesNotContain(key);
        assertThat(keys(EmployerQuery.builder().tag(tag).includeArchived(true).build())).contains(key);
        assertThat(employers.unarchive(key).archivedAt()).isNull();
    }

    @Test
    void pagesAreBoundedAndCounted() {
        for (int i = 0; i < 5; i++) {
            employers.create(spec(uniqueKey("page"), "Page " + i, 50), List.of());
        }
        PageResult<EmployerView> first = employers.search(EmployerQuery.builder().tag(tag).build(), EmployerSort.NAME, 0, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.totalItems()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
    }

    private List<String> keys(EmployerQuery query) {
        return employers.search(query, EmployerSort.PRIORITY, 0, 50).items().stream().map(EmployerView::key).toList();
    }
}
