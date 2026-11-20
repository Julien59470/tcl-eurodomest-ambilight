package fr.ambilight.eurodomest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.WindowManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONObject;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TV-only UI: no ADB, PC, root, external capture hardware or Tuya cloud. */
public final class MainActivity extends Activity {
    private static final int BG=0xff101b28,PANEL=0xff203348,ACCENT=0xffffc629,REQUEST_CAPTURE=420;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private Settings settings;
    private TextView status,message,secretState,zoneState;
    private LinearLayout setup;
    private EditText ip,id,key;
    private Spinner version;
    private Button fourConfirm;
    private boolean fourSent=false;
    private String lastDiagnostic="";
    private final Runnable refresh=new Runnable(){@Override public void run(){
        if(status!=null)status.setText(getSharedPreferences("ambilight_v07_media_runtime",MODE_PRIVATE)
            .getString("status","Capture MediaProjection arrêtée. Configurer le boîtier pour commencer."));
        handler.postDelayed(this,1200);
    }};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);settings=new Settings(this);buildUi();handler.post(refresh);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
            android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},435);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private void add(LinearLayout c,View v){c.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private TextView text(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);
        t.setTextSize(size);t.setPadding(dp(8),dp(8),dp(8),dp(8));return t;
    }
    private Button button(LinearLayout box,String label,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setTextSize(18);b.setMinHeight(dp(54));b.setText(label);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));
        box.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private EditText field(String hint,String value,boolean secret){
        EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setSingleLine(true);
        e.setTextSize(18);e.setTextColor(Color.WHITE);e.setHintTextColor(0xff9fb2c7);
        if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=column();root.setPadding(dp(28),dp(12),dp(28),dp(24));
        scroll.addView(root);
        TextView heading=text("AMBILIGHT TCL",29);heading.setTextColor(ACCENT);add(root,heading);
        add(root,text("Eurodomest RGBIC · 3 côtés · v1.0.1 MediaProjection · 4–20 zones",16));
        LinearLayout panel=column();panel.setPadding(dp(15),dp(10),dp(15),dp(12));
        panel.setBackgroundColor(PANEL);add(root,panel);
        status=text("Arrêté",16);add(panel,status);
        button(panel,"DÉMARRER AMBILIGHT (CAPTURE ANDROID)",this::requestCapture);
        button(panel,"ARRÊTER LA SYNCHRONISATION",()->{
            CaptureService.stop(this);notice("Arrêt demandé.");
        });
        zoneState=text("Profil RGBIC validé : "+settings.zones()+" zones",16);add(panel,zoneState);
        button(panel,"SAISIR LA LOCAL KEY (CLAVIER TV)",this::editLocalKey);
        button(panel,"DIAGNOSTIC TUYA (LECTURE SEULE)",this::readTuyaStatus);
        button(panel,"TESTER LE PALIER RGBIC SUIVANT",this::testNext);
        button(panel,"RÉTABLIR LE PROFIL VALIDÉ",this::restoreColors);
        button(panel,"COPIER / PARTAGER LE DIAGNOSTIC",this::exportDiagnostic);
        add(panel,text("Capture de tout l'écran : accepter la demande Android à chaque session. Les contenus DRM/HDR peuvent rester impossibles à capturer. Aucun ADB.",14));
        button(root,"Configuration du boîtier / test initial ▾",()->{
            setup.setVisibility(setup.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });
        setup=column();setup.setPadding(dp(15),dp(12),dp(15),dp(12));setup.setBackgroundColor(PANEL);
        add(setup,text("IP LOCALE Eurodomest (pas l'IP Internet Smart Life)",15));
        ip=field("Ex. 192.168.1.2",settings.ip(),false);add(setup,ip);
        add(setup,text("DEVICE ID Tuya / ID virtuel",15));
        id=field("ID du boîtier",settings.id(),false);add(setup,id);
        add(setup,text("Protocole local Tuya",15));
        version=new Spinner(this);String[] vals={"3.3","3.4","3.5"};
        ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,vals);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);version.setAdapter(ad);
        for(int i=0;i<vals.length;i++)if(vals[i].equals(settings.version()))version.setSelection(i);
        add(setup,version);
        add(setup,text("LOCAL KEY (chiffrée sur la TV, jamais incluse dans le dépôt)",15));
        key=field("Local Key temporaire", "",true);
        button(setup,"SAISIR / MODIFIER LA LOCAL KEY",this::editLocalKey);
        secretState=text(settings.hasSecret()?"Local Key déjà enregistrée":"Local Key absente",15);add(setup,secretState);
        button(setup,"ENREGISTRER LA CONFIGURATION",this::saveDevice);
        button(setup,"TESTER 4 COULEURS (VALIDATION INITIALE)",this::testFour);
        fourConfirm=button(setup,"OUI, 4 COULEURS SIMULTANÉES",this::approveFour);
        fourConfirm.setEnabled(false);
        add(setup,text("Un test envoie réellement un motif au ruban. Il peut remplacer la scène Smart Life active.",13));
        setup.setVisibility(settings.approved()&&settings.hasSecret()?View.GONE:View.VISIBLE);
        add(root,setup);
        button(root,"CORRIGER LE SENS DU RUBAN (SI NÉCESSAIRE)",()->{
            boolean old=getSharedPreferences("ambilight_v05_options",MODE_PRIVATE).getBoolean("reverse",true);
            getSharedPreferences("ambilight_v05_options",MODE_PRIVATE).edit().putBoolean("reverse",!old).apply();
            notice(old?"Mapping DP61 direct activé.":"Mapping DP61 inversé rétabli.");
        });
        message=text("",15);add(root,message);
        setContentView(scroll);
    }
    private void notice(String msg){message.setText(msg);}
    private void error(String msg){
        notice(msg);new AlertDialog.Builder(this).setTitle("Diagnostic").setMessage(msg).setPositiveButton("OK",null).show();
    }
    /** The remote focuses this button; the text entry lives in a separate dialog.
     * This avoids the Android TV D-pad skipping a password EditText inside a ScrollView.
     * The value is never persisted until Enregistrer la configuration is pressed.
     */
    private void editLocalKey(){
        EditText input=field("Local Key Tuya (16 octets)",key.getText().toString(),true);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        LinearLayout body=column();
        body.setPadding(dp(20),dp(10),dp(20),dp(10));
        add(body,text("Saisir la clé avec le clavier TV, puis valider. Le texte reste masqué par défaut.",15));
        add(body,input);
        CheckBox visible=new CheckBox(this);
        visible.setText("Afficher la clé pendant la saisie");
        visible.setTextColor(Color.WHITE);
        visible.setOnCheckedChangeListener((button,checked)->{
            int cursor=Math.max(0,input.getSelectionStart());
            input.setInputType(InputType.TYPE_CLASS_TEXT |
                (checked?InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:InputType.TYPE_TEXT_VARIATION_PASSWORD));
            input.setSingleLine(true);
            input.setSelection(Math.min(cursor,input.length()));
        });
        add(body,visible);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Local Key Eurodomest")
            .setView(body)
            .setPositiveButton("Valider",null)
            .setNegativeButton("Annuler",null)
            .create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String value=input.getText().toString();
                try{
                    TuyaWire.key(value); // Must be exactly 16 UTF-8 bytes.
                    key.setText(value);
                    secretState.setText("Local Key saisie : appuyer sur ENREGISTRER LA CONFIGURATION");
                    notice("Local Key saisie temporairement. Enregistrer la configuration.");
                    dialog.dismiss();
                }catch(Exception e){
                    input.setError("Local Key invalide : 16 octets UTF-8 requis");
                    input.requestFocus();
                }
            });
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            input.postDelayed(()->{
                if(dialog.isShowing()){
                    InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    keyboard.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
                }
            },180);
        });
        dialog.show();
    }

    private void saveDevice(){
        try{
            String address=ip.getText().toString().trim(),device=id.getText().toString().trim();
            if(!address.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))
                throw new IllegalArgumentException("Adresse IPv4 requise");
            InetAddress parsed=InetAddress.getByName(address);
            if(!parsed.isSiteLocalAddress()||parsed.isLoopbackAddress())
                throw new IllegalArgumentException("Utiliser l'IP privée locale du boîtier");
            if(device.isEmpty()||device.matches(".*\\s.*"))
                throw new IllegalArgumentException("Device ID invalide");
            CaptureService.stop(this);
            settings.save(device,address,version.getSelectedItem().toString(),key.getText().toString().trim());
            key.setText("");fourSent=false;fourConfirm.setEnabled(false);
            secretState.setText(settings.hasSecret()?"Local Key enregistrée et chiffrée":"Local Key absente");
            zoneState.setText("Profil RGBIC validé : "+settings.zones()+" zones");
            notice("Configuration enregistrée. Tester puis confirmer les quatre couleurs.");
        }catch(Exception e){error("Configuration : "+e.getMessage());}
    }
    private boolean ready(){
        if(!settings.hasSecret()||settings.id().isEmpty()||settings.ip().isEmpty()){
            setup.setVisibility(View.VISIBLE);error("Renseigner et enregistrer le boîtier d'abord.");return false;
        }return true;
    }
    private static int[][] colors(int n){
        int[][] palette={{255,0,0},{0,0,255},{0,255,0},{255,255,0},{255,0,255},{0,255,255}};
        int[][] out=new int[n][3];
        for(int i=0;i<n;i++)out[i]=palette[i%palette.length].clone();
        if(n==4)out=new int[][]{{255,0,0},{0,255,0},{0,0,255},{255,255,0}};
        return out;
    }
    private void sendTest(int n,Runnable after){
        CaptureService.stop(this);
        notice("Test de "+n+" zones : envoi du motif...");
        jobs.execute(()->{
            try{
                Thread.sleep(260);
                try(TuyaClient c=new TuyaClient(settings.id(),settings.ip(),settings.secret(),settings.version())){
                    c.setFourColors(colors(n),settings.uiSegments());
                }
                runOnUiThread(after);
            }catch(Exception e){
                String why=e.getClass().getSimpleName()+": "+(e.getMessage()==null?"":e.getMessage());
                runOnUiThread(()->error("Test "+n+" zones refusé : "+why));
            }
        });
    }
    private void testFour(){
        if(!ready())return;
        fourSent=false;fourConfirm.setEnabled(false);
        sendTest(4,()->{
            fourSent=true;fourConfirm.setEnabled(true);
            notice("Commande envoyée. Confirmer SEULEMENT si les 4 couleurs sont simultanément visibles.");
        });
    }
    private void approveFour(){
        if(!fourSent)return;
        new AlertDialog.Builder(this).setTitle("Validation visuelle obligatoire")
            .setMessage("As-tu vu quatre couleurs différentes simultanément sur le ruban ?")
            .setPositiveButton("Oui",(d,w)->{
                settings.setApproved(true);settings.setRgbic(true);settings.setZones(4);
                zoneState.setText("Profil RGBIC validé : 4 zones");
                fourConfirm.setEnabled(false);fourSent=false;notice("4 zones validées. Tu peux commencer ou tester 8 zones.");
            }).setNegativeButton("Non",(d,w)->notice("Validation refusée. Aucun nouveau profil activé."))
            .show();
    }
    private void testNext(){
        if(!ready())return;
        if(!settings.approved()){error("Valider d'abord les quatre couleurs dans la configuration.");return;}
        int current=settings.zones(),next=current+4;
        if(next>20){notice("20 zones déjà validées. Aucun palier supplémentaire prévu.");return;}
        sendTest(next,()->new AlertDialog.Builder(this)
            .setTitle("Test "+next+" zones — vérification visuelle")
            .setMessage("Vois-tu réellement "+next+" sections distinctes, avec des couleurs alternées, au lieu de seulement "+current+" ? Une réponse Tuya valide ne suffit PAS.")
            .setPositiveButton("Oui, "+next+" sections",(d,w)->{
                settings.setZones(next);
                zoneState.setText("Profil RGBIC validé : "+next+" zones");
                notice(next+" zones validées. Ce profil sera utilisé par MediaProjection.");
            })
            .setNegativeButton("Non, conserver "+current,(d,w)->{
                notice("Profil conservé à "+current+" zones. Appuie sur Rétablir le profil validé.");
            }).show());
    }
    private void restoreColors(){
        if(!ready())return;
        int n=settings.zones();
        sendTest(n,()->notice("Motif du profil validé à "+n+" zones rétabli. La scène Smart Life initiale n'est pas sauvegardée."));
    }
    private void requestCapture(){
        if(!ready())return;
        if(!settings.approved()||!settings.rgbic()){
            setup.setVisibility(View.VISIBLE);error("Valider visuellement les quatre couleurs avant de démarrer.");return;
        }
        try{
            MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            Intent request=Build.VERSION.SDK_INT>=34?
                mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):
                mgr.createScreenCaptureIntent();
            CaptureService.stop(this);
            startActivityForResult(request,REQUEST_CAPTURE);
        }catch(Exception e){error("Autorisation Android : "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_CAPTURE)return;
        if(result!=RESULT_OK||data==null){notice("Capture refusée / annulée.");return;}
        try{CaptureService.start(this,result,data);
            notice("Capture demandée. Ouvre une vidéo compatible puis consulte les statistiques.");
        }catch(Exception e){error("Démarrage MediaProjection : "+e.getMessage());}
    }
    private String captureStatus(){
        return getSharedPreferences("ambilight_v07_media_runtime",MODE_PRIVATE).getString("status","Aucun diagnostic de capture");
    }
    private void readTuyaStatus(){
        if(!ready())return;
        notice("Lecture locale des datapoints en cours...");
        jobs.execute(()->{
            try(TuyaClient c=new TuyaClient(settings.id(),settings.ip(),settings.secret(),settings.version())){
                JSONObject dps=c.status();
                StringBuilder result=new StringBuilder("Données Tuya locales (lecture seule)\n");
                String[] known={"20","21","24","61","107","108","109","110"};
                for(String code:known)if(dps.has(code)){
                    String val=String.valueOf(dps.get(code));
                    if(val.length()>180)val=val.substring(0,180)+"… (tronqué)";
                    result.append("DP").append(code).append(" = ").append(val).append('\n');
                }
                result.append("DP disponibles : ").append(dps.length()).append('\n');
                String msg=result.toString();
                lastDiagnostic=msg;
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Tuya — lecture locale")
                    .setMessage(msg).setPositiveButton("Copier",(d,w)->copyReport(msg))
                    .setNegativeButton("Fermer",null).show());
            }catch(Exception e){
                String why=e.getClass().getSimpleName()+": "+(e.getMessage()==null?"":e.getMessage());
                runOnUiThread(()->error("Lecture Tuya indisponible : "+why+
                    ". La connexion peut être fonctionnelle même si le firmware ne répond pas à cette requête."));
            }
        });
    }
    private String report(){
        return "Ambilight TCL v1.0 / rapport sans identifiant ni Local Key\n"+
            "Android : "+Build.VERSION.RELEASE+"\n"+
            "Modèle Android : "+Build.MODEL+"\n"+
            "Firmware : "+Build.DISPLAY+"\n"+
            "Profil validé : "+settings.zones()+" zones\n"+
            "Tuya protocole : "+settings.version()+"\n"+
            captureStatus()+"\n"+lastDiagnostic;
    }
    private void copyReport(String value){
        ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("Ambilight TCL diagnostic",value));
        notice("Rapport copié dans le presse-papiers de la TV.");
    }
    private void exportDiagnostic(){
        String r=report();
        copyReport(r);
        new AlertDialog.Builder(this).setTitle("Diagnostic local — sans secrets")
            .setMessage(r).setPositiveButton("Partager",(d,w)->{
                Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT,r);
                try{startActivity(Intent.createChooser(share,"Partager le diagnostic"));}
                catch(Exception e){notice("Rapport copié. Aucune application de partage disponible.");}
            }).setNegativeButton("Fermer",null).show();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacks(refresh);jobs.shutdownNow();super.onDestroy();
    }
}
