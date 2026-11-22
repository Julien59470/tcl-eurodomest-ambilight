package fr.ambilight.eurodomest;

import java.util.Arrays;

/** DP61 output index -> logical screen-edge index. Every assignment is a permutation. */
public final class SegmentMapping {
    private SegmentMapping(){}
    public static int[] defaults(int count,boolean reverse){
        validateCount(count);
        int[] result=new int[count];
        for(int i=0;i<count;i++)result[i]=reverse?count-1-i:i;
        return result;
    }
    public static void validateCount(int n){
        if(n<4||n>20||n%4!=0)throw new IllegalArgumentException("4, 8, 12, 16 ou 20 zones");
    }
    public static boolean valid(int[] map,int count){
        if(map==null||map.length!=count)return false;
        boolean[] used=new boolean[count];
        for(int index:map){if(index<0||index>=count||used[index])return false;used[index]=true;}
        return true;
    }
    /** Reassign one DP index to a logical screen index, swapping positions safely. */
    public static int[] swap(int[] old,int physical,int logical){
        if(!valid(old,old.length)||physical<0||physical>=old.length||logical<0||logical>=old.length)
            throw new IllegalArgumentException("Calibration invalide");
        int[] result=old.clone();
        int other=-1;
        for(int i=0;i<result.length;i++)if(result[i]==logical){other=i;break;}
        int previous=result[physical];
        result[physical]=logical;result[other]=previous;
        return result;
    }
    public static int[][] apply(int[][] logical,int[] physicalToLogical){
        if(logical==null||!valid(physicalToLogical,logical.length))
            throw new IllegalArgumentException("Mapping non bijectif");
        int[][] result=new int[logical.length][3];
        for(int i=0;i<logical.length;i++){
            int[] rgb=logical[physicalToLogical[i]];
            if(rgb==null||rgb.length!=3)throw new IllegalArgumentException("Couleur invalide");
            result[i]=rgb.clone();
        }
        return result;
    }
    public static String encode(int[] map){
        if(!valid(map,map.length))throw new IllegalArgumentException("Mapping invalide");
        StringBuilder out=new StringBuilder();
        for(int i=0;i<map.length;i++){if(i>0)out.append(',');out.append(map[i]);}
        return out.toString();
    }
    public static int[] decode(String encoded,int count,boolean defaultReverse){
        int[] backup=defaults(count,defaultReverse);
        if(encoded==null||encoded.isEmpty())return backup;
        try{
            String[] s=encoded.split(",",-1);
            if(s.length!=count)return backup;
            int[] result=new int[count];
            for(int i=0;i<count;i++)result[i]=Integer.parseInt(s[i]);
            return valid(result,count)?result:backup;
        }catch(NumberFormatException ex){return backup;}
    }
    public static String label(int logical,int count){
        validateCount(count);
        int side=count/4,top=count/2;
        if(logical<side)return "Droite · "+(logical+1)+"/"+side+" (bas vers haut)";
        if(logical<side+top)return "Haut · "+(logical-side+1)+"/"+top+" (droite vers gauche)";
        return "Gauche · "+(logical-side-top+1)+"/"+side+" (haut vers bas)";
    }
}
