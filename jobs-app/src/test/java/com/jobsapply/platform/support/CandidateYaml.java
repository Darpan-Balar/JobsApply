package com.jobsapply.platform.support;

/** Synthetic candidate profile documents (fictional person). */
public final class CandidateYaml {

    private CandidateYaml() {}

    public static String profile(String key) {
        return """
                schemaVersion: 1
                kind: CandidateProfile
                key: %s
                displayName: Main profile
                contact:
                  fullName: Jane Example
                  email: jane.example@example.com
                  countryCode: DE
                experiences:
                  - id: exp-acme
                    verification: VERIFIED
                    employer: Example GmbH
                    title: Software Engineer
                    start: 2019-01
                    end: 2022-12
                    skills: [java, postgresql]
                  - id: exp-beta
                    verification: VERIFIED
                    employer: Beta AG
                    title: Senior Backend Engineer
                    start: 2023-01
                    skills: [spring-boot, kafka]
                education:
                  - id: edu-1
                    verification: VERIFIED
                    institution: Example University
                    level: BACHELOR
                    field: Computer Science
                    completed: true
                skills:
                  - { id: sk-java, verification: VERIFIED, skill: java, years: 7 }
                achievements:
                  - id: ach-kafka
                    verification: VERIFIED
                    text: Built an event pipeline on Kafka processing 2M events per day.
                    experienceId: exp-beta
                    skills: [kafka]
                attributes:
                  - { id: auth-de, verification: VERIFIED, key: work-authorization.de, value: citizen }
                  - { id: pref-city, verification: USER_PREFERENCE, key: preferred-city, value: Berlin }
                """.formatted(key);
    }
}
