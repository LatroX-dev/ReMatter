package dev.latrox.rematter.guide;

import java.util.Locale;

/** Actual server-side milestones; none is a synthesis or recipe prerequisite. */
public enum GuidanceEvent {
    START_WORKSHOP, FIRST_LESSON, PUT_IT_BACK, HANDS_OFF, PROPER_TOOLS,
    SOMETHING_OLDER, ON_PAPER, INDUSTRIAL_SCALE, GROWN_NOT_MINED, ONE_WAY;
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String advancement() { return "rematter:progression/" + id(); }
}
