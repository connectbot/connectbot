package org.connectbot;
import android.os.StrictMode;
public class StrictModeSetup {
    public static void run() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX);
    }
}
