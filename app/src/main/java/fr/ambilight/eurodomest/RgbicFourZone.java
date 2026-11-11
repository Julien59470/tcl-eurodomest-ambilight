package fr.ambilight.eurodomest;

import java.util.Base64;

/**
 * EXPERIMENTAL Tuya paint_colour_data (DP61), combination mode. Not a proven
 * model-specific profile. Encodes evenly distributed color blocks, not pixels.
 * Source: Tuya Strip Lights Template / HDMI TV Lighting/65 community profile.
 */
public final class RgbicFourZone {
    private RgbicFourZone() {}
    public static int[][] orient(int[][] samples, int rotation, boolean reverse) {
        if(samples==null||samples.length!=4)throw new IllegalArgumentException("4 zones requises");
        int[][] result=new int[4][3];
        for(int i=0;i<4;i++){
            int idx=reverse?Math.floorMod(rotation-i,4):Math.floorMod(rotation+i,4);
            if(samples[idx]==null||samples[idx].length!=3)throw new IllegalArgumentException("RGB incorrect");
            result[i]=samples[idx].clone();
        }
        return result;
    }
    public static String dp61(int[][] colors, int uiSegments) {
        if(colors==null||colors.length!=4||uiSegments<4||uiSegments>128)
            throw new IllegalArgumentException("4 couleurs et 4-128 segments requis");
        byte[] payload=new byte[5+6*4];
        // 00 version, 03 combination, 00 no gradient, uiSegments, 00 even distribution
        payload[0]=0;payload[1]=3;payload[2]=0;payload[3]=(byte)uiSegments;payload[4]=0;
        for(int i=0;i<4;i++) {
            if(colors[i]==null||colors[i].length!=3)throw new IllegalArgumentException("RGB absent");
            for(int c:colors[i])if(c<0||c>255)throw new IllegalArgumentException("Canal RGB invalide");
            String hsv=RawRgb.hsv(colors[i]);
            for(int j=0;j<6;j++)payload[5+6*i+j]=(byte)Integer.parseInt(hsv.substring(j*2,j*2+2),16);
        }
        return Base64.getEncoder().encodeToString(payload);
    }
    public static int difference(int[][] before,int[][] after){
        if(before==null||after==null)return Integer.MAX_VALUE;
        if(before.length!=after.length)return Integer.MAX_VALUE;
        int sum=0;
        for(int i=0;i<before.length;i++)sum+=RawRgb.distance(before[i],after[i]);
        return sum;
    }
}
