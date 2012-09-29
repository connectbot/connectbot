/*
 * This file is part of "JTA - Telnet/SSH for the JAVA(tm) platform".
 *
 * (c) Matthias L. Jugel, Marcus Meiner 1996-2005. All Rights Reserved.
 *
 * Please visit http://javatelnet.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * --LICENSE NOTICE--
 *
 */

package de.mud.terminal;

import android.text.AndroidCharacter;

import java.util.Properties;

/**
 * Implementation of a VT terminal emulation plus ANSI compatible.
 * <P>
 * <B>Maintainer:</B> Marcus Meißner
 *
 * @version $Id: vt320.java 507 2005-10-25 10:14:52Z marcus $
 * @author  Matthias L. Jugel, Marcus Meißner
 */
public abstract class vt320 extends VDUBuffer implements VDUInput {

  /** The current version id tag.<P>
   * $Id: vt320.java 507 2005-10-25 10:14:52Z marcus $
   *
   */
  public final static String ID = "$Id: vt320.java 507 2005-10-25 10:14:52Z marcus $";

  /** the debug level */
  private final static int debug = 0;
  private StringBuilder debugStr;
  public abstract void debug(String notice);

  /**
   * Write an answer back to the remote host. This is needed to be able to
   * send terminal answers requests like status and type information.
   * @param b the array of bytes to be sent
   */
  public abstract void write(byte[] b);

  /**
   * Write an answer back to the remote host. This is needed to be able to
   * send terminal answers requests like status and type information.
   * @param b the array of bytes to be sent
   */
  public abstract void write(int b);

  /**
   * Play the beep sound ...
   */
  public void beep() { /* do nothing by default */
  }

  /**
   * Convenience function for putString(char[], int, int)
   */
  public void putString(String s) {
    int len = s.length();
    char[] tmp = new char[len];
    s.getChars(0, len, tmp, 0);
    putString(tmp, null, 0, len);
  }

  /**
   * Put string at current cursor position. Moves cursor
   * according to the String. Does NOT wrap.
   * @param s character array
   * @param start place to start in array
   * @param len number of characters to process
   */
  public void putString(char[] s, byte[] fullwidths, int start, int len) {
    if (len > 0) {
      //markLine(R, 1);
      int lastChar = -1;
      char c;
      boolean isWide = false;

      for (int i = 0; i < len; i++) {
        c = s[start + i];
        // Shortcut for my favorite ASCII
        if (c <= 0x7F) {
          if (lastChar != -1)
            putChar((char) lastChar, isWide, false);
          lastChar = c;
          isWide = false;
        } else if (!Character.isLowSurrogate(c) && !Character.isHighSurrogate(c)) {
          if (Character.getType(c) == Character.NON_SPACING_MARK) {
            if (lastChar != -1) {
              char nc = Precomposer.precompose((char) lastChar, c);
              putChar(nc, isWide, false);
              lastChar = -1;
            }
          } else {
            if (lastChar != -1)
              putChar((char) lastChar, isWide, false);
            lastChar = c;
            if (fullwidths != null) {
                final byte width = fullwidths[i];
                isWide = (width == AndroidCharacter.EAST_ASIAN_WIDTH_WIDE)
                    || (width == AndroidCharacter.EAST_ASIAN_WIDTH_FULL_WIDTH);
            }
          }
        }
      }

      if (lastChar != -1)
        putChar((char) lastChar, isWide, false);

      setCursorPosition(C, R);
      redraw();
    }
  }

  protected void sendTelnetCommand(byte cmd) {

  }

  /**
   * Sent the changed window size from the terminal to all listeners.
   */
  protected void setWindowSize(int c, int r) {
    /* To be overridden by Terminal.java */
  }

  @Override
public void setScreenSize(int c, int r, boolean broadcast) {
    int oldrows = height;

    if (debug>2) {
      if (debugStr == null)
        debugStr = new StringBuilder();

      debugStr.append("setscreensize (")
        .append(c)
        .append(',')
        .append(r)
        .append(',')
        .append(broadcast)
        .append(')');
      debug(debugStr.toString());
      debugStr.setLength(0);
    }

    super.setScreenSize(c,r,false);

    boolean cursorChanged = false;

    // Don't let the cursor go off the screen.
    if (C >= c) {
      C = c - 1;
      cursorChanged = true;
    }

    if (R >= r) {
      R = r - 1;
      cursorChanged = true;
    }

    if (cursorChanged) {
      setCursorPosition(C, R);
      redraw();
    }

    if (broadcast) {
      setWindowSize(c, r); /* broadcast up */
    }
  }


  /**
   * Create a new vt320 terminal and intialize it with useful settings.
   */
  public vt320(int width, int height) {
    super(width, height);

    debugStr = new StringBuilder();

    setVMS(false);
    setIBMCharset(false);
    setTerminalID("vt320");
    setBufferSize(100);
    //setBorder(2, false);

    gx = new char[4];
    reset();

    /* top row of numpad */
    PF1 = "\u001bOP";
    PF2 = "\u001bOQ";
    PF3 = "\u001bOR";
    PF4 = "\u001bOS";

    /* the 3x2 keyblock on PC keyboards */
    Insert = new String[4];
    Remove = new String[4];
    KeyHome = new String[4];
    KeyEnd = new String[4];
    NextScn = new String[4];
    PrevScn = new String[4];
    Escape = new String[4];
    BackSpace = new String[4];
    TabKey = new String[4];
    Insert[0] = Insert[1] = Insert[2] = Insert[3] = "\u001b[2~";
    Remove[0] = Remove[1] = Remove[2] = Remove[3] = "\u001b[3~";
    PrevScn[0] = PrevScn[1] = PrevScn[2] = PrevScn[3] = "\u001b[5~";
    NextScn[0] = NextScn[1] = NextScn[2] = NextScn[3] = "\u001b[6~";
    KeyHome[0] = KeyHome[1] = KeyHome[2] = KeyHome[3] = "\u001b[H";
    KeyEnd[0] = KeyEnd[1] = KeyEnd[2] = KeyEnd[3] = "\u001b[F";
    Escape[0] = Escape[1] = Escape[2] = Escape[3] = "\u001b";
    if (vms) {
      BackSpace[1] = "" + (char) 10;	//  VMS shift deletes word back
      BackSpace[2] = "\u0018";	//  VMS control deletes line back
      BackSpace[0] = BackSpace[3] = "\u007f";	//  VMS other is delete
    } else {
      //BackSpace[0] = BackSpace[1] = BackSpace[2] = BackSpace[3] = "\b";
      // ConnectBot modifications.
      BackSpace[0] = "\b";
      BackSpace[1] = "\u007f";
      BackSpace[2] = "\u001b[3~";
      BackSpace[3] = "\u001b[2~";
    }

    /* some more VT100 keys */
    Find = "\u001b[1~";
    Select = "\u001b[4~";
    Help = "\u001b[28~";
    Do = "\u001b[29~";

    FunctionKey = new String[21];
    FunctionKey[0] = "";
    FunctionKey[1] = PF1;
    FunctionKey[2] = PF2;
    FunctionKey[3] = PF3;
    FunctionKey[4] = PF4;
    /* following are defined differently for vt220 / vt132 ... */
    FunctionKey[5] = "\u001b[15~";
    FunctionKey[6] = "\u001b[17~";
    FunctionKey[7] = "\u001b[18~";
    FunctionKey[8] = "\u001b[19~";
    FunctionKey[9] = "\u001b[20~";
    FunctionKey[10] = "\u001b[21~";
    FunctionKey[11] = "\u001b[23~";
    FunctionKey[12] = "\u001b[24~";
    FunctionKey[13] = "\u001b[25~";
    FunctionKey[14] = "\u001b[26~";
    FunctionKey[15] = Help;
    FunctionKey[16] = Do;
    FunctionKey[17] = "\u001b[31~";
    FunctionKey[18] = "\u001b[32~";
    FunctionKey[19] = "\u001b[33~";
    FunctionKey[20] = "\u001b[34~";

    FunctionKeyShift = new String[21];
    FunctionKeyAlt = new String[21];
    FunctionKeyCtrl = new String[21];

    for (int i = 0; i < 20; i++) {
      FunctionKeyShift[i] = "";
      FunctionKeyAlt[i] = "";
      FunctionKeyCtrl[i] = "";
    }
    FunctionKeyShift[15] = Find;
    FunctionKeyShift[16] = Select;


    TabKey[0] = "\u0009";
    TabKey[1] = "\u001bOP\u0009";
    TabKey[2] = TabKey[3] = "";

    KeyUp = new String[4];
    KeyUp[0] = "\u001b[A";
    KeyDown = new String[4];
    KeyDown[0] = "\u001b[B";
    KeyRight = new String[4];
    KeyRight[0] = "\u001b[C";
    KeyLeft = new String[4];
    KeyLeft[0] = "\u001b[D";
    Numpad = new String[10];
    Numpad[0] = "\u001bOp";
    Numpad[1] = "\u001bOq";
    Numpad[2] = "\u001bOr";
    Numpad[3] = "\u001bOs";
    Numpad[4] = "\u001bOt";
    Numpad[5] = "\u001bOu";
    Numpad[6] = "\u001bOv";
    Numpad[7] = "\u001bOw";
    Numpad[8] = "\u001bOx";
    Numpad[9] = "\u001bOy";
    KPMinus = PF4;
    KPComma = "\u001bOl";
    KPPeriod = "\u001bOn";
    KPEnter = "\u001bOM";

    NUMPlus = new String[4];
    NUMPlus[0] = "+";
    NUMDot = new String[4];
    NUMDot[0] = ".";
  }

  public void setBackspace(int type) {
    switch (type) {
    case DELETE_IS_DEL:
      BackSpace[0] = "\u007f";
      BackSpace[1] = "\b";
      break;
    case DELETE_IS_BACKSPACE:
      BackSpace[0] = "\b";
      BackSpace[1] = "\u007f";
      break;
    }
  }

  /**
   * Create a default vt320 terminal with 80 columns and 24 lines.
   */
  public vt320() {
    this(80, 24);
  }

  /**
   * Terminal is mouse-aware and requires (x,y) coordinates of
   * on the terminal (character coordinates) and the button clicked.
   * @param x
   * @param y
   * @param modifiers
   */
  public void mousePressed(int x, int y, int modifiers) {
    if (mouserpt == 0)
      return;

    int mods = modifiers;
    mousebut = 3;
    if ((mods & 16) == 16) mousebut = 0;
    if ((mods & 8) == 8) mousebut = 1;
    if ((mods & 4) == 4) mousebut = 2;

    int mousecode;
    if (mouserpt == 9)	/* X10 Mouse */
      mousecode = 0x20 | mousebut;
    else			/* normal xterm mouse reporting */
      mousecode = mousebut | 0x20 | ((mods & 7) << 2);

    byte b[] = new byte[6];

    b[0] = 27;
    b[1] = (byte) '[';
    b[2] = (byte) 'M';
    b[3] = (byte) mousecode;
    b[4] = (byte) (0x20 + x + 1);
    b[5] = (byte) (0x20 + y + 1);

    write(b); // FIXME: writeSpecial here
  }

  /**
   * Terminal is mouse-aware and requires the coordinates and button
   * of the release.
   * @param x
   * @param y
   * @param modifiers
   */
  public void mouseReleased(int x, int y, int modifiers) {
    if (mouserpt == 0)
      return;

    /* problem is tht modifiers still have the released button set in them.
    int mods = modifiers;
    mousebut = 3;
    if ((mods & 16)==16) mousebut=0;
    if ((mods &  8)==8 ) mousebut=1;
    if ((mods &  4)==4 ) mousebut=2;
    */

    int mousecode;
    if (mouserpt == 9)
      mousecode = 0x20 + mousebut;	/* same as press? appears so. */
    else
      mousecode = '#';

    byte b[] = new byte[6];
    b[0] = 27;
    b[1] = (byte) '[';
    b[2] = (byte) 'M';
    b[3] = (byte) mousecode;
    b[4] = (byte) (0x20 + x + 1);
    b[5] = (byte) (0x20 + y + 1);
    write(b); // FIXME: writeSpecial here
    mousebut = 0;
  }


  /** we should do localecho (passed from other modules). false is default */
  private boolean localecho = false;

  /**
   * Enable or disable the local echo property of the terminal.
   * @param echo true if the terminal should echo locally
   */
  public void setLocalEcho(boolean echo) {
    localecho = echo;
  }

  /**
   * Enable the VMS mode of the terminal to handle some things differently
   * for VMS hosts.
   * @param vms true for vms mode, false for normal mode
   */
  public void setVMS(boolean vms) {
    this.vms = vms;
  }

  /**
   * Enable the usage of the IBM character set used by some BBS's. Special
   * graphical character are available in this mode.
   * @param ibm true to use the ibm character set
   */
  public void setIBMCharset(boolean ibm) {
    useibmcharset = ibm;
  }

  /**
   * Override the standard key codes used by the terminal emulation.
   * @param codes a properties object containing key code definitions
   */
  public void setKeyCodes(Properties codes) {
    String res, prefixes[] = {"", "S", "C", "A"};
    int i;

    for (i = 0; i < 10; i++) {
      res = codes.getProperty("NUMPAD" + i);
      if (res != null) Numpad[i] = unEscape(res);
    }
    for (i = 1; i < 20; i++) {
      res = codes.getProperty("F" + i);
      if (res != null) FunctionKey[i] = unEscape(res);
      res = codes.getProperty("SF" + i);
      if (res != null) FunctionKeyShift[i] = unEscape(res);
      res = codes.getProperty("CF" + i);
      if (res != null) FunctionKeyCtrl[i] = unEscape(res);
      res = codes.getProperty("AF" + i);
      if (res != null) FunctionKeyAlt[i] = unEscape(res);
    }
    for (i = 0; i < 4; i++) {
      res = codes.getProperty(prefixes[i] + "PGUP");
      if (res != null) PrevScn[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "PGDOWN");
      if (res != null) NextScn[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "END");
      if (res != null) KeyEnd[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "HOME");
      if (res != null) KeyHome[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "INSERT");
      if (res != null) Insert[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "REMOVE");
      if (res != null) Remove[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "UP");
      if (res != null) KeyUp[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "DOWN");
      if (res != null) KeyDown[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "LEFT");
      if (res != null) KeyLeft[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "RIGHT");
      if (res != null) KeyRight[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "ESCAPE");
      if (res != null) Escape[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "BACKSPACE");
      if (res != null) BackSpace[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "TAB");
      if (res != null) TabKey[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "NUMPLUS");
      if (res != null) NUMPlus[i] = unEscape(res);
      res = codes.getProperty(prefixes[i] + "NUMDECIMAL");
      if (res != null) NUMDot[i] = unEscape(res);
    }
  }

  /**
   * Set the terminal id used to identify this terminal.
   * @param terminalID the id string
   */
  public void setTerminalID(String terminalID) {
    this.terminalID = terminalID;

    if (terminalID.equals("scoansi")) {
      FunctionKey[1] = "\u001b[M";  FunctionKey[2] = "\u001b[N";
      FunctionKey[3] = "\u001b[O";  FunctionKey[4] = "\u001b[P";
      FunctionKey[5] = "\u001b[Q";  FunctionKey[6] = "\u001b[R";
      FunctionKey[7] = "\u001b[S";  FunctionKey[8] = "\u001b[T";
      FunctionKey[9] = "\u001b[U";  FunctionKey[10] = "\u001b[V";
      FunctionKey[11] = "\u001b[W"; FunctionKey[12] = "\u001b[X";
      FunctionKey[13] = "\u001b[Y"; FunctionKey[14] = "?";
      FunctionKey[15] = "\u001b[a"; FunctionKey[16] = "\u001b[b";
      FunctionKey[17] = "\u001b[c"; FunctionKey[18] = "\u001b[d";
      FunctionKey[19] = "\u001b[e"; FunctionKey[20] = "\u001b[f";
      PrevScn[0] = PrevScn[1] = PrevScn[2] = PrevScn[3] = "\u001b[I";
      NextScn[0] = NextScn[1] = NextScn[2] = NextScn[3] = "\u001b[G";
      // more theoretically.
    }
  }

  public void setAnswerBack(String ab) {
    this.answerBack = unEscape(ab);
  }

  /**
   * Get the terminal id used to identify this terminal.
   */
  public String getTerminalID() {
    return terminalID;
  }

  /**
   * A small conveniance method thar converts the string to a byte array
   * for sending.
   * @param s the string to be sent
   */
  private boolean write(String s, boolean doecho) {
    if (debug > 2) {
      debugStr.append("write(|")
        .append(s)
        .append("|,")
        .append(doecho);
      debug(debugStr.toString());
      debugStr.setLength(0);
    }
    if (s == null) // aka the empty string.
      return true;
    /* NOTE: getBytes() honours some locale, it *CONVERTS* the string.
     * However, we output only 7bit stuff towards the target, and *some*
     * 8 bit control codes. We must not mess up the latter, so we do hand
     * by hand copy.
     */

    byte arr[] = new byte[s.length()];
    for (int i = 0; i < s.length(); i++) {
      arr[i] = (byte) s.charAt(i);
    }
    write(arr);

    if (doecho)
      putString(s);
    return true;
  }

  private boolean write(int s, boolean doecho) {
    if (debug > 2) {
      debugStr.append("write(|")
        .append(s)
        .append("|,")
        .append(doecho);
      debug(debugStr.toString());
      debugStr.setLength(0);
    }

    write(s);

    // TODO check if character is wide
    if (doecho)
      putChar((char)s, false, false);
    return true;
  }

  private boolean write(String s) {
    return write(s, localecho);
  }

  // ===================================================================
  // the actual terminal emulation code comes here:
  // ===================================================================

  private String terminalID = "vt320";
  private String answerBack = "Use Terminal.answerback to set ...\n";

  // X - COLUMNS, Y - ROWS
  int R,C;
  int attributes = 0;

  int Sc,Sr,Sa,Stm,Sbm;
  char Sgr,Sgl;
  char Sgx[];

  int insertmode = 0;
  int statusmode = 0;
  boolean vt52mode = false;
  boolean keypadmode = false; /* false - numeric, true - application */
  boolean output8bit = false;
  int normalcursor = 0;
  boolean moveoutsidemargins = true;
  boolean wraparound = true;
  boolean sendcrlf = true;
  boolean capslock = false;
  boolean numlock = false;
  int mouserpt = 0;
  byte mousebut = 0;

  boolean useibmcharset = false;

  int lastwaslf = 0;
  boolean usedcharsets = false;

  private final static char ESC = 27;
  private final static char IND = 132;
  private final static char NEL = 133;
  private final static char RI = 141;
  private final static char SS2 = 142;
  private final static char SS3 = 143;
  private final static char DCS = 144;
  private final static char HTS = 136;
  private final static char CSI = 155;
  private final static char OSC = 157;
  private final static int TSTATE_DATA = 0;
  private final static int TSTATE_ESC = 1; /* ESC */
  private final static int TSTATE_CSI = 2; /* ESC [ */
  private final static int TSTATE_DCS = 3; /* ESC P */
  private final static int TSTATE_DCEQ = 4; /* ESC [? */
  private final static int TSTATE_ESCSQUARE = 5; /* ESC # */
  private final static int TSTATE_OSC = 6;       /* ESC ] */
  private final static int TSTATE_SETG0 = 7;     /* ESC (? */
  private final static int TSTATE_SETG1 = 8;     /* ESC )? */
  private final static int TSTATE_SETG2 = 9;     /* ESC *? */
  private final static int TSTATE_SETG3 = 10;    /* ESC +? */
  private final static int TSTATE_CSI_DOLLAR = 11; /* ESC [ Pn $ */
  private final static int TSTATE_CSI_EX = 12; /* ESC [ ! */
  private final static int TSTATE_ESCSPACE = 13; /* ESC <space> */
  private final static int TSTATE_VT52X = 14;
  private final static int TSTATE_VT52Y = 15;
  private final static int TSTATE_CSI_TICKS = 16;
  private final static int TSTATE_CSI_EQUAL = 17; /* ESC [ = */
  private final static int TSTATE_TITLE = 18; /* xterm title */

  /* Keys we support */
  public final static int KEY_PAUSE = 1;
  public final static int KEY_F1 = 2;
  public final static int KEY_F2 = 3;
  public final static int KEY_F3 = 4;
  public final static int KEY_F4 = 5;
  public final static int KEY_F5 = 6;
  public final static int KEY_F6 = 7;
  public final static int KEY_F7 = 8;
  public final static int KEY_F8 = 9;
  public final static int KEY_F9 = 10;
  public final static int KEY_F10 = 11;
  public final static int KEY_F11 = 12;
  public final static int KEY_F12 = 13;
  public final static int KEY_UP = 14;
  public final static int KEY_DOWN =15 ;
  public final static int KEY_LEFT = 16;
  public final static int KEY_RIGHT = 17;
  public final static int KEY_PAGE_DOWN = 18;
  public final static int KEY_PAGE_UP = 19;
  public final static int KEY_INSERT = 20;
  public final static int KEY_DELETE = 21;
  public final static int KEY_BACK_SPACE = 22;
  public final static int KEY_HOME = 23;
  public final static int KEY_END = 24;
  public final static int KEY_NUM_LOCK = 25;
  public final static int KEY_CAPS_LOCK = 26;
  public final static int KEY_SHIFT = 27;
  public final static int KEY_CONTROL = 28;
  public final static int KEY_ALT = 29;
  public final static int KEY_ENTER = 30;
  public final static int KEY_NUMPAD0 = 31;
  public final static int KEY_NUMPAD1 = 32;
  public final static int KEY_NUMPAD2 = 33;
  public final static int KEY_NUMPAD3 = 34;
  public final static int KEY_NUMPAD4 = 35;
  public final static int KEY_NUMPAD5 = 36;
  public final static int KEY_NUMPAD6 = 37;
  public final static int KEY_NUMPAD7 = 38;
  public final static int KEY_NUMPAD8 = 39;
  public final static int KEY_NUMPAD9 = 40;
  public final static int KEY_DECIMAL = 41;
  public final static int KEY_ADD = 42;
  public final static int KEY_ESCAPE = 43;

  public final static int DELETE_IS_DEL = 0;
  public final static int DELETE_IS_BACKSPACE = 1;

  /* The graphics charsets
   * B - default ASCII
   * A - ISO Latin 1
   * 0 - DEC SPECIAL
   * < - User defined
   * ....
   */
  char gx[];
  char gl;		// GL (left charset)
  char gr;		// GR (right charset)
  int onegl;	// single shift override for GL.

  // Map from scoansi linedrawing to DEC _and_ unicode (for the stuff which
  // is not in linedrawing). Got from experimenting with scoadmin.
  private final static String scoansi_acs = "Tm7k3x4u?kZl@mYjEnB\u2566DqCtAvM\u2550:\u2551N\u2557I\u2554;\u2557H\u255a0a<\u255d";
  // array to store DEC Special -> Unicode mapping
  //  Unicode   DEC  Unicode name    (DEC name)
  private static char DECSPECIAL[] = {
    '\u0040', //5f blank
    '\u2666', //60 black diamond
    '\u2592', //61 grey square
    '\u2409', //62 Horizontal tab  (ht) pict. for control
    '\u240c', //63 Form Feed       (ff) pict. for control
    '\u240d', //64 Carriage Return (cr) pict. for control
    '\u240a', //65 Line Feed       (lf) pict. for control
    '\u00ba', //66 Masculine ordinal indicator
    '\u00b1', //67 Plus or minus sign
    '\u2424', //68 New Line        (nl) pict. for control
    '\u240b', //69 Vertical Tab    (vt) pict. for control
    '\u2518', //6a Forms light up   and left
    '\u2510', //6b Forms light down and left
    '\u250c', //6c Forms light down and right
    '\u2514', //6d Forms light up   and right
    '\u253c', //6e Forms light vertical and horizontal
    '\u2594', //6f Upper 1/8 block                        (Scan 1)
    '\u2580', //70 Upper 1/2 block                        (Scan 3)
    '\u2500', //71 Forms light horizontal or ?em dash?    (Scan 5)
    '\u25ac', //72 \u25ac black rect. or \u2582 lower 1/4 (Scan 7)
    '\u005f', //73 \u005f underscore  or \u2581 lower 1/8 (Scan 9)
    '\u251c', //74 Forms light vertical and right
    '\u2524', //75 Forms light vertical and left
    '\u2534', //76 Forms light up   and horizontal
    '\u252c', //77 Forms light down and horizontal
    '\u2502', //78 vertical bar
    '\u2264', //79 less than or equal
    '\u2265', //7a greater than or equal
    '\u00b6', //7b paragraph
    '\u2260', //7c not equal
    '\u00a3', //7d Pound Sign (british)
    '\u00b7'  //7e Middle Dot
  };

  /** Strings to send on function key pressing */
  private String Numpad[];
  private String FunctionKey[];
  private String FunctionKeyShift[];
  private String FunctionKeyCtrl[];
  private String FunctionKeyAlt[];
  private String TabKey[];
  private String KeyUp[],KeyDown[],KeyLeft[],KeyRight[];
  private String KPMinus, KPComma, KPPeriod, KPEnter;
  private String PF1, PF2, PF3, PF4;
  private String Help, Do, Find, Select;

  private String KeyHome[], KeyEnd[], Insert[], Remove[], PrevScn[], NextScn[];
  private String Escape[], BackSpace[], NUMDot[], NUMPlus[];

  private String osc,dcs;  /* to memorize OSC & DCS control sequence */

  /** vt320 state variable (internal) */
  private int term_state = TSTATE_DATA;
  /** in vms mode, set by Terminal.VMS property */
  private boolean vms = false;
  /** Tabulators */
  private byte[] Tabs;
  /** The list of integers as used by CSI */
  private int[] DCEvars = new int[30];
  private int DCEvar;

  /**
   * Replace escape code characters (backslash + identifier) with their
   * respective codes.
   * @param tmp the string to be parsed
   * @return a unescaped string
   */
  static String unEscape(String tmp) {
    int idx = 0, oldidx = 0;
    String cmd;
    // f.println("unescape("+tmp+")");
    cmd = "";
    while ((idx = tmp.indexOf('\\', oldidx)) >= 0 &&
            ++idx <= tmp.length()) {
      cmd += tmp.substring(oldidx, idx - 1);
      if (idx == tmp.length()) return cmd;
      switch (tmp.charAt(idx)) {
        case 'b':
          cmd += "\b";
          break;
        case 'e':
          cmd += "\u001b";
          break;
        case 'n':
          cmd += "\n";
          break;
        case 'r':
          cmd += "\r";
          break;
        case 't':
          cmd += "\t";
          break;
        case 'v':
          cmd += "\u000b";
          break;
        case 'a':
          cmd += "\u0012";
          break;
        default :
          if ((tmp.charAt(idx) >= '0') && (tmp.charAt(idx) <= '9')) {
            int i;
            for (i = idx; i < tmp.length(); i++)
              if ((tmp.charAt(i) < '0') || (tmp.charAt(i) > '9'))
                break;
            cmd += (char) Integer.parseInt(tmp.substring(idx, i));
            idx = i - 1;
          } else
            cmd += tmp.substring(idx, ++idx);
          break;
      }
      oldidx = ++idx;
    }
    if (oldidx <= tmp.length()) cmd += tmp.substring(oldidx);
    return cmd;
  }

  /**
   * A small conveniance method thar converts a 7bit string to the 8bit
   * version depending on VT52/Output8Bit mode.
   *
   * @param s the string to be sent
   */
  private boolean writeSpecial(String s) {
    if (s == null)
      return true;
    if (((s.length() >= 3) && (s.charAt(0) == 27) && (s.charAt(1) == 'O'))) {
      if (vt52mode) {
        if ((s.charAt(2) >= 'P') && (s.charAt(2) <= 'S')) {
          s = "\u001b" + s.substring(2); /* ESC x */
        } else {
          s = "\u001b?" + s.substring(2); /* ESC ? x */
        }
      } else {
        if (output8bit) {
          s = "\u008f" + s.substring(2);  /* SS3 x */
        } /* else keep string as it is */
      }
    }
    if (((s.length() >= 3) && (s.charAt(0) == 27) && (s.charAt(1) == '['))) {
      if (output8bit) {
        s = "\u009b" + s.substring(2); /* CSI ... */
      } /* else keep */
    }
    return write(s, false);
  }

  /**
   * main keytyping event handler...
   */
  public void keyPressed(int keyCode, char keyChar, int modifiers) {
    boolean control = (modifiers & VDUInput.KEY_CONTROL) != 0;
    boolean shift = (modifiers & VDUInput.KEY_SHIFT) != 0;
    boolean alt = (modifiers & VDUInput.KEY_ALT) != 0;

    if (debug > 1) {
      debugStr.append("keyPressed(")
        .append(keyCode)
        .append(", ")
        .append((int)keyChar)
        .append(", ")
        .append(modifiers)
        .append(')');
      debug(debugStr.toString());
      debugStr.setLength(0);
    }

    int xind;
    String fmap[];
    xind = 0;
    fmap = FunctionKey;
    if (shift) {
      fmap = FunctionKeyShift;
      xind = 1;
    }
    if (control) {
      fmap = FunctionKeyCtrl;
      xind = 2;
    }
    if (alt) {
      fmap = FunctionKeyAlt;
      xind = 3;
    }

    switch (keyCode) {
      case KEY_PAUSE:
        if (shift || control)
          sendTelnetCommand((byte) 243); // BREAK
        break;
      case KEY_F1:
        writeSpecial(fmap[1]);
        break;
      case KEY_F2:
        writeSpecial(fmap[2]);
        break;
      case KEY_F3:
        writeSpecial(fmap[3]);
        break;
      case KEY_F4:
        writeSpecial(fmap[4]);
        break;
      case KEY_F5:
        writeSpecial(fmap[5]);
        break;
      case KEY_F6:
        writeSpecial(fmap[6]);
        break;
      case KEY_F7:
        writeSpecial(fmap[7]);
        break;
      case KEY_F8:
        writeSpecial(fmap[8]);
        break;
      case KEY_F9:
        writeSpecial(fmap[9]);
        break;
      case KEY_F10:
        writeSpecial(fmap[10]);
        break;
      case KEY_F11:
        writeSpecial(fmap[11]);
        break;
      case KEY_F12:
        writeSpecial(fmap[12]);
        break;
      case KEY_UP:
        writeSpecial(KeyUp[xind]);
        break;
      case KEY_DOWN:
        writeSpecial(KeyDown[xind]);
        break;
      case KEY_LEFT:
        writeSpecial(KeyLeft[xind]);
        break;
      case KEY_RIGHT:
        writeSpecial(KeyRight[xind]);
        break;
      case KEY_PAGE_DOWN:
        writeSpecial(NextScn[xind]);
        break;
      case KEY_PAGE_UP:
        writeSpecial(PrevScn[xind]);
        break;
      case KEY_INSERT:
        writeSpecial(Insert[xind]);
        break;
      case KEY_DELETE:
        writeSpecial(Remove[xind]);
        break;
      case KEY_BACK_SPACE:
        writeSpecial(BackSpace[xind]);
	if (localecho) {
	  if (BackSpace[xind] == "\b") {
	    putString("\b \b"); // make the last char 'deleted'
	  } else {
	    putString(BackSpace[xind]); // echo it
	  }
	}
        break;
      case KEY_HOME:
        writeSpecial(KeyHome[xind]);
        break;
      case KEY_END:
        writeSpecial(KeyEnd[xind]);
        break;
      case KEY_NUM_LOCK:
        if (vms && control) {
          writeSpecial(PF1);
        }
        if (!control)
          numlock = !numlock;
        break;
      case KEY_CAPS_LOCK:
        capslock = !capslock;
        return;
      case KEY_SHIFT:
      case KEY_CONTROL:
      case KEY_ALT:
        return;
      default:
        break;
    }
  }
/*
  public void keyReleased(KeyEvent evt) {
    if (debug > 1) debug("keyReleased("+evt+")");
    // ignore
  }
*/
  /**
   * Handle key Typed events for the terminal, this will get
   * all normal key types, but no shift/alt/control/numlock.
   */
  public void keyTyped(int keyCode, char keyChar, int modifiers) {
    boolean control = (modifiers & VDUInput.KEY_CONTROL) != 0;
    boolean shift = (modifiers & VDUInput.KEY_SHIFT) != 0;
    boolean alt = (modifiers & VDUInput.KEY_ALT) != 0;

    if (debug > 1) debug("keyTyped("+keyCode+", "+(int)keyChar+", "+modifiers+")");

    if (keyChar == '\t') {
      if (shift) {
        write(TabKey[1], false);
      } else {
        if (control) {
          write(TabKey[2], false);
        } else {
          if (alt) {
            write(TabKey[3], false);
          } else {
            write(TabKey[0], false);
          }
        }
      }
      return;
    }
    if (alt) {
      write(((char) (keyChar | 0x80)));
      return;
    }

    if (((keyCode == KEY_ENTER) || (keyChar == 10))
            && !control) {
      write('\r');
      if (localecho) putString("\r\n"); // bad hack
      return;
    }

    if ((keyCode == 10) && !control) {
      debug("Sending \\r");
      write('\r');
      return;
    }

    // FIXME: on german PC keyboards you have to use Alt-Ctrl-q to get an @,
    // so we can't just use it here... will probably break some other VMS
    // codes.  -Marcus
    // if(((!vms && keyChar == '2') || keyChar == '@' || keyChar == ' ')
    //    && control)
    if (((!vms && keyChar == '2') || keyChar == ' ') && control)
      write(0);

    if (vms) {
      if (keyChar == 127 && !control) {
        if (shift)
          writeSpecial(Insert[0]);        //  VMS shift delete = insert
        else
          writeSpecial(Remove[0]);        //  VMS delete = remove
        return;
      } else if (control)
        switch (keyChar) {
          case '0':
            writeSpecial(Numpad[0]);
            return;
          case '1':
            writeSpecial(Numpad[1]);
            return;
          case '2':
            writeSpecial(Numpad[2]);
            return;
          case '3':
            writeSpecial(Numpad[3]);
            return;
          case '4':
            writeSpecial(Numpad[4]);
            return;
          case '5':
            writeSpecial(Numpad[5]);
            return;
          case '6':
            writeSpecial(Numpad[6]);
            return;
          case '7':
            writeSpecial(Numpad[7]);
            return;
          case '8':
            writeSpecial(Numpad[8]);
            return;
          case '9':
            writeSpecial(Numpad[9]);
            return;
          case '.':
            writeSpecial(KPPeriod);
            return;
          case '-':
          case 31:
            writeSpecial(KPMinus);
            return;
          case '+':
            writeSpecial(KPComma);
            return;
          case 10:
            writeSpecial(KPEnter);
            return;
          case '/':
            writeSpecial(PF2);
            return;
          case '*':
            writeSpecial(PF3);
            return;
            /* NUMLOCK handled in keyPressed */
          default:
            break;
        }
      /* Now what does this do and how did it get here. -Marcus
      if (shift && keyChar < 32) {
        write(PF1+(char)(keyChar + 64));
        return;
      }
      */
    }

    // FIXME: not used?
    //String fmap[];
    int xind;
    xind = 0;
    //fmap = FunctionKey;
    if (shift) {
      //fmap = FunctionKeyShift;
      xind = 1;
    }
    if (control) {
      //fmap = FunctionKeyCtrl;
      xind = 2;
    }
    if (alt) {
      //fmap = FunctionKeyAlt;
      xind = 3;
    }

    if (keyCode == KEY_ESCAPE) {
      writeSpecial(Escape[xind]);
      return;
    }

    if ((modifiers & VDUInput.KEY_ACTION) != 0)
      switch (keyCode) {
        case KEY_NUMPAD0:
          writeSpecial(Numpad[0]);
          return;
        case KEY_NUMPAD1:
          writeSpecial(Numpad[1]);
          return;
        case KEY_NUMPAD2:
          writeSpecial(Numpad[2]);
          return;
        case KEY_NUMPAD3:
          writeSpecial(Numpad[3]);
          return;
        case KEY_NUMPAD4:
          writeSpecial(Numpad[4]);
          return;
        case KEY_NUMPAD5:
          writeSpecial(Numpad[5]);
          return;
        case KEY_NUMPAD6:
          writeSpecial(Numpad[6]);
          return;
        case KEY_NUMPAD7:
          writeSpecial(Numpad[7]);
          return;
        case KEY_NUMPAD8:
          writeSpecial(Numpad[8]);
          return;
        case KEY_NUMPAD9:
          writeSpecial(Numpad[9]);
          return;
        case KEY_DECIMAL:
          writeSpecial(NUMDot[xind]);
          return;
        case KEY_ADD:
          writeSpecial(NUMPlus[xind]);
          return;
      }

    if (!((keyChar == 8) || (keyChar == 127) || (keyChar == '\r') || (keyChar == '\n'))) {
      write(keyChar);
      return;
    }
  }

  private void handle_dcs(String dcs) {
    debugStr.append("DCS: ")
      .append(dcs);
    debug(debugStr.toString());
    debugStr.setLength(0);
  }

  private void handle_osc(String osc) {
	  if (osc.length() > 2 && osc.substring(0, 2).equals("4;")) {
			// Define color palette
			String[] colorData = osc.split(";");

			try {
				int colorIndex = Integer.parseInt(colorData[1]);

				if ("rgb:".equals(colorData[2].substring(0, 4))) {
					String[] rgb = colorData[2].substring(4).split("/");

					int red = Integer.parseInt(rgb[0].substring(0, 2), 16) & 0xFF;
					int green = Integer.parseInt(rgb[1].substring(0, 2), 16) & 0xFF;
					int blue = Integer.parseInt(rgb[2].substring(0, 2), 16) & 0xFF;
					display.setColor(colorIndex, red, green, blue);
				}
			} catch (Exception e) {
				debugStr.append("OSC: invalid color sequence encountered: ")
				  .append(osc);
				debug(debugStr.toString());
				debugStr.setLength(0);
			}
		} else
			debug("OSC: " + osc);
  }

  private final static char unimap[] = {
    //#
    //#    Name:     cp437_DOSLatinUS to Unicode table
    //#    Unicode version: 1.1
    //#    Table version: 1.1
    //#    Table format:  Format A
    //#    Date:          03/31/95
    //#    Authors:       Michel Suignard <michelsu@microsoft.com>
    //#                   Lori Hoerth <lorih@microsoft.com>
    //#    General notes: none
    //#
    //#    Format: Three tab-separated columns
    //#        Column #1 is the cp1255_WinHebrew code (in hex)
    //#        Column #2 is the Unicode (in hex as 0xXXXX)
    //#        Column #3 is the Unicode name (follows a comment sign, '#')
    //#
    //#    The entries are in cp437_DOSLatinUS order
    //#

    0x0000, // #NULL
    0x0001, // #START OF HEADING
    0x0002, // #START OF TEXT
    0x0003, // #END OF TEXT
    0x0004, // #END OF TRANSMISSION
    0x0005, // #ENQUIRY
    0x0006, // #ACKNOWLEDGE
    0x0007, // #BELL
    0x0008, // #BACKSPACE
    0x0009, // #HORIZONTAL TABULATION
    0x000a, // #LINE FEED
    0x000b, // #VERTICAL TABULATION
    0x000c, // #FORM FEED
    0x000d, // #CARRIAGE RETURN
    0x000e, // #SHIFT OUT
    0x000f, // #SHIFT IN
    0x0010, // #DATA LINK ESCAPE
    0x0011, // #DEVICE CONTROL ONE
    0x0012, // #DEVICE CONTROL TWO
    0x0013, // #DEVICE CONTROL THREE
    0x0014, // #DEVICE CONTROL FOUR
    0x0015, // #NEGATIVE ACKNOWLEDGE
    0x0016, // #SYNCHRONOUS IDLE
    0x0017, // #END OF TRANSMISSION BLOCK
    0x0018, // #CANCEL
    0x0019, // #END OF MEDIUM
    0x001a, // #SUBSTITUTE
    0x001b, // #ESCAPE
    0x001c, // #FILE SEPARATOR
    0x001d, // #GROUP SEPARATOR
    0x001e, // #RECORD SEPARATOR
    0x001f, // #UNIT SEPARATOR
    0x0020, // #SPACE
    0x0021, // #EXCLAMATION MARK
    0x0022, // #QUOTATION MARK
    0x0023, // #NUMBER SIGN
    0x0024, // #DOLLAR SIGN
    0x0025, // #PERCENT SIGN
    0x0026, // #AMPERSAND
    0x0027, // #APOSTROPHE
    0x0028, // #LEFT PARENTHESIS
    0x0029, // #RIGHT PARENTHESIS
    0x002a, // #ASTERISK
    0x002b, // #PLUS SIGN
    0x002c, // #COMMA
    0x002d, // #HYPHEN-MINUS
    0x002e, // #FULL STOP
    0x002f, // #SOLIDUS
    0x0030, // #DIGIT ZERO
    0x0031, // #DIGIT ONE
    0x0032, // #DIGIT TWO
    0x0033, // #DIGIT THREE
    0x0034, // #DIGIT FOUR
    0x0035, // #DIGIT FIVE
    0x0036, // #DIGIT SIX
    0x0037, // #DIGIT SEVEN
    0x0038, // #DIGIT EIGHT
    0x0039, // #DIGIT NINE
    0x003a, // #COLON
    0x003b, // #SEMICOLON
    0x003c, // #LESS-THAN SIGN
    0x003d, // #EQUALS SIGN
    0x003e, // #GREATER-THAN SIGN
    0x003f, // #QUESTION MARK
    0x0040, // #COMMERCIAL AT
    0x0041, // #LATIN CAPITAL LETTER A
    0x0042, // #LATIN CAPITAL LETTER B
    0x0043, // #LATIN CAPITAL LETTER C
    0x0044, // #LATIN CAPITAL LETTER D
    0x0045, // #LATIN CAPITAL LETTER E
    0x0046, // #LATIN CAPITAL LETTER F
    0x0047, // #LATIN CAPITAL LETTER G
    0x0048, // #LATIN CAPITAL LETTER H
    0x0049, // #LATIN CAPITAL LETTER I
    0x004a, // #LATIN CAPITAL LETTER J
    0x004b, // #LATIN CAPITAL LETTER K
    0x004c, // #LATIN CAPITAL LETTER L
    0x004d, // #LATIN CAPITAL LETTER M
    0x004e, // #LATIN CAPITAL LETTER N
    0x004f, // #LATIN CAPITAL LETTER O
    0x0050, // #LATIN CAPITAL LETTER P
    0x0051, // #LATIN CAPITAL LETTER Q
    0x0052, // #LATIN CAPITAL LETTER R
    0x0053, // #LATIN CAPITAL LETTER S
    0x0054, // #LATIN CAPITAL LETTER T
    0x0055, // #LATIN CAPITAL LETTER U
    0x0056, // #LATIN CAPITAL LETTER V
    0x0057, // #LATIN CAPITAL LETTER W
    0x0058, // #LATIN CAPITAL LETTER X
    0x0059, // #LATIN CAPITAL LETTER Y
    0x005a, // #LATIN CAPITAL LETTER Z
    0x005b, // #LEFT SQUARE BRACKET
    0x005c, // #REVERSE SOLIDUS
    0x005d, // #RIGHT SQUARE BRACKET
    0x005e, // #CIRCUMFLEX ACCENT
    0x005f, // #LOW LINE
    0x0060, // #GRAVE ACCENT
    0x0061, // #LATIN SMALL LETTER A
    0x0062, // #LATIN SMALL LETTER B
    0x0063, // #LATIN SMALL LETTER C
    0x0064, // #LATIN SMALL LETTER D
    0x0065, // #LATIN SMALL LETTER E
    0x0066, // #LATIN SMALL LETTER F
    0x0067, // #LATIN SMALL LETTER G
    0x0068, // #LATIN SMALL LETTER H
    0x0069, // #LATIN SMALL LETTER I
    0x006a, // #LATIN SMALL LETTER J
    0x006b, // #LATIN SMALL LETTER K
    0x006c, // #LATIN SMALL LETTER L
    0x006d, // #LATIN SMALL LETTER M
    0x006e, // #LATIN SMALL LETTER N
    0x006f, // #LATIN SMALL LETTER O
    0x0070, // #LATIN SMALL LETTER P
    0x0071, // #LATIN SMALL LETTER Q
    0x0072, // #LATIN SMALL LETTER R
    0x0073, // #LATIN SMALL LETTER S
    0x0074, // #LATIN SMALL LETTER T
    0x0075, // #LATIN SMALL LETTER U
    0x0076, // #LATIN SMALL LETTER V
    0x0077, // #LATIN SMALL LETTER W
    0x0078, // #LATIN SMALL LETTER X
    0x0079, // #LATIN SMALL LETTER Y
    0x007a, // #LATIN SMALL LETTER Z
    0x007b, // #LEFT CURLY BRACKET
    0x007c, // #VERTICAL LINE
    0x007d, // #RIGHT CURLY BRACKET
    0x007e, // #TILDE
    0x007f, // #DELETE
    0x00c7, // #LATIN CAPITAL LETTER C WITH CEDILLA
    0x00fc, // #LATIN SMALL LETTER U WITH DIAERESIS
    0x00e9, // #LATIN SMALL LETTER E WITH ACUTE
    0x00e2, // #LATIN SMALL LETTER A WITH CIRCUMFLEX
    0x00e4, // #LATIN SMALL LETTER A WITH DIAERESIS
    0x00e0, // #LATIN SMALL LETTER A WITH GRAVE
    0x00e5, // #LATIN SMALL LETTER A WITH RING ABOVE
    0x00e7, // #LATIN SMALL LETTER C WITH CEDILLA
    0x00ea, // #LATIN SMALL LETTER E WITH CIRCUMFLEX
    0x00eb, // #LATIN SMALL LETTER E WITH DIAERESIS
    0x00e8, // #LATIN SMALL LETTER E WITH GRAVE
    0x00ef, // #LATIN SMALL LETTER I WITH DIAERESIS
    0x00ee, // #LATIN SMALL LETTER I WITH CIRCUMFLEX
    0x00ec, // #LATIN SMALL LETTER I WITH GRAVE
    0x00c4, // #LATIN CAPITAL LETTER A WITH DIAERESIS
    0x00c5, // #LATIN CAPITAL LETTER A WITH RING ABOVE
    0x00c9, // #LATIN CAPITAL LETTER E WITH ACUTE
    0x00e6, // #LATIN SMALL LIGATURE AE
    0x00c6, // #LATIN CAPITAL LIGATURE AE
    0x00f4, // #LATIN SMALL LETTER O WITH CIRCUMFLEX
    0x00f6, // #LATIN SMALL LETTER O WITH DIAERESIS
    0x00f2, // #LATIN SMALL LETTER O WITH GRAVE
    0x00fb, // #LATIN SMALL LETTER U WITH CIRCUMFLEX
    0x00f9, // #LATIN SMALL LETTER U WITH GRAVE
    0x00ff, // #LATIN SMALL LETTER Y WITH DIAERESIS
    0x00d6, // #LATIN CAPITAL LETTER O WITH DIAERESIS
    0x00dc, // #LATIN CAPITAL LETTER U WITH DIAERESIS
    0x00a2, // #CENT SIGN
    0x00a3, // #POUND SIGN
    0x00a5, // #YEN SIGN
    0x20a7, // #PESETA SIGN
    0x0192, // #LATIN SMALL LETTER F WITH HOOK
    0x00e1, // #LATIN SMALL LETTER A WITH ACUTE
    0x00ed, // #LATIN SMALL LETTER I WITH ACUTE
    0x00f3, // #LATIN SMALL LETTER O WITH ACUTE
    0x00fa, // #LATIN SMALL LETTER U WITH ACUTE
    0x00f1, // #LATIN SMALL LETTER N WITH TILDE
    0x00d1, // #LATIN CAPITAL LETTER N WITH TILDE
    0x00aa, // #FEMININE ORDINAL INDICATOR
    0x00ba, // #MASCULINE ORDINAL INDICATOR
    0x00bf, // #INVERTED QUESTION MARK
    0x2310, // #REVERSED NOT SIGN
    0x00ac, // #NOT SIGN
    0x00bd, // #VULGAR FRACTION ONE HALF
    0x00bc, // #VULGAR FRACTION ONE QUARTER
    0x00a1, // #INVERTED EXCLAMATION MARK
    0x00ab, // #LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
    0x00bb, // #RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
    0x2591, // #LIGHT SHADE
    0x2592, // #MEDIUM SHADE
    0x2593, // #DARK SHADE
    0x2502, // #BOX DRAWINGS LIGHT VERTICAL
    0x2524, // #BOX DRAWINGS LIGHT VERTICAL AND LEFT
    0x2561, // #BOX DRAWINGS VERTICAL SINGLE AND LEFT DOUBLE
    0x2562, // #BOX DRAWINGS VERTICAL DOUBLE AND LEFT SINGLE
    0x2556, // #BOX DRAWINGS DOWN DOUBLE AND LEFT SINGLE
    0x2555, // #BOX DRAWINGS DOWN SINGLE AND LEFT DOUBLE
    0x2563, // #BOX DRAWINGS DOUBLE VERTICAL AND LEFT
    0x2551, // #BOX DRAWINGS DOUBLE VERTICAL
    0x2557, // #BOX DRAWINGS DOUBLE DOWN AND LEFT
    0x255d, // #BOX DRAWINGS DOUBLE UP AND LEFT
    0x255c, // #BOX DRAWINGS UP DOUBLE AND LEFT SINGLE
    0x255b, // #BOX DRAWINGS UP SINGLE AND LEFT DOUBLE
    0x2510, // #BOX DRAWINGS LIGHT DOWN AND LEFT
    0x2514, // #BOX DRAWINGS LIGHT UP AND RIGHT
    0x2534, // #BOX DRAWINGS LIGHT UP AND HORIZONTAL
    0x252c, // #BOX DRAWINGS LIGHT DOWN AND HORIZONTAL
    0x251c, // #BOX DRAWINGS LIGHT VERTICAL AND RIGHT
    0x2500, // #BOX DRAWINGS LIGHT HORIZONTAL
    0x253c, // #BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
    0x255e, // #BOX DRAWINGS VERTICAL SINGLE AND RIGHT DOUBLE
    0x255f, // #BOX DRAWINGS VERTICAL DOUBLE AND RIGHT SINGLE
    0x255a, // #BOX DRAWINGS DOUBLE UP AND RIGHT
    0x2554, // #BOX DRAWINGS DOUBLE DOWN AND RIGHT
    0x2569, // #BOX DRAWINGS DOUBLE UP AND HORIZONTAL
    0x2566, // #BOX DRAWINGS DOUBLE DOWN AND HORIZONTAL
    0x2560, // #BOX DRAWINGS DOUBLE VERTICAL AND RIGHT
    0x2550, // #BOX DRAWINGS DOUBLE HORIZONTAL
    0x256c, // #BOX DRAWINGS DOUBLE VERTICAL AND HORIZONTAL
    0x2567, // #BOX DRAWINGS UP SINGLE AND HORIZONTAL DOUBLE
    0x2568, // #BOX DRAWINGS UP DOUBLE AND HORIZONTAL SINGLE
    0x2564, // #BOX DRAWINGS DOWN SINGLE AND HORIZONTAL DOUBLE
    0x2565, // #BOX DRAWINGS DOWN DOUBLE AND HORIZONTAL SINGLE
    0x2559, // #BOX DRAWINGS UP DOUBLE AND RIGHT SINGLE
    0x2558, // #BOX DRAWINGS UP SINGLE AND RIGHT DOUBLE
    0x2552, // #BOX DRAWINGS DOWN SINGLE AND RIGHT DOUBLE
    0x2553, // #BOX DRAWINGS DOWN DOUBLE AND RIGHT SINGLE
    0x256b, // #BOX DRAWINGS VERTICAL DOUBLE AND HORIZONTAL SINGLE
    0x256a, // #BOX DRAWINGS VERTICAL SINGLE AND HORIZONTAL DOUBLE
    0x2518, // #BOX DRAWINGS LIGHT UP AND LEFT
    0x250c, // #BOX DRAWINGS LIGHT DOWN AND RIGHT
    0x2588, // #FULL BLOCK
    0x2584, // #LOWER HALF BLOCK
    0x258c, // #LEFT HALF BLOCK
    0x2590, // #RIGHT HALF BLOCK
    0x2580, // #UPPER HALF BLOCK
    0x03b1, // #GREEK SMALL LETTER ALPHA
    0x00df, // #LATIN SMALL LETTER SHARP S
    0x0393, // #GREEK CAPITAL LETTER GAMMA
    0x03c0, // #GREEK SMALL LETTER PI
    0x03a3, // #GREEK CAPITAL LETTER SIGMA
    0x03c3, // #GREEK SMALL LETTER SIGMA
    0x00b5, // #MICRO SIGN
    0x03c4, // #GREEK SMALL LETTER TAU
    0x03a6, // #GREEK CAPITAL LETTER PHI
    0x0398, // #GREEK CAPITAL LETTER THETA
    0x03a9, // #GREEK CAPITAL LETTER OMEGA
    0x03b4, // #GREEK SMALL LETTER DELTA
    0x221e, // #INFINITY
    0x03c6, // #GREEK SMALL LETTER PHI
    0x03b5, // #GREEK SMALL LETTER EPSILON
    0x2229, // #INTERSECTION
    0x2261, // #IDENTICAL TO
    0x00b1, // #PLUS-MINUS SIGN
    0x2265, // #GREATER-THAN OR EQUAL TO
    0x2264, // #LESS-THAN OR EQUAL TO
    0x2320, // #TOP HALF INTEGRAL
    0x2321, // #BOTTOM HALF INTEGRAL
    0x00f7, // #DIVISION SIGN
    0x2248, // #ALMOST EQUAL TO
    0x00b0, // #DEGREE SIGN
    0x2219, // #BULLET OPERATOR
    0x00b7, // #MIDDLE DOT
    0x221a, // #SQUARE ROOT
    0x207f, // #SUPERSCRIPT LATIN SMALL LETTER N
    0x00b2, // #SUPERSCRIPT TWO
    0x25a0, // #BLACK SQUARE
    0x00a0, // #NO-BREAK SPACE
  };

  public char map_cp850_unicode(char x) {
    if (x >= 0x100)
      return x;
    return unimap[x];
  }

  private void _SetCursor(int row, int col) {
    int maxr = height - 1;
    int tm = getTopMargin();

    R = (row < 0)?0: row;
    C = (col < 0)?0: (col >= width) ? width - 1 : col;

    if (!moveoutsidemargins) {
      R += tm;
      maxr = getBottomMargin();
    }
    if (R > maxr) R = maxr;
  }

  private void putChar(char c, boolean isWide, boolean doshowcursor) {
    int rows = this.height; //statusline
    int columns = this.width;
    // byte msg[];

//    if (debug > 4) {
//      debugStr.append("putChar(")
//        .append(c)
//        .append(" [")
//        .append((int) c)
//        .append("]) at R=")
//        .append(R)
//        .append(" , C=")
//        .append(C)
//        .append(", columns=")
//        .append(columns)
//        .append(", rows=")
//        .append(rows);
//      debug(debugStr.toString());
//      debugStr.setLength(0);
//    }
//    markLine(R, 1);
//    if (c > 255) {
//      if (debug > 0)
//        debug("char > 255:" + (int) c);
//      //return;
//    }

    switch (term_state) {
      case TSTATE_DATA:
        /* FIXME: we shouldn't use chars with bit 8 set if ibmcharset.
         * probably... but some BBS do anyway...
         */
        if (!useibmcharset) {
          boolean doneflag = true;
          switch (c) {
            case OSC:
              osc = "";
              term_state = TSTATE_OSC;
              break;
            case RI:
              if (R > getTopMargin())
                R--;
              else
                insertLine(R, 1, SCROLL_DOWN);
              if (debug > 1)
                debug("RI");
              break;
            case IND:
              if (debug > 2) {
                debugStr.append("IND at ")
                  .append(R)
                  .append(", tm is ")
                  .append(getTopMargin())
                  .append(", bm is ")
                  .append(getBottomMargin());
                debug(debugStr.toString());
                debugStr.setLength(0);
              }
              if (R == getBottomMargin() || R == rows - 1)
                insertLine(R, 1, SCROLL_UP);
              else
                R++;
              if (debug > 1)
                debug("IND (at " + R + " )");
              break;
            case NEL:
              if (R == getBottomMargin() || R == rows - 1)
                insertLine(R, 1, SCROLL_UP);
              else
                R++;
              C = 0;
              if (debug > 1)
                debug("NEL (at " + R + " )");
              break;
            case HTS:
              Tabs[C] = 1;
              if (debug > 1)
                debug("HTS");
              break;
            case DCS:
              dcs = "";
              term_state = TSTATE_DCS;
              break;
            default:
              doneflag = false;
              break;
          }
          if (doneflag) break;
        }
        switch (c) {
          case SS3:
            onegl = 3;
            break;
          case SS2:
            onegl = 2;
            break;
          case CSI: // should be in the 8bit section, but some BBS use this
            DCEvar = 0;
            DCEvars[0] = 0;
            DCEvars[1] = 0;
            DCEvars[2] = 0;
            DCEvars[3] = 0;
            term_state = TSTATE_CSI;
            break;
          case ESC:
            term_state = TSTATE_ESC;
            lastwaslf = 0;
            break;
          case 5: /* ENQ */
            write(answerBack, false);
            break;
          case 12:
            /* FormFeed, Home for the BBS world */
            deleteArea(0, 0, columns, rows, attributes);
            C = R = 0;
            break;
          case '\b': /* 8 */
            C--;
            if (C < 0)
              C = 0;
            lastwaslf = 0;
            break;
          case '\t':
            do {
              // Don't overwrite or insert! TABS are not destructive, but movement!
              C++;
            } while (C < columns && (Tabs[C] == 0));
            lastwaslf = 0;
            break;
          case '\r': // 13 CR
            C = 0;
            break;
          case '\n': // 10 LF
            if (debug > 3)
              debug("R= " + R + ", bm " + getBottomMargin() + ", tm=" + getTopMargin() + ", rows=" + rows);
            if (!vms) {
              if (lastwaslf != 0 && lastwaslf != c)   //  Ray: I do not understand this logic.
                break;
              lastwaslf = c;
              /*C = 0;*/
            }
            if (R == getBottomMargin() || R >= rows - 1)
              insertLine(R, 1, SCROLL_UP);
            else
              R++;
            break;
          case 7:
            beep();
            break;
          case '\016': /* SMACS , as */
            /* ^N, Shift out - Put G1 into GL */
            gl = 1;
            usedcharsets = true;
            break;
          case '\017': /* RMACS , ae */
            /* ^O, Shift in - Put G0 into GL */
            gl = 0;
            usedcharsets = true;
            break;
          default:
            {
              int thisgl = gl;

              if (onegl >= 0) {
                thisgl = onegl;
                onegl = -1;
              }
              lastwaslf = 0;
              if (c < 32) {
                if (c != 0)
                  if (debug > 0)
                    debug("TSTATE_DATA char: " + ((int) c));
                /*break; some BBS really want those characters, like hearst etc. */
                if (c == 0) /* print 0 ... you bet */
                  break;
              }
              if (C >= columns) {
                if (wraparound) {
                  int bot = rows;

                  // If we're in the scroll region, check against the bottom margin
                  if (R <= getBottomMargin() && R >= getTopMargin())
                    bot = getBottomMargin() + 1;

                  if (R < bot - 1)
                    R++;
                  else {
                    if (debug > 3) debug("scrolling due to wrap at " + R);
                    insertLine(R, 1, SCROLL_UP);
                  }
                  C = 0;
                } else {
                  // cursor stays on last character.
                  C = columns - 1;
                }
              }

              boolean mapped = false;

              // Mapping if DEC Special is chosen charset
              if (usedcharsets) {
                if (c >= '\u0020' && c <= '\u007f') {
                  switch (gx[thisgl]) {
                    case '0':
                      // Remap SCOANSI line drawing to VT100 line drawing chars
                      // for our SCO using customers.
                      if (terminalID.equals("scoansi") || terminalID.equals("ansi")) {
                        for (int i = 0; i < scoansi_acs.length(); i += 2) {
                          if (c == scoansi_acs.charAt(i)) {
                            c = scoansi_acs.charAt(i + 1);
                            break;
                          }
                        }
                      }
                      if (c >= '\u005f' && c <= '\u007e') {
                        c = DECSPECIAL[(short) c - 0x5f];
                        mapped = true;
                      }
                      break;
                    case '<': // 'user preferred' is currently 'ISO Latin-1 suppl
                      c = (char) ((c & 0x7f) | 0x80);
                      mapped = true;
                      break;
                    case 'A':
                    case 'B': // Latin-1 , ASCII -> fall through
                      mapped = true;
                      break;
                    default:
                      debug("Unsupported GL mapping: " + gx[thisgl]);
                      break;
                  }
                }
                if (!mapped && (c >= '\u0080' && c <= '\u00ff')) {
                  switch (gx[gr]) {
                    case '0':
                      if (c >= '\u00df' && c <= '\u00fe') {
                        c = DECSPECIAL[c - '\u00df'];
                        mapped = true;
                      }
                      break;
                    case '<':
                    case 'A':
                    case 'B':
                      mapped = true;
                      break;
                    default:
                      debug("Unsupported GR mapping: " + gx[gr]);
                      break;
                  }
                }
              }
              if (!mapped && useibmcharset)
                c = map_cp850_unicode(c);

              /*if(true || (statusmode == 0)) { */
              if (isWide) {
                if (C >= columns - 1) {
                  if (wraparound) {
                    int bot = rows;

                    // If we're in the scroll region, check against the bottom margin
                    if (R <= getBottomMargin() && R >= getTopMargin())
                      bot = getBottomMargin() + 1;

                    if (R < bot - 1)
                      R++;
                    else {
                      if (debug > 3) debug("scrolling due to wrap at " + R);
                      insertLine(R, 1, SCROLL_UP);
                    }
                    C = 0;
                  } else {
                    // cursor stays on last wide character.
                    C = columns - 2;
                  }
                }
              }

              if (insertmode == 1) {
                if (isWide) {
                  insertChar(C++, R, c, attributes | FULLWIDTH);
                  insertChar(C, R, ' ', attributes | FULLWIDTH);
                } else
                  insertChar(C, R, c, attributes);
              } else {
                if (isWide) {
                  putChar(C++, R, c, attributes | FULLWIDTH);
                  putChar(C, R, ' ', attributes | FULLWIDTH);
                } else
                  putChar(C, R, c, attributes);
              }

              /*
                } else {
                if (insertmode==1) {
                insertChar(C, rows, c, attributes);
                } else {
                putChar(C, rows, c, attributes);
                }
                }
              */
              C++;
              break;
            }
        } /* switch(c) */
        break;
      case TSTATE_OSC:
        if ((c < 0x20) && (c != ESC)) {// NP - No printing character
          handle_osc(osc);
          term_state = TSTATE_DATA;
          break;
        }
        //but check for vt102 ESC \
        if (c == '\\' && osc.charAt(osc.length() - 1) == ESC) {
          handle_osc(osc);
          term_state = TSTATE_DATA;
          break;
        }
        osc = osc + c;
        break;
      case TSTATE_ESCSPACE:
        term_state = TSTATE_DATA;
        switch (c) {
          case 'F': /* S7C1T, Disable output of 8-bit controls, use 7-bit */
            output8bit = false;
            break;
          case 'G': /* S8C1T, Enable output of 8-bit control codes*/
            output8bit = true;
            break;
          default:
            debug("ESC <space> " + c + " unhandled.");
        }
        break;
      case TSTATE_ESC:
        term_state = TSTATE_DATA;
        switch (c) {
          case ' ':
            term_state = TSTATE_ESCSPACE;
            break;
          case '#':
            term_state = TSTATE_ESCSQUARE;
            break;
          case 'c':
            /* Hard terminal reset */
            reset();
            break;
          case '[':
            DCEvar = 0;
            DCEvars[0] = 0;
            DCEvars[1] = 0;
            DCEvars[2] = 0;
            DCEvars[3] = 0;
            term_state = TSTATE_CSI;
            break;
          case ']':
            osc = "";
            term_state = TSTATE_OSC;
            break;
          case 'P':
            dcs = "";
            term_state = TSTATE_DCS;
            break;
          case 'A': /* CUU */
            R--;
            if (R < 0) R = 0;
            break;
          case 'B': /* CUD */
            R++;
            if (R >= rows) R = rows - 1;
            break;
          case 'C':
            C++;
            if (C >= columns) C = columns - 1;
            break;
          case 'I': // RI
            insertLine(R, 1, SCROLL_DOWN);
            break;
          case 'E': /* NEL */
            if (R == getBottomMargin() || R == rows - 1)
              insertLine(R, 1, SCROLL_UP);
            else
              R++;
            C = 0;
            if (debug > 1)
              debug("ESC E (at " + R + ")");
            break;
          case 'D': /* IND */
            if (R == getBottomMargin() || R == rows - 1)
              insertLine(R, 1, SCROLL_UP);
            else
              R++;
            if (debug > 1)
              debug("ESC D (at " + R + " )");
            break;
          case 'J': /* erase to end of screen */
            if (R < rows - 1)
              deleteArea(0, R + 1, columns, rows - R - 1, attributes);
            if (C < columns - 1)
              deleteArea(C, R, columns - C, 1, attributes);
            break;
          case 'K':
            if (C < columns - 1)
              deleteArea(C, R, columns - C, 1, attributes);
            break;
          case 'M': // RI
            debug("ESC M : R is "+R+", tm is "+getTopMargin()+", bm is "+getBottomMargin());
            if (R > getTopMargin()) { // just go up 1 line.
              R--;
            } else { // scroll down
              insertLine(R, 1, SCROLL_DOWN);
            }
            /* else do nothing ; */
            if (debug > 2)
              debug("ESC M ");
            break;
          case 'H':
            if (debug > 1)
              debug("ESC H at " + C);
            /* right border probably ...*/
            if (C >= columns)
              C = columns - 1;
            Tabs[C] = 1;
            break;
          case 'N': // SS2
            onegl = 2;
            break;
          case 'O': // SS3
            onegl = 3;
            break;
          case '=':
            /*application keypad*/
            if (debug > 0)
              debug("ESC =");
            keypadmode = true;
            break;
          case '<': /* vt52 mode off */
            vt52mode = false;
            break;
          case '>': /*normal keypad*/
            if (debug > 0)
              debug("ESC >");
            keypadmode = false;
            break;
          case '7': /* DECSC: save cursor, attributes */
            Sc = C;
            Sr = R;
            Sgl = gl;
            Sgr = gr;
            Sa = attributes;
            Sgx = new char[4];
            for (int i = 0; i < 4; i++) Sgx[i] = gx[i];
            if (debug > 1)
              debug("ESC 7");
            break;
          case '8': /* DECRC: restore cursor, attributes */
            C = Sc;
            R = Sr;
            gl = Sgl;
            gr = Sgr;
            if (Sgx != null)
              for (int i = 0; i < 4; i++) gx[i] = Sgx[i];
            attributes = Sa;
            if (debug > 1)
              debug("ESC 8");
            break;
          case '(': /* Designate G0 Character set (ISO 2022) */
            term_state = TSTATE_SETG0;
            usedcharsets = true;
            break;
          case ')': /* Designate G1 character set (ISO 2022) */
            term_state = TSTATE_SETG1;
            usedcharsets = true;
            break;
          case '*': /* Designate G2 Character set (ISO 2022) */
            term_state = TSTATE_SETG2;
            usedcharsets = true;
            break;
          case '+': /* Designate G3 Character set (ISO 2022) */
            term_state = TSTATE_SETG3;
            usedcharsets = true;
            break;
          case '~': /* Locking Shift 1, right */
            gr = 1;
            usedcharsets = true;
            break;
          case 'n': /* Locking Shift 2 */
            gl = 2;
            usedcharsets = true;
            break;
          case '}': /* Locking Shift 2, right */
            gr = 2;
            usedcharsets = true;
            break;
          case 'o': /* Locking Shift 3 */
            gl = 3;
            usedcharsets = true;
            break;
          case '|': /* Locking Shift 3, right */
            gr = 3;
            usedcharsets = true;
            break;
          case 'Y': /* vt52 cursor address mode , next chars are x,y */
            term_state = TSTATE_VT52Y;
            break;
          case '_':
          	term_state = TSTATE_TITLE;
          	break;
          case '\\':
          	// TODO save title
          	term_state = TSTATE_DATA;
          	break;
          default:
            debug("ESC unknown letter: " + c + " (" + ((int) c) + ")");
            break;
        }
        break;
      case TSTATE_VT52X:
        C = c - 37;
        if (C < 0)
          C = 0;
        else if (C >= width)
          C = width - 1;
        term_state = TSTATE_VT52Y;
        break;
      case TSTATE_VT52Y:
        R = c - 37;
        if (R < 0)
          R = 0;
        else if (R >= height)
          R = height - 1;
        term_state = TSTATE_DATA;
        break;
      case TSTATE_SETG0:
        if (c != '0' && c != 'A' && c != 'B' && c != '<')
          debug("ESC ( " + c + ": G0 char set?  (" + ((int) c) + ")");
        else {
          if (debug > 2) debug("ESC ( : G0 char set  (" + c + " " + ((int) c) + ")");
          gx[0] = c;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_SETG1:
        if (c != '0' && c != 'A' && c != 'B' && c != '<') {
          debug("ESC ) " + c + " (" + ((int) c) + ") :G1 char set?");
        } else {
          if (debug > 2) debug("ESC ) :G1 char set  (" + c + " " + ((int) c) + ")");
          gx[1] = c;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_SETG2:
        if (c != '0' && c != 'A' && c != 'B' && c != '<')
          debug("ESC*:G2 char set?  (" + ((int) c) + ")");
        else {
          if (debug > 2) debug("ESC*:G2 char set  (" + c + " " + ((int) c) + ")");
          gx[2] = c;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_SETG3:
        if (c != '0' && c != 'A' && c != 'B' && c != '<')
          debug("ESC+:G3 char set?  (" + ((int) c) + ")");
        else {
          if (debug > 2) debug("ESC+:G3 char set  (" + c + " " + ((int) c) + ")");
          gx[3] = c;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_ESCSQUARE:
        switch (c) {
          case '8':
            for (int i = 0; i < columns; i++)
              for (int j = 0; j < rows; j++)
                putChar(i, j, 'E', 0);
            break;
          default:
            debug("ESC # " + c + " not supported.");
            break;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_DCS:
        if (c == '\\' && dcs.charAt(dcs.length() - 1) == ESC) {
          handle_dcs(dcs);
          term_state = TSTATE_DATA;
          break;
        }
        dcs = dcs + c;
        break;

      case TSTATE_DCEQ:
        term_state = TSTATE_DATA;
        switch (c) {
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            DCEvars[DCEvar] = DCEvars[DCEvar] * 10 + (c) - 48;
            term_state = TSTATE_DCEQ;
            break;
          case ';':
            DCEvar++;
            DCEvars[DCEvar] = 0;
            term_state = TSTATE_DCEQ;
            break;
          case 's': // XTERM_SAVE missing!
            if (true || debug > 1)
              debug("ESC [ ? " + DCEvars[0] + " s unimplemented!");
            break;
          case 'r': // XTERM_RESTORE
            if (true || debug > 1)
              debug("ESC [ ? " + DCEvars[0] + " r");
            /* DEC Mode reset */
            for (int i = 0; i <= DCEvar; i++) {
              switch (DCEvars[i]) {
                case 3: /* 80 columns*/
                  setScreenSize(80, height, true);
                  break;
                case 4: /* scrolling mode, smooth */
                  break;
                case 5: /* light background */
                  break;
                case 6: /* DECOM (Origin Mode) move inside margins. */
                  moveoutsidemargins = true;
                  break;
                case 7: /* DECAWM: Autowrap Mode */
                  wraparound = false;
                  break;
                case 12:/* local echo off */
                  break;
                case 9: 	/* X10 mouse */
                case 1000:	/* xterm style mouse report on */
                case 1001:
                case 1002:
                case 1003:
                  mouserpt = DCEvars[i];
                  break;
                default:
                  debug("ESC [ ? " + DCEvars[0] + " r, unimplemented!");
              }
            }
            break;
          case 'h': // DECSET
            if (debug > 0)
              debug("ESC [ ? " + DCEvars[0] + " h");
            /* DEC Mode set */
            for (int i = 0; i <= DCEvar; i++) {
              switch (DCEvars[i]) {
                case 1:  /* Application cursor keys */
                  KeyUp[0] = "\u001bOA";
                  KeyDown[0] = "\u001bOB";
                  KeyRight[0] = "\u001bOC";
                  KeyLeft[0] = "\u001bOD";
                  break;
                case 2: /* DECANM */
                  vt52mode = false;
                  break;
                case 3: /* 132 columns*/
                  setScreenSize(132, height, true);
                  break;
                case 6: /* DECOM: move inside margins. */
                  moveoutsidemargins = false;
                  break;
                case 7: /* DECAWM: Autowrap Mode */
                  wraparound = true;
                  break;
                case 25: /* turn cursor on */
                  showCursor(true);
                  break;
                case 9: 	/* X10 mouse */
                case 1000:	/* xterm style mouse report on */
                case 1001:
                case 1002:
                case 1003:
                  mouserpt = DCEvars[i];
                  break;

                  /* unimplemented stuff, fall through */
                  /* 4  - scrolling mode, smooth */
                  /* 5  - light background */
                  /* 12 - local echo off */
                  /* 18 - DECPFF - Printer Form Feed Mode -> On */
                  /* 19 - DECPEX - Printer Extent Mode -> Screen */
                default:
                  debug("ESC [ ? " + DCEvars[0] + " h, unsupported.");
                  break;
              }
            }
            break;
          case 'i': // DEC Printer Control, autoprint, echo screenchars to printer
            // This is different to CSI i!
            // Also: "Autoprint prints a final display line only when the
            // cursor is moved off the line by an autowrap or LF, FF, or
            // VT (otherwise do not print the line)."
            switch (DCEvars[0]) {
              case 1:
                if (debug > 1)
                  debug("CSI ? 1 i : Print line containing cursor");
                break;
              case 4:
                if (debug > 1)
                  debug("CSI ? 4 i : Start passthrough printing");
                break;
              case 5:
                if (debug > 1)
                  debug("CSI ? 4 i : Stop passthrough printing");
                break;
            }
            break;
          case 'l':	//DECRST
            /* DEC Mode reset */
            if (debug > 0)
              debug("ESC [ ? " + DCEvars[0] + " l");
            for (int i = 0; i <= DCEvar; i++) {
              switch (DCEvars[i]) {
                case 1:  /* Application cursor keys */
                  KeyUp[0] = "\u001b[A";
                  KeyDown[0] = "\u001b[B";
                  KeyRight[0] = "\u001b[C";
                  KeyLeft[0] = "\u001b[D";
                  break;
                case 2: /* DECANM */
                  vt52mode = true;
                  break;
                case 3: /* 80 columns*/
                  setScreenSize(80, height, true);
                  break;
                case 6: /* DECOM: move outside margins. */
                  moveoutsidemargins = true;
                  break;
                case 7: /* DECAWM: Autowrap Mode OFF */
                  wraparound = false;
                  break;
                case 25: /* turn cursor off */
                  showCursor(false);
                  break;
                  /* Unimplemented stuff: */
                  /* 4  - scrolling mode, jump */
                  /* 5  - dark background */
                  /* 7  - DECAWM - no wrap around mode */
                  /* 12 - local echo on */
                  /* 18 - DECPFF - Printer Form Feed Mode -> Off*/
                  /* 19 - DECPEX - Printer Extent Mode -> Scrolling Region */
                case 9: 	/* X10 mouse */
                case 1000:	/* xterm style mouse report OFF */
                case 1001:
                case 1002:
                case 1003:
                  mouserpt = 0;
                  break;
                default:
                  debug("ESC [ ? " + DCEvars[0] + " l, unsupported.");
                  break;
              }
            }
            break;
          case 'n':
            if (debug > 0)
              debug("ESC [ ? " + DCEvars[0] + " n");
            switch (DCEvars[0]) {
              case 15:
                /* printer? no printer. */
                write((ESC) + "[?13n", false);
                debug("ESC[5n");
                break;
              default:
                debug("ESC [ ? " + DCEvars[0] + " n, unsupported.");
                break;
            }
            break;
          default:
            debug("ESC [ ? " + DCEvars[0] + " " + c + ", unsupported.");
            break;
        }
        break;
      case TSTATE_CSI_EX:
        term_state = TSTATE_DATA;
        switch (c) {
          case ESC:
            term_state = TSTATE_ESC;
            break;
          default:
            debug("Unknown character ESC[! character is " + (int) c);
            break;
        }
        break;
      case TSTATE_CSI_TICKS:
        term_state = TSTATE_DATA;
        switch (c) {
          case 'p':
            debug("Conformance level: " + DCEvars[0] + " (unsupported)," + DCEvars[1]);
            if (DCEvars[0] == 61) {
              output8bit = false;
              break;
            }
            if (DCEvars[1] == 1) {
              output8bit = false;
            } else {
              output8bit = true; /* 0 or 2 */
            }
            break;
          default:
            debug("Unknown ESC [...  \"" + c);
            break;
        }
        break;
      case TSTATE_CSI_EQUAL:
        term_state = TSTATE_DATA;
        switch (c) {
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            DCEvars[DCEvar] = DCEvars[DCEvar] * 10 + (c) - 48;
            term_state = TSTATE_CSI_EQUAL;
            break;
          case ';':
            DCEvar++;
            DCEvars[DCEvar] = 0;
            term_state = TSTATE_CSI_EQUAL;
            break;

          case 'F': /* SCO ANSI foreground */
	  {
	    int newcolor;

            debug("ESC [ = "+DCEvars[0]+" F");

            attributes &= ~COLOR_FG;
	    newcolor =	((DCEvars[0] & 1) << 2)	|
	    		 (DCEvars[0] & 2)	|
	    		((DCEvars[0] & 4) >> 2) ;
            attributes |= (newcolor+1) << COLOR_FG_SHIFT;

	    break;
	  }
          case 'G': /* SCO ANSI background */
	  {
	    int newcolor;

            debug("ESC [ = "+DCEvars[0]+" G");

            attributes &= ~COLOR_BG;
	    newcolor =	((DCEvars[0] & 1) << 2)	|
	    		 (DCEvars[0] & 2)	|
	    		((DCEvars[0] & 4) >> 2) ;
            attributes |= (newcolor+1) << COLOR_BG_SHIFT;
	    break;
          }

          default:
            debugStr.append("Unknown ESC [ = ");
            for (int i=0;i<=DCEvar;i++) {
              debugStr.append(DCEvars[i])
                .append(',');
            }
            debugStr.append(c);
            debug(debugStr.toString());
            debugStr.setLength(0);
            break;
        }
        break;
      case TSTATE_CSI_DOLLAR:
        term_state = TSTATE_DATA;
        switch (c) {
          case '}':
            debug("Active Status Display now " + DCEvars[0]);
            statusmode = DCEvars[0];
            break;
            /* bad documentation?
               case '-':
               debug("Set Status Display now "+DCEvars[0]);
               break;
            */
          case '~':
            debug("Status Line mode now " + DCEvars[0]);
            break;
          default:
            debug("UNKNOWN Status Display code " + c + ", with Pn=" + DCEvars[0]);
            break;
        }
        break;
      case TSTATE_CSI:
        term_state = TSTATE_DATA;
        switch (c) {
          case '"':
            term_state = TSTATE_CSI_TICKS;
            break;
          case '$':
            term_state = TSTATE_CSI_DOLLAR;
            break;
          case '=':
            term_state = TSTATE_CSI_EQUAL;
            break;
          case '!':
            term_state = TSTATE_CSI_EX;
            break;
          case '?':
            DCEvar = 0;
            DCEvars[0] = 0;
            term_state = TSTATE_DCEQ;
            break;
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            DCEvars[DCEvar] = DCEvars[DCEvar] * 10 + (c) - 48;
            term_state = TSTATE_CSI;
            break;
          case ';':
            DCEvar++;
            DCEvars[DCEvar] = 0;
            term_state = TSTATE_CSI;
            break;
          case 'c':/* send primary device attributes */
            /* send (ESC[?61c) */

            String subcode = "";
            if (terminalID.equals("vt320")) subcode = "63;";
            if (terminalID.equals("vt220")) subcode = "62;";
            if (terminalID.equals("vt100")) subcode = "61;";
            write((ESC) + "[?" + subcode + "1;2c", false);
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " c");
            break;
          case 'q':
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " q");
            break;
          case 'g':
            /* used for tabsets */
            switch (DCEvars[0]) {
              case 3:/* clear them */
                Tabs = new byte[width];
                break;
              case 0:
                Tabs[C] = 0;
                break;
            }
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " g");
            break;
          case 'h':
            switch (DCEvars[0]) {
              case 4:
                insertmode = 1;
                break;
              case 20:
                debug("Setting CRLF to TRUE");
                sendcrlf = true;
                break;
              default:
                debug("unsupported: ESC [ " + DCEvars[0] + " h");
                break;
            }
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " h");
            break;
          case 'i': // Printer Controller mode.
            // "Transparent printing sends all output, except the CSI 4 i
            //  termination string, to the printer and not the screen,
            //  uses an 8-bit channel if no parity so NUL and DEL will be
            //  seen by the printer and by the termination recognizer code,
            //  and all translation and character set selections are
            //  bypassed."
            switch (DCEvars[0]) {
              case 0:
                if (debug > 1)
                  debug("CSI 0 i:  Print Screen, not implemented.");
                break;
              case 4:
                if (debug > 1)
                  debug("CSI 4 i:  Enable Transparent Printing, not implemented.");
                break;
              case 5:
                if (debug > 1)
                  debug("CSI 4/5 i:  Disable Transparent Printing, not implemented.");
                break;
              default:
                debug("ESC [ " + DCEvars[0] + " i, unimplemented!");
            }
            break;
          case 'l':
            switch (DCEvars[0]) {
              case 4:
                insertmode = 0;
                break;
              case 20:
                debug("Setting CRLF to FALSE");
                sendcrlf = false;
                break;
              default:
                debug("ESC [ " + DCEvars[0] + " l, unimplemented!");
                break;
            }
            break;
          case 'A': // CUU
            {
              int limit;
              /* FIXME: xterm only cares about 0 and topmargin */
              if (R >= getTopMargin()) {
                limit = getTopMargin();
              } else
                limit = 0;
              if (DCEvars[0] == 0)
                R--;
              else
                R -= DCEvars[0];
              if (R < limit)
                R = limit;
              if (debug > 1)
                debug("ESC [ " + DCEvars[0] + " A");
              break;
            }
          case 'B':	// CUD
            /* cursor down n (1) times */
            {
              int limit;
              if (R <= getBottomMargin()) {
                limit = getBottomMargin();
              } else
                limit = rows - 1;
              if (DCEvars[0] == 0)
                R++;
              else
                R += DCEvars[0];
              if (R > limit)
                R = limit;
              else {
                if (debug > 2) debug("Not limited.");
              }
              if (debug > 2) debug("to: " + R);
              if (debug > 1)
                debug("ESC [ " + DCEvars[0] + " B (at C=" + C + ")");
              break;
            }
          case 'C':
            if (DCEvars[0] == 0)
              DCEvars[0] = 1;
            while (DCEvars[0]-- > 0) {
              C++;
            }
            if (C >= columns)
              C = columns - 1;
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " C");
            break;
          case 'd': // CVA
            R = DCEvars[0] - 1;
            if (R < 0)
              R = 0;
            else if (R >= height)
              R = height - 1;
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " d");
            break;
          case 'D':
            if (DCEvars[0] == 0)
              DCEvars[0] = 1;
            while (DCEvars[0]-- > 0) {
              C--;
            }
            if (C < 0) C = 0;
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " D");
            break;
          case 'r': // DECSTBM
            if (DCEvar > 0)   //  Ray:  Any argument is optional
            {
              R = DCEvars[1] - 1;
              if (R < 0)
                R = rows - 1;
              else if (R >= rows) {
                R = rows - 1;
              }
            } else
              R = rows - 1;
            int bot = R;
            if (R >= DCEvars[0]) {
              R = DCEvars[0] - 1;
              if (R < 0)
                R = 0;
            }
            setMargins(R, bot);
            _SetCursor(0, 0);
            if (debug > 1)
              debug("ESC [" + DCEvars[0] + " ; " + DCEvars[1] + " r");
            break;
          case 'G':  /* CUP  / cursor absolute column */
            C = DCEvars[0];
            if (C < 0)
              C = 0;
            else if (C >= width)
              C = width - 1;
            if (debug > 1) debug("ESC [ " + DCEvars[0] + " G");
            break;
          case 'H':  /* CUP  / cursor position */
            /* gets 2 arguments */
            _SetCursor(DCEvars[0] - 1, DCEvars[1] - 1);
            if (debug > 2) {
              debug("ESC [ " + DCEvars[0] + ";" + DCEvars[1] + " H, moveoutsidemargins " + moveoutsidemargins);
              debug("	-> R now " + R + ", C now " + C);
            }
            break;
          case 'f':  /* move cursor 2 */
            /* gets 2 arguments */
            R = DCEvars[0] - 1;
            C = DCEvars[1] - 1;
            if (C < 0)
              C = 0;
            else if (C >= width)
              C = width - 1;
            if (R < 0)
              R = 0;
            else if (R >= height)
              R = height - 1;
            if (debug > 2)
              debug("ESC [ " + DCEvars[0] + ";" + DCEvars[1] + " f");
            break;
          case 'S': /* ind aka 'scroll forward' */
            if (DCEvars[0] == 0)
              insertLine(getBottomMargin(), SCROLL_UP);
            else
              insertLine(getBottomMargin(), DCEvars[0], SCROLL_UP);
            break;
          case 'L':
            /* insert n lines */
            if (DCEvars[0] == 0)
              insertLine(R, SCROLL_DOWN);
            else
              insertLine(R, DCEvars[0], SCROLL_DOWN);
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + "" + (c) + " (at R " + R + ")");
            break;
          case 'T': /* 'ri' aka scroll backward */
            if (DCEvars[0] == 0)
              insertLine(getTopMargin(), SCROLL_DOWN);
            else
              insertLine(getTopMargin(), DCEvars[0], SCROLL_DOWN);
            break;
          case 'M':
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + "" + (c) + " at R=" + R);
            if (DCEvars[0] == 0)
              deleteLine(R);
            else
              for (int i = 0; i < DCEvars[0]; i++)
                deleteLine(R);
            break;
          case 'K':
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " K");
            /* clear in line */
            switch (DCEvars[0]) {
              case 6: /* 97801 uses ESC[6K for delete to end of line */
              case 0:/*clear to right*/
                if (C < columns - 1)
                  deleteArea(C, R, columns - C, 1, attributes);
                break;
              case 1:/*clear to the left, including this */
                if (C > 0)
                  deleteArea(0, R, C + 1, 1, attributes);
                break;
              case 2:/*clear whole line */
                deleteArea(0, R, columns, 1, attributes);
                break;
            }
            break;
          case 'J':
            /* clear below current line */
            switch (DCEvars[0]) {
              case 0:
                if (R < rows - 1)
                  deleteArea(0, R + 1, columns, rows - R - 1, attributes);
                if (C < columns - 1)
                  deleteArea(C, R, columns - C, 1, attributes);
                break;
              case 1:
                if (R > 0)
                  deleteArea(0, 0, columns, R, attributes);
                if (C > 0)
                  deleteArea(0, R, C + 1, 1, attributes);// include up to and including current
                break;
              case 2:
                deleteArea(0, 0, columns, rows, attributes);
                break;
            }
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " J");
            break;
          case '@':
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " @");
            for (int i = 0; i < DCEvars[0]; i++)
              insertChar(C, R, ' ', attributes);
            break;
          case 'X':
            {
              int toerase = DCEvars[0];
              if (debug > 1)
                debug("ESC [ " + DCEvars[0] + " X, C=" + C + ",R=" + R);
              if (toerase == 0)
                toerase = 1;
              if (toerase + C > columns)
                toerase = columns - C;
              deleteArea(C, R, toerase, 1, attributes);
              // does not change cursor position
              break;
            }
          case 'P':
            if (debug > 1)
              debug("ESC [ " + DCEvars[0] + " P, C=" + C + ",R=" + R);
            if (DCEvars[0] == 0) DCEvars[0] = 1;
            for (int i = 0; i < DCEvars[0]; i++)
              deleteChar(C, R);
            break;
          case 'n':
            switch (DCEvars[0]) {
              case 5: /* malfunction? No malfunction. */
                writeSpecial((ESC) + "[0n");
                if (debug > 1)
                  debug("ESC[5n");
                break;
              case 6:
                // DO NOT offset R and C by 1! (checked against /usr/X11R6/bin/resize
                // FIXME check again.
                // FIXME: but vttest thinks different???
                writeSpecial((ESC) + "[" + R + ";" + C + "R");
                if (debug > 1)
                  debug("ESC[6n");
                break;
              default:
                if (debug > 0)
                  debug("ESC [ " + DCEvars[0] + " n??");
                break;
            }
            break;
          case 's':  /* DECSC - save cursor */
            Sc = C;
            Sr = R;
            Sa = attributes;
            if (debug > 3)
              debug("ESC[s");
            break;
          case 'u': /* DECRC - restore cursor */
            C = Sc;
            R = Sr;
            attributes = Sa;
            if (debug > 3)
              debug("ESC[u");
            break;
          case 'm':  /* attributes as color, bold , blink,*/
            if (debug > 3)
              debug("ESC [ ");
            if (DCEvar == 0 && DCEvars[0] == 0)
              attributes = 0;
            for (int i = 0; i <= DCEvar; i++) {
              switch (DCEvars[i]) {
                case 0:
                  if (DCEvar > 0) {
                    if (terminalID.equals("scoansi")) {
                      attributes &= COLOR; /* Keeps color. Strange but true. */
                    } else {
                      attributes = 0;
                    }
                  }
                  break;
                case 1:
                  attributes |= BOLD;
                  attributes &= ~LOW;
                  break;
                case 2:
                  /* SCO color hack mode */
                  if (terminalID.equals("scoansi") && ((DCEvar - i) >= 2)) {
                    int ncolor;
                    attributes &= ~(COLOR | BOLD);

                    ncolor = DCEvars[i + 1];
                    if ((ncolor & 8) == 8)
                      attributes |= BOLD;
                    ncolor = ((ncolor & 1) << 2) | (ncolor & 2) | ((ncolor & 4) >> 2);
                    attributes |= ((ncolor) + 1) << COLOR_FG_SHIFT;
                    ncolor = DCEvars[i + 2];
                    ncolor = ((ncolor & 1) << 2) | (ncolor & 2) | ((ncolor & 4) >> 2);
                    attributes |= ((ncolor) + 1) << COLOR_BG_SHIFT;
                    i += 2;
                  } else {
                    attributes |= LOW;
                  }
                  break;
                case 3: /* italics */
                  attributes |= INVERT;
                  break;
                case 4:
                  attributes |= UNDERLINE;
                  break;
                case 7:
                  attributes |= INVERT;
                  break;
                case 8:
                  attributes |= INVISIBLE;
                  break;
                case 5: /* blink on */
                  break;
                  /* 10 - ANSI X3.64-1979, select primary font, don't display control
                   *      chars, don't set bit 8 on output */
                case 10:
                  gl = 0;
                  usedcharsets = true;
                  break;
                  /* 11 - ANSI X3.64-1979, select second alt. font, display control
                   *      chars, set bit 8 on output */
                case 11: /* SMACS , as */
                case 12:
                  gl = 1;
                  usedcharsets = true;
                  break;
                case 21: /* normal intensity */
                  attributes &= ~(LOW | BOLD);
                  break;
                case 23: /* italics off */
                  attributes &= ~INVERT;
                  break;
                case 25: /* blinking off */
                  break;
                case 27:
                  attributes &= ~INVERT;
                  break;
                case 28:
                  attributes &= ~INVISIBLE;
                  break;
                case 24:
                  attributes &= ~UNDERLINE;
                  break;
                case 22:
                  attributes &= ~BOLD;
                  break;
                case 30:
                case 31:
                case 32:
                case 33:
                case 34:
                case 35:
                case 36:
                case 37:
                  attributes &= ~COLOR_FG;
                  attributes |= ((DCEvars[i] - 30) + 1)<< COLOR_FG_SHIFT;
                  break;
                case 38:
                  if (DCEvars[i+1] == 5) {
                    attributes &= ~COLOR_FG;
                    attributes |= ((DCEvars[i + 2]) + 1) << COLOR_FG_SHIFT;
                    i += 2;
                  }
                  break;
                case 39:
                  attributes &= ~COLOR_FG;
                  break;
                case 40:
                case 41:
                case 42:
                case 43:
                case 44:
                case 45:
                case 46:
                case 47:
                  attributes &= ~COLOR_BG;
                  attributes |= ((DCEvars[i] - 40) + 1) << COLOR_BG_SHIFT;
                  break;
                case 48:
                  if (DCEvars[i+1] == 5) {
                    attributes &= ~COLOR_BG;
                    attributes |= (DCEvars[i + 2] + 1) << COLOR_BG_SHIFT;
                    i += 2;
                  }
                  break;
                case 49:
                  attributes &= ~COLOR_BG;
                  break;
                case 90:
                case 91:
                case 92:
                case 93:
                case 94:
                case 95:
                case 96:
                case 97:
                  attributes &= ~COLOR_FG;
                  attributes |= ((DCEvars[i] - 82) + 1) << COLOR_FG_SHIFT;
                  break;
                case 100:
                case 101:
                case 102:
                case 103:
                case 104:
                case 105:
                case 106:
                case 107:
                  attributes &= ~COLOR_BG;
                  attributes |= ((DCEvars[i] - 92) + 1) << COLOR_BG_SHIFT;
                  break;

                default:
                  debugStr.append("ESC [ ")
                    .append(DCEvars[i])
                    .append(" m unknown...");
                  debug(debugStr.toString());
                  debugStr.setLength(0);
                  break;
              }
              if (debug > 3) {
                debugStr.append(DCEvars[i])
                  .append(';');
                debug(debugStr.toString());
                debugStr.setLength(0);
              }
            }
            if (debug > 3) {
              debugStr.append(" (attributes = ")
                .append(attributes)
                .append(")m");
              debug(debugStr.toString());
              debugStr.setLength(0);
            }
            break;
          default:
            debugStr.append("ESC [ unknown letter: ")
              .append(c)
              .append(" (")
              .append((int)c)
              .append(')');
            debug(debugStr.toString());
            debugStr.setLength(0);
            break;
        }
        break;
      case TSTATE_TITLE:
        switch (c) {
          case ESC:
            term_state = TSTATE_ESC;
            break;
          default:
            // TODO save title
            break;
        }
        break;
      default:
        term_state = TSTATE_DATA;
        break;
    }

    setCursorPosition(C, R);
  }

  /* hard reset the terminal */
  public void reset() {
    gx[0] = 'B';
    gx[1] = 'B';
    gx[2] = 'B';
    gx[3] = 'B';

    gl = 0;  // default GL to G0
    gr = 2;  // default GR to G2

    onegl = -1; // Single shift override

    /* reset tabs */
    int nw = width;
    if (nw < 132) nw = 132;
    Tabs = new byte[nw];
    for (int i = 0; i < nw; i += 8) {
      Tabs[i] = 1;
    }

    deleteArea(0, 0, width, height, attributes);
    setMargins(0, height);
    C = R = 0;
    _SetCursor(0, 0);

    if (display != null)
      display.resetColors();

    showCursor(true);
    /*FIXME:*/
    term_state = TSTATE_DATA;
  }
}
