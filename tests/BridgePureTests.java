import fr.ambilight.eurodomest.RawRgb;
import fr.ambilight.eurodomest.TuyaWire;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

public class BridgePureTests {
    private static int pass;
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);pass++;
    }
    public static void main(String[] args) throws Exception {
        check(Arrays.equals(RawRgb.average(new byte[]{(byte)255,0,0,0,(byte)255,0},6),new int[]{128,128,0}),"avg");
        check("000003e803e8".equals(RawRgb.hsv(new int[]{255,0,0})),"red hsv");
        check("007803e803e8".equals(RawRgb.hsv(new int[]{0,255,0})),"green hsv");
        check("00f003e803e8".equals(RawRgb.hsv(new int[]{0,0,255})),"blue hsv");
        try{RawRgb.average(new byte[]{1,2},2);throw new AssertionError("invalid should fail");}
        catch(IllegalArgumentException expected){pass++;}
        byte[] key="0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] json="{\"dps\":{\"20\":true}}".getBytes(StandardCharsets.UTF_8);
        for(int version:new int[]{33,34,35}) {
            byte[] frame=TuyaWire.encode(version,17,version==33?10:16,json,key);
            check(ByteBuffer.wrap(frame).getInt()==(version==35?0x6699:0x55aa),"prefix "+version);
            if(version!=35){
                byte[] payload=Arrays.copyOfRange(frame,16,frame.length-(version==34?36:8));
                byte[] plain=TuyaWire.decryptPayload(version,key,payload);
                check(Arrays.equals(plain,json),"query crypto roundtrip "+version);
                byte[] reply=make55Response(frame,version==34,key);
                TuyaWire.Packet p=TuyaWire.receive(new ByteArrayInputStream(reply),version,key);
                check(p.retcode==0&&p.cmd==(version==33?10:16),"55 read "+version);
                check(Arrays.equals(TuyaWire.decryptPayload(version,key,p.payload),json),"55 decoded "+version);
                reply[reply.length-5]^=1;
                try{TuyaWire.receive(new ByteArrayInputStream(reply),version,key);throw new AssertionError("Invalid auth accepted");}
                catch(java.security.GeneralSecurityException expected){pass++;}
            }else{
                // Version 3.5 GCM frame creation roundtrip for device-like reply with retcode=0.
                Method method=TuyaWire.class.getDeclaredMethod("frame66",int.class,int.class,byte[].class,byte[].class,byte[].class);
                method.setAccessible(true);
                byte[] clear=TuyaWire.concat(new byte[4],json);
                byte[] iv=new byte[12];Arrays.fill(iv,(byte)33);
                byte[] reply=(byte[])method.invoke(null,17,16,clear,key,iv);
                TuyaWire.Packet p=TuyaWire.receive(new ByteArrayInputStream(reply),35,key);
                check(p.retcode==0&&Arrays.equals(p.payload,json),"GCM parse");
                reply[reply.length-10]^=1;
                try{TuyaWire.receive(new ByteArrayInputStream(reply),35,key);throw new AssertionError("Invalid tag accepted");}
                catch(java.security.GeneralSecurityException expected){pass++;}
            }
        }
        byte[] nonce=TuyaWire.random(16),other=TuyaWire.random(16);
        check(TuyaWire.deriveSession(34,key,nonce,other).length==16,"v3.4 session key");
        check(TuyaWire.deriveSession(35,key,nonce,other).length==16,"v3.5 session key");
        check(!Arrays.equals(TuyaWire.deriveSession(34,key,nonce,other),TuyaWire.deriveSession(35,key,nonce,other)),"version-specific session keys");
        System.out.println("OK : "+pass+" tests locaux (moyenne, HSV, AES ECB, HMAC, AES-GCM, intégrité, session key)");
    }
    private static byte[] make55Response(byte[] req,boolean hmac,byte[] key) throws Exception {
        int suffix=hmac?36:8;
        byte[] body=Arrays.copyOfRange(req,16,req.length-suffix);
        ByteBuffer b=ByteBuffer.allocate(req.length+4);
        b.putInt(0x55aa).putInt(17).putInt(ByteBuffer.wrap(req,8,4).getInt()).putInt(body.length+4+suffix);
        b.putInt(0).put(body);
        byte[] signatureInput=Arrays.copyOf(b.array(),b.position());
        if(hmac)b.put(TuyaWire.mac(key,signatureInput));
        else {CRC32 crc=new CRC32();crc.update(signatureInput);b.putInt((int)crc.getValue());}
        b.putInt(0xaa55);return b.array();
    }
}
