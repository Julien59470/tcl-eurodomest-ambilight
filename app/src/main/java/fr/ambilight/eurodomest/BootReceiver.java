package fr.ambilight.eurodomest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx,Intent intent) {
        String action=intent==null?"":intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        Settings cfg=new Settings(ctx);
        if(cfg.approved()&&cfg.autoBoot()) {
            try{ BridgeService.start(ctx); }
            catch(RuntimeException blocked){
                cfg.stats("Démarrage automatique refusé par Android : ouvrir l'application",0,0,0,"aucune");
            }
        }
    }
}
