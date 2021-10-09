package io.josemmo.bukkit.plugin.renderer;

public class FakeMapsContainer {
    private final FakeMap[][][] fakeMaps;
    private final int delay;

    public FakeMapsContainer(FakeMap[][][] fakeMaps, int delay) {
        this.fakeMaps = fakeMaps;
        this.delay = delay;
    }

    /**
     * Get fake maps
     * @return Tri-dimensional array of maps (column, row, step)
     */
    public FakeMap[][][] getFakeMaps() {
        return fakeMaps;
    }

    /**
     * Get delay between steps
     * @return Delay in milliseconds or <code>0</code> if not applicable
     */
    public int getDelay() {
        return delay;
    }
}
