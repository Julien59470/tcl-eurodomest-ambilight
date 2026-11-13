package fr.ambilight.eurodomest;

import java.nio.ByteBuffer;

/** Small allocation-free sampling core. Coordinates follow the physical strip:
 * right bottom -> right top -> top right -> top left -> left bottom.
 * Never samples the black bars when a stable letterbox is detected.
 */
public final class FrameAnalyzer {
    public static final class Colors {
        public final int[][] rgb;
        public final int upper, lower;
        public Colors(int[][] rgb, int upper, int lower) {
            this.rgb=rgb; this.upper=upper; this.lower=lower;
        }
    }
    private int upper=0, lower=0, candidateTop=-1, candidateBottom=-1, stable=0;
    private static int clamp(int v,int low,int high){return Math.min(high,Math.max(low,v));}
    private static int channel(ByteBuffer b,int offset){return b.get(offset)&255;}
    private static boolean dark(ByteBuffer b,int w,int h,int stride,int ps,int y){
        int samples=0,black=0;
        for(int x=w/8;x<7*w/8;x+=Math.max(1,w/16)){
            int off=y*stride+x*ps;
            int r=channel(b,off),g=channel(b,off+1),bl=channel(b,off+2);
            if(Math.max(r,Math.max(g,bl))<29)black++;
            samples++;
        }
        return samples>0 && black*100>=samples*93;
    }
    /** @param stride bytes per row, ps bytes per pixel (usually four). */
    public Colors analyze(ByteBuffer buffer,int w,int h,int stride,int ps){
        if(buffer==null || w<32 || h<24 || ps<3 || stride<w*ps ||
           (long)(h-1)*stride+(long)(w-1)*ps+2>=buffer.limit())
            throw new IllegalArgumentException("Image RGBA invalide");
        int proposedTop=0, proposedBottom=0;
        int limit=h/3;
        for(int y=0;y<limit;y++){
            if(dark(buffer,w,h,stride,ps,y))proposedTop=y+1;
            else break;
        }
        for(int y=h-1;y>=h-limit;y--){
            if(dark(buffer,w,h,stride,ps,y))proposedBottom=h-y;
            else break;
        }
        // Treat dark scenes cautiously. Require both black bars of useful height.
        if(proposedTop<3 || proposedBottom<3 || proposedTop+proposedBottom>h/2){
            proposedTop=0;proposedBottom=0;
        }
        if(candidateTop==proposedTop && candidateBottom==proposedBottom)stable++;
        else {candidateTop=proposedTop;candidateBottom=proposedBottom;stable=1;}
        if(stable>=4){upper=proposedTop;lower=proposedBottom;}
        int top=upper, bottom=h-1-lower;
        if(bottom-top<12){top=0;bottom=h-1;}
        int usable=Math.max(1,bottom-top);
        int[][] colors=new int[4][];
        // Four logical color zones follow the actual physical LED path.
        colors[0]=sampleArea(buffer,w,h,stride,ps,(int)(w*.91),(int)(top+usable*.22),(int)(w*.97),(int)(top+usable*.78));
        colors[1]=sampleArea(buffer,w,h,stride,ps,(int)(w*.56),top+Math.max(1,(int)(usable*.04)),(int)(w*.91),top+Math.max(2,(int)(usable*.13)));
        colors[2]=sampleArea(buffer,w,h,stride,ps,(int)(w*.09),top+Math.max(1,(int)(usable*.04)),(int)(w*.44),top+Math.max(2,(int)(usable*.13)));
        colors[3]=sampleArea(buffer,w,h,stride,ps,(int)(w*.03),(int)(top+usable*.22),(int)(w*.09),(int)(top+usable*.78));
        return new Colors(colors,upper,lower);
    }
    private static int[] sampleArea(ByteBuffer b,int w,int h,int stride,int ps,int x0,int y0,int x1,int y1){
        x0=clamp(x0,0,w-1);x1=clamp(Math.max(x0+1,x1),1,w);
        y0=clamp(y0,0,h-1);y1=clamp(Math.max(y0+1,y1),1,h);
        int[] sums=new int[3];int count=0;
        for(int gy=0;gy<4;gy++)for(int gx=0;gx<6;gx++){
            int x=clamp(x0+(2*gx+1)*(x1-x0)/12,0,w-1);
            int y=clamp(y0+(2*gy+1)*(y1-y0)/8,0,h-1);
            int off=y*stride+x*ps;
            int r=channel(b,off),g=channel(b,off+1),bl=channel(b,off+2);
            // Ignore nearly black pixels to reduce letterbox bleed (unless all are black).
            if(r+g+bl<30)continue;
            sums[0]+=r;sums[1]+=g;sums[2]+=bl;count++;
        }
        if(count==0)return new int[]{0,0,0};
        return new int[]{sums[0]/count,sums[1]/count,sums[2]/count};
    }
}
