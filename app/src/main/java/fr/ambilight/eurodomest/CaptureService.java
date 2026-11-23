package fr.ambilight.eurodomest;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
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
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.content.SharedPreferences;
import java.nio.ByteBuffer;

/** One-device local MediaProjection pipeline, 4-sided crop, calibrated 4-20 zone RGBIC.
 * On this TCL the Android 14 consent is required for each capture session.
 * Never saves screen content, logs identifiers or sends images to a server.
 */
public final class CaptureService extends Service {
    public static final String START="fr.ambilight.eurodomest.autonome.START";
    public static final String STOP="fr.ambilight.eurodomest.autonome.STOP";
    public static final String EXTRA_RESULT="permission_result",EXTRA_DATA="permission_data";
    private static final int WIDTH=160,HEIGHT=90,NOTIFICATION_ID=49041;
    private static final long MIN_SEND_MS=200,STALE_MS=2400;
    private volatile boolean running=false;
    private volatile FrameAnalyzer.Colors latest;
    private volatile long latestAt,received,sampled,sent,failures,ackMs,responseMax,latestFps;
    private volatile String state="Arrêté",rawSwatches="",outSwatches="";
    private HandlerThread captureThread;
    private Handler captureHandler;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Thread writer;
    private final FrameAnalyzer analyzer=new FrameAnalyzer();
    private Settings cfg;
    private int zones=4;
    private int[] mapping;
    private ColorEngine.Config colorConfig;
    private long lastRead,readsInWindow,windowStart;
    private volatile long lastSuccessAt;
    private volatile int consecutiveErrors;
    private volatile boolean blackConfirmed;
    private volatile boolean stopBlack;
    private volatile boolean sleepOffRequested;
    private volatile boolean sleepOffFinished;
    private boolean screenReceiverRegistered;
    private PowerManager.WakeLock sleepWakeLock;
    private final Handler lifecycleHandler=new Handler(Looper.getMainLooper());
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            String action=intent==null?null:intent.getAction();
            if(Intent.ACTION_SCREEN_OFF.equals(action))requestStandbyShutdown("SCREEN_OFF Android");
            else if(Intent.ACTION_SHUTDOWN.equals(action))requestStandbyShutdown("Arrêt Android");
        }
    };

    public static void start(android.content.Context context,int code,Intent result){
        Intent intent=new Intent(context,CaptureService.class).setAction(START)
            .putExtra(EXTRA_RESULT,code).putExtra(EXTRA_DATA,result);
        context.startForegroundService(intent);
    }
    public static void stop(android.content.Context context){
        context.startService(new Intent(context,CaptureService.class).setAction(STOP));
    }
    @Override public void onCreate(){
        super.onCreate();cfg=new Settings(this);
        IntentFilter screenFilter=new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SHUTDOWN);
        // Android 14: broadcasts sent by the system can use a context-registered receiver without export flags.
        registerReceiver(screenReceiver,screenFilter);
        screenReceiverRegistered=true;
    }
    private void requestStandbyShutdown(String reason){
        if(!running||sleepOffRequested)return;
        if(!cfg.offOnTvStandby()){
            state="Mise en veille détectée : extinction automatique désactivée";
            save();stopSelf();return;
        }
        synchronized(this){
            if(!running||sleepOffRequested)return;
            sleepOffRequested=true;
            running=false;
            state="Veille détectée ("+reason+") : extinction totale DP20 en cours";
            cfg.setLastStandbyStatus(state);
            save();
            try{
                PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
                sleepWakeLock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AmbilightTCL:standbyOff");
                sleepWakeLock.acquire(15000); // only keep CPU briefly awake to finish local Tuya DP20=false
            }catch(Exception ignored){}
        }
        if(writer!=null){
            if(Thread.currentThread()!=writer)writer.interrupt();
        }else{
            Thread fallback=new Thread(()->{
                sendStandbyPowerOff(null);
                finishStandbyStop();
            },"Ambilight-Sleep-Off");
            fallback.start();
        }
    }
    private void releaseSleepWakeLock(){
        PowerManager.WakeLock lock=sleepWakeLock;
        sleepWakeLock=null;
        if(lock!=null&&lock.isHeld())try{lock.release();}catch(RuntimeException ignored){}
    }
    private void sendStandbyPowerOff(TuyaClient original){
        if(sleepOffFinished)return;
        TuyaClient c=original;
        boolean success=false;
        for(int attempt=1;attempt<=2&&!success;attempt++){
            try{
                if(c==null)c=new TuyaClient(cfg.id(),cfg.ip(),cfg.secret(),cfg.version());
                c.setPower(false); // DP20=false: full controller LED switch-off, not black RGBIC
                success=true;
                state="Veille TCL : DP20=false acquitté par Eurodomest";
            }catch(Exception e){
                state="Veille TCL : tentative "+attempt+" échouée ("+e.getClass().getSimpleName()+")";
                if(c!=null){c.close();c=null;}
            }
        }
        if(c!=null)c.close();
        sleepOffFinished=true;
        cfg.setLastStandbyStatus(state);
        save();
    }
    private void finishStandbyStop(){
        releaseSleepWakeLock();
        lifecycleHandler.post(this::stopSelf);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||STOP.equals(intent.getAction())){state="Arrêt demandé";save();stopSelf();return START_NOT_STICKY;}
        if(!START.equals(intent.getAction())||running){return START_NOT_STICKY;}
        if(!cfg.approved()||!cfg.rgbic()||!cfg.hasSecret()){
            state="Configuration RGBIC non validée";save();stopSelf();return START_NOT_STICKY;
        }
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("ambilight_v11_capture",
            "Ambilight TCL · capture",NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"ambilight_v11_capture")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Ambilight TCL actif")
            .setContentText("Capture locale · RGBIC Eurodomest")
            .setContentIntent(pi).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)
            startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIFICATION_ID,notification);
        running=true;
        sleepOffRequested=false;sleepOffFinished=false;
        zones=cfg.zones();mapping=cfg.mapping(zones);colorConfig=cfg.colorConfig();
        blackConfirmed=cfg.blackVerified();stopBlack=cfg.blackOnStop();
        try{
            int result=intent.getIntExtra(EXTRA_RESULT,Activity.RESULT_CANCELED);
            Intent token=intent.getParcelableExtra(EXTRA_DATA);
            if(result!=Activity.RESULT_OK||token==null)
                throw new IllegalStateException("Consentement Android absent");
            MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=mgr.getMediaProjection(result,token);
            if(projection==null)throw new IllegalStateException("Projection non disponible");
            captureThread=new HandlerThread("TCL-Ambilight-Image");
            captureThread.start();
            captureHandler=new Handler(captureThread.getLooper());
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){
                    if(sleepOffRequested||!running)return;
                    PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
                    if(power!=null&&!power.isInteractive()){
                        requestStandbyShutdown("MediaProjection arrêtée avec écran en veille");
                        return;
                    }
                    // The system may stop projection just before SCREEN_OFF on some TVs.
                    lifecycleHandler.postDelayed(()->{
                        if(sleepOffRequested||!running)return;
                        PowerManager current=(PowerManager)getSystemService(POWER_SERVICE);
                        if(current!=null&&!current.isInteractive())
                            requestStandbyShutdown("Projection arrêtée, TV non interactive");
                        else{
                            state="Projection Android terminée; rouvrir Ambilight pour reprendre";
                            save();stopSelf();
                        }
                    },350);
                }
            },captureHandler);
            reader=ImageReader.newInstance(WIDTH,HEIGHT,PixelFormat.RGBA_8888,3);
            reader.setOnImageAvailableListener(this::onImage,captureHandler);
            int dpi=getResources().getConfiguration().densityDpi;
            display=projection.createVirtualDisplay("TCL-Ambilight-RGBIC",WIDTH,HEIGHT,dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,captureHandler);
            state="Capture active, attente des couleurs";
            writer=new Thread(this::writeLoop,"TCL-Ambilight-Tuya");
            writer.start();save();
        }catch(Exception ex){
            state="Impossible de capturer : "+ex.getClass().getSimpleName()+" "+safe(ex);
            save();stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void onImage(ImageReader from){
        Image img=null;
        try{
            img=from.acquireLatestImage();
            if(img==null)return;
            received++;
            long now=SystemClock.elapsedRealtime();
            if(now-lastRead<80)return; // latest-only, max about 12.5 samples per second
            lastRead=now;
            Image.Plane plane=img.getPlanes()[0];
            ByteBuffer buffer=plane.getBuffer();
            FrameAnalyzer.Colors colors=analyzer.analyzeZones(buffer,img.getWidth(),img.getHeight(),
                plane.getRowStride(),plane.getPixelStride(),zones);
            latest=colors;latestAt=now;sampled++;readsInWindow++;
            if(windowStart==0)windowStart=now;
            if(now-windowStart>=4000){
                latestFps=readsInWindow*1000/(now-windowStart);
                windowStart=now;readsInWindow=0;
            }
        }catch(Exception ex){
            state="Erreur analyse : "+ex.getClass().getSimpleName()+" "+safe(ex);
            failures++;
        }finally{if(img!=null)img.close();}
    }
    private static String safe(Exception ex){
        String msg=ex.getMessage();
        return msg==null?"":msg.replaceAll("[\\r\\n]"," ").substring(0,Math.min(110,msg.length()));
    }
    private static String palette(int[][] rgb){
        if(rgb==null)return "";
        StringBuilder b=new StringBuilder();
        final char[] digits="0123456789ABCDEF".toCharArray();
        for(int i=0;i<rgb.length;i++){
            if(i>0)b.append(',');
            for(int k=0;k<3;k++){
                int v=Math.max(0,Math.min(255,rgb[i][k]));
                b.append(digits[v>>4]).append(digits[v&15]);
            }
        }
        return b.toString();
    }
    private void writeLoop(){
        TuyaClient client=null;
        int[][] previous=null;
        long retryUntil=0,lastWrite=0,lastReport=0;
        boolean staleWasBlacked=false;
        try{
            while(running){
                try{Thread.sleep(20);}catch(InterruptedException ex){break;}
                long now=SystemClock.elapsedRealtime();
                if(cfg.offOnTvStandby()&&!sleepOffRequested){
                    PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
                    if(power!=null&&!power.isInteractive()){
                        requestStandbyShutdown("TV non interactive (contrôle périodique)");
                        continue;
                    }
                }
                if(now-lastReport>=1300){save();lastReport=now;}
                if(now<retryUntil)continue;
                FrameAnalyzer.Colors frame=latest;
                boolean stale=frame==null||now-latestAt>STALE_MS;
                if(stale){
                    state=sampled==0?"Capture initialisée, aucune image reçue":
                        "Capture figée ou interrompue · "+(now-latestAt)+" ms sans image";
                    if(blackConfirmed&&sent>0&&!staleWasBlacked&&now-lastWrite>=MIN_SEND_MS){
                        try{
                            if(client==null)client=new TuyaClient(cfg.id(),cfg.ip(),cfg.secret(),cfg.version());
                            long start=SystemClock.elapsedRealtime();
                            client.setFourColors(ColorEngine.black(zones),cfg.uiSegments());
                            ackMs=SystemClock.elapsedRealtime()-start;lastWrite=SystemClock.elapsedRealtime();
                            lastSuccessAt=lastWrite;sent++;
                            previous=ColorEngine.black(zones);outSwatches=palette(previous);
                            staleWasBlacked=true;state="Capture absente : extinction RGBIC envoyée";
                        }catch(Exception ex){
                            failures++;consecutiveErrors++;
                            state="Extinction de sécurité échouée : "+ex.getClass().getSimpleName();
                            if(client!=null){client.close();client=null;}
                            retryUntil=now+Math.min(6000,700L*consecutiveErrors);
                        }
                    }else if(!blackConfirmed){
                        state+=" · extinction par zone non validée";
                    }
                    continue;
                }
                if(staleWasBlacked)staleWasBlacked=false;
                if(now-lastWrite<MIN_SEND_MS)continue;
                int[][] normalized=ColorEngine.correct(frame.rgb,colorConfig);
                int[][] ordered=SegmentMapping.apply(normalized,mapping);
                int[][] output=ColorEngine.temporal(previous,ordered,colorConfig.smooth);
                if(!ColorEngine.sendNeeded(previous,output))continue;
                try{
                    if(client==null){
                        client=new TuyaClient(cfg.id(),cfg.ip(),cfg.secret(),cfg.version());
                        client.setPower(true); // Reactivate LEDs on a newly authorized capture session.
                    }
                    lastWrite=SystemClock.elapsedRealtime();
                    client.setFourColors(output,cfg.uiSegments());
                    ackMs=SystemClock.elapsedRealtime()-lastWrite;
                    responseMax=Math.max(responseMax,ackMs);
                    previous=output;sent++;consecutiveErrors=0;
                    lastSuccessAt=SystemClock.elapsedRealtime();
                    rawSwatches=palette(frame.rgb);outSwatches=palette(output);
                    state="Synchronisation active · "+zones+" zones · 4 bords";
                }catch(Exception ex){
                    failures++;consecutiveErrors++;
                    state="Tuya : "+ex.getClass().getSimpleName()+" "+safe(ex);
                    if(client!=null){client.close();client=null;}
                    previous=null;
                    retryUntil=SystemClock.elapsedRealtime()+Math.min(6000,700L*consecutiveErrors);
                }
            }
        }finally{
            // Standby has priority over the DP61 black-on-manual-stop policy.
            if(sleepOffRequested){
                sendStandbyPowerOff(client);
                finishStandbyStop();
                return;
            }
            // Best-effort, never pretend off succeeded on an unverified device.
            if(client!=null){
                if(stopBlack&&sent>0){
                    try{client.setFourColors(ColorEngine.black(zones),cfg.uiSegments());
                        state="Capture arrêtée; extinction RGBIC demandée";
                    }catch(Exception ex){state="Capture arrêtée; extinction non confirmée";}
                }
                client.close();
            }
            save();
        }
    }
    private void save(){
        FrameAnalyzer.Colors c=latest;
        long age=c==null?-1:SystemClock.elapsedRealtime()-latestAt;
        String status=state+"\nImages reçues : "+received+" | analysées : "+sampled+
            " | capture : "+latestFps+" images/s | âge : "+age+" ms"+
            "\nCommandes RGBIC : "+sent+" | Tuya minimum "+MIN_SEND_MS+
            " ms | réponse "+ackMs+" ms (max "+responseMax+") | erreurs "+failures+
            " (consécutives "+consecutiveErrors+")"+
            "\nBandes noires : haut "+(c==null?0:c.upper)+
            " / bas "+(c==null?0:c.lower)+
            " / gauche "+(c==null?0:c.left)+
            " / droite "+(c==null?0:c.right)+
            " px · 160×90 | image sombre "+(c!=null&&c.darkFrame)+
            " | noir RGBIC validé "+blackConfirmed;
        SharedPreferences.Editor e=getSharedPreferences("ambilight_v07_media_runtime",MODE_PRIVATE).edit();
        e.putString("status",status).putString("preview_source",rawSwatches)
            .putString("preview_output",outSwatches).apply();
    }
    @Override public void onDestroy(){
        running=false;
        if(screenReceiverRegistered){
            try{unregisterReceiver(screenReceiver);}catch(IllegalArgumentException ignored){}
            screenReceiverRegistered=false;
        }
        if(!sleepOffRequested)releaseSleepWakeLock();
        if(writer!=null)writer.interrupt();
        if(reader!=null)reader.setOnImageAvailableListener(null,null);
        if(display!=null){display.release();display=null;}
        if(reader!=null){reader.close();reader=null;}
        if(projection!=null){
            MediaProjection p=projection;projection=null;
            try{p.stop();}catch(Exception ignored){}
        }
        if(captureThread!=null){captureThread.quitSafely();captureThread=null;}
        if(writer==null){state="Ambilight arrêté";save();}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
