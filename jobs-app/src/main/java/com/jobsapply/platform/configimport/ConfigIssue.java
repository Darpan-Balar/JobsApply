package com.jobsapply.platform.configimport;

/**
 * A configuration problem.
 *
 * @param file path of the file, relative to the configuration directory
 * @param path dotted path of the offending element inside the document ({@code ""} for the whole document)
 */
public record ConfigIssue(String file, String path, String message) {

    @Override
    public String toString() {
        return path.isEmpty() ? file + ": " + message : file + ":" + path + ": " + message;
    }
}
