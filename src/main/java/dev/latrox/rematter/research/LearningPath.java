package dev.latrox.rematter.research;

public enum LearningPath {
    DECOMPOSITION(true), ANALYSIS(true), ASSAY(true), DERIVATION(true), DISCOVERY(true), STARTING(false), ADMIN(false);
    private final boolean earned;
    LearningPath(boolean earned) { this.earned = earned; }
    public boolean earned() { return earned; }
}
