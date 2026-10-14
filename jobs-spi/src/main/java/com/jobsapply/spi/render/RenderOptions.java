package com.jobsapply.spi.render;

import java.util.Locale;

/** @param mediaType requested output, e.g. {@code application/pdf} */
public record RenderOptions(String mediaType, Locale locale) {}
