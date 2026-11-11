package fr.ambilight.eurodomest;

/** UDP raw uses exactly three unsigned bytes per RGB pixel and no header. */
public final class RawRgb {
    private RawRgb() { }
    public static int[] average(byte[] data, int size) {
        if (data == null || size < 3 || size > 490 * 3 || size % 3 != 0 || size > data.length) {
            throw new IllegalArgumentException("UDP raw : 3*N octets requis (N=1..490)");
        }
        int count = size / 3, r=0, g=0, b=0;
        for (int i=0; i<size; i+=3) { r += data[i]&255; g += data[i+1]&255; b += data[i+2]&255; }
        return new int[] { (r + count/2) / count, (g + count/2) / count, (b + count/2) / count };
    }
    /** Ordered contiguous zones. Their order follows Universal Ambient Light's LED layout. */
    public static int[][] zones(byte[] data, int size, int zoneCount) {
        if (zoneCount < 1 || zoneCount > 16 || data == null || size < 3 ||
            size > 490*3 || size % 3 != 0 || size > data.length) {
            throw new IllegalArgumentException("Trame RGB invalide");
        }
        int pixels=size/3;
        int[][] out=new int[zoneCount][3];
        for(int zone=0;zone<zoneCount;zone++) {
            int start=zone*pixels/zoneCount;
            int end=(zone+1)*pixels/zoneCount;
            if(end<=start)end=Math.min(pixels,start+1);
            int count=Math.max(1,end-start),r=0,g=0,b=0;
            for(int x=start;x<end;x++){
                r+=data[x*3]&255;g+=data[x*3+1]&255;b+=data[x*3+2]&255;
            }
            out[zone]=new int[]{(r+count/2)/count,(g+count/2)/count,(b+count/2)/count};
        }
        return out;
    }
    public static String hsv(int[] rgb) {
        float r=rgb[0]/255f, g=rgb[1]/255f, b=rgb[2]/255f;
        float max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b)), d=max-min;
        float h=0f;
        if (d != 0f) {
            if (max == r) h=((g-b)/d)%6;
            else if (max == g) h=((b-r)/d)+2;
            else h=((r-g)/d)+4;
            h*=60f;
            if (h<0) h+=360f;
        }
        float s=max==0 ? 0 : d/max;
        // Tuya DP24: H 0..360, S 0..1000, V 0..1000, each 16-bit hex.
        return String.format(java.util.Locale.ROOT, "%04x%04x%04x", Math.round(h), Math.round(s*1000), Math.round(max*1000));
    }
    public static int distance(int[] a, int[] b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        return Math.abs(a[0]-b[0])+Math.abs(a[1]-b[1])+Math.abs(a[2]-b[2]);
    }
}
