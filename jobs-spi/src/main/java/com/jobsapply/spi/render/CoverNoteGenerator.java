package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.matching.MatchContext;
import java.util.List;
import java.util.Map;

/**
 * Chooses the fact-backed claims for a short cover note; the platform adds only a fixed greeting and closing. The
 * plugin id is the strategy name used in role profiles ({@code documents.coverNote.strategy}).
 */
public interface CoverNoteGenerator extends Plugin {

    String strategy();

    List<Claim> claims(MatchContext context, Map<String, String> skillNames);
}
