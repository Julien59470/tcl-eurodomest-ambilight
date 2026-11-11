package fr.ambilight.eurodomest;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Minimal direct local Tuya client. Never sends configuration data to the cloud. */
public final class TuyaClient implements AutoCloseable {
    private final String id,ip;
    private final int version;
    private final byte[] originalKey;
    private byte[] sessionKey;
    private Socket sock;
    private int seq=1;
    public TuyaClient(String id,String ip,String localKey,String version) {
        this.id=id;this.ip=ip;this.version=Integer.parseInt(version.replace(".",""));
        if(this.version!=33&&this.version!=34&&this.version!=35)
            throw new IllegalArgumentException("Seuls 3.3, 3.4, 3.5 sont implémentés");
        this.originalKey=TuyaWire.key(localKey);this.sessionKey=originalKey;
    }
    private void open() throws Exception {
        if(sock!=null&&!sock.isClosed())return;
        close();sessionKey=originalKey;seq=1;
        sock=new Socket();sock.connect(new InetSocketAddress(ip,6668),1800);
        sock.setTcpNoDelay(true);sock.setSoTimeout(2200);
        if(version>=34) negotiate();
    }
    private void negotiate() throws Exception {
        byte[] client=TuyaWire.random(16);
        sendRaw(3,client);
        TuyaWire.Packet response=waitPacket(4, originalKey);
        byte[] body=TuyaWire.decryptPayload(version,originalKey,response.payload);
        if(body.length<48)throw new IOException("Négociation : réponse trop courte");
        byte[] remote=Arrays.copyOfRange(body,0,16);
        byte[] hmac=TuyaWire.mac(originalKey,client);
        if(!java.security.MessageDigest.isEqual(hmac,Arrays.copyOfRange(body,16,48)))
            throw new GeneralSecurityException("Négociation : HMAC invalide (Local Key/version ?) ");
        sendRaw(5,TuyaWire.mac(originalKey,remote));
        sessionKey=TuyaWire.deriveSession(version,originalKey,client,remote);
    }
    private void sendRaw(int command,byte[] body) throws Exception {
        sock.getOutputStream().write(TuyaWire.encode(version,seq++,command,body,sessionKey));
        sock.getOutputStream().flush();
    }
    private TuyaWire.Packet waitPacket(int expected,byte[] key) throws Exception {
        for(int tries=0;tries<6;tries++) {
            TuyaWire.Packet p=TuyaWire.receive(sock.getInputStream(),version,key);
            if(p.retcode!=0)throw new IOException("Code retour Tuya non nul : "+p.retcode);
            if(p.cmd==expected)return p;
        }
        throw new IOException("Aucune réponse attendue Tuya");
    }
    private JSONObject parseDps(byte[] plain) throws Exception {
        if(plain.length==0)throw new IOException("Réponse de statut Tuya vide");
        String value=new String(plain,StandardCharsets.UTF_8);
        if(value.contains("data unvalid")) throw new IOException("device22: data unvalid");
        JSONObject root=new JSONObject(value);
        if(root.has("dps"))return root.getJSONObject("dps");
        JSONObject data=root.optJSONObject("data");
        if(data!=null&&data.has("dps"))return data.getJSONObject("dps");
        throw new IOException("DP absents de la réponse Tuya : vérifier version ou device22");
    }
    public synchronized JSONObject status() throws Exception {
        open();int cmd=version==33?10:16;
        if(version==33) {
            JSONObject q=new JSONObject().put("gwId",id).put("devId",id).put("uid",id)
                .put("t",Long.toString(System.currentTimeMillis()/1000));
            sendRaw(cmd,q.toString().getBytes(StandardCharsets.UTF_8));
        } else sendRaw(cmd,"{}".getBytes(StandardCharsets.UTF_8));
        try {
            TuyaWire.Packet p=waitPacket(cmd,sessionKey);
            return parseDps(TuyaWire.decryptPayload(version,sessionKey,p.payload));
        } catch(IOException e) {
            if(!e.getMessage().contains("device22"))throw e;
            // Some Tuya 3.3/3.4 devices need a read-only CONTROL_NEW query with null DP map.
            // This does not change any data points: null means 'request DP value'.
            JSONObject requestDps=new JSONObject().put("20",JSONObject.NULL).put("21",JSONObject.NULL)
                .put("24",JSONObject.NULL);
            JSONObject query;
            if(version==33)query=new JSONObject().put("devId",id).put("uid",id)
                .put("t",Long.toString(System.currentTimeMillis()/1000)).put("dps",requestDps);
            else query=new JSONObject().put("protocol",5).put("t",System.currentTimeMillis()/1000)
                .put("data",new JSONObject().put("dps",requestDps));
            sendRaw(13,query.toString().getBytes(StandardCharsets.UTF_8));
            TuyaWire.Packet p=waitPacket(13,sessionKey);
            return parseDps(TuyaWire.decryptPayload(version,sessionKey,p.payload));
        }
    }
    public synchronized void setColor(int[] rgb) throws Exception {
        open();
        JSONObject dps=new JSONObject().put("20",true).put("21","colour").put("24",RawRgb.hsv(rgb));
        JSONObject message;
        int cmd;
        if(version==33) {
            cmd=7;
            message=new JSONObject().put("devId",id).put("uid",id)
                .put("t",Long.toString(System.currentTimeMillis()/1000)).put("dps",dps);
        } else {
            cmd=13;
            message=new JSONObject().put("protocol",5).put("t",System.currentTimeMillis()/1000)
                .put("data",new JSONObject().put("dps",dps));
        }
        sendRaw(cmd,message.toString().getBytes(StandardCharsets.UTF_8));
        // Some devices only ACK with an empty payload. A valid authenticated ACK does not prove the LED changed.
        waitPacket(cmd,sessionKey);
    }
    public synchronized void restore(JSONObject before) throws Exception {
        if(before==null||!before.has("20"))throw new IOException("Ancien état non disponible");
        JSONObject dps=new JSONObject();
        for(String k:new String[]{"20","21","24"})if(before.has(k))dps.put(k,before.get(k));
        open();int cmd=version==33?7:13;
        JSONObject message;
        if(version==33)message=new JSONObject().put("devId",id).put("uid",id)
            .put("t",Long.toString(System.currentTimeMillis()/1000)).put("dps",dps);
        else message=new JSONObject().put("protocol",5).put("t",System.currentTimeMillis()/1000)
            .put("data",new JSONObject().put("dps",dps));
        sendRaw(cmd,message.toString().getBytes(StandardCharsets.UTF_8));
        waitPacket(cmd,sessionKey);
    }
    @Override public synchronized void close(){
        if(sock!=null)try{sock.close();}catch(IOException ignored){}finally{sock=null;}
    }
}
