/*
 * Mosh support Copyright 2012 Daniel Drown
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.util;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;

import org.connectbot.R;
import org.connectbot.util.PreferenceConstants;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.os.Build;

public final class InstallMosh implements Runnable {
    private File data_dir;
    private File bindir;
    private File sharedir;
    private Context context;

    private final static String BINARY_VERSION = "1.5";

    // using installMessage as the object to lock to access static properties
    private static StringBuilder installMessage = new StringBuilder();
    private static String moshPath = null;
    private static String terminfoPath = null;
    private static boolean installStarted = false;
    private static boolean installDone = false;
    private static boolean installFailed;

    public InstallMosh(Context context) {
        this.context = context;
        String path = MessageFormat.format("/data/data/{0}/files/", context.getApplicationInfo().packageName);
        data_dir = new File(path); // hard-coded in binary packages
        bindir = new File(data_dir, "bin");
        sharedir = new File(data_dir, "share");
        File moshFile = new File(bindir, "mosh-client");
        File terminfoDir = new File(sharedir, "terminfo");

        synchronized (installMessage) {
            moshPath = moshFile.getPath();
            terminfoPath = terminfoDir.getPath();
            installStarted = true;
        }
        Thread installThread = new Thread(this);
        installThread.setName("Install Thread");
        installThread.start();
    }

    public void run() {
        boolean installStatus = install();
        synchronized (installMessage) {
            installFailed = installStatus;
            installDone = true;
            installMessage.notifyAll();
        }
    }

    public static String getTerminfoPath() {
        synchronized (installMessage) {
            return terminfoPath;
        }
    }

    public static String getMoshPath() {
        synchronized (installMessage) {
            return moshPath;
        }
    }

    public static boolean getMoshInstallStatus() {
        String path = getMoshPath();
        if (path == null) {
            throw new NullPointerException("no mosh path - was InstallMosh called?");
        }
        File moshFile = new File(path);
        return moshFile.exists();
    }

    public static void waitForInstall() {
        synchronized(installMessage) {
            while(installDone != true) {
                try {
                    installMessage.wait();
                } catch(java.lang.InterruptedException e) {
                    return;
                }
            }
        }
        return;
    }

    public static boolean isInstallStarted() {
        synchronized(installMessage) {
            return installStarted;
        }
    }

    public static boolean isInstallDone() {
        synchronized(installMessage) {
            return installDone;
        }
    }

    public static String getInstallMessages() {
        synchronized(installMessage) {
            return installMessage.toString();
        }
    }

    public boolean install() {
        if(!data_dir.exists()) {
            if(!data_dir.mkdir()) {
                installMessage.append("mkdir "+data_dir.getPath()+" failed\r\n");
                return false;
            }
            installMessage.append("mkdir "+data_dir.getPath()+": done\r\n");
        }

        if(!bindir.exists()) {
            if(!bindir.mkdir()) {
                installMessage.append("mkdir "+bindir.getPath()+" failed\r\n");
                return false;
            }
            installMessage.append("mkdir "+bindir.getPath()+": done\r\n");
        }

        if(!installBusybox())
            return false;
        return installMosh();
    }

    private boolean installMosh() {
        File mosh_client_path = new File(moshPath);
        File busybox_path = new File(bindir, "busybox");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String moshVersion = prefs.getString(PreferenceConstants.INSTALLED_MOSH_VERSION, "");

        if(!mosh_client_path.exists() || !moshVersion.equals(BINARY_VERSION)) {
            installMessage.append("installing mosh-client binary\r\n");
            try {
                InputStream bin_tar = context.getResources().openRawResource(R.raw.mosh_tar_gz);
                Process untar = Runtime.getRuntime().exec(busybox_path.getPath() + " tar -C " + data_dir + " -zxf -");
                OutputStream tar_out = untar.getOutputStream();
                InputStream stdout = untar.getInputStream();
                InputStream stderr = untar.getErrorStream();
                byte[] buffer = new byte[4096];
                int num;

                while((num = bin_tar.read(buffer)) > 0) {
                    tar_out.write(buffer, 0, num);
                    if(stdout.available() > 0) {
                        byte[] std_str = new byte[4096];
                        num = stdout.read(std_str);
                        if(num > 0) {
                            installMessage.append("untar: "+new String(std_str, 0, num)+"\r\n");
                        }
                    }
                    if(stderr.available() > 0) {
                        byte[] err_str = new byte[4096];
                        num = stderr.read(err_str);
                        if(num > 0) {
                            installMessage.append("untar/error: "+new String(err_str, 0, num)+"\r\n");
                        }
                    }
                }
                bin_tar.close();
                tar_out.close(); 
                if(untar.waitFor() > 0) {
                    installMessage.append("mosh binary install failed/untar: exit status != 0\r\n");
                    return false;
                }
            } catch (Exception e) {
                installMessage.append("mosh binary install failed/untar: "+e.toString()+"\r\n");
                return false;
            }
            try {
                String binarytype = use_pie() ? "arm.pie" : "arm.nopie";
                Process ln_s = Runtime.getRuntime().exec(busybox_path.getPath()+" ln -sf "+data_dir+"/bin/mosh-client."+binarytype+" "+data_dir+"/bin/mosh-client");
                if(ln_s.waitFor() > 0) {
                    installMessage.append("mosh binary install failed/ln -s: exit status != 0\r\n");
                    return false;
                }
            } catch (Exception e) {
                installMessage.append("mosh binary install failed/ln -s: "+e.toString()+"\r\n");
                return false;
            }
            installMessage.append("mosh-client binary done\r\n");
            Editor edit = prefs.edit();
            edit.putString(PreferenceConstants.INSTALLED_MOSH_VERSION, BINARY_VERSION);
            edit.commit();
        }
        return true;
    }

    private boolean use_pie() { // use binaries compiled with -fPIE
        return Build.VERSION.SDK_INT >= 21; // Android Lollipop or later
    }

    private boolean installBusybox() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String busyboxVersion = prefs.getString(PreferenceConstants.INSTALLED_MOSH_VERSION, "");

        File busybox_path = new File(bindir, "busybox");
        if(!busybox_path.exists() || !busyboxVersion.equals(BINARY_VERSION)) {
            installMessage.append("installing busybox binary\r\n");
            try {
                InputStream busybox;
                if(use_pie()) {
                  busybox = context.getResources().openRawResource(R.raw.busybox);
                } else {
                  busybox = context.getResources().openRawResource(R.raw.busybox_arm_nopie);
                }
                FileOutputStream busybox_out = new FileOutputStream(busybox_path.getPath(), false);
                byte[] buffer = new byte[4096];
                int num;

                while((num = busybox.read(buffer)) > 0) {
                    busybox_out.write(buffer,0,num);
                }
                busybox_out.close();
                busybox.close();
            } catch(IOException e) {
                installMessage.append("mosh binary install failed/busybox: "+e.toString()+"\r\n");
                return false;
            }
            try {
                File chmod_bin = new File("/system/bin/chmod");
                if(!chmod_bin.exists()) {
                    chmod_bin = new File("/system/xbin/chmod");
                    if(!chmod_bin.exists()) {
                        installMessage.append("mosh binary install failed/chmod: unable to find chmod\r\n");
                        return false;
                    }
                }
                Process process = Runtime.getRuntime().exec(chmod_bin.getPath()+" 755 "+busybox_path.getPath());
                if(process.waitFor() > 0) {
                    installMessage.append("mosh binary install failed/chmod: exit status != 0\r\n");
                    return false;
                }
            } catch (Exception e) {
                installMessage.append("mosh binary install failed/chmod: "+e.toString()+"\r\n");
                return false;
            }
            installMessage.append("busybox written\r\n");
        }
        busybox_path.setExecutable(true);

        return true;
    }
}
