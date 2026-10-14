package com.jobsapply.spi.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CountriesTest {

    @Test
    void resolvesCodesNamesAndAliases() {
        assertThat(Countries.toAlpha2("de")).contains("DE");
        assertThat(Countries.toAlpha2("Germany")).contains("DE");
        assertThat(Countries.toAlpha2("United States")).contains("US");
        assertThat(Countries.toAlpha2("USA")).contains("US");
        assertThat(Countries.toAlpha2("UK")).contains("GB");
        assertThat(Countries.toAlpha2(" india ")).contains("IN");
    }

    @Test
    void unknownValuesResolveToNothing() {
        assertThat(Countries.toAlpha2("Remote")).isEmpty();
        assertThat(Countries.toAlpha2("XX")).isEmpty();
        assertThat(Countries.toAlpha2(null)).isEmpty();
    }
}
