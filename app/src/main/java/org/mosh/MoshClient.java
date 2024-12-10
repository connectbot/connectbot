package org.mosh;

import android.util.Pair;
import android.util.Log;
import java.io.FileDescriptor;

public class MoshClient {
  public FileDescriptor clientFd;
  public Long processId;

  public MoshClient(String ip, Integer port) {
    Log.i("MoshClient", "Connecting to " + ip + ":" + port);
    Pair<FileDescriptor, Long> pair = forkExec(ip, port.toString());
    this.clientFd = pair.first;
    this.processId = pair.second;
  }

  /**
   * Fork-execute main() from mosh-client.
   *
   * @param ip The IP to connect to, as a string
   * @param port The port to connect to, as a string
   * @return A pair of the file descriptor of the started process, and it's PID.
   *
   */
  private static native Pair<FileDescriptor, Long> forkExec(String ip, String port);

  /**
   * Causes the calling thread to wait for the process associated with the receiver to finish
   * executing.
   *
   * @return The exit value of the Process being waited on
   *
   */
  private static native int waitFor(long processId);
  public int waitFor() {
    return waitFor(this.processId);
  }

  private static native int kill(long pid, int signal);
  public int kill(int signal) {
    return kill(this.processId, signal);
  }

  private static native void setPtyWindowSize(FileDescriptor fd, int row, int col,
      int xpixel, int ypixel);
  public void setPtyWindowSize(int row, int col, int xpixel, int ypixel) {
    setPtyWindowSize(this.clientFd, row, col, xpixel, ypixel);
  }

  public static native int setenv(String name, String value);

  /**
   * Execute main() from mosh-client.
   *
   * @return The exit value of mosh-client
   *
   */
  protected native int main(String[] args);

  static {
    try {
      System.loadLibrary("moshclient");
    } catch (UnsatisfiedLinkError e) {
      Log.e(MoshClient.class.getName(), "MoshClient failed to load", e);
      throw new RuntimeException("MoshClient failed to load", e);
    }
  }
}
