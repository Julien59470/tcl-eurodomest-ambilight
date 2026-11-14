package fr.ambilight.eurodomest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.SystemClock;
import java.nio.ByteBuffer;

/**
 * Experimental all-display ADB shell screencap. No MediaProjection, root or accessibility.
 * PNG screenshot assembly may be expensive on 4K/HDR; monitor measurements and stop on failure.
 */
public final class AdbCaptureService extends Service {
    private static final String START="ADB_CAPTURE_START_V07", STOP="ADB_CAPTURE_STOP_V07";
    private static final int W=160,H=90;
    private volatile boolean running=false;
    private volatile FrameAnalyzer.Colors latest;
    private volatile long latestAt=0,readCount=0,writeCount=0,errors=0,lastAck=0,maxAck=0,captureMs=0,rateMs=200,captureDelay=450;
    private volatile String state="Capture ADB en attente";
    private Thread camera,writer;
    private Settings config;
    private final FrameAnalyzer analyzer=new FrameAnalyzer();
    private final Bitmap small=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
    private final Canvas canvas=new Canvas(small);
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect area=new Rect(0,0,W,H);
    private final int[] pixels=new int[W*H];
    private final byte[] rgba=new byte[W*H*4];
    private final ByteBuffer bytes=ByteBuffer.wrap(rgba);

    public static void start(android.content.Context context){
        context.startForegroundService(new Intent(context,AdbCaptureService.class).setAction(START));
    }
    public static void stop(android.content.Context context){
        context.startService(new Intent(context,AdbCaptureService.class).setAction(STOP));
    }
    @Override public void onCreate(){super.onCreate();config=new Settings(this);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        if(!config.approved()||!config.rgbic()||!config.hasSecret()){state="Eurodomest non configuré";save();stopSelf();return START_NOT_STICKY;}
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("adb_capture_v07","Ambilight ADB",NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi=PendingIntent.getActivity(this,3,new Intent(this,MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"adb_capture_v07").setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Ambilight TCL : capture ADB")
            .setContentText("Traitement local des couleurs et contrôle RGBIC")
            .setOngoing(true).setContentIntent(pi).build();
        startForeground(49107,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        running=true;state="Connexion ADB…";save();
        camera=new Thread(this::captureLoop,"ADB-Screen-Capture");
        writer=new Thread(this::writeLoop,"ADB-Tuya-Writer");
        camera.start();writer.start();
        return START_NOT_STICKY;
    }
    private void captureLoop(){
        long lastStats=0;
        int consecutiveFailures=0;
        while(running){
            String step="Connexion TLS";
            try{
                AdbBridge adb=AdbBridge.get();
                if(!adb.isConnected() && !adb.connectLocal(this))
                    throw new IllegalStateException("ADB non associé ou débogage sans fil désactivé");
                step="Commande screencap";
                long t=SystemClock.elapsedRealtime();
                byte[] png=adb.screenshotPng();
                BitmapFactory.Options opts=new BitmapFactory.Options();
                opts.inSampleSize=8;
                Bitmap picture=BitmapFactory.decodeByteArray(png,0,png.length,opts);
                if(picture==null)throw new IllegalStateException("PNG illisible");
                try{
                    canvas.drawColor(Color.BLACK);
                    canvas.drawBitmap(picture,null,area,paint);
                    small.getPixels(pixels,0,W,0,0,W,H);
                    for(int i=0,j=0;i<pixels.length;i++){
                        int px=pixels[i];
                        rgba[j++]=(byte)(px>>>16);rgba[j++]=(byte)(px>>>8);
                        rgba[j++]=(byte)px;rgba[j++]=(byte)255;
                    }
                    FrameAnalyzer.Colors fresh=analyzer.analyze(bytes,W,H,W*4,4);
                    latest=fresh;latestAt=SystemClock.elapsedRealtime();readCount++;
                    consecutiveFailures=0;
                    captureMs=latestAt-t;
                    // Raise delay if composing a full-resolution screenshot is slow.
                    captureDelay=captureMs>900?1100:captureMs>500?750:450;
                    state="Capture ADB active";
                    long remaining=Math.max(0,captureDelay-(SystemClock.elapsedRealtime()-t));
                    if(remaining>0)Thread.sleep(remaining);
                }finally{picture.recycle();}
                if(SystemClock.elapsedRealtime()-lastStats>1300){save();lastStats=SystemClock.elapsedRealtime();}
            }catch(InterruptedException interrupted){break;}
             catch(Exception error){
                errors++;consecutiveFailures++;
                String msg=error.getMessage();
                if(msg==null||msg.isEmpty())msg=error.getClass().getSimpleName();
                msg=msg.replaceAll("[\\r\\n\\t]"," ");
                if(msg.length()>170)msg=msg.substring(0,170);
                state=step+" : "+msg;
                if("Commande screencap".equals(step))
                    state+=" | Si le firmware bloque screencap, le mode ADB ne peut pas capturer.";
                save();
                try{AdbBridge.get().disconnectSafely();}catch(Exception ignored){}
                if(consecutiveFailures>=4){
                    state+=" | Arrêt après 4 erreurs. Mode de secours disponible.";
                    save();
                    break;
                }
                try{Thread.sleep(2500);}catch(InterruptedException e){break;}
            }
        }
        save();
    }
    private static int[][] smooth(int[][] previous,int[][] fresh){
        if(previous==null)return fresh;
        int[][] out=new int[4][3];
        for(int i=0;i<4;i++){
            int d=Math.abs(previous[i][0]-fresh[i][0])+Math.abs(previous[i][1]-fresh[i][1])+Math.abs(previous[i][2]-fresh[i][2]);
            int blend=d>110?85:(d>40?70:50);
            for(int c=0;c<3;c++)out[i][c]=(previous[i][c]*(100-blend)+fresh[i][c]*blend+50)/100;
        }
        return out;
    }
    private void writeLoop(){
        TuyaClient tuya=null;int[][] previous=null;
        long lastWrite=0,nextRetry=0,lastSave=0;long maxPeriod=0;
        int successful=0;
        try{
            while(running){
                try{Thread.sleep(35);}catch(InterruptedException e){break;}
                long now=SystemClock.elapsedRealtime();
                if(now-lastSave>1500){save();lastSave=now;}
                FrameAnalyzer.Colors frame=latest;
                if(frame==null||now-latestAt>2000||now-lastWrite<rateMs||now<nextRetry)continue;
                boolean reversed=getSharedPreferences("ambilight_v05_options",MODE_PRIVATE).getBoolean("reverse",true);
                int[][] mapped=LedOrientation.physical(frame.rgb,reversed);
                int[][] colors=smooth(previous,mapped);
                if(previous!=null&&RgbicFourZone.difference(previous,colors)<16)continue;
                try{
                    if(tuya==null)tuya=new TuyaClient(config.id(),config.ip(),config.secret(),config.version());
                    lastWrite=SystemClock.elapsedRealtime();
                    tuya.setFourColors(colors,config.uiSegments());
                    lastAck=SystemClock.elapsedRealtime()-lastWrite;
                    maxAck=Math.max(maxAck,lastAck);maxPeriod=Math.max(maxPeriod,lastAck);
                    successful++;writeCount++;previous=colors;
                    if(successful>=25){
                        rateMs=maxPeriod<115?160:200;
                        maxPeriod=0;successful=0;
                    }
                }catch(Exception error){
                    errors++;state="Tuya : "+error.getClass().getSimpleName();
                    rateMs=200;successful=0;maxPeriod=0;previous=null;
                    if(tuya!=null){tuya.close();tuya=null;}
                    nextRetry=SystemClock.elapsedRealtime()+2000;
                }
            }
        }finally{if(tuya!=null)tuya.close();save();}
    }
    private void save(){
        FrameAnalyzer.Colors c=latest;
        String output=state+"\nCaptures : "+readCount+" | temps capture : "+captureMs+" ms | pause : "+captureDelay+" ms"+
            "\nCommandes Tuya : "+writeCount+" | intervalle : "+rateMs+" ms | réponse : "+lastAck+" ms (max "+maxAck+") | erreurs : "+errors+
            "\nBandes noires : haut "+(c==null?0:c.upper)+" / bas "+(c==null?0:c.lower)+" px";
        getSharedPreferences("ambilight_v08_runtime",MODE_PRIVATE).edit().putString("status",output).apply();
    }
    @Override public void onDestroy(){
        running=false;
        if(camera!=null)camera.interrupt();
        if(writer!=null)writer.interrupt();
        try{AdbBridge.get().cancelCapture();}catch(Exception ignored){}
        small.recycle();state="Capture ADB arrêtée";save();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
