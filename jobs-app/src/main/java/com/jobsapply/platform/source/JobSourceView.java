package com.jobsapply.platform.source;

import java.util.Map;
import java.util.UUID;

/** @param adapterInstalled whether the named adapter plugin is registered in this installation */
public record JobSourceView(
        UUID id, String key, String adapterId, String displayName, boolean enabled, boolean adapterInstalled,
        Map<String, Object> settings, long version) {}
