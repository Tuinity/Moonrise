package ca.spottedleaf.moonrise.patches.random_ticking;

public interface RandomTickLevelChunk {

    public void moonrise$noteRandomTickSection(final int index, final boolean ticking);

    public void moonrise$bindRandomTickSections();

    public void moonrise$ensureRandomTickSections();

    public void moonrise$beginRandomTickGetSections();

    public void moonrise$endRandomTickGetSections();

    public void moonrise$noteSectionArrayBorrowed();

    public boolean moonrise$consumeSectionArrayBorrowed();

    public int moonrise$randomTickEligibleCount();

    public long[] moonrise$randomTickSectionMask();

}
