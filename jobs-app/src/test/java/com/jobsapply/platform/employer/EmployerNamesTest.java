package com.jobsapply.platform.employer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EmployerNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Acme, Inc.|acme",
        "ACME Corporation|acme",
        "Acme Co. Ltd|acme",
        "Société Générale|societe generale",
        "Two Sigma|two sigma",
        "  Jane Street  |jane street",
        "Inc|inc",
    })
    void normalizes(String input, String expected) {
        assertThat(EmployerNames.normalize(input)).isEqualTo(expected);
    }
}
