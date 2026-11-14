package fr.ambilight.eurodomest;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

/**
 * All TLS connections are local to the television; no insecure adb tcpip 5555.
 * The client RSA key is an exportable software key for interoperability with the
 * Android-compatible Conscrypt TLS provider, but is encrypted at rest with
 * non-exportable AndroidKeystore AES-GCM. No private key or pairing code in logs.
 */
public final class AdbBridge extends AbsAdbConnectionManager {
    private static final String WRAP_ALIAS="eurodomest_adb_key_wrap_v07";
    private static volatile AdbBridge singleton;
    private final PrivateKey privateKey;
    private final Certificate certificate;
    private volatile AdbStream activeCapture;

    private AdbBridge() throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setTimeout(5,TimeUnit.SECONDS);
        Context context=AppContext.get();
        File certFile=new File(context.getFilesDir(),"adb_v07_cert.der");
        File encryptedKey=new File(context.getFilesDir(),"adb_v07_key.gcm");
        PrivateKey k;
        Certificate cert;
        if(certFile.exists()&&encryptedKey.exists()){
            try {
                byte[] cipher=Files.readAllBytes(encryptedKey.toPath());
                byte[] pkcs8=unwrap(cipher);
                k=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
                java.util.Arrays.fill(pkcs8,(byte)0);
                try(InputStream in=Files.newInputStream(certFile.toPath())){
                    cert=CertificateFactory.getInstance("X.509").generateCertificate(in);
                }
            }catch(Exception e){
                throw new IllegalStateException("Identité ADB chiffrée inaccessible. Réinstaller l'application pour réassocier.",e);
            }
        }else {
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048,new SecureRandom());
            KeyPair kp=generator.generateKeyPair();
            k=kp.getPrivate();
            X500Principal subject=new X500Principal("CN=Ambilight TCL");
            Date notBefore=new Date(System.currentTimeMillis()-86400000L);
            Calendar end=Calendar.getInstance();end.add(Calendar.YEAR,10);
            JcaX509v3CertificateBuilder builder=new JcaX509v3CertificateBuilder(
                subject,BigInteger.valueOf(new SecureRandom().nextInt(1000000000)+1),
                notBefore,end.getTime(),subject,kp.getPublic());
            cert=new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(k)));
            byte[] clear=k.getEncoded();
            byte[] encrypted=wrap(clear);
            java.util.Arrays.fill(clear,(byte)0);
            writePrivate(encryptedKey,encrypted);
            writePrivate(certFile,cert.getEncoded());
        }
        privateKey=k;certificate=cert;
    }
    private static void writePrivate(File file,byte[] data)throws Exception{
        File tmp=new File(file.getParentFile(),file.getName()+".tmp");
        try(FileOutputStream output=new FileOutputStream(tmp)){output.write(data);output.getFD().sync();}
        if(!tmp.renameTo(file))throw new IllegalStateException("Impossible de conserver l'identité ADB");
    }
    private static SecretKey wrappingKey()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(WRAP_ALIAS))return (SecretKey)ks.getKey(WRAP_ALIAS,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(WRAP_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return gen.generateKey();
    }
    private static byte[] wrap(byte[] plain)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,wrappingKey());
        byte[] nonce=cipher.getIV();
        byte[] result=cipher.doFinal(plain);
        byte[] output=new byte[nonce.length+result.length];
        System.arraycopy(nonce,0,output,0,nonce.length);
        System.arraycopy(result,0,output,nonce.length,result.length);
        return output;
    }
    private static byte[] unwrap(byte[] data)throws Exception{
        if(data.length<29)throw new IllegalArgumentException("Identité ADB tronquée");
        byte[] nonce=java.util.Arrays.copyOfRange(data,0,12);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,wrappingKey(),new GCMParameterSpec(128,nonce));
        return cipher.doFinal(data,12,data.length-12);
    }
    public static synchronized AdbBridge get() throws Exception{
        if(singleton==null)singleton=new AdbBridge();
        return singleton;
    }
    @Override protected PrivateKey getPrivateKey(){return privateKey;}
    @Override protected Certificate getCertificate(){return certificate;}
    @Override protected String getDeviceName(){return "TCL-Eurodomest-Ambilight";}

    public synchronized boolean pairLocal(int port,String code)throws Exception{
        if(port<1024||port>65535||code==null||!code.matches("[0-9]{6}"))
            throw new IllegalArgumentException("Port/code d'association invalide");
        return pair("127.0.0.1",port,code);
    }
    public synchronized boolean connectLocal(Context context)throws Exception{
        if(isConnected())return true;
        try{disconnect();}catch(Exception ignored){}
        Exception discoveryError=null;
        try{
            if(connectTls(context.getApplicationContext(),3500)||isConnected())return true;
        }catch(Exception error){discoveryError=error;}
        int port=context.getSharedPreferences("ambilight_adb_v08",Context.MODE_PRIVATE)
            .getInt("tls_connect_port",0);
        if(port>=1024&&port<=65535){
            try{
                try{disconnect();}catch(Exception ignored){}
                if(connect("127.0.0.1",port)||isConnected())return true;
                throw new java.io.IOException("Connexion TLS refusée");
            }catch(Exception directError){
                throw new java.io.IOException("Échec port TLS "+port+" : "+shortError(directError),directError);
            }
        }
        throw new java.io.IOException("Découverte automatique mDNS échouée : "+
            shortError(discoveryError)+". Renseigner le port de CONNEXION affiché dans Débogage sans fil.");
    }
    private static String shortError(Exception e){
        if(e==null)return "pas de service TLS découvert";
        String message=e.getMessage();
        if(message==null||message.trim().isEmpty())message=e.getClass().getSimpleName();
        return message.replaceAll("[\\r\\n\\t]"," ").substring(0,Math.min(160,message.length()));
    }
    public byte[] screenshotPng()throws Exception{
        AdbStream stream;
        synchronized(this){
            if(!isConnected())throw new IllegalStateException("ADB non connecté");
            stream=openStream("exec:screencap -p");
            activeCapture=stream;
        }
        try(AdbStream work=stream;InputStream in=stream.openInputStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(262144)){
            byte[] buf=new byte[16384];int count;
            while((count=in.read(buf))!=-1){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("Capture interrompue");
                if(bytes.size()+count>24*1024*1024)throw new IllegalStateException("PNG trop volumineux");
                bytes.write(buf,0,count);
            }
            byte[] out=bytes.toByteArray();
            if(out.length<16||out[0]!=(byte)0x89||out[1]!='P'||out[2]!='N'||out[3]!='G'){
                String prefix=new String(out,0,Math.min(out.length,100),java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll("[^\\x20-\\x7E]"," ");
                throw new IllegalStateException("ADB connecté mais screencap ne retourne pas de PNG ("+
                    out.length+" octets) : "+prefix);
            }
            return out;
        }finally{activeCapture=null;}
    }
    public void cancelCapture(){
        AdbStream s=activeCapture;
        if(s!=null)try{s.close();}catch(Exception ignored){}
    }
    public synchronized void disconnectSafely(){
        cancelCapture();
        try{disconnect();}catch(Exception ignored){}
    }

    // Set once by our Application class on the UI thread before service use.
    static final class AppContext {
        private static volatile Context context;
        static void init(Context c){context=c.getApplicationContext();}
        static Context get(){
            if(context==null)throw new IllegalStateException("Contexte Ambilight indisponible");
            return context;
        }
    }
}
