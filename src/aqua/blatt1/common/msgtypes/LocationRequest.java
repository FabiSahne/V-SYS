package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public final class LocationRequest implements Serializable {
    private final String fishId;

    public LocationRequest(String fishId) {
        this.fishId = fishId;
    }

    public String getFishId() {
        return fishId;
    }
}
