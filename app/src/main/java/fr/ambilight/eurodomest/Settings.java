package fr.ambilight.eurodomest;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stored secrets encrypted with AndroidKeyStore; never included in backup or logs. */
public final class Settings {
    private static final String ALIAS="eurodomest_local_key_v1";
    private static final String STORE="eurodomest_private_v1";
    private final SharedPreferences pref;
    public Settings(Context ctx) { pref=ctx.getApplicationContext().getSharedPreferences(STORE,Context.MODE_PRIVATE); }
    public String id(){return pref.getString("id","");}
    public String ip(){return pref.getString("ip","");}
    public String version(){return pref.getString("version","3.3");}
    public boolean approved(){return pref.getBoolean("approved",false);}
    public boolean rgbic(){return pref.getBoolean("rgbic",false);}
    public int uiSegments(){return pref.getInt("uiSegments",20);}
    public int zones(){
        int n=pref.getInt("validated_zones",4);
        return n>=4&&n<=20&&n%4==0?n:4;
    }
    public void setZones(int n){
        if(n<4||n>20||n%4!=0)throw new IllegalArgumentException("Nombre de zones invalide");
        if(!approved()||!rgbic())throw new IllegalStateException("Valider d'abord le test 4 couleurs");
        if(!pref.edit().putInt("validated_zones",n).commit())
            throw new IllegalStateException("Impossible d'enregistrer le profil RGBIC");
    }
    /** Mappings are a verified permutation, keyed by logical segment count. */
    public int[] mapping(int count){
        boolean reversed=pref.getBoolean("mapping_reverse",true);
        return SegmentMapping.decode(pref.getString("mapping_"+count,""),count,reversed);
    }
    public void assignSegment(int count,int physicalIndex,int screenIndex){
        int[] result=SegmentMapping.swap(mapping(count),physicalIndex,screenIndex);
        if(!pref.edit().putString("mapping_"+count,SegmentMapping.encode(result)).commit())
            throw new IllegalStateException("Enregistrement du mapping impossible");
    }
    public void resetMapping(int count){
        pref.edit().remove("mapping_"+count).commit();
    }
    public void reverseMapping(int count){
        int[] old=mapping(count);
        int[] flipped=new int[count];
        for(int i=0;i<count;i++)flipped[i]=old[count-1-i];
        pref.edit().putString("mapping_"+count,SegmentMapping.encode(flipped)).commit();
    }
    public ColorEngine.Config colorConfig(){
        return new ColorEngine.Config(pref.getInt("color_brightness",75),
            pref.getInt("color_black",4),pref.getInt("color_red",100),
            pref.getInt("color_green",100),pref.getInt("color_blue",100),
            pref.getInt("color_gamma10",12)/10.0,pref.getInt("color_smooth",45));
    }
    public void setColorConfig(ColorEngine.Config c){
        pref.edit().putInt("color_brightness",c.brightness).putInt("color_black",c.black)
            .putInt("color_red",c.red).putInt("color_green",c.green)
            .putInt("color_blue",c.blue).putInt("color_gamma10",(int)Math.round(c.gamma*10))
            .putInt("color_smooth",c.smooth).commit();
    }
    /** When the foreground MediaProjection service observes Android entering standby,
     * send DP20=false. No black-DP61 prerequisite: this is the real Tuya switch.
     */
    public boolean offOnTvStandby(){return pref.getBoolean("off_on_tv_standby",true);}
    public void setOffOnTvStandby(boolean enabled){
        if(!pref.edit().putBoolean("off_on_tv_standby",enabled).commit())
            throw new IllegalStateException("Enregistrement de la veille impossible");
    }
    public String lastStandbyStatus(){
        return pref.getString("last_standby_status","Aucun arrêt TV observé depuis cette installation");
    }
    public void setLastStandbyStatus(String status){
        pref.edit().putString("last_standby_status",status).apply();
    }
    public boolean blackVerified(){return pref.getBoolean("black_verified",false);}
    public void setBlackVerified(boolean verified){
        pref.edit().putBoolean("black_verified",verified).commit();
    }
    public boolean blackOnStop(){return pref.getBoolean("black_on_stop",true)&&blackVerified();}
    public void setBlackOnStop(boolean enabled){pref.edit().putBoolean("black_on_stop",enabled).commit();}
    public int intervalMs(){return pref.getInt("interval_ms",333);}
    public void setIntervalMs(int millis){if(millis!=200&&millis!=250&&millis!=333&&millis!=500&&millis!=667)throw new IllegalArgumentException("Cadence invalide");pref.edit().putInt("interval_ms",millis).apply();}
    public int rotation(){return pref.getInt("rotation",0);}
    public boolean reverse(){return pref.getBoolean("reverse",false);}
    public void setRgbic(boolean enabled){pref.edit().putBoolean("rgbic",enabled).apply();}
    public void setRgbicOptions(int segments,int rot,boolean rev){
        if(segments<4||segments>128||rot<0||rot>3)throw new IllegalArgumentException("Configuration RGBIC invalide");
        pref.edit().putInt("uiSegments",segments).putInt("rotation",rot).putBoolean("reverse",rev).putBoolean("rgbic",false).apply();
    }
    public boolean autoBoot(){return pref.getBoolean("boot",false);}
    public boolean hasSecret(){return pref.contains("key_cipher");}
    public void setApproved(boolean yes){pref.edit().putBoolean("approved",yes).apply();}
    public void setBoot(boolean yes){pref.edit().putBoolean("boot",yes).apply();}
    public void save(String id,String ip,String version,String newKey) throws Exception {
        boolean modified=!id.equals(id())||!ip.equals(ip())||!version.equals(version()) || (newKey!=null&&!newKey.isEmpty());
        SharedPreferences.Editor ed=pref.edit().putString("id",id).putString("ip",ip).putString("version",version);
        if(newKey!=null&&!newKey.isEmpty()) {
            byte[] k=TuyaWire.key(newKey); // validates byte size before mutation
            ed.putString("key_cipher",encrypt(newKey));
            java.util.Arrays.fill(k,(byte)0);
        }
        if(modified)ed.putBoolean("approved",false).putBoolean("boot",false).putBoolean("rgbic",false).putInt("validated_zones",4);
        if(!ed.commit())throw new IllegalStateException("Écriture du stockage Android impossible");
    }
    public String secret() throws Exception {
        String encrypted=pref.getString("key_cipher",null);
        if(encrypted==null)throw new IllegalStateException("Local Key non enregistrée");
        return decrypt(encrypted);
    }
    public void clear(){ pref.edit().clear().commit(); }
    private static SecretKey getOrCreate() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return gen.generateKey();
    }
    private static String encrypt(String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,getOrCreate());
        byte[] iv=c.getIV(),body=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(TuyaWire.concat(iv,body),Base64.NO_WRAP);
    }
    private static String decrypt(String stored) throws Exception {
        byte[] enc=Base64.decode(stored,Base64.DEFAULT);
        if(enc.length<29)throw new IllegalStateException("Clé enregistrée corrompue");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,getOrCreate(),new GCMParameterSpec(128,java.util.Arrays.copyOfRange(enc,0,12)));
        return new String(c.doFinal(enc,12,enc.length-12),StandardCharsets.UTF_8);
    }
    public void stats(String status,long received,long invalid,long sent,String lastColor) {
        pref.edit().putString("runtime_state",status).putLong("runtime_packets",received)
            .putLong("runtime_invalid",invalid).putLong("runtime_sent",sent)
            .putString("runtime_color",lastColor).apply();
    }
    public void telemetry(long failures,long ackMs,long lastAgeMs){pref.edit().putLong("runtime_failures",failures).putLong("runtime_ack_ms",ackMs).putLong("runtime_frame_age_ms",lastAgeMs).apply();}
    public String summary(){
        return pref.getString("runtime_state","Service arrêté")+"\nPaquets UDP : "+pref.getLong("runtime_packets",0)
            +"  | invalides : "+pref.getLong("runtime_invalid",0)
            +"  | commandes envoyées : "+pref.getLong("runtime_sent",0)
            +"\nCouleur moyenne : "+pref.getString("runtime_color","aucune")
            +"\nErreurs Tuya : "+pref.getLong("runtime_failures",0)
            +" | derniere reponse : "+pref.getLong("runtime_ack_ms",0)+" ms"
            +" | age de l image : "+pref.getLong("runtime_frame_age_ms",0)+" ms";
    }
}
