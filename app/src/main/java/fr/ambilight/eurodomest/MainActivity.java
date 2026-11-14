package fr.ambilight.eurodomest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import java.util.Locale;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote-friendly one-page interface. No UDP, no Universal, no Tuya cloud. */
public final class MainActivity extends Activity {
    private static final int BG=0xff101b28,PANEL=0xff203348,ACCENT=0xffffc629;
    private static final int REQUEST_CAPTURE=420;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private Settings settings;
    private TextView status,message,secretState;
    private LinearLayout setup;
    private EditText ip,id,key;
    private Spinner version;
    private Button confirm;
    private boolean tested=false;
    private final Runnable refresh=new Runnable(){@Override public void run(){
        if(status!=null){String s=getSharedPreferences("ambilight_v07_runtime",MODE_PRIVATE).getString("status","Mode ADB prêt : association nécessaire");
            status.setText(s);}
        handler.postDelayed(this,1200);
    }};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);settings=new Settings(this);buildUi();handler.post(refresh);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
            android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},435);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private LinearLayout col(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private void add(LinearLayout c,View v){c.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);
        t.setTextSize(size);t.setPadding(dp(8),dp(9),dp(8),dp(9));return t;}
    private Button button(LinearLayout c,String title,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setTextSize(19);b.setMinHeight(dp(55));b.setText(title);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));c.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    private EditText field(String hint,String value,boolean secure){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextSize(19);e.setSingleLine(true);
        e.setTextColor(Color.WHITE);e.setHintTextColor(0xffa3bbcf);e.setPadding(dp(12),dp(10),dp(12),dp(10));
        if(secure)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;}
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=col();root.setPadding(dp(34),dp(14),dp(34),dp(24));scroll.addView(root);
        TextView heading=label("AMBILIGHT TCL",30);heading.setTextColor(ACCENT);add(root,heading);
        add(root,label("Autonome · Eurodomest RGBIC · 3 côtés · v0.7 ADB expérimental",16));
        LinearLayout main=col();main.setPadding(dp(18),dp(10),dp(18),dp(15));main.setBackgroundColor(PANEL);add(root,main);
        status=label("Prêt à démarrer",17);add(main,status);
        button(main,"DÉMARRER SANS PICTOGRAMME (ADB)",this::startAdbCapture);
        button(main,"Associer ADB sans PC (Safari sur iPhone)",this::openAdbPairing);
        button(main,"Capture de secours (MediaProjection)",this::requestCapture);
        button(main,"Arrêter",()->{AdbCaptureService.stop(this);CaptureService.stop(this);notice("Synchronisation arrêtée.");});
        add(main,label("Capture globale des menus et applications compatibles. Première association via code ADB depuis Safari sur ton iPhone, puis aucune commande à taper. 4K/HDR reste expérimentale.",15));
        button(root,"Corriger la répartition gauche / droite",()->{
            boolean original=getSharedPreferences("ambilight_v05_options",MODE_PRIVATE).getBoolean("reverse",true);
            getSharedPreferences("ambilight_v05_options",MODE_PRIVATE).edit().putBoolean("reverse",!original).apply();
            notice("Sens des zones inversé. Relance une vidéo gauche/ droite : l'ajustement est immédiat si l'Ambilight tourne. " +
                (original?"Mode direct activé.":"Mode inversé activé."));
        });
        button(root,"Configurer le boîtier / premiers tests  ▾",()->{
            setup.setVisibility(setup.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });
        setup=col();setup.setPadding(dp(20),dp(12),dp(20),dp(12));setup.setBackgroundColor(PANEL);
        add(setup,label("Adresse IP locale Eurodomest",16));ip=field("192.168.1.x",settings.ip(),false);add(setup,ip);
        add(setup,label("Identifiant Tuya / Smart Life",16));id=field("Device ID",settings.id(),false);add(setup,id);
        add(setup,label("Version du protocole Tuya",16));version=new Spinner(this);
        String[] values={"3.3","3.4","3.5"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);version.setAdapter(adapter);
        for(int j=0;j<values.length;j++)if(values[j].equals(settings.version()))version.setSelection(j);
        add(setup,version);
        add(setup,label("Local Key (enregistrée chiffrée, ne pas publier)",16));key=field("Coller / saisir la Local Key", "",true);add(setup,key);
        secretState=label(settings.hasSecret()?"Clé déjà enregistrée":"Clé non enregistrée",15);add(setup,secretState);
        button(setup,"Enregistrer le boîtier",this::saveDevice);
        button(setup,"Tester quatre couleurs",this::testFourColors);
        confirm=button(setup,"OUI, j'ai vu 4 couleurs distinctes",this::approve);
        confirm.setEnabled(false);
        add(setup,label("Le test RGBIC peut laisser les couleurs du ruban modifiées. En cas de souci, utiliser Smart Life pour revenir au mode habituel.",14));
        setup.setVisibility(settings.approved()&&settings.hasSecret()?View.GONE:View.VISIBLE);add(root,setup);
        message=label("",16);add(root,message);
        button(root,"Diagnostic et limites",()->new AlertDialog.Builder(this).setTitle("Diagnostic")
            .setMessage("Capture ADB sans indicateur MediaProjection : screencap via shell ADB TLS après association.\n"+
                "Il s'agit de captures PNG complètes, susceptibles de ralentir la TV ; aucune garantie 4K HDR.\n"+
                "LED : quatre couleurs DP61, cadence 200 à 160 ms selon réponses Tuya.\n"+
                "Recadrage automatique des bandes noires stabilisé sur plusieurs images.\n"+
                "Un effet sur la lecture 4K/HDR reste possible ; la compatibilité ne peut être confirmée que sur la télévision.")
            .setPositiveButton("OK",null).show());
        setContentView(scroll);
    }
    private void notice(String s){message.setText(s);}
    private void error(String s){notice(s);new AlertDialog.Builder(this).setTitle("Diagnostic").setMessage(s).setPositiveButton("OK",null).show();}
    private void saveDevice(){
        try{
            String address=ip.getText().toString().trim();String dev=id.getText().toString().trim();
            InetAddress a=InetAddress.getByName(address);
            if(!address.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")||!a.isSiteLocalAddress()||a.isLoopbackAddress())
                throw new IllegalArgumentException("IP locale invalide");
            if(dev.isEmpty()||dev.matches(".*\\s.*"))throw new IllegalArgumentException("Device ID invalide");
            settings.save(dev,address,version.getSelectedItem().toString(),key.getText().toString().trim());
            key.setText("");secretState.setText("Clé enregistrée chiffrée : "+(settings.hasSecret()?"oui":"non"));
            tested=false;confirm.setEnabled(false);notice("Configuration enregistrée. Effectuer le test quatre couleurs.");
        }catch(Exception e){error("Enregistrement : "+e.getClass().getSimpleName()+" "+e.getMessage());}
    }
    private void testFourColors(){
        if(!settings.hasSecret()||settings.ip().isEmpty()||settings.id().isEmpty()){error("Configurer d'abord le boîtier.");return;}
        confirm.setEnabled(false);tested=false;notice("Test RGBIC en cours...");
        executor.execute(()->{
            try(TuyaClient client=new TuyaClient(settings.id(),settings.ip(),settings.secret(),settings.version())){
                client.setFourColors(new int[][]{{255,0,0},{0,255,0},{0,0,255},{255,255,0}},20);
                runOnUiThread(()->{tested=true;confirm.setEnabled(true);notice("Commande envoyée. Confirmer uniquement si 4 couleurs différentes sont visibles.");});
            }catch(Exception e){runOnUiThread(()->error("Test RGBIC : "+e.getClass().getSimpleName()+" "+e.getMessage()));}
        });
    }
    private void approve(){if(!tested)return;
        new AlertDialog.Builder(this).setTitle("Confirmer les couleurs")
            .setMessage("As-tu réellement vu quatre couleurs simultanées sur le ruban Eurodomest ?")
            .setPositiveButton("Oui",(d,w)->{
                settings.setApproved(true);settings.setRgbic(true);confirm.setEnabled(false);
                notice("Eurodomest validé. Appuyer sur DÉMARRER AMBILIGHT puis autoriser l'écran entier.");
            }).setNegativeButton("Non",(d,w)->{settings.setApproved(false);notice("Mode automatique désactivé.");}).show();
    }
    private void startAdbCapture(){
        if(!settings.approved()||!settings.rgbic()||!settings.hasSecret()){
            setup.setVisibility(View.VISIBLE);
            error("Configurer et confirmer les quatre couleurs Eurodomest avant le démarrage.");
            return;
        }
        CaptureService.stop(this);
        try{
            AdbCaptureService.start(this);
            notice("Démarrage ADB demandé. Si l'association manque, utilise le bouton Associer ADB.");
        }catch(Exception e){error("Capture ADB : "+e.getMessage());}
    }
    private void openAdbPairing(){
        String tv=AdbPairServerService.tvIp();
        if(tv.equals("IP_TV")){error("Adresse IP de la TV non détectée : vérifier le Wi-Fi.");return;}
        String pin=AdbPairServerService.newPin();
        try{AdbPairServerService.start(this,pin);}
        catch(Exception e){error("Serveur d'association : "+e.getMessage());return;}
        String url="http://"+tv+":"+AdbPairServerService.PORT;
        new AlertDialog.Builder(this)
            .setTitle("Association ADB sans ordinateur")
            .setMessage("1. Note cette adresse Safari : "+url+
                "\n2. Note le PIN temporaire : "+pin+
                "\n3. Sur la TCL, ouvre Options pour développeurs > Débogage sans fil > Associer avec un code. Garde le code affiché."+
                "\n4. Sur l'iPhone (même Wi-Fi), ouvre Safari à l'adresse ci-dessus et saisis PIN, port et code affichés sur la TCL."+
                "\n5. Reviens dans Ambilight et lance le mode ADB."+
                "\n\nLe serveur local s'arrête automatiquement après 10 minutes. Aucun identifiant Tuya n'est envoyé.")
            .setPositiveButton("Ouvrir options développeur",(d,w)->{
                try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}
                catch(Exception e){error("Ouvre manuellement Paramètres > Options pour les développeurs.");}
            })
            .setNeutralButton("Fermer",null)
            .setNegativeButton("Annuler le serveur",(d,w)->AdbPairServerService.stop(this)).show();
    }
    private void requestCapture(){
        if(!settings.approved()||!settings.rgbic()||!settings.hasSecret()){
            setup.setVisibility(View.VISIBLE);error("Configurer puis confirmer le test quatre couleurs avant la capture.");return;
        }
        try{
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            Intent request=Build.VERSION.SDK_INT>=34?
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();
            AdbCaptureService.stop(this);
            startActivityForResult(request,REQUEST_CAPTURE);
        }catch(Exception e){error("Autorisation capture impossible : "+e.getMessage());}
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code==REQUEST_CAPTURE){
            if(result==RESULT_OK&&data!=null){
                try{CaptureService.start(this,result,data);notice("Capture démarrée. Ouvre SmartTube ou YouTube et contrôle la fluidité.");}
                catch(Exception e){error("Démarrage service : "+e.getMessage());}
            }else notice("Capture refusée ou annulée. Aucun accès écran conservé.");
        }
    }
    @Override protected void onDestroy(){handler.removeCallbacks(refresh);executor.shutdownNow();super.onDestroy();}
}
