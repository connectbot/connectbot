/*
 * This file is part of "JTA - Telnet/SSH for the JAVA(tm) platform".
 *
 * (c) Matthias L. Jugel, Marcus Mei�ner 1996-2005. All Rights Reserved.
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

import java.util.Arrays;

/**
 * Implementation of a Video Display Unit (VDU) buffer. This class contains
 * all methods to manipulate the buffer that stores characters and their
 * attributes as well as the regions displayed.
 *
 * @author Matthias L. Jugel, Marcus Meißner
 * @version $Id: VDUBuffer.java 503 2005-10-24 07:34:13Z marcus $
 */
public class VDUBuffer {

  /** The current version id tag */
  public final static String ID = "$Id: VDUBuffer.java 503 2005-10-24 07:34:13Z marcus $";

  /** Enable debug messages. */
  public final static int debug = 0;

  public int height, width;                          /* rows and columns */
  public boolean[] update;        /* contains the lines that need update */
  public char[][] charArray;                  /* contains the characters */
  public int[][] charAttributes;             /* contains character attrs */
  public int bufSize;
  public int maxBufSize;                                 /* buffer sizes */
  public int screenBase;                      /* the actual screen start */
  public int windowBase;                   /* where the start displaying */
  public int scrollMarker;               /* marks the last line inserted */

  private int topMargin;                               /* top scroll margin */
  private int bottomMargin;                         /* bottom scroll margin */

  // cursor variables
  protected boolean showcursor = true;
  protected int cursorX, cursorY;

  /** Scroll up when inserting a line. */
  public final static boolean SCROLL_UP = false;
  /** Scroll down when inserting a line. */
  public final static boolean SCROLL_DOWN = true;

  /*  Attributes bit-field usage:
   *
   *  8421 8421 8421 8421 8421 8421 8421 8421
   *  |||| |||| |||| |||| |||| |||| |||| |||`- Bold
   *  |||| |||| |||| |||| |||| |||| |||| ||`-- Underline
   *  |||| |||| |||| |||| |||| |||| |||| |`--- Invert
   *  |||| |||| |||| |||| |||| |||| |||| `---- Low
   *  |||| |||| |||| |||| |||| |||| |||`------ Invisible
   *  |||| |||| |||| |||| ||`+-++++-+++------- Foreground Color
   *  |||| |||| |`++-++++-++------------------ Background Color
   *  |||| |||| `----------------------------- Fullwidth character
   *  `+++-++++------------------------------- Reserved for future use
   */

  /** Make character normal. */
  public final static int NORMAL = 0x00;
  /** Make character bold. */
  public final static int BOLD = 0x01;
  /** Underline character. */
  public final static int UNDERLINE = 0x02;
  /** Invert character. */
  public final static int INVERT = 0x04;
  /** Lower intensity character. */
  public final static int LOW = 0x08;
  /** Invisible character. */
  public final static int INVISIBLE = 0x10;
  /** Unicode full-width character (CJK, et al.) */
  public final static int FULLWIDTH = 0x8000000;

  /** how much to left shift the foreground color */
  public final static int COLOR_FG_SHIFT = 5;
  /** how much to left shift the background color */
  public final static int COLOR_BG_SHIFT = 14;
  /** color mask */
  public final static int COLOR = 0x7fffe0;    /* 0000 0000 0111 1111 1111 1111 1110 0000 */
  /** foreground color mask */
  public final static int COLOR_FG = 0x3fe0;   /* 0000 0000 0000 0000 0011 1111 1110 0000 */
  /** background color mask */
  public final static int COLOR_BG = 0x7fc000; /* 0000 0000 0111 1111 1100 0000 0000 0000 */

  /**
   * Create a new video display buffer with the passed width and height in
   * characters.
   * @param width the length of the character lines
   * @param height the amount of lines on the screen
   */
  public VDUBuffer(int width, int height) {
    // set the display screen size
    setScreenSize(width, height, false);
  }

  /**
   * Create a standard video display buffer with 80 columns and 24 lines.
   */
  public VDUBuffer() {
    this(80, 24);
  }

  /**
   * Put a character on the screen with normal font and outline.
   * The character previously on that position will be overwritten.
   * You need to call redraw() to update the screen.
   * @param c x-coordinate (column)
   * @param l y-coordinate (line)
   * @param ch the character to show on the screen
   * @see #insertChar
   * @see #deleteChar
   * @see #redraw
   */
  public void putChar(int c, int l, char ch) {
    putChar(c, l, ch, NORMAL);
  }

  /**
   * Put a character on the screen with specific font and outline.
   * The character previously on that position will be overwritten.
   * You need to call redraw() to update the screen.
   * @param c x-coordinate (column)
   * @param l y-coordinate (line)
   * @param ch the character to show on the screen
   * @param attributes the character attributes
   * @see #BOLD
   * @see #UNDERLINE
   * @see #INVERT
   * @see #INVISIBLE
   * @see #NORMAL
   * @see #LOW
   * @see #insertChar
   * @see #deleteChar
   * @see #redraw
   */

  public void putChar(int c, int l, char ch, int attributes) {
    charArray[screenBase + l][c] = ch;
    charAttributes[screenBase + l][c] = attributes;
    if (l < height)
      update[l + 1] = true;
  }

  /**
   * Get the character at the specified position.
   * @param c x-coordinate (column)
   * @param l y-coordinate (line)
   * @see #putChar
   */
  public char getChar(int c, int l) {
    return charArray[screenBase + l][c];
  }

  /**
   * Get the attributes for the specified position.
   * @param c x-coordinate (column)
   * @param l y-coordinate (line)
   * @see #putChar
   */
  public int getAttributes(int c, int l) {
    return charAttributes[screenBase + l][c];
  }

  /**
   * Insert a character at a specific position on the screen.
   * All character right to from this position will be moved one to the right.
   * You need to call redraw() to update the screen.
   * @param c x-coordinate (column)
   * @param l y-coordinate (line)
   * @param ch the character to insert
   * @param attributes the character attributes
   * @see #BOLD
   * @see #UNDERLINE
   * @see #INVERT
   * @see #INVISIBLE
   * @see #NORMAL
   * @see #LOW
   * @see #putChar
   * @see #deleteChar
   * @see #redraw
   */
  public void insertChar(int c, int l, char ch, int attributes) {
    System.arraycopy(charArray[screenBase + l], c,
                     charArray[screenBase + l], c + 1, width - c - 1);
    System.arraycopy(charAttributes[screenBase + l], c,
                     charAttributes[screenBase + l], c + 1, width - c - 1);
    putChar(c, l, ch, attributes);
  }

  /**
   * Delete a character at a given position on the screen.
   * All characters right to the position will be moved one to the left.
   * You need to call redraw() to update the screen.
   * @param c x-coordinate (column)
   * @param l y-coordinate (line)
   * @see #putChar
   * @see #insertChar
   * @see #redraw
   */
  public void deleteChar(int c, int l) {
    if (c < width - 1) {
      System.arraycopy(charArray[screenBase + l], c + 1,
                       charArray[screenBase + l], c, width - c - 1);
      System.arraycopy(charAttributes[screenBase + l], c + 1,
                       charAttributes[screenBase + l], c, width - c - 1);
    }
    putChar(width - 1, l, (char) 0);
  }

  /**
   * Put a String at a specific position. Any characters previously on that
   * position will be overwritten. You need to call redraw() for screen update.
   * @param c x-coordinate (column)
   * @param l y-coordinate (line)
   * @param s the string to be shown on the screen
   * @see #BOLD
   * @see #UNDERLINE
   * @see #INVERT
   * @see #INVISIBLE
   * @see #NORMAL
   * @see #LOW
   * @see #putChar
   * @see #insertLine
   * @see #deleteLine
   * @see #redraw
   */
  public void putString(int c, int l, String s) {
    putString(c, l, s, NORMAL);
  }

  /**
   * Put a String at a specific position giving all characters the same
   * attributes. Any characters previously on that position will be
   * overwritten. You need to call redraw() to update the screen.
   * @param c x-coordinate (column)
   * @param l y-coordinate (line)
   * @param s the string to be shown on the screen
   * @param attributes character attributes
   * @see #BOLD
   * @see #UNDERLINE
   * @see #INVERT
   * @see #INVISIBLE
   * @see #NORMAL
   * @see #LOW
   * @see #putChar
   * @see #insertLine
   * @see #deleteLine
   * @see #redraw
   */
  public void putString(int c, int l, String s, int attributes) {
    for (int i = 0; i < s.length() && c + i < width; i++)
      putChar(c + i, l, s.charAt(i), attributes);
  }

  /**
   * Insert a blank line at a specific position.
   * The current line and all previous lines are scrolled one line up. The
   * top line is lost. You need to call redraw() to update the screen.
   * @param l the y-coordinate to insert the line
   * @see #deleteLine
   * @see #redraw
   */
  public void insertLine(int l) {
    insertLine(l, 1, SCROLL_UP);
  }

  /**
   * Insert blank lines at a specific position.
   * You need to call redraw() to update the screen
   * @param l the y-coordinate to insert the line
   * @param n amount of lines to be inserted
   * @see #deleteLine
   * @see #redraw
   */
  public void insertLine(int l, int n) {
    insertLine(l, n, SCROLL_UP);
  }

  /**
   * Insert a blank line at a specific position. Scroll text according to
   * the argument.
   * You need to call redraw() to update the screen
   * @param l the y-coordinate to insert the line
   * @param scrollDown scroll down
   * @see #deleteLine
   * @see #SCROLL_UP
   * @see #SCROLL_DOWN
   * @see #redraw
   */
  public void insertLine(int l, boolean scrollDown) {
    insertLine(l, 1, scrollDown);
  }

  /**
   * Insert blank lines at a specific position.
   * The current line and all previous lines are scrolled one line up. The
   * top line is lost. You need to call redraw() to update the screen.
   * @param l the y-coordinate to insert the line
   * @param n number of lines to be inserted
   * @param scrollDown scroll down
   * @see #deleteLine
   * @see #SCROLL_UP
   * @see #SCROLL_DOWN
   * @see #redraw
   */
  public synchronized void insertLine(int l, int n, boolean scrollDown) {
    char cbuf[][] = null;
    int abuf[][] = null;
    int offset = 0;
    int oldBase = screenBase;

    int newScreenBase = screenBase;
    int newWindowBase = windowBase;
    int newBufSize = bufSize;

    if (l > bottomMargin) /* We do not scroll below bottom margin (below the scrolling region). */
      return;
    int top = (l < topMargin ?
            0 : (l > bottomMargin ?
            (bottomMargin + 1 < height ?
            bottomMargin + 1 : height - 1) : topMargin));
    int bottom = (l > bottomMargin ?
            height - 1 : (l < topMargin ?
            (topMargin > 0 ?
            topMargin - 1 : 0) : bottomMargin));

    // System.out.println("l is "+l+", top is "+top+", bottom is "+bottom+", bottomargin is "+bottomMargin+", topMargin is "+topMargin);

    if (scrollDown) {
      if (n > (bottom - top)) n = (bottom - top);
      int size = bottom - l - (n - 1);
      if(size < 0) size = 0;
      cbuf = new char[size][];
      abuf = new int[size][];

      System.arraycopy(charArray, oldBase + l, cbuf, 0, bottom - l - (n - 1));
      System.arraycopy(charAttributes, oldBase + l,
                       abuf, 0, bottom - l - (n - 1));
      System.arraycopy(cbuf, 0, charArray, oldBase + l + n,
                       bottom - l - (n - 1));
      System.arraycopy(abuf, 0, charAttributes, oldBase + l + n,
                       bottom - l - (n - 1));
      cbuf = charArray;
      abuf = charAttributes;
    } else {
      try {
        if (n > (bottom - top) + 1) n = (bottom - top) + 1;
        if (bufSize < maxBufSize) {
          if (bufSize + n > maxBufSize) {
            offset = n - (maxBufSize - bufSize);
            scrollMarker += offset;
            newBufSize = maxBufSize;
            newScreenBase = maxBufSize - height - 1;
            newWindowBase = screenBase;
          } else {
            scrollMarker += n;
            newScreenBase += n;
            newWindowBase += n;
            newBufSize += n;
          }

          cbuf = new char[newBufSize][];
          abuf = new int[newBufSize][];
        } else {
          offset = n;
          cbuf = charArray;
          abuf = charAttributes;
        }
        // copy anything from the top of the buffer (+offset) to the new top
        // up to the screenBase.
        if (oldBase > 0) {
          System.arraycopy(charArray, offset,
                           cbuf, 0,
                           oldBase - offset);
          System.arraycopy(charAttributes, offset,
                           abuf, 0,
                           oldBase - offset);
        }
        // copy anything from the top of the screen (screenBase) up to the
        // topMargin to the new screen
        if (top > 0) {
          System.arraycopy(charArray, oldBase,
                           cbuf, newScreenBase,
                           top);
          System.arraycopy(charAttributes, oldBase,
                           abuf, newScreenBase,
                           top);
        }
        // copy anything from the topMargin up to the amount of lines inserted
        // to the gap left over between scrollback buffer and screenBase
        if (oldBase >= 0) {
          System.arraycopy(charArray, oldBase + top,
                           cbuf, oldBase - offset,
                           n);
          System.arraycopy(charAttributes, oldBase + top,
                           abuf, oldBase - offset,
                           n);
        }
        // copy anything from topMargin + n up to the line linserted to the
        // topMargin
        System.arraycopy(charArray, oldBase + top + n,
                         cbuf, newScreenBase + top,
                         l - top - (n - 1));
        System.arraycopy(charAttributes, oldBase + top + n,
                         abuf, newScreenBase + top,
                         l - top - (n - 1));
        //
        // copy the all lines next to the inserted to the new buffer
        if (l < height - 1) {
          System.arraycopy(charArray, oldBase + l + 1,
                           cbuf, newScreenBase + l + 1,
                           (height - 1) - l);
          System.arraycopy(charAttributes, oldBase + l + 1,
                           abuf, newScreenBase + l + 1,
                           (height - 1) - l);
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        // this should not happen anymore, but I will leave the code
        // here in case something happens anyway. That code above is
        // so complex I always have a hard time understanding what
        // I did, even though there are comments
        System.err.println("*** Error while scrolling up:");
        System.err.println("--- BEGIN STACK TRACE ---");
        e.printStackTrace();
        System.err.println("--- END STACK TRACE ---");
        System.err.println("bufSize=" + bufSize + ", maxBufSize=" + maxBufSize);
        System.err.println("top=" + top + ", bottom=" + bottom);
        System.err.println("n=" + n + ", l=" + l);
        System.err.println("screenBase=" + screenBase + ", windowBase=" + windowBase);
        System.err.println("newScreenBase=" + newScreenBase + ", newWindowBase=" + newWindowBase);
        System.err.println("oldBase=" + oldBase);
        System.err.println("size.width=" + width + ", size.height=" + height);
        System.err.println("abuf.length=" + abuf.length + ", cbuf.length=" + cbuf.length);
        System.err.println("*** done dumping debug information");
      }
    }

    // this is a little helper to mark the scrolling
    scrollMarker -= n;


    for (int i = 0; i < n; i++) {
      cbuf[(newScreenBase + l) + (scrollDown ? i : -i)] = new char[width];
      Arrays.fill(cbuf[(newScreenBase + l) + (scrollDown ? i : -i)], ' ');
      abuf[(newScreenBase + l) + (scrollDown ? i : -i)] = new int[width];
    }

    charArray = cbuf;
    charAttributes = abuf;
    screenBase = newScreenBase;
    windowBase = newWindowBase;
    bufSize = newBufSize;

    if (scrollDown)
      markLine(l, bottom - l + 1);
    else
      markLine(top, l - top + 1);

    display.updateScrollBar();
  }

  /**
   * Delete a line at a specific position. Subsequent lines will be scrolled
   * up to fill the space and a blank line is inserted at the end of the
   * screen.
   * @param l the y-coordinate to insert the line
   * @see #deleteLine
   */
  public void deleteLine(int l) {
    int bottom = (l > bottomMargin ? height - 1:
            (l < topMargin?topMargin:bottomMargin + 1));
    int numRows = bottom - l - 1;

    char[] discardedChars = charArray[screenBase + l];
    int[] discardedAttributes = charAttributes[screenBase + l];

    if (numRows > 0) {
	    System.arraycopy(charArray, screenBase + l + 1,
	                     charArray, screenBase + l, numRows);
	    System.arraycopy(charAttributes, screenBase + l + 1,
	                     charAttributes, screenBase + l, numRows);
    }

    int newBottomRow = screenBase + bottom - 1;
    charArray[newBottomRow] = discardedChars;
    charAttributes[newBottomRow] = discardedAttributes;
    Arrays.fill(charArray[newBottomRow], ' ');
    Arrays.fill(charAttributes[newBottomRow], 0);

    markLine(l, bottom - l);
  }

  /**
   * Delete a rectangular portion of the screen.
   * You need to call redraw() to update the screen.
   * @param c x-coordinate (column)
   * @param l y-coordinate (row)
   * @param w with of the area in characters
   * @param h height of the area in characters
   * @param curAttr attribute to fill
   * @see #deleteChar
   * @see #deleteLine
   * @see #redraw
   */
  public void deleteArea(int c, int l, int w, int h, int curAttr) {
    int endColumn = c + w;
    int targetRow = screenBase + l;
    for (int i = 0; i < h && l + i < height; i++) {
      Arrays.fill(charAttributes[targetRow], c, endColumn, curAttr);
      Arrays.fill(charArray[targetRow], c, endColumn, ' ');
      targetRow++;
    }
    markLine(l, h);
  }

  /**
   * Delete a rectangular portion of the screen.
   * You need to call redraw() to update the screen.
   * @param c x-coordinate (column)
   * @param l y-coordinate (row)
   * @param w with of the area in characters
   * @param h height of the area in characters
   * @see #deleteChar
   * @see #deleteLine
   * @see #redraw
   */
  public void deleteArea(int c, int l, int w, int h) {
    deleteArea(c, l, w, h, 0);
  }

  /**
   * Sets whether the cursor is visible or not.
   * @param doshow
   */
  public void showCursor(boolean doshow) {
    showcursor = doshow;
  }

  /**
   * Check whether the cursor is currently visible.
   * @return visibility
   */
  public boolean isCursorVisible() {
    return showcursor;
  }

  /**
   * Puts the cursor at the specified position.
   * @param c column
   * @param l line
   */
  public void setCursorPosition(int c, int l) {
    cursorX = c;
    cursorY = l;
  }

  /**
   * Get the current column of the cursor position.
   */
  public int getCursorColumn() {
    return cursorX;
  }

  /**
   * Get the current line of the cursor position.
   */
  public int getCursorRow() {
    return cursorY;
  }

  /**
   * Set the current window base. This allows to view the scrollback buffer.
   * @param line the line where the screen window starts
   * @see #setBufferSize
   * @see #getBufferSize
   */
  public void setWindowBase(int line) {
    if (line > screenBase)
      line = screenBase;
    else if (line < 0) line = 0;
    windowBase = line;
    update[0] = true;
    redraw();
  }

  /**
   * Get the current window base.
   * @see #setWindowBase
   */
  public int getWindowBase() {
    return windowBase;
  }

  /**
   * Set the scroll margins simultaneously.  If they're out of bounds, trim them.
   * @param l1 line that is the top
   * @param l2 line that is the bottom
   */
  public void setMargins(int l1, int l2) {
    if (l1 > l2)
      return;

    if (l1 < 0)
      l1 = 0;
    if (l2 >= height)
      l2 = height - 1;

    topMargin = l1;
    bottomMargin = l2;
  }

  /**
   * Set the top scroll margin for the screen. If the current bottom margin
   * is smaller it will become the top margin and the line will become the
   * bottom margin.
   * @param l line that is the margin
   */
  public void setTopMargin(int l) {
    if (l > bottomMargin) {
      topMargin = bottomMargin;
      bottomMargin = l;
    } else
      topMargin = l;
    if (topMargin < 0) topMargin = 0;
    if (bottomMargin >= height) bottomMargin = height - 1;
  }

  /**
   * Get the top scroll margin.
   */
  public int getTopMargin() {
    return topMargin;
  }

  /**
   * Set the bottom scroll margin for the screen. If the current top margin
   * is bigger it will become the bottom margin and the line will become the
   * top margin.
   * @param l line that is the margin
   */
  public void setBottomMargin(int l) {
    if (l < topMargin) {
      bottomMargin = topMargin;
      topMargin = l;
    } else
      bottomMargin = l;
    if (topMargin < 0) topMargin = 0;
    if (bottomMargin >= height) bottomMargin = height - 1;
  }

  /**
   * Get the bottom scroll margin.
   */
  public int getBottomMargin() {
    return bottomMargin;
  }

  /**
   * Set scrollback buffer size.
   * @param amount new size of the buffer
   */
  public void setBufferSize(int amount) {
    if (amount < height) amount = height;
    if (amount < maxBufSize) {
      char cbuf[][] = new char[amount][width];
      int abuf[][] = new int[amount][width];
      int copyStart = bufSize - amount < 0 ? 0 : bufSize - amount;
      int copyCount = bufSize - amount < 0 ? bufSize : amount;
      if (charArray != null)
        System.arraycopy(charArray, copyStart, cbuf, 0, copyCount);
      if (charAttributes != null)
        System.arraycopy(charAttributes, copyStart, abuf, 0, copyCount);
      charArray = cbuf;
      charAttributes = abuf;
      bufSize = copyCount;
      screenBase = bufSize - height;
      windowBase = screenBase;
    }
    maxBufSize = amount;

    update[0] = true;
    redraw();
  }

  /**
   * Retrieve current scrollback buffer size.
   * @see #setBufferSize
   */
  public int getBufferSize() {
    return bufSize;
  }

  /**
   * Retrieve maximum buffer Size.
   * @see #getBufferSize
   */
  public int getMaxBufferSize() {
    return maxBufSize;
  }

  /**
   * Change the size of the screen. This will include adjustment of the
   * scrollback buffer.
   * @param w of the screen
   * @param h of the screen
   */
  public void setScreenSize(int w, int h, boolean broadcast) {
    char cbuf[][];
    int abuf[][];
    int maxSize = bufSize;

    if (w < 1 || h < 1) return;

    if (debug > 0)
      System.err.println("VDU: screen size [" + w + "," + h + "]");

    if (h > maxBufSize)
      maxBufSize = h;

    if (h > bufSize) {
      bufSize = h;
      screenBase = 0;
      windowBase = 0;
    }

    if (windowBase + h >= bufSize)
      windowBase = bufSize - h;

    if (screenBase + h >= bufSize)
      screenBase = bufSize - h;


    cbuf = new char[bufSize][w];
    abuf = new int[bufSize][w];


    for (int i = 0; i < bufSize; i++) {
      Arrays.fill(cbuf[i], ' ');
    }

    if (bufSize < maxSize)
      maxSize = bufSize;

    int rowLength;
    if (charArray != null && charAttributes != null) {
      for (int i = 0; i < maxSize && charArray[i] != null; i++) {
        rowLength = charArray[i].length;
        System.arraycopy(charArray[i], 0, cbuf[i], 0,
                         w < rowLength ? w : rowLength);
        System.arraycopy(charAttributes[i], 0, abuf[i], 0,
                         w < rowLength ? w : rowLength);
      }
    }

    int C = getCursorColumn();
    if (C < 0)
      C = 0;
    else if (C >= width)
      C = width - 1;

    int R = getCursorRow();
    if (R < 0)
      R = 0;
    else if (R >= height)
      R = height - 1;

    setCursorPosition(C, R);

    charArray = cbuf;
    charAttributes = abuf;
    width = w;
    height = h;
    topMargin = 0;
    bottomMargin = h - 1;
    update = new boolean[h + 1];
    update[0] = true;
    /*  FIXME: ???
    if(resizeStrategy == RESIZE_FONT)
      setBounds(getBounds());
    */
  }

  /**
   * Get amount of rows on the screen.
   */
  public int getRows() {
    return height;
  }

  /**
   * Get amount of columns on the screen.
   */
  public int getColumns() {
    return width;
  }

  /**
   * Mark lines to be updated with redraw().
   * @param l starting line
   * @param n amount of lines to be updated
   * @see #redraw
   */
  public void markLine(int l, int n) {
    for (int i = 0; (i < n) && (l + i < height); i++)
      update[l + i + 1] = true;
  }

//  private static int checkBounds(int value, int lower, int upper) {
//    if (value < lower)
//      return lower;
//    else if (value > upper)
//      return upper;
//    else
//      return value;
//  }

  /** a generic display that should redraw on demand */
  protected VDUDisplay display;

  public void setDisplay(VDUDisplay display) {
    this.display = display;
  }

  /**
   * Trigger a redraw on the display.
   */
  protected void redraw() {
    if (display != null)
      display.redraw();
  }
}
