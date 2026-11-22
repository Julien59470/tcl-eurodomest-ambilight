package fr.ambilight.eurodomest;

import java.nio.ByteBuffer;

/** Robust, allocation-light 4-side crop and edge sampling at 160x90.
 * Samples INCLUDE black pixels: a dark area must drive dark LED output.
 * Logical order: RIGHT bottom-to-top, TOP right-to-left, LEFT top-to-bottom.
 */
public final class FrameAnalyzer {
    public static final class Colors {
        public final int[][] rgb;
        public final int upper, lower, left, right, brightness;
        public final boolean darkFrame;
        Colors(int[][] rgb,int top,int bottom,int left,int right,int brightness,boolean dark){
            this.rgb=rgb;this.upper=top;this.lower=bottom;this.left=left;this.right=right;
            this.brightness=brightness;this.darkFrame=dark;
        }
    }
    private final int[] borders=new int[4], proposed={-1,-1,-1,-1}, stability=new int[4];
    private static int cap(int n,int a,int b){return Math.max(a,Math.min(b,n));}
    private static int color(ByteBuffer b,int off){return Math.max(b.get(off)&255,Math.max(b.get(off+1)&255,b.get(off+2)&255));}
    private static boolean darkRow(ByteBuffer b,int w,int stride,int pixel,int y){
        int dark=0,total=0,step=Math.max(1,w/28);
        for(int x=w/6;x<5*w/6;x+=step){total++;if(color(b,y*stride+x*pixel)<25)dark++;}
        return total>0&&dark*100>=total*94;
    }
    private static boolean darkColumn(ByteBuffer b,int h,int stride,int pixel,int x){
        int dark=0,total=0,step=Math.max(1,h/22);
        for(int y=h/6;y<5*h/6;y+=step){total++;if(color(b,y*stride+x*pixel)<25)dark++;}
        return total>0&&dark*100>=total*94;
    }
    private void update(int index,int candidate){
        if(proposed[index]==candidate)stability[index]++;else{proposed[index]=candidate;stability[index]=1;}
        if(stability[index]>=4)borders[index]=candidate;
    }
    public Colors analyze(ByteBuffer b,int w,int h,int stride,int pixel){
        return analyzeZones(b,w,h,stride,pixel,4);
    }
    public Colors analyzeZones(ByteBuffer b,int w,int h,int stride,int pixel,int count){
        if(b==null||w<32||h<24||pixel<3||stride<w*pixel||
           (long)(h-1)*stride+(long)(w-1)*pixel+2>=b.limit()||
           count<4||count>20||count%4!=0)
            throw new IllegalArgumentException("Image RGBA / nombre de zones incorrect");
        int lit=0,bright=0,seen=0;
        for(int iy=1;iy<=7;iy++)for(int ix=1;ix<=9;ix++){
            int v=color(b,(iy*h/8)*stride+(ix*w/10)*pixel);
            if(v>=60)lit++;
            bright+=v;seen++;
        }
        boolean dark=lit<3;
        // Preserve prior boundaries during black frames, fades and very dark scenes.
        if(!dark){
            int top=0,bottom=0,left=0,right=0;
            for(int y=0;y<h/3;y++){if(darkRow(b,w,stride,pixel,y))top++;else break;}
            for(int y=h-1;y>=2*h/3;y--){if(darkRow(b,w,stride,pixel,y))bottom++;else break;}
            for(int x=0;x<w/3;x++){if(darkColumn(b,h,stride,pixel,x))left++;else break;}
            for(int x=w-1;x>=2*w/3;x--){if(darkColumn(b,h,stride,pixel,x))right++;else break;}
            if(top<3)top=0;if(bottom<3)bottom=0;
            if(left<3)left=0;if(right<3)right=0;
            if(top+bottom>h/2){top=0;bottom=0;}
            if(left+right>w/2){left=0;right=0;}
            // Ensure bright content exists just inside the candidate boundary.
            if(top>0&&!hasContent(b,w,h,stride,pixel,0,top,w,h-bottom))top=0;
            update(0,top);update(1,bottom);update(2,left);update(3,right);
        }
        int l=borders[2],r=w-borders[3],t=borders[0],bot=h-borders[1];
        if(r-l<24){l=0;r=w;}
        if(bot-t<18){t=0;bot=h;}
        int usefulW=r-l,usefulH=bot-t,side=count/4,topCount=count/2;
        int[][] out=new int[count][3];
        int depthX=Math.max(2,usefulW/16),depthY=Math.max(2,usefulH/12);
        int marginX=Math.max(1,usefulW/160),marginY=Math.max(1,usefulH/90);
        for(int i=0;i<side;i++){
            int cy=bot-1-(int)((i+.5)*usefulH/side);
            int half=Math.max(2,usefulH/(side*3));
            out[i]=sample(b,w,h,stride,pixel,r-marginX-depthX,cy-half,r-marginX,cy+half);
        }
        for(int i=0;i<topCount;i++){
            int cx=r-1-(int)((i+.5)*usefulW/topCount);
            int half=Math.max(2,usefulW/(topCount*3));
            out[side+i]=sample(b,w,h,stride,pixel,cx-half,t+marginY,cx+half,t+marginY+depthY);
        }
        for(int i=0;i<side;i++){
            int cy=t+(int)((i+.5)*usefulH/side);
            int half=Math.max(2,usefulH/(side*3));
            out[side+topCount+i]=sample(b,w,h,stride,pixel,l+marginX,cy-half,l+marginX+depthX,cy+half);
        }
        return new Colors(out,borders[0],borders[1],borders[2],borders[3],bright/seen,dark);
    }
    private static boolean hasContent(ByteBuffer b,int w,int h,int stride,int ps,int x0,int y0,int x1,int y1){
        int count=0;
        for(int y=Math.max(0,y0);y<Math.min(h,y1);y+=Math.max(1,h/8))
            for(int x=Math.max(0,x0);x<Math.min(w,x1);x+=Math.max(1,w/12))
                if(color(b,y*stride+x*ps)>50)count++;
        return count>2;
    }
    private static int[] sample(ByteBuffer b,int w,int h,int stride,int ps,int xa,int ya,int xb,int yb){
        int x0=cap(Math.min(xa,xb),0,w-1),x1=cap(Math.max(xa,xb),x0+1,w);
        int y0=cap(Math.min(ya,yb),0,h-1),y1=cap(Math.max(ya,yb),y0+1,h);
        long r=0,g=0,blue=0;
        final int NX=8,NY=6,number=NX*NY;
        for(int iy=0;iy<NY;iy++)for(int ix=0;ix<NX;ix++){
            int x=cap(x0+(2*ix+1)*(x1-x0)/(2*NX),0,w-1);
            int y=cap(y0+(2*iy+1)*(y1-y0)/(2*NY),0,h-1);
            int p=y*stride+x*ps;
            r+=b.get(p)&255;g+=b.get(p+1)&255;blue+=b.get(p+2)&255;
        }
        return new int[]{(int)((r+number/2)/number),(int)((g+number/2)/number),(int)((blue+number/2)/number)};
    }
}
