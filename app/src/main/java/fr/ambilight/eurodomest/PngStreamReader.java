package fr.ambilight.eurodomest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Parses standard PNG framing from an ADB exec stream.
 * Stops immediately when IEND is received: broken ADB EOF handling cannot
 * misclassify a complete frame as a failed command or block waiting for CLSE.
 * No disk I/O or scanning of user screen data.
 */
public final class PngStreamReader {
    private static final int LIMIT=24*1024*1024;
    private static final byte[] MAGIC={(byte)0x89,80,78,71,13,10,26,10};
    private byte[] buffer=new byte[262144];
    private int size=0,position=8;
    private boolean complete=false;

    public static byte[] read(InputStream input) throws IOException {
        PngStreamReader reader=new PngStreamReader();
        byte[] block=new byte[32768];
        while(!reader.complete){
            int received;
            try{
                received=input.read(block);
            }catch(IOException error){
                // LibADB 3.1.1 can throw "Stream closed." for a normal peer CLSE.
                // Never suppress an incomplete frame: a true disconnect remains an error.
                throw new IOException("Flux ADB fermé avant la fin du PNG ("+
                    reader.size+" octets) : "+error.getMessage(),error);
            }
            if(received<0){
                throw new IOException(reader.size==0
                    ?"ADB a fermé la commande sans renvoyer d'image"
                    :"PNG tronqué : fermeture après "+reader.size+" octets");
            }
            if(received==0)continue;
            reader.accept(block,0,received);
        }
        return Arrays.copyOf(reader.buffer,reader.position);
    }

    public void accept(byte[] source,int offset,int length) throws IOException {
        if(complete)throw new IOException("Image déjà terminée");
        if(length<0||offset<0||offset>source.length-length)throw new IOException("Données PNG incorrectes");
        if((long)size+length>LIMIT)throw new IOException("PNG trop volumineux (>24 Mio)");
        ensureCapacity(size+length);
        System.arraycopy(source,offset,buffer,size,length);
        size+=length;
        if(size>=8){
            for(int i=0;i<8;i++)if(buffer[i]!=MAGIC[i])
                throw new IOException("Le service ADB n'a pas renvoyé de PNG (entête invalide)");
        }
        while(size-position>=12&&!complete){
            long chunkLength=((long)(buffer[position]&255)<<24)
                |((long)(buffer[position+1]&255)<<16)
                |((long)(buffer[position+2]&255)<<8)
                |(buffer[position+3]&255);
            if(chunkLength>LIMIT||position+12+chunkLength>LIMIT)
                throw new IOException("Taille de bloc PNG invalide");
            int end=position+12+(int)chunkLength;
            if(end>size)break;
            boolean iend=buffer[position+4]=='I'&&buffer[position+5]=='E'
                &&buffer[position+6]=='N'&&buffer[position+7]=='D';
            if(position==8 && !(buffer[position+4]=='I'&&buffer[position+5]=='H'
                &&buffer[position+6]=='D'&&buffer[position+7]=='R'))
                throw new IOException("PNG sans IHDR");
            position=end;
            if(iend){
                if(chunkLength!=0)throw new IOException("IEND PNG incorrect");
                complete=true;
            }
        }
    }
    private void ensureCapacity(int required){
        if(required<=buffer.length)return;
        int target=buffer.length;
        while(target<required)target=Math.min(LIMIT,target*2);
        buffer=Arrays.copyOf(buffer,target);
    }
}
