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
        if(modified)ed.putBoolean("approved",false).putBoolean("boot",false).putBoolean("rgbic",false);
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
    public String summary(){
        return pref.getString("runtime_state","Service arrêté")+"\nPaquets UDP : "+pref.getLong("runtime_packets",0)
            +"  | invalides : "+pref.getLong("runtime_invalid",0)
            +"  | commandes envoyées : "+pref.getLong("runtime_sent",0)
            +"\nCouleur moyenne : "+pref.getString("runtime_color","aucune");
    }
}
