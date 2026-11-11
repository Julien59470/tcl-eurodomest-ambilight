package fr.ambilight.eurodomest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;

/** Foreground connectedDevice service: local UDP receiver + latest-frame throttled Tuya writer. */
public final class BridgeService extends Service {
    public static final String START="fr.ambilight.eurodomest.START";
    public static final String STOP="fr.ambilight.eurodomest.STOP";
    public static final int PORT=19446;
    private volatile boolean alive=false;
    private volatile int[] current=null;
    private volatile byte[] rawFrame=null;
    private volatile long lastPacketAt=0;
    private volatile long valid=0, invalid=0, written=0, failures=0, lastAckMs=0;
    private volatile String state="En préparation";
    private DatagramSocket sock;
    private Thread listener,writer;
    private Settings settings;
    public static void start(android.content.Context ctx) {
        Intent intent=new Intent(ctx,BridgeService.class).setAction(START);
        if(Build.VERSION.SDK_INT>=26)ctx.startForegroundService(intent);else ctx.startService(intent);
    }
    public static void stop(android.content.Context ctx) {
        ctx.startService(new Intent(ctx,BridgeService.class).setAction(STOP));
    }
    @Override public void onCreate(){super.onCreate();settings=new Settings(this);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){
            stopWork();stopForeground(true);stopSelf();return START_NOT_STICKY;
        }
        showForeground();
        if(!alive) {
            alive=true;valid=invalid=written=failures=lastAckMs=0;lastPacketAt=0;current=null;rawFrame=null;
            listener=new Thread(this::listen,"EurodomestUdpListener");listener.start();
            writer=new Thread(this::writeColors,"EurodomestTuyaWriter");writer.start();
        }
        return START_STICKY;
    }
    private void showForeground(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        String channel="eurodomest_bridge_channel";
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel(channel,
            "Synchronisation Eurodomest",NotificationManager.IMPORTANCE_LOW));
        PendingIntent launch=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,channel)
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Ambilight TCL → Eurodomest")
            .setContentText("Récepteur UDP local actif · 19446")
            .setContentIntent(launch).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(17842,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(17842,n);
    }
    private void listen(){
        byte[] buf=new byte[1500];long lastStore=0;
        try(DatagramSocket s=new DatagramSocket(null)) {
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),PORT));
            sock=s;s.setSoTimeout(1000);state="UDP prêt sur 127.0.0.1:19446";
            while(alive){
                try {
                    DatagramPacket p=new DatagramPacket(buf,buf.length);s.receive(p);
                    int[] rgb=RawRgb.average(p.getData(),p.getLength());
                    current=rgb;rawFrame=Arrays.copyOf(p.getData(),p.getLength());lastPacketAt=SystemClock.elapsedRealtime();valid++;
                } catch(java.net.SocketTimeoutException ignored) {}
                  catch(IllegalArgumentException malformed){invalid++;}
                long now=SystemClock.elapsedRealtime();
                if(now-lastStore>1700){saveStats();lastStore=now;}
            }
        } catch(Exception e){if(alive)state="Erreur écoute UDP : "+e.getClass().getSimpleName();}
        finally{sock=null;saveStats();}
    }
    private void writeColors(){
        int[] lastSent=null;int[][] lastZones=null;TuyaClient client=null;
        long lastSendAt=0,lastRetryAt=0;String configId="";boolean lastMode=false;
        try{
            while(alive){
                try{Thread.sleep(130);}catch(InterruptedException interrupted){break;}
                if(!settings.approved()){if(client!=null){client.close();client=null;}continue;}
                int[] sample=current;byte[] frame=rawFrame;
                if(sample==null||frame==null||SystemClock.elapsedRealtime()-lastPacketAt>2500)continue;
                long now=SystemClock.elapsedRealtime();
                if(now-lastSendAt<settings.intervalMs()||now<lastRetryAt)continue;
                boolean rgbic=settings.rgbic();
                int[][] zones=null;
                try{
                    if(rgbic){
                        zones=RgbicFourZone.orient(RawRgb.zones(frame,frame.length,4),settings.rotation(),settings.reverse());
                        if(lastMode&&RgbicFourZone.difference(lastZones,zones)<18)continue;
                    }else if(!lastMode&&RawRgb.distance(lastSent,sample)<9)continue;
                    String id=settings.id()+"#"+settings.ip()+"#"+settings.version();
                    if(client==null||!id.equals(configId)){
                        if(client!=null)client.close();
                        client=new TuyaClient(settings.id(),settings.ip(),settings.secret(),settings.version());
                        configId=id;
                    }
                    long writeStart=SystemClock.elapsedRealtime();
                    lastSendAt=writeStart;
                    if(rgbic)client.setFourColors(zones,settings.uiSegments());
                    else client.setColor(sample);
                    lastAckMs=SystemClock.elapsedRealtime()-writeStart;
                    written++;lastMode=rgbic;
                    lastZones=zones;lastSent=Arrays.copyOf(sample,3);
                    state=rgbic?"UDP + Tuya RGBIC 4 zones ("+settings.intervalMs()+" ms minimum)":"UDP et Tuya couleur globale actifs";
                }catch(Exception failure){
                    failures++;
                    state="Tuya "+(rgbic?"RGBIC":"global")+" : "+failure.getClass().getSimpleName()+" (tentative "+failures+")";
                    lastRetryAt=SystemClock.elapsedRealtime()+Math.min(4000,750*failures);
                    if(client!=null){client.close();client=null;}
                    lastSent=null;lastZones=null;
                }
                saveStats();
            }
        }finally{if(client!=null)client.close();}
    }
    private void saveStats(){
        int[] rgb=current;String color=rgb==null?"aucune":rgb[0]+" / "+rgb[1]+" / "+rgb[2];
        if(settings.rgbic()&&rawFrame!=null){
            try{int[][] z=RawRgb.zones(rawFrame,rawFrame.length,4);
                color += " | Z1 "+Arrays.toString(z[0])+" Z2 "+Arrays.toString(z[1])+" Z3 "+Arrays.toString(z[2])+" Z4 "+Arrays.toString(z[3]);
            }catch(Exception ignored){}
        }
        settings.stats(state,valid,invalid,written,color);
        settings.telemetry(failures,lastAckMs,lastPacketAt==0?0:Math.max(0,SystemClock.elapsedRealtime()-lastPacketAt));
    }
    private void stopWork(){
        alive=false;if(sock!=null)sock.close();
        if(listener!=null)listener.interrupt();if(writer!=null)writer.interrupt();
        state="Service arrêté";saveStats();
    }
    @Override public void onDestroy(){stopWork();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
