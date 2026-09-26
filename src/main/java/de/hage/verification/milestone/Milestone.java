package de.hage.verification.milestone;

public final class Milestone {

    private final String id;
    private final String displayName;
    private final MilestoneType type;
    private final long threshold;
    private final String value;

    public Milestone(String id, String displayName, MilestoneType type, long threshold, String value) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.threshold = threshold;
        this.value = value;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public MilestoneType getType() { return type; }
    public long getThreshold() { return threshold; }
    public String getValue() { return value; }
}