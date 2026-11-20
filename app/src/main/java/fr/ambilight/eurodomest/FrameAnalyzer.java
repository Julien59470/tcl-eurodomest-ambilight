package fr.ambilight.eurodomest;

import java.nio.ByteBuffer;

/** Local 4-sided crop and ordered 3-sided RGBIC sampling. No image is stored. */
public final class FrameAnalyzer {
    public static final class Colors {
        public final int[][] rgb;
        public final int upper,lower,left,right,brightness;
        public final boolean darkFrame;
        Colors(int[][] rgb,int top,int bottom,int left,int right,int brightness,boolean dark) {
            this.rgb=rgb;this.upper=top;this.lower=bottom;this.left=left;this.right=right;
            this.brightness=brightness;this.darkFrame=dark;
        }
    }
    private final int[] borders=new int[4],candidates={-1,-1,-1,-1},stable=new int[4];
    private static int clamp(int n,int low,int high){return Math.max(low,Math.min(high,n));}
    private static int ch(ByteBuffer p,int off){return p.get(off)&255;}
    private static int value(ByteBuffer b,int off){
        return Math.max(ch(b,off),Math.max(ch(b,off+1),ch(b,off+2)));
    }
    private static boolean darkRow(ByteBuffer b,int w,int h,int stride,int ps,int y){
        int dark=0,total=0;
        // Several points well inside the active central columns. Ignore logos at edges.
        for(int x=w/4;x<=3*w/4;x+=Math.max(1,w/24)){
            total++;
            if(value(b,y*stride+x*ps)<27)dark++;
        }
        return dark*100>=total*90;
    }
    private static boolean darkColumn(ByteBuffer b,int w,int h,int stride,int ps,int x){
        int dark=0,total=0;
        for(int y=h/4;y<=3*h/4;y+=Math.max(1,h/18)){
            total++;
            if(value(b,y*stride+x*ps)<27)dark++;
        }
        return dark*100>=total*90;
    }
    private void accept(int i,int proposed){
        if(candidates[i]==proposed)stable[i]++;else{candidates[i]=proposed;stable[i]=1;}
        if(stable[i]>=4)borders[i]=proposed; // temporal confirmation, per-side hysteresis
    }
    public Colors analyze(ByteBuffer b,int w,int h,int stride,int ps){
        return analyzeZones(b,w,h,stride,ps,4);
    }
    public Colors analyzeZones(ByteBuffer b,int w,int h,int stride,int ps,int count){
        if(b==null||w<32||h<24||ps<3||stride<w*ps||
            (long)(h-1)*stride+(long)(w-1)*ps+2>=b.limit() ||
            count<4||count>20||count%4!=0)
            throw new IllegalArgumentException("Image ou nombre de zones invalide");
        int bright=0,illum=0,checks=0;
        for(int gy=1;gy<=5;gy++)for(int gx=1;gx<=7;gx++){
            int x=gx*w/8,y=gy*h/6;
            int v=value(b,y*stride+x*ps);
            illum+=v;checks++;if(v>=55)bright++;
        }
        int brightness=illum/checks;
        boolean dark=bright<3;
        // When the image is uniformly dark, keep the previous crop. Do not chase darkness.
        if(!dark){
            int top=0,bottom=0,left=0,right=0;
            for(int y=0;y<h/3;y++){if(darkRow(b,w,h,stride,ps,y))top++;else break;}
            for(int y=h-1;y>=2*h/3;y--){if(darkRow(b,w,h,stride,ps,y))bottom++;else break;}
            for(int x=0;x<w/3;x++){if(darkColumn(b,w,h,stride,ps,x))left++;else break;}
            for(int x=w-1;x>=2*w/3;x--){if(darkColumn(b,w,h,stride,ps,x))right++;else break;}
            if(top<3)top=0;if(bottom<3)bottom=0;
            if(left<3)left=0;if(right<3)right=0;
            if(top+bottom>=h/2){top=0;bottom=0;}
            if(left+right>=w/2){left=0;right=0;}
            accept(0,top);accept(1,bottom);accept(2,left);accept(3,right);
        }
        int l=borders[2],r=w-1-borders[3],t=borders[0],bt=h-1-borders[1];
        if(r-l<24){l=0;r=w-1;}
        if(bt-t<18){t=0;bt=h-1;}
        int usefulW=r-l+1,usefulH=bt-t+1;
        int side=count/4,alongTop=count/2;
        int[][] out=new int[count][3];
        for(int i=0;i<side;i++){
            double v=(i+0.5)/side;
            int cy=bt-(int)Math.round(v*(usefulH-1));
            int spread=Math.max(2,usefulH/Math.max(3,side*3));
            out[i]=sample(b,w,h,stride,ps,r-Math.max(3,usefulW/14),cy-spread,r-Math.max(1,usefulW/45),cy+spread);
        }
        for(int i=0;i<alongTop;i++){
            double v=(i+0.5)/alongTop;
            int cx=r-(int)Math.round(v*(usefulW-1));
            int spread=Math.max(2,usefulW/Math.max(6,alongTop*3));
            out[side+i]=sample(b,w,h,stride,ps,cx-spread,t+Math.max(1,usefulH/35),
                cx+spread,t+Math.max(3,usefulH/9));
        }
        for(int i=0;i<side;i++){
            double v=(i+0.5)/side;
            int cy=t+(int)Math.round(v*(usefulH-1));
            int spread=Math.max(2,usefulH/Math.max(3,side*3));
            out[side+alongTop+i]=sample(b,w,h,stride,ps,l+Math.max(1,usefulW/45),cy-spread,
                l+Math.max(3,usefulW/14),cy+spread);
        }
        return new Colors(out,borders[0],borders[1],borders[2],borders[3],brightness,dark);
    }
    private static int[] sample(ByteBuffer b,int w,int h,int stride,int ps,int xa,int ya,int xb,int yb){
        int x0=clamp(Math.min(xa,xb),0,w-1),x1=clamp(Math.max(xa,xb),x0+1,w);
        int y0=clamp(Math.min(ya,yb),0,h-1),y1=clamp(Math.max(ya,yb),y0+1,h);
        int[] sum=new int[3];int samples=0;
        for(int gy=0;gy<4;gy++)for(int gx=0;gx<6;gx++){
            int x=clamp(x0+(2*gx+1)*(x1-x0)/12,0,w-1);
            int y=clamp(y0+(2*gy+1)*(y1-y0)/8,0,h-1);
            int off=y*stride+x*ps;
            int rr=ch(b,off),gg=ch(b,off+1),bb=ch(b,off+2);
            if(rr+gg+bb<30)continue;
            sum[0]+=rr;sum[1]+=gg;sum[2]+=bb;samples++;
        }
        if(samples==0)return new int[]{0,0,0};
        return new int[]{sum[0]/samples,sum[1]/samples,sum[2]/samples};
    }
}
