package fr.ambilight.eurodomest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/** One-time local pairing web page, used while the TV stays on its pairing-code screen.
 * Local IP and short-lived PIN are the only things shown on TV. Never serves Local Key.
 */
public final class AdbPairServerService extends Service {
    public static final int PORT=28914;
    public static final String ACTION_START="PAIR_START_V07",ACTION_STOP="PAIR_STOP_V07";
    public static final String EXTRA_PIN="pair_pin_v07";
    private volatile boolean active=false, pairing=false;
    private volatile int attempts=0;
    private volatile String status="En attente du code ADB";
    private volatile String pin;
    private volatile long deadline;
    private Thread acceptThread;
    private ServerSocket listener;

    public static void start(android.content.Context c,String pin){
        c.startForegroundService(new Intent(c,AdbPairServerService.class).setAction(ACTION_START).putExtra(EXTRA_PIN,pin));
    }
    public static void stop(android.content.Context c){
        c.startService(new Intent(c,AdbPairServerService.class).setAction(ACTION_STOP));
    }
    public static String newPin(){
        return String.format(java.util.Locale.US,"%08d",new SecureRandom().nextInt(100000000));
    }
    public static String tvIp(){
        try{
            Enumeration<NetworkInterface> adapters=NetworkInterface.getNetworkInterfaces();
            String candidate=null;
            while(adapters.hasMoreElements()){
                NetworkInterface net=adapters.nextElement();
                if(!net.isUp()||net.isLoopback())continue;
                Enumeration<InetAddress> addrs=net.getInetAddresses();
                while(addrs.hasMoreElements()){
                    InetAddress address=addrs.nextElement();
                    if(!(address instanceof Inet4Address)||!address.isSiteLocalAddress())continue;
                    if(net.getName().startsWith("wlan"))return address.getHostAddress();
                    candidate=address.getHostAddress();
                }
            }
            return candidate==null?"IP_TV":candidate;
        }catch(Exception e){return "IP_TV";}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||ACTION_STOP.equals(intent.getAction())){shutdown();return START_NOT_STICKY;}
        if(active)return START_NOT_STICKY;
        pin=intent.getStringExtra(EXTRA_PIN);
        if(pin==null||!pin.matches("[0-9]{8}")){stopSelf();return START_NOT_STICKY;}
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("adb_pairing_v07","Association ADB locale",NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi=PendingIntent.getActivity(this,7,new Intent(this,MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"adb_pairing_v07")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Association ADB Ambilight")
            .setContentText("Serveur de configuration temporaire actif")
            .setContentIntent(pi).setOngoing(true).build();
        startForeground(49106,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        active=true;
        deadline=SystemClock.elapsedRealtime()+10*60*1000L;
        publish();
        acceptThread=new Thread(this::serve,"ADB-Pair-Web");acceptThread.start();
        return START_NOT_STICKY;
    }
    private void serve(){
        try(ServerSocket socket=new ServerSocket(PORT,8,InetAddress.getByName("0.0.0.0"))){
            listener=socket;
            socket.setSoTimeout(1500);
            while(active&&SystemClock.elapsedRealtime()<deadline){
                try(Socket client=socket.accept()){
                    client.setSoTimeout(4000);
                    handle(client);
                }catch(SocketTimeoutException ignored){}catch(Exception e){status="Erreur locale : "+e.getClass().getSimpleName();publish();}
            }
        }catch(Exception e){status="Serveur local inaccessible : "+e.getClass().getSimpleName();publish();}
        finally{active=false;stopSelf();}
    }
    private static String escape(String value){
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
            .replace("\"","&quot;").replace("'","&#39;");
    }
    private void publish(){
        getSharedPreferences("ambilight_v07_pair",MODE_PRIVATE).edit().putString("status",status).apply();
    }
    private void handle(Socket client) throws Exception{
        BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));
        String first=reader.readLine();
        if(first==null||first.length()>200)return;
        String[] parts=first.split(" ");
        if(parts.length<2)return;
        if("POST".equals(parts[0])&&"/pair".equals(parts[1])){
            int length=0;
            for(int lines=0;lines<30;lines++){
                String line=reader.readLine();
                if(line==null||line.isEmpty())break;
                if(line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))
                    length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
            }
            if(length>0&&length<=512&&!pairing&&attempts<5){
                char[] body=new char[length];
                int pos=0;
                while(pos<length){int read=reader.read(body,pos,length-pos);if(read<0)break;pos+=read;}
                Map<String,String> fields=new HashMap<>();
                for(String kv:new String(body,0,pos).split("&")){
                    String[] pair=kv.split("=",2);
                    if(pair.length==2)fields.put(URLDecoder.decode(pair[0],"UTF-8"),URLDecoder.decode(pair[1],"UTF-8"));
                }
                String userPin=fields.get("pin"),port=fields.get("port"),code=fields.get("code");
                if(pin.equals(userPin)&&port!=null&&port.matches("[0-9]{4,5}")&&code!=null&&code.matches("[0-9]{6}")){
                    int pairingPort=Integer.parseInt(port);
                    attempts++;pairing=true;status="Association en cours…";publish();
                    new Thread(()->{
                        try{
                            if(!AdbBridge.get().pairLocal(pairingPort,code))throw new Exception("Association refusée");
                            status="Association acceptée. Retournez à Ambilight et démarrez le mode ADB.";
                            try{AdbBridge.get().connectLocal(this);}catch(Exception ignored){}
                        }catch(Exception e){status="Échec d'association : "+e.getClass().getSimpleName()+" (vérifier port/code et laisser la fenêtre ouverte)";}
                        finally{pairing=false;publish();}
                    },"ADB-Pair-Worker").start();
                }else{attempts++;status="PIN, port ou code invalide";publish();}
            }
            redirect(client.getOutputStream());
            return;
        }
        if(!"GET".equals(parts[0]))return;
        String disabled=pairing||attempts>=5?"disabled":"";
        String page="<!doctype html><html lang='fr'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
          +"<title>Association Ambilight</title>"
          +"<style>body{font:18px system-ui;background:#102234;color:white;max-width:480px;margin:auto;padding:24px}input,button{display:block;width:100%;padding:13px;margin:10px 0;font:20px system-ui;box-sizing:border-box}button{background:#ffce35;border:0;border-radius:6px}p{line-height:1.4}</style></head><body>"
          +"<h2>Associer Ambilight TCL</h2><p>Gardez sur la télé la fenêtre « Associer avec un code » ouverte.</p>"
          +"<p><b>État :</b> "+escape(status)+"</p>"
          +"<form method='POST' action='/pair'><input name='pin' placeholder='PIN à 8 chiffres affiché sur la TV' inputmode='numeric' required maxlength='8'>"
          +"<input name='port' placeholder='Port d’association (TV)' inputmode='numeric' required maxlength='5'>"
          +"<input name='code' placeholder='Code ADB (6 chiffres)' inputmode='numeric' required maxlength='6'>"
          +"<button "+disabled+">Associer</button></form><p>Aucune Local Key n'est demandée ici. Fermez cette page après l'association.</p></body></html>";
        byte[] data=page.getBytes(StandardCharsets.UTF_8);
        String headers="HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+data.length+
            "\r\nCache-Control: no-store\r\nX-Frame-Options: DENY\r\nConnection: close\r\n\r\n";
        OutputStream out=client.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.UTF_8));out.write(data);out.flush();
    }
    private void redirect(OutputStream out)throws Exception{
        out.write(("HTTP/1.1 303 See Other\r\nLocation: /\r\nContent-Length: 0\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    private void shutdown(){
        active=false;
        try{if(listener!=null)listener.close();}catch(Exception ignored){}
        stopSelf();
    }
    @Override public void onDestroy(){shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
