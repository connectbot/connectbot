/* -*-mode:java; c-basic-offset:2; -*- */
/* JCTerm
 * Copyright (C) 2002,2007 ymnk, JCraft,Inc.
 *  
 * Written by: ymnk<ymnk@jcaft.com>
 *   
 *   
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
   
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 * 
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package com.jcraft.jcterm;

import java.io.InputStream;
import java.io.OutputStream;

public interface Term{

  void start(InputStream in, OutputStream out);

  int getRowCount();

  int getColumnCount();

  int getCharWidth();

  int getCharHeight();

  void setCursor(int x, int y);

  void clear();

  void draw_cursor();

  void redraw(int x, int y, int width, int height);

  //void redraw();
  void clear_area(int x1, int y1, int x2, int y2);

  void scroll_area(int x, int y, int w, int h, int dx, int dy);

  void drawBytes(byte[] buf, int s, int len, int x, int y);

  void drawString(String str, int x, int y);

  void beep();

  void setDefaultForeGround(Object foreground);

  void setDefaultBackGround(Object background);

  void setForeGround(Object foreground);

  void setBackGround(Object background);

  void setBold();

  void setUnderline();

  void setReverse();

  void resetAllAttributes();

  int getTermWidth();

  int getTermHeight();

  Object getColor(int index);
}
