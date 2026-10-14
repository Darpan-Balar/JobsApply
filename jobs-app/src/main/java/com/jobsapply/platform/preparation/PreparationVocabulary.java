package com.jobsapply.platform.preparation;

import java.util.Set;

/** Names that templates and role profiles may reference, from the installed plugins. Used by config validation. */
public interface PreparationVocabulary {

    Set<String> contentGenerators();

    Set<String> renderers();

    /** Cover-note strategies; {@code none} is always accepted. */
    Set<String> coverNoteStrategies();
}
