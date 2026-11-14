package fr.ambilight.eurodomest;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.content.Context;
import java.nio.ByteBuffer;

/**
 * Optional no-MediaProjection capture method for Android 11+.
 * User explicitly enables the accessibility service in Android settings.
 * No ADB, root, network screen streaming or saved screenshots.
 *
 * This is EXPERIMENTAL: screenshots arrive at display resolution and the
 * mandatory GPU -> CPU copy may worsen 4K playback on some Android TVs.
 */
public final class AccessibilityCaptureService extends AccessibilityService {
    private static final String PREF="ambilight_accessibility_v06";
    private static final String RUNTIME="ambilight_v06_runtime";
    private static final int WIDTH=160, HEIGHT=90;
    private static volatile AccessibilityCaptureService instance;
    private volatile boolean live=false, wanted=false;
    private final FrameAnalyzer analyzer=new FrameAnalyzer();
    private HandlerThread captureThread;
    private Handler handler;
    private Thread writer;
    private Bitmap scaled;
    private Canvas scaledCanvas;
    private Paint paint;
    private final int[] pixels=new int[WIDTH*HEIGHT];
    private final byte[] rgba=new byte[WIDTH*HEIGHT*4];
    private final ByteBuffer bytes=ByteBuffer.wrap(rgba);
    private volatile FrameAnalyzer.Colors latest;
    private volatile long latestAt=0, sampled=0, sent=0, failures=0, ackMs=0, maxAckMs=0;
    private volatile long captureCostMs=0, accessibilityFailures=0, rateMs=200;
    private volatile String state="Service d'accessibilité non démarré";
    private long startWindow=0, windowFrames=0, measuredFps=0;

    private Settings config;

    public static boolean connected(){return instance!=null && instance.live;}
    public static boolean wanted(Context context){
        return context.getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("wanted",false);
    }
    public static void enable(Context context, boolean enabled){
        context.getSharedPreferences(PREF,MODE_PRIVATE).edit().putBoolean("wanted",enabled).apply();
        AccessibilityCaptureService current=instance;
        if(current!=null)current.wanted=enabled;
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        instance=this; live=true; config=new Settings(this);
        wanted=wanted(this);
        captureThread=new HandlerThread("TCL-Accessibility-Capture");
        captureThread.start();
        handler=new Handler(captureThread.getLooper());
        scaled=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        scaledCanvas=new Canvas(scaled);
        paint=new Paint(Paint.FILTER_BITMAP_FLAG);
        state=wanted?"Capture par accessibilité active":"Service prêt, appuyer sur Démarrer";
        handler.post(this::captureTick);
        writer=new Thread(this::writeLoop,"TCL-Accessibility-Tuya");
        writer.start();save();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* no event logging */ }
    @Override public void onInterrupt(){state="Accessibilité interrompue";save();}
    private void schedule(long delay){
        Handler h=handler;
        if(h!=null && live)h.postDelayed(this::captureTick,delay);
    }
    private void captureTick(){
        if(!live)return;
        if(!wanted){schedule(400);return;}
        if(android.os.Build.VERSION.SDK_INT<30){state="Android 11 requis";save();schedule(1200);return;}
        final long begun=SystemClock.elapsedRealtime();
        try{
            takeScreenshot(Display.DEFAULT_DISPLAY,task -> {if(handler!=null)handler.post(task);},
                new TakeScreenshotCallback(){
                    @Override public void onSuccess(ScreenshotResult result){
                        HardwareBuffer hardware=result.getHardwareBuffer();
                        Bitmap mapped=null,software=null;
                        try{
                            // Hardware Bitmap pixels cannot be read from a software Canvas.
                            mapped=Bitmap.wrapHardwareBuffer(hardware,result.getColorSpace());
                            if(mapped==null)throw new IllegalStateException("HardwareBitmap indisponible");
                            software=mapped.copy(Bitmap.Config.ARGB_8888,false);
                            if(software==null)throw new IllegalStateException("Copie GPU impossible");
                            scaledCanvas.drawColor(Color.BLACK);
                            scaledCanvas.drawBitmap(software,null,new Rect(0,0,WIDTH,HEIGHT),paint);
                            scaled.getPixels(pixels,0,WIDTH,0,0,WIDTH,HEIGHT);
                            for(int i=0,k=0;i<pixels.length;i++){
                                int c=pixels[i];
                                rgba[k++]=(byte)(c>>16);rgba[k++]=(byte)(c>>8);
                                rgba[k++]=(byte)c;rgba[k++]=(byte)255;
                            }
                            FrameAnalyzer.Colors fresh=analyzer.analyze(bytes,WIDTH,HEIGHT,WIDTH*4,4);
                            latest=fresh;latestAt=SystemClock.elapsedRealtime();sampled++;windowFrames++;
                            if(startWindow==0)startWindow=latestAt;
                            if(latestAt-startWindow>4000){
                                measuredFps=windowFrames*1000/(latestAt-startWindow);
                                windowFrames=0;startWindow=latestAt;
                            }
                            captureCostMs=SystemClock.elapsedRealtime()-begun;
                            state="Capture sans MediaProjection active";
                        }catch(Exception failure){
                            accessibilityFailures++;
                            state="Capture Accessibility : "+failure.getClass().getSimpleName();
                        }finally{
                            if(software!=null)software.recycle();
                            if(mapped!=null)mapped.recycle();
                            hardware.close();
                            schedule(captureCostMs>150?500:260);
                        }
                    }
                    @Override public void onFailure(int code){
                        accessibilityFailures++;
                        state="Capture Accessibility refusée : code "+code;
                        schedule(code==ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT?700:550);
                    }
                });
        }catch(Exception error){
            accessibilityFailures++;
            state="Capture inaccessible : "+error.getClass().getSimpleName();
            schedule(800);
        }
    }
    private static int[][] smooth(int[][] previous,int[][] fresh){
        if(previous==null)return fresh;
        int[][] out=new int[4][3];
        for(int i=0;i<4;i++){
            int delta=Math.abs(previous[i][0]-fresh[i][0])
                +Math.abs(previous[i][1]-fresh[i][1])
                +Math.abs(previous[i][2]-fresh[i][2]);
            int ratio=delta>110?85:(delta>40?70:50);
            for(int c=0;c<3;c++)out[i][c]=(previous[i][c]*(100-ratio)+fresh[i][c]*ratio+50)/100;
        }
        return out;
    }
    private void writeLoop(){
        TuyaClient client=null;int[][] old=null;long lastWrite=0, retryUntil=0, lastSaved=0;
        int good=0;long maxPeriod=0;
        try{
            while(live){
                try{Thread.sleep(45);}catch(InterruptedException ex){break;}
                if(!wanted || !config.approved() || !config.rgbic() || !config.hasSecret()){
                    if(client!=null){client.close();client=null;}old=null;continue;
                }
                long now=SystemClock.elapsedRealtime();
                if(now-lastSaved>1800){save();lastSaved=now;}
                FrameAnalyzer.Colors frame=latest;
                if(frame==null || now-latestAt>1700 || now-lastWrite<rateMs || now<retryUntil)continue;
                boolean reverse=getSharedPreferences("ambilight_v05_options",MODE_PRIVATE).getBoolean("reverse",true);
                int[][] mapped=LedOrientation.physical(frame.rgb,reverse);
                int[][] desired=smooth(old,mapped);
                if(old!=null && RgbicFourZone.difference(old,desired)<16)continue;
                try{
                    if(client==null)client=new TuyaClient(config.id(),config.ip(),config.secret(),config.version());
                    lastWrite=SystemClock.elapsedRealtime();
                    client.setFourColors(desired,config.uiSegments());
                    ackMs=SystemClock.elapsedRealtime()-lastWrite;
                    maxAckMs=Math.max(maxAckMs,ackMs);
                    maxPeriod=Math.max(maxPeriod,ackMs);
                    old=desired;sent++;good++;
                    if(good>=25){
                        rateMs=maxPeriod<115?160:200;
                        good=0;maxPeriod=0;
                    }
                }catch(Exception e){
                    failures++;state="Tuya : "+e.getClass().getSimpleName();
                    rateMs=200;good=0;maxPeriod=0;old=null;
                    if(client!=null){client.close();client=null;}
                    retryUntil=SystemClock.elapsedRealtime()+Math.min(5000,800*failures);
                }
            }
        }finally{
            if(client!=null)client.close();
            save();
        }
    }
    private void save(){
        FrameAnalyzer.Colors f=latest;
        String output=state+"\nCaptures : "+sampled+" ("+measuredFps+"/s), coût "+captureCostMs+" ms, erreurs "+accessibilityFailures+
            "\nRGBIC : "+sent+" commandes, Tuya "+ackMs+" ms (max "+maxAckMs+"), erreurs "+failures+
            "\nBandes noires : haut "+(f==null?0:f.upper)+" / bas "+(f==null?0:f.lower)+" px";
        getSharedPreferences(RUNTIME,MODE_PRIVATE).edit().putString("status",output).apply();
    }
    @Override public void onDestroy(){
        live=false;wanted=false;
        if(writer!=null)writer.interrupt();
        if(handler!=null)handler.removeCallbacksAndMessages(null);
        if(captureThread!=null)captureThread.quitSafely();
        if(scaled!=null)scaled.recycle();
        if(instance==this)instance=null;
        super.onDestroy();
    }
}