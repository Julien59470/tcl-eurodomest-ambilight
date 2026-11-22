import java.nio.ByteBuffer;
import java.util.Arrays;
import fr.ambilight.eurodomest.FrameAnalyzer;
import fr.ambilight.eurodomest.ColorEngine;
import fr.ambilight.eurodomest.SegmentMapping;

public final class ColorMappingTests {
    private static int checks=0;
    private static void ok(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    private static ByteBuffer image(int w,int h,int top,int bottom,int left,int right,int rgb){
        ByteBuffer out=ByteBuffer.allocate(w*h*4);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=y<top||y>=h-bottom||x<left||x>=w-right?0:rgb;
            int pos=(y*w+x)*4;
            out.put(pos,(byte)(v>>16));out.put(pos+1,(byte)(v>>8));out.put(pos+2,(byte)v);out.put(pos+3,(byte)255);
        }
        return out;
    }
    private static FrameAnalyzer.Colors settle(FrameAnalyzer a,ByteBuffer b,int n){
        FrameAnalyzer.Colors result=null;
        for(int i=0;i<6;i++)result=a.analyzeZones(b,160,90,640,4,n);
        return result;
    }
    public static void main(String[] args){
        FrameAnalyzer a=new FrameAnalyzer();
        FrameAnalyzer.Colors c=settle(a,image(160,90,11,11,0,0,0x4080ee),20);
        ok(c.upper==11&&c.lower==11,"letterbox with top/bottom");
        ok(c.left==0&&c.right==0,"no side bars");
        ok(c.rgb.length==20,"zones returned");
        c=settle(a,image(160,90,0,0,20,20,0xee6030),20);
        ok(c.left==20&&c.right==20,"pillarbox detected");
        c=settle(a,image(160,90,8,8,13,13,0x3060ee),16);
        ok(c.upper==8&&c.lower==8&&c.left==13&&c.right==13,"four borders");
        c=settle(a,image(160,90,0,0,0,0,0),16);
        ok(c.darkFrame,"black detected");
        ok(c.upper==8&&c.left==13,"preserve crop during temporary blackout");
        c=settle(a,image(160,90,0,0,0,0,0x406090),16);
        ok(c.upper==0&&c.left==0&&c.lower==0&&c.right==0,"reset bars to fullscreen");
        c=settle(a,image(160,90,0,0,0,0,0x040203),20);
        ok(c.rgb[0][0]==4&&c.rgb[0][1]==2&&c.rgb[0][2]==3,"dark pixels included");
        ColorEngine.Config settings=new ColorEngine.Config(75,4,100,100,100,1.2,45);
        int[][] dim=ColorEngine.correct(new int[][]{{255,0,0},{3,3,3},{100,50,0}},settings);
        ok(dim[0][0]==191&&dim[0][1]==0,"brightness scaling");
        ok(Arrays.equals(dim[1],new int[]{0,0,0}),"black threshold");
        ok(dim[2][0]<100,"gamma reduces medium brightness");
        ok(Arrays.equals(ColorEngine.temporal(new int[][]{{65,10,0}},new int[][]{{0,0,0}},70)[0],new int[]{0,0,0}),"black immediate");
        ok(ColorEngine.sendNeeded(new int[][]{{3,0,0}},new int[][]{{0,0,0}}),"black transition must transmit");
        ok(!ColorEngine.sendNeeded(new int[][]{{12,12,12}},new int[][]{{13,12,12}}),"minor per-zone noise is ignored");
        int[] defaults=SegmentMapping.defaults(20,true);
        ok(defaults[0]==19&&defaults[19]==0,"reverse physical mapping");
        int[] changed=SegmentMapping.swap(defaults,0,6);
        ok(changed[0]==6&&SegmentMapping.valid(changed,20),"swap preserves bijection");
        ok(Arrays.equals(SegmentMapping.decode(SegmentMapping.encode(changed),20,true),changed),"mapping roundtrip");
        ok(Arrays.equals(SegmentMapping.decode("0,1,1",20,true),defaults),"invalid mapping reset");
        int[][] logical=new int[20][3];for(int i=0;i<20;i++)logical[i]=new int[]{i,0,0};
        ok(SegmentMapping.apply(logical,changed)[0][0]==6,"physical mapping selects correct zone");
        for(int n:new int[]{4,8,12,16,20}){
            int[] m=SegmentMapping.defaults(n,true);
            ok(SegmentMapping.valid(m,n),"valid map "+n);
            ok(SegmentMapping.label(0,n).startsWith("Droite"),"label right "+n);
            ok(SegmentMapping.label(n-1,n).startsWith("Gauche"),"label left "+n);
        }
        System.out.println("OK : "+checks+" assertions couleur, bordures, noir et mapping 4-20 zones");
    }
}
