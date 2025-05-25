package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public class SnapshotToken implements Serializable {
    private static final long serialVersionUID = 1L;
    private int fishCount;
    private boolean isInitiator;

    public SnapshotToken(int fishCount, boolean isInitiator) {
        this.fishCount = fishCount;
        this.isInitiator = isInitiator;
    }

    public int getFishCount() {
        return fishCount;
    }

    public void addFishCount(int count) {
        this.fishCount += count;
    }

    public boolean isInitiator() {
        return isInitiator;
    }
}
