package com.jobsapply.platform.configimport;

/**
 * A successfully parsed and structurally valid document, already upgraded to the latest schema version.
 *
 * @param sourceSchemaVersion the version declared in the file (before upgrade)
 */
public record ConfigDocument(String file, ConfigKind kind, int sourceSchemaVersion, Object content) {

    public <T> T contentAs(Class<T> type) {
        return type.cast(content);
    }
}
