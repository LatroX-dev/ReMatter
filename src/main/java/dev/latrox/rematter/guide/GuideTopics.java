package dev.latrox.rematter.guide;

/** The same teaching topics in the dependency-free book and the GuideME chapter tree. */
public enum GuideTopics {
    RESEARCH(29, "analyzer.md"), PRIMAL_ORES(52, "primal.md"), PRIMAL_CELLS(54, "primal.md"), CONVERTER(56, "primal.md"),
    DISCOVERY(58, "discovery.md"), HARDWARE(61, "hardware.md"), THROUGHPUT(64, "throughput.md"), GENERATORS(66, "generation.md"), ENTANGLED_LINK(68, "links.md"), ENERGY_STORAGE(70,"energy.md"), CELL_TRANSFERS(72,"automation.md"), CELL_PARTITIONS(73,"automation.md");
    public static final int PAGE_COUNT = 76;
    private final int bookPage;
    private final String chapter;
    GuideTopics(int bookPage, String chapter) { this.bookPage = bookPage; this.chapter = chapter; }
    public int bookPage() { return bookPage; }
    public String chapter() { return chapter; }
}
