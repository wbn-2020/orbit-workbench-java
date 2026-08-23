package com.orbitworkbench.memory.application;

public record MemoryProposalSummary(
        int proposedCount,
        int rejectedCount,
        boolean blockFound
) {

    public static MemoryProposalSummary none() {
        return new MemoryProposalSummary(0, 0, false);
    }

    public static MemoryProposalSummary failed() {
        return new MemoryProposalSummary(0, 1, false);
    }
}
