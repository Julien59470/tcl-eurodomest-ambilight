package fr.ambilight.eurodomest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import org.json.JSONObject;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Android TV / D-pad friendly diagnostic and control UI, zero external dependencies. */
public final class MainActivity extends Activity {
    private static final int BG=0xff101c28, PANEL=0xff1d3040, WHITE=Color.WHITE, ACCENT=0xffffc629;
    private Settings cfg;
    private EditText ip,id,key;
    private Spinner version;
    private CheckBox boot,force24;
    private TextView status,output,subtitle;
    private Button confirm,confirmRgbic;
    private Spinner segments,rotation;
    private CheckBox reverse;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService tasks=Executors.newSingleThreadExecutor();
    private JSONObject lastProbe;
    private volatile boolean testInProgress=false;
    private final Runnable updater=new Runnable(){
        @Override public void run(){ if(status!=null) status.setText(cfg.summary()); handler.postDelayed(this,1300); }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);cfg=new Settings(this);
        getWindow().setFlags(128,128); // Keep awake while doing setup, not permanent service.
        createScreen();
        handler.post(updater);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
            android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},700);
    }
    private int px(int dp){return Math.round(dp*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int sp,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextColor(WHITE);v.setTextSize(sp);
        v.setPadding(px(5),px(8),px(5),px(8));
        if(bold)v.setTypeface(null,1);return v;
    }
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private void add(LinearLayout parent,View child){parent.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    private EditText edit(String placeholder,String value,boolean secret){
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(WHITE);e.setHintTextColor(0xffbdc8d0);
        e.setTextSize(19);e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        e.setPadding(px(12),px(12),px(12),px(12));e.setHint(placeholder);e.setText(value);
        e.setInputType(secret?(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD):InputType.TYPE_CLASS_TEXT);
        return e;
    }
    private Button button(String caption,LinearLayout parent,Runnable onClick){
        Button b=new Button(this);b.setText(caption);b.setAllCaps(false);b.setTextSize(18);b.setMinHeight(px(54));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,px(6),0,px(6));
        parent.addView(b,p);b.setOnClickListener(v->onClick.run());return b;
    }
    private LinearLayout section(LinearLayout root,String title){
        LinearLayout block=vertical();block.setPadding(px(18),px(14),px(18),px(14));block.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,px(10),0,px(10));root.addView(block,p);
        TextView header=text(title,22,true);header.setTextColor(ACCENT);add(block,header);return block;
    }
    private void createScreen(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=vertical();root.setPadding(px(40),px(22),px(40),px(36));scroll.addView(root);
        TextView title=text("AMBILIGHT  |  TCL + EURODOMEST",27,true);title.setTextColor(ACCENT);add(root,title);
        subtitle=text("Pont UDP Raw → Tuya local • Prototype 0.2 • RGBIC expérimental",17,false);add(root,subtitle);
        LinearLayout feed=section(root,"1 • Réception des couleurs");
        status=text("Chargement…",17,false);add(feed,status);
        button("Démarrer l’écoute UDP (sans écrire tant que non validé)",feed,()->{try{BridgeService.start(this);msg("Récepteur activé");}catch(Exception e){error("Démarrage bloqué : "+kind(e));}});
        button("Envoyer 3 couleurs de démonstration à 127.0.0.1",feed,this::demoUdp);
        button("Arrêter la synchronisation et l'écoute",feed,()->{BridgeService.stop(this);msg("Arrêt demandé");});
        add(feed,text("Universal Ambient Light : sortie UDP raw, hôte 127.0.0.1, port 19446, ordre RGB. Active d’abord sa capture d’écran.",17,false));
        LinearLayout config=section(root,"2 • Configuration Eurodomest (locale uniquement)");
        add(config,text("Adresse IP privée du boîtier Eurodomest",17,false));
        ip=edit("192.168.1.100",cfg.ip(),false);ip.setInputType(InputType.TYPE_CLASS_PHONE);add(config,ip);
        button("Chercher des annonces Tuya sur le Wi-Fi (10 secondes)",config,this::discover);
        add(config,text("Identifiant virtuel (Device ID) Smart Life",17,false));
        id=edit("Device ID",cfg.id(),false);add(config,id);
        add(config,text("Version du protocole local",17,false));
        version=new Spinner(this);String[] values={"3.3","3.4","3.5"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        version.setAdapter(adapter);
        for(int i=0;i<values.length;i++)if(values[i].equals(cfg.version()))version.setSelection(i);
        add(config,version);
        add(config,text("Local Key (16 octets ; le champ reste volontairement vide)",17,false));
        key=edit("Saisir la Local Key sur la TV", "",true);add(config,key);
        add(config,text("Local Key enregistrée : "+(cfg.hasSecret()?"oui, chiffrée":"non"),15,false));
        button("Enregistrer la configuration",config,this::saveSettings);
        LinearLayout testing=section(root,"3 • Vérification du boîtier et autorisation");
        button("Interroger les points de données (lecture seule)",testing,this::probe);
        force24=new CheckBox(this);force24.setText("Autoriser le test si DP24 est absent du statut (expérimental)");
        force24.setTextColor(WHITE);force24.setTextSize(16);add(testing,force24);
        button("Tester ROUGE puis BLEU pendant 2 secondes",testing,this::askPhysicalTest);
        confirm=button("J’ai VU rouge et bleu : autoriser la synchronisation",testing,this::confirmTest);
        confirm.setEnabled(false);
        LinearLayout rgbicBlock=section(root,"4 • RGBIC multizone : test expérimental DP61");
        add(rgbicBlock,text("Le matériel doit d'abord répondre au test 4 couleurs. Sinon garder le mode global. 20 = nombre de zones UI Tuya hypothétique, PAS le nombre de LED physiques.",16,false));
        add(rgbicBlock,text("Nombre de segments UI dans le message Tuya",16,false));
        segments=new Spinner(this);String[] opts={"12","16","20","24","32"};
        ArrayAdapter<String> segAdapt=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,opts);
        segAdapt.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);segments.setAdapter(segAdapt);
        for(int i=0;i<opts.length;i++)if(Integer.toString(cfg.uiSegments()).equals(opts[i]))segments.setSelection(i);
        add(rgbicBlock,segments);
        add(rgbicBlock,text("Décalage du premier quart du ruban",16,false));
        rotation=new Spinner(this);String[] rot={"0","1","2","3"};
        ArrayAdapter<String> rotAdapt=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,rot);
        rotAdapt.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);rotation.setAdapter(rotAdapt);
        rotation.setSelection(cfg.rotation());add(rgbicBlock,rotation);
        reverse=new CheckBox(this);reverse.setText("Inverser l'ordre des quatre zones");
        reverse.setTextColor(WHITE);reverse.setChecked(cfg.reverse());add(rgbicBlock,reverse);
        button("Enregistrer disposition RGBIC (désactive RGBIC pour sécurité)",rgbicBlock,()->{
            cfg.setRgbicOptions(Integer.parseInt(segments.getSelectedItem().toString()),rotation.getSelectedItemPosition(),reverse.isChecked());
            confirmRgbic.setEnabled(false);msg("Disposition enregistrée. Refaire le test RGBIC avant de l'activer.");
        });
        button("Tester 4 couleurs différentes via DP61",rgbicBlock,this::askRgbicTest);
        confirmRgbic=button("J'ai VU 4 zones : autoriser le mode RGBIC",rgbicBlock,this::confirmRgbicTest);
        confirmRgbic.setEnabled(false);
        button("Revenir à la couleur globale",rgbicBlock,()->{cfg.setRgbic(false);BridgeService.stop(this);msg("RGBIC désactivé, revenir à UDP et Tuya global : redémarrer l'écoute.");});
        add(rgbicBlock,text("État RGBIC : "+(cfg.rgbic()?"activé (à tester en mouvement)":"non activé"),16,false));
        boot=new CheckBox(this);boot.setText("Démarrer le pont après démarrage Android (uniquement après validation)");
        boot.setTextColor(WHITE);boot.setTextSize(17);boot.setChecked(cfg.autoBoot());add(testing,boot);
        button("Enregistrer le choix de démarrage automatique",testing,()->{
            if(boot.isChecked()&&!cfg.approved()){error("Confirmer le test physique avant d'activer le démarrage automatique.");boot.setChecked(false);return;}
            cfg.setBoot(boot.isChecked());msg("Préférence enregistrée. Redémarrage Android complet à tester.");
        });
        LinearLayout logs=section(root,"5 • Diagnostic et protection");
        output=text("Aucun diagnostic encore exécuté.",16,false);output.setTextIsSelectable(true);add(logs,output);
        button("Effacer les identifiants et réinitialiser l’application",logs,()->new AlertDialog.Builder(this)
            .setTitle("Effacer la configuration ?")
            .setMessage("Supprime l'IP, l'identifiant, la clé chiffrée, l'autorisation et l'autodémarrage. L'Eurodomest conserve son état actuel.")
            .setPositiveButton("Effacer",(d,w)->{BridgeService.stop(this);cfg.clear();lastProbe=null;confirm.setEnabled(false);recreate();})
            .setNegativeButton("Annuler",null).show());
        setContentView(scroll);
    }
    private void msg(String s){output.setText(s);}
    private void error(String s){msg("ERREUR : "+s);new AlertDialog.Builder(this).setTitle("Diagnostic").setMessage(s).setPositiveButton("OK",null).show();}
    private String kind(Throwable t){String s=t.getMessage();return t.getClass().getSimpleName()+(s==null?"":": "+s.replaceAll("[\\r\\n]"," "));}
    private void runAsync(String label,Task task){
        msg(label+" …");tasks.execute(()->{
            try{String result=task.run();runOnUiThread(()->msg(result));}
            catch(Exception e){runOnUiThread(()->error(label+" : "+kind(e)));}
        });
    }
    interface Task {String run() throws Exception;}
    private void saveSettings(){
        String newIp=ip.getText().toString().trim(),newId=id.getText().toString().trim();
        String ver=version.getSelectedItem().toString(),secret=key.getText().toString().trim();
        if(!newIp.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")){error("Une adresse IPv4 numérique est requise.");return;}
        try {
            InetAddress address=InetAddress.getByName(newIp);
            if(!address.isSiteLocalAddress()||address.isLoopbackAddress())throw new IllegalArgumentException("Utiliser l’IP privée de l’Eurodomest, pas 127.0.0.1.");
            if(newId.isEmpty()||newId.length()>128||newId.matches(".*\\s.*"))throw new IllegalArgumentException("Device ID invalide.");
            BridgeService.stop(this); // avoids sending previous configuration during change
            cfg.save(newId,newIp,ver,secret);
            key.setText("");lastProbe=null;confirm.setEnabled(false);boot.setChecked(cfg.autoBoot());
            msg("Configuration enregistrée. Autorisation précédente invalidée si des paramètres ont changé.\nLa Local Key n'a pas été affichée.");
        }catch(Exception e){error("Configuration : "+kind(e));}
    }
    private void probe(){
        if(!cfg.hasSecret()){error("Il faut enregistrer une Local Key avant d'interroger Tuya.");return;}
        BridgeService.stop(this);
        runAsync("Interrogation en lecture seule",()->{
            try(TuyaClient client=new TuyaClient(cfg.id(),cfg.ip(),cfg.secret(),cfg.version())){
                JSONObject dps=client.status();
                lastProbe=dps;
                boolean valid=dps.opt("20") instanceof Boolean&&dps.opt("21") instanceof String;
                boolean dp24=!dps.has("24")||dps.opt("24") instanceof String;
                String result="Communication Tuya établie. DP détectés :\n"+truncate(dps.toString(2),1100);
                result+="\nDP20/21 : "+(valid?"types cohérents":"type ou présence incompatible");
                result+="\nDP24 : "+(dps.has("24")?dp24?"texte":"type incompatible":"non transmis (inconnu)");
                return result+"\nAUCUNE commande LED envoyée.";
            }
        });
    }
    private static String truncate(String text,int cap){return text.length()>cap?text.substring(0,cap)+"…":text;}
    private void askPhysicalTest(){
        if(testInProgress){error("Un test est déjà en cours.");return;}
        JSONObject dps=lastProbe;
        if(dps==null){error("Effectuer d'abord le test de lecture seule des DP.");return;}
        if(!(dps.opt("20") instanceof Boolean)||!(dps.opt("21") instanceof String)){
            error("DP20 booléen et DP21 texte sont obligatoires avant toute écriture.");return;
        }
        if(dps.has("24")&&!(dps.opt("24") instanceof String)){
            error("DP24 existe mais n'est pas une chaîne. Écriture bloquée.");return;
        }
        if(!dps.has("24")&&!force24.isChecked()){
            error("DP24 absent : cocher l'option expérimentale pour autoriser un essai manuel.");return;
        }
        new AlertDialog.Builder(this).setTitle("Autoriser un essai physique ?")
            .setMessage("Écrit les DP20/21/24 : ROUGE 2 secondes puis BLEU 2 secondes. L'état initial sera ensuite demandé, mais sa restauration n'est pas garantie. Vérifier les LED à l'œil.")
            .setPositiveButton("Je confirme",(d,w)->physicalTest())
            .setNegativeButton("Annuler",null).show();
    }
    private void physicalTest(){
        testInProgress=true;confirm.setEnabled(false);cfg.setApproved(false);cfg.setBoot(false);boot.setChecked(false);
        BridgeService.stop(this);
        msg("Test rouge/bleu en cours (environ 4 secondes + réseau)…");
        tasks.execute(()->{
            JSONObject before=null;String note="";boolean succeeded=false;
            try(TuyaClient client=new TuyaClient(cfg.id(),cfg.ip(),cfg.secret(),cfg.version())){
                before=client.status();
                if(!(before.opt("20") instanceof Boolean)||!(before.opt("21") instanceof String))
                    throw new IllegalStateException("Profil DP incohérent au début du test");
                try{
                    client.setColor(new int[]{255,0,0});Thread.sleep(2000);
                    client.setColor(new int[]{0,0,255});Thread.sleep(2000);
                    succeeded=true;
                } finally {
                    try{ client.restore(before);note="Restauration demandée (résultat non garanti)."; }
                    catch(Exception failure){note="Restauration NON CONFIRMÉE : remettre le mode depuis Smart Life.";}
                }
            }catch(Exception e){note="Test non validé : "+kind(e)+". "+note;}
            final String report=note;final boolean ok=succeeded;
            runOnUiThread(()->{
                testInProgress=false;
                msg((ok?"Commandes envoyées. ":"Test échoué. ")+report+
                    "\nConfirmer seulement si les LED ont réellement affiché ROUGE et BLEU.");
                confirm.setEnabled(ok);
            });
        });
    }
    private void confirmTest(){
        if(testInProgress||!confirm.isEnabled()){error("Le test physique n'est pas terminé.");return;}
        new AlertDialog.Builder(this).setTitle("As-tu réellement vu les deux couleurs ?")
            .setMessage("Appuie sur OUI uniquement si les LED Eurodomest sont devenues rouges puis bleues. Ne confirme pas uniquement parce que l'application affiche 'envoyé'.")
            .setPositiveButton("OUI, VU",(dialog,which)->{cfg.setApproved(true);confirm.setEnabled(false);msg("Test physique confirmé. Synchronisation automatique autorisée. Démarrer l'écoute UDP.");})
            .setNegativeButton("Non",(dialog,which)->{cfg.setApproved(false);confirm.setEnabled(false);msg("Test non confirmé. Écritures automatiques interdites.");}).show();
    }
    private void askRgbicTest(){
        if(!cfg.approved()){error("Valider d'abord les changements rouge/bleu de la couleur globale.");return;}
        if(testInProgress){error("Un test est déjà en cours.");return;}
        new AlertDialog.Builder(this).setTitle("Essai RGBIC sur le boîtier réel")
            .setMessage("Une unique commande expérimentale DP61 place 4 couleurs (rouge, vert, bleu, jaune) sur le ruban. Peut modifier le mode d'éclairage. Si besoin revenir à Smart Life. Ne pas confirmer si tout le ruban reste uni.")
            .setPositiveButton("Tester",(d,w)->rgbicTest())
            .setNegativeButton("Annuler",null).show();
    }
    private void rgbicTest(){
        testInProgress=true;confirmRgbic.setEnabled(false);BridgeService.stop(this);
        runAsync("Test RGBIC (une commande DP61)",()->{
            try(TuyaClient client=new TuyaClient(cfg.id(),cfg.ip(),cfg.secret(),cfg.version())){
                client.setFourColors(new int[][]{{255,0,0},{0,255,0},{0,0,255},{255,255,0}},cfg.uiSegments());
            }finally{runOnUiThread(()->{testInProgress=false;confirmRgbic.setEnabled(true);});}
            return "DP61 envoyé. Regarder le ruban : 4 sections de couleurs distinctes ? Si oui confirmer. Sinon revenir au mode global. Aucun retour automatique à l'état précédent garanti.";
        });
    }
    private void confirmRgbicTest(){
        if(!confirmRgbic.isEnabled()){error("Faire d'abord l'essai DP61.");return;}
        new AlertDialog.Builder(this).setTitle("Confirmation visuelle")
            .setMessage("As-tu réellement VU quatre sections du ruban de couleurs différentes (rouge / vert / bleu / jaune) ? Ne pas confirmer si le ruban est uniforme.")
            .setPositiveButton("Oui, 4 couleurs",(d,w)->{cfg.setRgbic(true);confirmRgbic.setEnabled(false);msg("RGBIC 4 zones activé. Démarrer l'écoute UDP puis Universal Ambient Light avec Send Average Color désactivé.");})
            .setNegativeButton("Non",(d,w)->{cfg.setRgbic(false);confirmRgbic.setEnabled(false);msg("Test non confirmé, mode global conservé.");}).show();
    }
    private void demoUdp(){
        runAsync("Démonstration UDP",()->{
            try(DatagramSocket socket=new DatagramSocket()){
                InetAddress host=InetAddress.getByName("127.0.0.1");
                for(int[] color:new int[][]{{255,0,0},{0,255,0},{0,0,255}}){
                    byte[] b=new byte[24*3];for(int i=0;i<24;i++)for(int j=0;j<3;j++)b[i*3+j]=(byte)color[j];
                    socket.send(new DatagramPacket(b,b.length,host,BridgeService.PORT));Thread.sleep(330);
                }
                return "3 datagrammes RGB envoyés au pont. Regarder le compteur UDP ci-dessus. Si la validation Tuya est active, les LED peuvent réagir.";
            }
        });
    }
    private void discover(){
        runAsync("Recherche passive de diffusion Tuya 6666/6667",()->{
            java.util.Set<String> ips=java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
            java.util.List<DatagramSocket> sockets=new ArrayList<>();
            List<Thread> threads=new ArrayList<>();
            android.net.wifi.WifiManager.MulticastLock lock=null;
            try {
                android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                if(wifi!=null){lock=wifi.createMulticastLock("Eurodomest-Discovery");lock.setReferenceCounted(false);lock.acquire();}
                for(int p:new int[]{6666,6667}){
                    try {
                        DatagramSocket s=new DatagramSocket(null);s.setReuseAddress(true);
                        s.bind(new InetSocketAddress(p));s.setSoTimeout(1000);sockets.add(s);
                        Thread worker=new Thread(()->{
                            byte[] buffer=new byte[4096];
                            while(!s.isClosed()){
                                try{DatagramPacket packet=new DatagramPacket(buffer,buffer.length);s.receive(packet);
                                    String found=packet.getAddress().getHostAddress();
                                    if(packet.getAddress().isSiteLocalAddress())ips.add(found);
                                }catch(java.net.SocketTimeoutException ignored){}
                                catch(Exception e){break;}
                            }
                        });worker.start();threads.add(worker);
                    }catch(Exception ignored){}
                }
                Thread.sleep(10000);
            }finally{
                for(DatagramSocket s:sockets)s.close();
                for(Thread t:threads)t.join(1200);
                if(lock!=null&&lock.isHeld())lock.release();
            }
            if(ips.isEmpty())return "Aucune diffusion Tuya reçue. Cela ne prouve pas que le boîtier est inaccessible : utiliser l’IP indiquée par le routeur.";
            List<String> list=new ArrayList<>(ips);
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("IP annonçant du trafic Tuya")
                .setItems(list.toArray(new String[0]),(dialog,which)->ip.setText(list.get(which)))
                .setNegativeButton("Annuler",null).show());
            return "Annonces UDP de "+list.size()+" IP privées. Choisir seulement l'IP correspondant à l'Eurodomest. Le modèle n'est pas identifié par ce scan.";
        });
    }
    @Override protected void onDestroy(){handler.removeCallbacks(updater);tasks.shutdownNow();super.onDestroy();}
}
