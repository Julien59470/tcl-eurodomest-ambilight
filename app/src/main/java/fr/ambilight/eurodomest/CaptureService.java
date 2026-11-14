package fr.ambilight.eurodomest;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import java.nio.ByteBuffer;

/** Experimental standalone media-projection -> 4-zones -> Tuya DP61 pipeline.
 * Do not start without a fresh Android screen-capture consent on Android 14.
 */
public final class CaptureService extends Service {
    public static final String START="fr.ambilight.eurodomest.autonome.START";
    public static final String STOP="fr.ambilight.eurodomest.autonome.STOP";
    public static final String EXTRA_RESULT="permission_result";
    public static final String EXTRA_DATA="permission_data";
    private static final int WIDTH=160,HEIGHT=90,CHANNEL=49041;
    private volatile boolean running=false;
    private volatile FrameAnalyzer.Colors latest;
    private volatile long latestAt=0, sampled=0, sent=0, failures=0, ackMs=0;
    private volatile String state="Arrêté";
    private HandlerThread captureThread;
    private Handler captureHandler;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Thread writer;
    private Settings config;
    private final FrameAnalyzer analyzer=new FrameAnalyzer();
    private long lastRead=0;
    private volatile long responseMax=0, adaptiveInterval=200, readWindowStart=0, readsInWindow=0, latestFps=0;

    public static void start(android.content.Context context,int code,Intent result){
        Intent i=new Intent(context,CaptureService.class).setAction(START)
            .putExtra(EXTRA_RESULT,code).putExtra(EXTRA_DATA,result);
        context.startForegroundService(i);
    }
    public static void stop(android.content.Context context){
        context.startService(new Intent(context,CaptureService.class).setAction(STOP));
    }
    @Override public void onCreate(){super.onCreate();config=new Settings(this);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null || STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(!START.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        if(!config.approved() || !config.rgbic() || !config.hasSecret()){
            state="Valider d'abord le test quatre couleurs";save();stopSelf();return START_NOT_STICKY;
        }
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("ambilight_capture_v04","Ambilight autonome",NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"ambilight_capture_v04")
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Ambilight TCL en cours")
            .setContentText("Capture locale et RGBIC Eurodomest").setContentIntent(pi).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(CHANNEL,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(CHANNEL,notification);
        running=true;
        state="Initialisation de la capture";save();
        try{
            int code=intent.getIntExtra(EXTRA_RESULT,Activity.RESULT_CANCELED);
            Intent token=intent.getParcelableExtra(EXTRA_DATA);
            if(code!=Activity.RESULT_OK||token==null)throw new IllegalStateException("Autorisation de capture absente");
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=manager.getMediaProjection(code,token);
            if(projection==null)throw new IllegalStateException("MediaProjection non disponible");
            captureThread=new HandlerThread("Ambilight-Capture");captureThread.start();
            captureHandler=new Handler(captureThread.getLooper());
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){state="Capture Android arrêtée : rouvrir l'app";save();stopSelf();}
            },captureHandler);
            reader=ImageReader.newInstance(WIDTH,HEIGHT,PixelFormat.RGBA_8888,3);
            reader.setOnImageAvailableListener(this::onImage,captureHandler);
            int dpi=getResources().getConfiguration().densityDpi;
            display=projection.createVirtualDisplay("TCL-Eurodomest-Capture",WIDTH,HEIGHT,dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,captureHandler);
            state="Capture active, analyse des images";save();
            writer=new Thread(this::writeLoop,"Ambilight-Tuya-Writer");writer.start();
        }catch(Exception e){state="Capture impossible : "+e.getClass().getSimpleName()+" "+safe(e);save();stopSelf();}
        return START_NOT_STICKY;
    }
    private void onImage(ImageReader source){
        Image im=null;
        try{
            im=source.acquireLatestImage();
            if(im==null)return;
            long now=SystemClock.elapsedRealtime();
            if(now-lastRead<80)return; // at most ~12.5 samples/s; latest frame only
            lastRead=now;
            Image.Plane plane=im.getPlanes()[0];
            ByteBuffer b=plane.getBuffer();
            latest=analyzer.analyze(b,im.getWidth(),im.getHeight(),plane.getRowStride(),plane.getPixelStride());
            latestAt=now;sampled++;readsInWindow++;
            if(readWindowStart==0)readWindowStart=now;
            if(now-readWindowStart>=4000){latestFps=readsInWindow*1000/(now-readWindowStart);readsInWindow=0;readWindowStart=now;}
        }catch(Exception e){state="Erreur analyse : "+e.getClass().getSimpleName();}
        finally{if(im!=null)im.close();}
    }
    private static String safe(Exception e){String s=e.getMessage();return s==null?"":s.replaceAll("[\\r\\n]"," ");}
    private static int[][] smooth(int[][] before,int[][] after){
        if(before==null)return LedOrientation.physical(after,false);
        int[][] out=new int[4][3];
        for(int i=0;i<4;i++){
            int delta=Math.abs(before[i][0]-after[i][0])+Math.abs(before[i][1]-after[i][1])+Math.abs(before[i][2]-after[i][2]);
            int percent=delta>110?82:(delta>40?68:45); // fast scene changes, smooth minor noise
            for(int j=0;j<3;j++)out[i][j]=(before[i][j]*(100-percent)+after[i][j]*percent+50)/100;
        }
        return out;
    }
    private void writeLoop(){
        TuyaClient tuya=null;int[][] previous=null;
        long retryUntil=0,lastWrite=0,lastStats=0;
        int cleanWrites=0;long maxSinceReset=0;
        try{
            while(running){
                try{Thread.sleep(20);}catch(InterruptedException interrupted){break;}
                long now=SystemClock.elapsedRealtime();
                if(now-lastStats>1500){save();lastStats=now;}
                FrameAnalyzer.Colors frame=latest;
                if(now<retryUntil || now-lastWrite<adaptiveInterval || frame==null || now-latestAt>1500)continue;
                // On the tested box DP61 color 0 is physical LEFT, not the first screen sample (RIGHT).
                boolean reverse=getSharedPreferences("ambilight_v05_options",MODE_PRIVATE).getBoolean("reverse",true);
                int[][] mapped=LedOrientation.physical(frame.rgb,reverse);
                int[][] colors=smooth(previous,mapped);
                if(previous!=null && RgbicFourZone.difference(previous,colors)<16)continue;
                try{
                    if(tuya==null)tuya=new TuyaClient(config.id(),config.ip(),config.secret(),config.version());
                    lastWrite=SystemClock.elapsedRealtime();
                    tuya.setFourColors(colors,config.uiSegments());
                    ackMs=SystemClock.elapsedRealtime()-lastWrite;
                    responseMax=Math.max(responseMax,ackMs);
                    maxSinceReset=Math.max(maxSinceReset,ackMs);
                    previous=colors;sent++;cleanWrites++;
                    // Speed up only after stable feedback; return to 200ms after slow ACKs/errors.
                    if(cleanWrites>=25){
                        if(maxSinceReset<115)adaptiveInterval=160;
                        else adaptiveInterval=200;
                        maxSinceReset=0;cleanWrites=0;
                    }
                    state="Ambilight actif · 4 zones · cadrage automatique";
                }catch(Exception e){
                    failures++;state="Tuya : "+e.getClass().getSimpleName()+" (reconnexion automatique)";
                    adaptiveInterval=200;cleanWrites=0;maxSinceReset=0;
                    if(tuya!=null){tuya.close();tuya=null;}
                    previous=null;retryUntil=SystemClock.elapsedRealtime()+Math.min(5000,700*(int)Math.min(7,failures));
                }
            }
        }finally{if(tuya!=null)tuya.close();save();}
    }
    private void save(){
        FrameAnalyzer.Colors c=latest;
        String status=state+"\nImages analysées : "+sampled+" | commandes RGBIC : "+sent+
            "\nCapture : "+latestFps+" images/s | Tuya : "+adaptiveInterval+" ms | réponse : "+ackMs+" ms (max "+responseMax+" ms) | erreurs : "+failures+
            "\nBandes noires détectées : haut "+(c==null?0:c.upper)+" px / bas "+(c==null?0:c.lower)+" px (image réduite)";
        getSharedPreferences("ambilight_v04_runtime",MODE_PRIVATE).edit().putString("status",status).apply();
    }
    @Override public void onDestroy(){
        running=false;
        if(writer!=null)writer.interrupt();
        if(reader!=null){reader.setOnImageAvailableListener(null,null);}
        if(display!=null){display.release();display=null;}
        if(reader!=null){reader.close();reader=null;}
        if(projection!=null){MediaProjection p=projection;projection=null;try{p.stop();}catch(Exception ignored){}}
        if(captureThread!=null){captureThread.quitSafely();captureThread=null;}
        state="Ambilight arrêté";save();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
