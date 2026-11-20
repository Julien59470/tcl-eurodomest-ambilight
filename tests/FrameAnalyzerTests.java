import java.nio.ByteBuffer;
import java.util.Arrays;
import fr.ambilight.eurodomest.FrameAnalyzer;
import fr.ambilight.eurodomest.LedOrientation;

public class FrameAnalyzerTests {
    static void ok(boolean cond,String message){if(!cond)throw new AssertionError(message);}
    static ByteBuffer image(int w,int h,int top,int bottom,int left,int right,boolean blank){
        ByteBuffer b=ByteBuffer.allocate(w*h*4);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            boolean bar=blank||y<top||y>=h-bottom||x<left||x>=w-right;
            int rgb=bar?0:(x<w/2?0xff3030:0x3030ff);
            int p=(y*w+x)*4;b.put(p,(byte)(rgb>>16));b.put(p+1,(byte)(rgb>>8));
            b.put(p+2,(byte)rgb);b.put(p+3,(byte)255);
        }return b;
    }
    static FrameAnalyzer.Colors settle(FrameAnalyzer analyzer,ByteBuffer b,int zones){
        FrameAnalyzer.Colors result=null;
        for(int i=0;i<5;i++)result=analyzer.analyzeZones(b,160,90,160*4,4,zones);
        return result;
    }
    public static void main(String[] args){
        FrameAnalyzer a=new FrameAnalyzer();
        FrameAnalyzer.Colors c=settle(a,image(160,90,10,10,0,0,false),4);
        ok(c.upper==10&&c.lower==10,"letterbox");
        ok(c.left==0&&c.right==0,"no pillarbox");
        ok(c.rgb[1][2]>c.rgb[1][0],"top-right blue");
        ok(c.rgb[2][0]>c.rgb[2][2],"top-left red");
        c=settle(a,image(160,90,0,0,20,20,false),20);
        ok(c.left==20&&c.right==20,"pillarbox");
        ok(c.rgb.length==20,"20 zones");
        ok(c.rgb[0][2]>c.rgb[0][0],"right video color must survive pillarbox");
        ok(c.rgb[19][0]>c.rgb[19][2],"left video color must survive pillarbox");
        c=settle(a,image(160,90,8,8,12,12,false),16);
        ok(c.upper==8&&c.lower==8&&c.left==12&&c.right==12,"four sides");
        c=settle(a,image(160,90,0,0,0,0,true),16);
        ok(c.darkFrame,"black scene detected");
        ok(c.left==12&&c.right==12,"do not chase black scene");
        c=settle(a,image(160,90,0,0,0,0,false),8);
        ok(c.upper==0&&c.lower==0&&c.left==0&&c.right==0,"clear crop");
        int[][] zones=new int[20][3];
        for(int i=0;i<20;i++)zones[i]=new int[]{i,i*2,i*3};
        int[][] phys=LedOrientation.physical(zones,true);
        ok(Arrays.equals(phys[0],zones[19])&&Arrays.equals(phys[19],zones[0]),"physical reverse 20");
        ok(Arrays.equals(LedOrientation.physical(zones,false)[0],zones[0]),"direct mapping");
        System.out.println("OK: crop 4 sides, black-scene stability, 4/8/16/20 zones, orientation");
    }
}
