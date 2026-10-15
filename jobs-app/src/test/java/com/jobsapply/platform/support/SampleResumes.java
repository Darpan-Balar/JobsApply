package com.jobsapply.platform.support;

/** A fictional resume (no real person) for import tests. */
public final class SampleResumes {

    private SampleResumes() {}

    public static final String CLASSIC = """
            JANE EXAMPLE
            Senior Backend Engineer
            Berlin, Germany | jane.example@example.com | +49 30 1234 5678
            linkedin.com/in/jane-example

            EXPERIENCE
            Senior Backend Engineer | Beta AG | Berlin, Germany
            Jan 2023 – Present
            • Built an event pipeline on Kafka processing 2M events per day.
            Software Engineer, Example GmbH                      03/2019 - 12/2022
            • Developed REST APIs in Java and PostgreSQL.

            EDUCATION
            Example University
            B.Sc. in Computer Science, 2014 – 2018

            SKILLS
            Java, Kotlin, SQL, Spring Boot, Docker, pandas
            """;
}
