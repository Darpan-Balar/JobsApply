package com.jobsapply.platform.discovery;

/**
 * @param discovered jobs returned by the source (including unparseable ones)
 * @param unchanged seen again with identical content (only "last seen" refreshed)
 * @param closed listings closed on authoritative evidence
 * @param failed items that could not be parsed or stored
 */
public record SyncCounts(int discovered, int inserted, int updated, int unchanged, int closed, int failed) {

    public static SyncCounts zero() {
        return new SyncCounts(0, 0, 0, 0, 0, 0);
    }
}
