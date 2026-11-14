import fr.ambilight.eurodomest.PngStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Test data checks PNG framing only; pixels are not decoded by this class. */
public final class PngStreamReaderTests {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private static byte[] png(){
        return new byte[]{
            (byte)0x89,80,78,71,13,10,26,10,
            0,0,0,13,'I','H','D','R',
            0,0,0,1,0,0,0,1,8,2,0,0,0,
            0,0,0,0, // CRC placeholder for framing test
            0,0,0,0,'I','E','N','D',0,0,0,0
        };
    }
    public static void main(String[] args)throws Exception{
        byte[] source=png();
        check(Arrays.equals(source,PngStreamReader.read(new ByteArrayInputStream(source))),"Full PNG");
        InputStream fragmented=new InputStream(){
            int i=0;
            @Override public int read(){return i<source.length?source[i++]&255:-1;}
            @Override public int read(byte[] b,int off,int len){
                if(i==source.length)throw new RuntimeException("Must stop after IEND before EOF");
                b[off]=source[i++];return 1;
            }
        };
        check(Arrays.equals(source,PngStreamReader.read(fragmented)),"Fragmented PNG must not read EOF");
        InputStream remoteClose=new InputStream(){
            int i=0;
            @Override public int read(){return i<source.length?source[i++]&255:-1;}
            @Override public int read(byte[] b,int off,int len)throws IOException{
                if(i==source.length)throw new IOException("Stream closed.");
                int n=Math.min(len,Math.min(9,source.length-i));
                System.arraycopy(source,i,b,off,n);i+=n;return n;
            }
        };
        check(Arrays.equals(source,PngStreamReader.read(remoteClose)),"Closed at end of valid PNG");
        try{PngStreamReader.read(new ByteArrayInputStream(Arrays.copyOf(source,source.length-3)));
            throw new AssertionError("Truncated PNG accepted");
        }catch(IOException expected){check(expected.getMessage().contains("tronqué"),"Unexpected truncation error");}
        try{PngStreamReader.read(new ByteArrayInputStream(new byte[]{1,2,3,4,5,6,7,8}));
            throw new AssertionError("Wrong magic accepted");
        }catch(IOException expected){check(expected.getMessage().contains("entête invalide"),"Unexpected header error");}
        System.out.println("PngStreamReader : 5 tests OK");
    }
}
