package com.jobsapply.platform.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MasterResumeServiceTest {

    @Test
    void displayNamesCannotCarryPathsOrControlCharacters() {
        assertThat(MasterResumeService.safeName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(MasterResumeService.safeName("C:\\Users\\me\\Jane CV.pdf")).isEqualTo("Jane CV.pdf");
        assertThat(MasterResumeService.safeName("cv\r\n\".pdf")).isEqualTo("cv.pdf");
        assertThat(MasterResumeService.safeName(null)).isEqualTo("resume");
    }
}
