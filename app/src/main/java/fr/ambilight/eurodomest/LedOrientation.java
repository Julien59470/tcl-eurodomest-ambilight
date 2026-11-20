package fr.ambilight.eurodomest;

/** Right bottom -> right top -> top right -> top left -> left bottom.
 * On the verified Eurodomest DP61 ordering, physical output is reversed.
 */
public final class LedOrientation {
    private LedOrientation(){}
    public static int[][] physical(int[][] logical,boolean reversed){
        if(logical==null||logical.length<4||logical.length>20||logical.length%4!=0)
            throw new IllegalArgumentException("Zones invalides");
        int n=logical.length;
        int[][] physical=new int[n][3];
        for(int i=0;i<n;i++){
            int[] source=logical[reversed?n-i-1:i];
            if(source==null||source.length!=3)throw new IllegalArgumentException("Couleur invalide");
            physical[i]=source.clone();
        }
        return physical;
    }
}
