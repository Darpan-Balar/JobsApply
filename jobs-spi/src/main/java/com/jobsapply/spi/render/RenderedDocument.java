package com.jobsapply.spi.render;

/** Rendered bytes. Note: record equality on {@code byte[]} is by reference; compare via a content hash. */
public record RenderedDocument(String mediaType, byte[] content, String suggestedFileName) {}
