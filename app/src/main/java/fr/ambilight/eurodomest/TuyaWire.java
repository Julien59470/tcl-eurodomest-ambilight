package fr.ambilight.eurodomest;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Local Tuya transport for 3.3, 3.4, 3.5. Independent of Android classes.
 * Designed against TinyTuya PROTOCOL.md and message_helper.py; physical compatibility untested.
 */
public final class TuyaWire {
    static final int P55=0x000055aa, S55=0x0000aa55, P66=0x00006699, S66=0x00009966;
    private static final SecureRandom RANDOM = new SecureRandom();
    private TuyaWire() {}
    public static final class Packet {
        public final int seq, cmd, retcode;
        public final byte[] payload;
        Packet(int seq, int cmd, int retcode, byte[] payload) {
            this.seq=seq; this.cmd=cmd; this.retcode=retcode; this.payload=payload;
        }
    }
    public static byte[] key(String localKey) {
        if (localKey == null) throw new IllegalArgumentException("Local Key absente");
        byte[] key = localKey.getBytes(StandardCharsets.UTF_8);
        if (key.length != 16) throw new IllegalArgumentException("Local Key : 16 octets UTF-8 requis");
        return key;
    }
    public static byte[] random(int bytes) {
        byte[] out=new byte[bytes]; RANDOM.nextBytes(out); return out;
    }
    public static byte[] xor(byte[] a,byte[] b) {
        if(a.length!=b.length) throw new IllegalArgumentException("Nonce incohérent");
        byte[] out=new byte[a.length]; for(int i=0;i<a.length;i++) out[i]=(byte)(a[i]^b[i]); return out;
    }
    public static byte[] ecbEncrypt(byte[] key,byte[] plain,boolean pad) throws GeneralSecurityException {
        Cipher c=Cipher.getInstance(pad?"AES/ECB/PKCS5Padding":"AES/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES")); return c.doFinal(plain);
    }
    public static byte[] ecbDecrypt(byte[] key,byte[] ciphertext) throws GeneralSecurityException {
        Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES")); return c.doFinal(ciphertext);
    }
    public static byte[] mac(byte[] key,byte[] data) throws GeneralSecurityException {
        Mac m=Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(key,"HmacSHA256")); return m.doFinal(data);
    }
    private static byte[] gcm(boolean encrypt,byte[] key,byte[] iv,byte[] aad,byte[] data) throws GeneralSecurityException {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        if(aad!=null)c.updateAAD(aad);
        return c.doFinal(data);
    }
    public static byte[] deriveSession(int version,byte[] localKey,byte[] clientNonce,byte[] deviceNonce) throws GeneralSecurityException {
        byte[] combined=xor(clientNonce,deviceNonce);
        if(version==34) return ecbEncrypt(localKey,combined,false);
        if(version==35) {
            // TinyTuya takes the 16 cipher bytes at [12:28] from IV|cipher|tag.
            return Arrays.copyOfRange(gcm(true,localKey,Arrays.copyOf(clientNonce,12),null,combined),0,16);
        }
        throw new IllegalArgumentException("Pas de session pour ce protocole");
    }
    public static byte[] encode(int version,int seq,int cmd,byte[] content,byte[] key) throws GeneralSecurityException {
        byte[] body=content;
        boolean noHeader=(cmd==3||cmd==4||cmd==5||cmd==9||cmd==10||cmd==16||cmd==18||cmd==64);
        if(!noHeader) {
            byte[] header=(version==35?"3.5":version==34?"3.4":"3.3").getBytes(StandardCharsets.US_ASCII);
            body=concat(header,new byte[12],body);
        }
        if(version==33) {
            byte[] ciphertext=ecbEncrypt(key,content,true);
            if(!noHeader) body=concat(Arrays.copyOf(body,15),ciphertext); else body=ciphertext;
            return frame55(seq,cmd,body,null);
        }
        if(version==34) return frame55(seq,cmd,ecbEncrypt(key,body,true),key);
        if(version==35) return frame66(seq,cmd,body,key,random(12));
        throw new IllegalArgumentException("Version non prise en charge : "+version);
    }
    private static byte[] frame55(int seq,int cmd,byte[] payload,byte[] hmacKey) throws GeneralSecurityException {
        int endSize=hmacKey==null?8:36;
        ByteBuffer buf=ByteBuffer.allocate(16+payload.length+endSize).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(P55).putInt(seq).putInt(cmd).putInt(payload.length+endSize).put(payload);
        int used=buf.position();
        if(hmacKey==null) {
            CRC32 crc=new CRC32();crc.update(buf.array(),0,used);buf.putInt((int)crc.getValue());
        } else buf.put(mac(hmacKey,Arrays.copyOf(buf.array(),used)));
        buf.putInt(S55); return buf.array();
    }
    private static byte[] frame66(int seq,int cmd,byte[] payload,byte[] key,byte[] iv) throws GeneralSecurityException {
        int size=18+12+payload.length+16+4;
        ByteBuffer buf=ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(P66).putShort((short)0).putInt(seq).putInt(cmd).putInt(12+payload.length+16);
        byte[] header=Arrays.copyOfRange(buf.array(),4,18);
        byte[] ciphertext=gcm(true,key,iv,header,payload);
        buf.put(iv).put(ciphertext).putInt(S66);return buf.array();
    }
    public static Packet receive(InputStream input,int version,byte[] key) throws IOException, GeneralSecurityException {
        byte[] prefix=readFully(input,4);
        int marker=ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN).getInt();
        if(marker==P55) {
            if(version==35)throw new IOException("Trame 55AA inattendue pour 3.5");
            byte[] words=readFully(input,12);
            ByteBuffer head=ByteBuffer.wrap(words).order(ByteOrder.BIG_ENDIAN);
            int seq=head.getInt(),cmd=head.getInt(),len=head.getInt();
            int authLen=version==34?36:8;
            if(len<authLen||len>262144)throw new IOException("Taille trame Tuya invalide");
            byte[] tail=readFully(input,len);
            ByteBuffer check=ByteBuffer.wrap(tail).order(ByteOrder.BIG_ENDIAN);
            if(check.getInt(len-4)!=S55)throw new IOException("Suffixe Tuya incorrect");
            byte[] authInput=concat(prefix,words,Arrays.copyOfRange(tail,0,len-authLen));
            if(version==34) {
                byte[] expected=mac(key,authInput);
                if(!MessageDigest.isEqual(expected,Arrays.copyOfRange(tail,len-36,len-4)))
                    throw new GeneralSecurityException("HMAC Tuya incorrect");
            } else {
                CRC32 crc=new CRC32();crc.update(authInput);
                if((int)crc.getValue()!=check.getInt(len-8))throw new GeneralSecurityException("CRC Tuya incorrect");
            }
            if(len<authLen+4)throw new IOException("Trame de réponse Tuya trop courte");
            int ret=check.getInt(0); // receive frames include a clear return code
            byte[] payload=Arrays.copyOfRange(tail,4,len-authLen);
            return new Packet(seq,cmd,ret,payload);
        }
        if(marker==P66) {
            if(version!=35)throw new IOException("Trame 6699 inattendue pour Tuya 3.3/3.4");
            byte[] words=readFully(input,14);
            ByteBuffer head=ByteBuffer.wrap(words).order(ByteOrder.BIG_ENDIAN);
            head.getShort();int seq=head.getInt(),cmd=head.getInt(),len=head.getInt();
            if(len<28||len>262144)throw new IOException("Longueur GCM invalide");
            byte[] content=readFully(input,len+4);
            if(ByteBuffer.wrap(content,len,4).getInt()!=S66)throw new IOException("Suffixe GCM incorrect");
            byte[] iv=Arrays.copyOfRange(content,0,12);
            byte[] encrypted=Arrays.copyOfRange(content,12,len);
            byte[] plain=gcm(false,key,iv,words,encrypted);
            if(plain.length<4) throw new IOException("Retour GCM trop court");
            int ret=ByteBuffer.wrap(plain).getInt();
            return new Packet(seq,cmd,ret,Arrays.copyOfRange(plain,4,plain.length));
        }
        throw new IOException("Préfixe Tuya incorrect");
    }
    public static byte[] decryptPayload(int version,byte[] key,byte[] message) throws GeneralSecurityException {
        if(message.length==0)return message;
        if(version==35)return trimHeader("3.5",message);
        if(version==34)return trimHeader("3.4",ecbDecrypt(key,message));
        if(version==33) {
            byte[] body=message;
            if(message.length>=15&&message[0]=='3'&&message[1]=='.'&&message[2]=='3')
                body=Arrays.copyOfRange(message,15,message.length);
            return ecbDecrypt(key,body);
        }
        throw new IllegalArgumentException("Version non prise en charge");
    }
    private static byte[] trimHeader(String v,byte[] message) {
        byte[] h=v.getBytes(StandardCharsets.US_ASCII);
        if(message.length>=15&&message[0]==h[0]&&message[1]==h[1]&&message[2]==h[2])
            return Arrays.copyOfRange(message,15,message.length);
        return message;
    }
    public static byte[] concat(byte[]... chunks) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] part:chunks) out.write(part,0,part.length);
        return out.toByteArray();
    }
    static byte[] readFully(InputStream in,int count) throws IOException {
        byte[] bytes=new byte[count];int read=0;
        while(read<count) {int n=in.read(bytes,read,count-read);if(n<0)throw new EOFException("Connexion Tuya interrompue");read+=n;}
        return bytes;
    }
}
