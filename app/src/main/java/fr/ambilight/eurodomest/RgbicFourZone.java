package fr.ambilight.eurodomest;

import java.util.Base64;

/** Experimental DP61 combination mode, proven at four colors on the user's box.
 * More zones require explicit visual acceptance in the TV app.
 */
public final class RgbicFourZone {
    private RgbicFourZone(){}
    public static int[][] orient(int[][] samples,int rotation,boolean reverse){
        if(samples==null||samples.length<4||samples.length>20||samples.length%4!=0)
            throw new IllegalArgumentException("Zones invalides");
        int n=samples.length;int[][] out=new int[n][3];
        for(int i=0;i<n;i++){
            int index=reverse?Math.floorMod(rotation-i,n):Math.floorMod(rotation+i,n);
            if(samples[index]==null||samples[index].length!=3)throw new IllegalArgumentException("RGB invalide");
            out[i]=samples[index].clone();
        }return out;
    }
    public static String dp61(int[][] colors,int uiSegments){
        if(colors==null||colors.length<4||colors.length>20||colors.length%4!=0||
            uiSegments<4||uiSegments>128)
            throw new IllegalArgumentException("Nombre de zones ou segments invalide");
        int count=colors.length;
        if(5+6*count>128)throw new IllegalArgumentException("Données DP61 > 128 octets");
        byte[] data=new byte[5+6*count];
        data[0]=0;data[1]=3;data[2]=0;data[3]=(byte)uiSegments;data[4]=0;
        for(int i=0;i<count;i++){
            if(colors[i]==null||colors[i].length!=3)throw new IllegalArgumentException("RGB absent");
            for(int channel:colors[i])if(channel<0||channel>255)throw new IllegalArgumentException("RGB hors limite");
            String hsv=RawRgb.hsv(colors[i]);
            for(int j=0;j<6;j++)data[5+6*i+j]=(byte)Integer.parseInt(hsv.substring(j*2,j*2+2),16);
        }
        return Base64.getEncoder().encodeToString(data);
    }
    public static int difference(int[][] before,int[][] after){
        if(before==null||after==null||before.length!=after.length)return Integer.MAX_VALUE;
        int diff=0;
        for(int i=0;i<before.length;i++)diff+=RawRgb.distance(before[i],after[i]);
        return diff;
    }
}
