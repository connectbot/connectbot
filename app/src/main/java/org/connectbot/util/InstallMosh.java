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
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import org.connectbot.R;
import org.connectbot.util.PreferenceConstants;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.os.Build;

public final class InstallMosh implements Runnable {
  private File data_dir;
  private File sharedir;
  private Context context;

  private final static String BINARY_VERSION = "1.5";

  // using installMessage as the object to lock to access static properties
  private static StringBuilder installMessage = new StringBuilder();
  private static String terminfoPath = null;
  private static boolean installStarted = false;
  private static boolean installDone = false;
  private static boolean installFailed;

  public InstallMosh(Context context) {
    this.context = context;
    String path = MessageFormat.format("/data/data/{0}/files/", context.getApplicationInfo().packageName);
    data_dir = new File(path);
    sharedir = new File(data_dir, "share");
    File terminfoDir = new File(sharedir, "terminfo");

    synchronized (installMessage) {
      terminfoPath = terminfoDir.getPath();

      Thread installThread = new Thread(this);
      installThread.setName("Install Thread");
      installThread.start();

      installStarted = true;
    }
  }

  @Override
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

  public static boolean getTerminfoInstallStatus() {
    String path = getTerminfoPath();
    if (path == null) {
      throw new NullPointerException("no terminfo path - was InstallTerminfo called?");
    }
    File terminfoDir = new File(path);
    return terminfoDir.exists();
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

    return installTerminfo();
  }

  private boolean installTerminfo() {
    File terminfoDir = new File(getTerminfoPath());
    if(!terminfoDir.exists()) {
      installMessage.append("installing curses\r\n");
      installMessage.append("into: "+terminfoDir.getPath()+"\r\n");
      try (InputStream terminfo_zip = context.getAssets().open("terminfo.zip");
          ZipInputStream zipInputStream = new ZipInputStream(terminfo_zip)) {
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
          File outputFile = new File(data_dir, entry.getName());
          installMessage.append("extracting: "+entry.getName()+"\r\n");

          // If it's a directory, ensure the directory exists
          if (entry.isDirectory()) {
            if (!outputFile.exists()) {
              outputFile.mkdirs();  // Create directories as needed
            }
          } else {
            // It's a file, so write it to the appropriate location
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
              parentDir.mkdirs();  // Ensure parent directories exist
            }

            try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
              byte[] buffer = new byte[1024];
              int length;
              while ((length = zipInputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, length);
              }
            }
          }

          // Close the current entry
          zipInputStream.closeEntry();
        }
      } catch (Exception e) {
        installMessage.append("exorcism failed (zip: "+e.toString()+")\r\n");
        return false;
      }
      installMessage.append("curses extracted\r\n");
    }
    return true;
  }
}
