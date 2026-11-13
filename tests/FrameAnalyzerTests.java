import java.nio.ByteBuffer;
import fr.ambilight.eurodomest.FrameAnalyzer;
public class FrameAnalyzerTests {
  static void check(boolean cond, String msg){if(!cond)throw new AssertionError(msg);}
  static ByteBuffer img(int w,int h,int top,int bottom){
    ByteBuffer b=ByteBuffer.allocate(w*h*4);
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      boolean bar=y<top||y>=h-bottom;int rgb=bar?0:(x<w/2?0xff3333:0x3333ff);
      int p=(y*w+x)*4;b.put(p,(byte)(rgb>>16));b.put(p+1,(byte)(rgb>>8));b.put(p+2,(byte)rgb);b.put(p+3,(byte)255);
    }return b;
  }
  public static void main(String[] args){
    FrameAnalyzer a=new FrameAnalyzer();ByteBuffer b=img(160,90,10,10);FrameAnalyzer.Colors c=null;
    for(int i=0;i<5;i++)c=a.analyze(b,160,90,640,4);
    check(c.upper==10&&c.lower==10,"Letterbox not detected");
    check(c.rgb.length==4,"Expected four zones");
    check(c.rgb[1][2]>c.rgb[1][0],"Top-right should be blue");
    check(c.rgb[2][0]>c.rgb[2][2],"Top-left should be red");
    b=img(160,90,0,0);
    for(int i=0;i<5;i++)c=a.analyze(b,160,90,640,4);
    check(c.upper==0&&c.lower==0,"Crop did not clear");
    System.out.println("FrameAnalyzer: letterbox + 4 zones OK");
  }
}
