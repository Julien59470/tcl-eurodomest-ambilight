package fr.ambilight.eurodomest;

/** Local brightness, black cutoff, white balance and temporal filtering.
 * All components derived from the actual frame, with NO forced minimum light.
 */
public final class ColorEngine {
    private ColorEngine(){}
    public static final class Config {
        public final int brightness,black,red,green,blue,smooth;
        public final double gamma;
        public Config(int brightness,int black,int red,int green,int blue,double gamma,int smooth){
            if(brightness<0||brightness>100||black<0||black>30||
                red<50||red>150||green<50||green>150||blue<50||blue>150||
                gamma<.7||gamma>2.5||smooth<0||smooth>100)
                throw new IllegalArgumentException("Réglage de couleurs hors limites");
            this.brightness=brightness;this.black=black;this.red=red;this.green=green;this.blue=blue;
            this.gamma=gamma;this.smooth=smooth;
        }
    }
    private static int clamp(int c){return Math.max(0,Math.min(255,c));}
    /** Gamma applied per RGB channel after retaining true black area coverage in source sampling. */
    public static int[][] correct(int[][] rgb,Config config){
        int[][] result=new int[rgb.length][3];int[] gains={config.red,config.green,config.blue};
        for(int i=0;i<rgb.length;i++){
            int max=Math.max(rgb[i][0],Math.max(rgb[i][1],rgb[i][2]));
            if(max<=config.black)continue;
            for(int c=0;c<3;c++){
                double light=Math.pow(clamp(rgb[i][c])/255.0,config.gamma);
                result[i][c]=clamp((int)Math.round(255*light*config.brightness/100.0*gains[c]/100.0));
            }
        }
        return result;
    }
    /** Hard off for black target; fast response to scene cuts, smoother on smaller variations. */
    public static int[][] temporal(int[][] previous,int[][] target,int smooth){
        if(previous==null)return copy(target);
        int[][] out=new int[target.length][3];
        for(int i=0;i<target.length;i++){
            int[] b=target[i],a=previous[i];
            if(b[0]==0&&b[1]==0&&b[2]==0)continue;
            int delta=Math.max(Math.abs(b[0]-a[0]),Math.max(Math.abs(b[1]-a[1]),Math.abs(b[2]-a[2])));
            double factor=delta>=85?Math.max(.80,1.0-smooth/250.0):
                 Math.max(.25,1.0-smooth/130.0);
            for(int c=0;c<3;c++)out[i][c]=clamp((int)Math.round(a[c]+factor*(b[c]-a[c])));
        }
        return out;
    }
    public static boolean sendNeeded(int[][] before,int[][] after){
        if(before==null||before.length!=after.length)return true;
        for(int i=0;i<after.length;i++){
            int a=Math.max(before[i][0],Math.max(before[i][1],before[i][2]));
            int b=Math.max(after[i][0],Math.max(after[i][1],after[i][2]));
            if((a==0)!=(b==0))return true;
            if(Math.abs(before[i][0]-after[i][0])+
               Math.abs(before[i][1]-after[i][1])+
               Math.abs(before[i][2]-after[i][2])>=5)return true;
        }
        return false;
    }
    public static int[][] black(int n){SegmentMapping.validateCount(n);return new int[n][3];}
    private static int[][] copy(int[][] x){
        int[][] out=new int[x.length][3];
        for(int i=0;i<x.length;i++)out[i]=x[i].clone();
        return out;
    }
}
