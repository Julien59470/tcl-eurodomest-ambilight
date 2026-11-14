package fr.ambilight.eurodomest;

/** Maps screen zones to the real DP61 order. The Eurodomest tested strip
 * displays the DP61 first color at the physical LEFT end, while the camera's
 * first sample is the RIGHT side (strip cable starts bottom right).
 */
public final class LedOrientation {
    private LedOrientation() { }
    public static int[][] physical(int[][] screen, boolean reversed) {
        if (screen == null || screen.length != 4) throw new IllegalArgumentException("Four zones required");
        int[][] result = new int[4][3];
        for (int i = 0; i < 4; i++) {
            int[] src = screen[reversed ? 3-i : i];
            if (src == null || src.length != 3) throw new IllegalArgumentException("Invalid RGB zone");
            result[i] = src.clone();
        }
        return result;
    }
}
