package com.jobsapply.platform.discovery;

public enum SyncRunStatus {
    RUNNING,
    SUCCEEDED,
    /** Completed with some failures; successful items were persisted. */
    PARTIAL,
    FAILED,
    ABORTED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
