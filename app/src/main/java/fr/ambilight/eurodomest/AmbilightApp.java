package fr.ambilight.eurodomest;

import android.app.Application;

public final class AmbilightApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        AdbBridge.AppContext.init(this);
    }
}
