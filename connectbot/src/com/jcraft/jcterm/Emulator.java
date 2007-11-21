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
import java.io.IOException;

public abstract class Emulator{
  Term term=null;
  InputStream in=null;

  public Emulator(Term term, InputStream in){
    this.term=term;
    this.in=in;
  }

  public abstract void start();

  public abstract byte[] getCodeENTER();

  public abstract byte[] getCodeUP();

  public abstract byte[] getCodeDOWN();

  public abstract byte[] getCodeRIGHT();

  public abstract byte[] getCodeLEFT();

  public abstract byte[] getCodeF1();

  public abstract byte[] getCodeF2();

  public abstract byte[] getCodeF3();

  public abstract byte[] getCodeF4();

  public abstract byte[] getCodeF5();

  public abstract byte[] getCodeF6();

  public abstract byte[] getCodeF7();

  public abstract byte[] getCodeF8();

  public abstract byte[] getCodeF9();

  public abstract byte[] getCodeF10();

  public abstract byte[] getCodeTAB();

  public void reset(){
    term_width=term.getColumnCount();
    term_height=term.getRowCount();
    char_width=term.getCharWidth();
    char_height=term.getCharHeight();
    region_y1=1;
    region_y2=term_height;
  }

  byte[] buf=new byte[1024];
  int bufs=0;
  int buflen=0;

  byte getChar() throws java.io.IOException{
    if(buflen==0){
      fillBuf();
    }
    buflen--;

    //    System.out.println("getChar: "+new Character((char)buf[bufs])+"["+Integer.toHexString(buf[bufs]&0xff)+"]");

    return buf[bufs++];
  }

  void fillBuf() throws java.io.IOException{
    buflen=bufs=0;
    buflen=in.read(buf, bufs, buf.length-bufs);
    /*
    System.out.println("fillBuf: ");
    for(int i=0; i<buflen; i++){
    byte b=buf[i];
    System.out.print(new Character((char)b)+"["+Integer.toHexString(b&0xff)+"], ");
    }
    System.out.println("");
    */
    if(buflen<=0){
      buflen=0;
      throw new IOException("fillBuf");
    }
  }

  void pushChar(byte foo) throws java.io.IOException{
    //System.out.println("pushChar: "+new Character((char)foo)+"["+Integer.toHexString(foo&0xff)+"]");
    buflen++;
    buf[--bufs]=foo;
  }

  int getASCII(int len) throws java.io.IOException{
    //System.out.println("bufs="+bufs+", buflen="+buflen+", len="+len);
    if(buflen==0){
      fillBuf();
    }
    if(len>buflen)
      len=buflen;
    int foo=len;
    byte tmp;
    while(len>0){
      tmp=buf[bufs++];
      if(0x20<=tmp&&tmp<=0x7f){
        buflen--;
        len--;
        continue;
      }
      bufs--;
      break;
    }
    //System.out.println(" return "+(foo-len));
    return foo-len;
  }

  protected int term_width=80;
  protected int term_height=24;

  protected int x=0;
  protected int y=0;

  protected int char_width;
  protected int char_height;

  private int region_y2;
  private int region_y1;

  protected int tab=8;

  // Reverse scroll
  protected void scroll_reverse(){
    term.draw_cursor();
    term.scroll_area(0, (region_y1-1)*char_height, term_width*char_width,
        (region_y2-region_y1)*char_height, 0, char_height);
    term.clear_area(x, y-char_height, term_width*char_width, y);
    term.redraw(0, 0, term_width*char_width, term_height*char_height
        -char_height);
    //term.setCursor(x, y);
    term.draw_cursor();
  }

  // Normal scroll one line
  protected void scroll_forward(){
    term.draw_cursor();
    term.scroll_area(0, (region_y1-1)*char_height, term_width*char_width,
        (region_y2-region_y1+1)*char_height, 0, -char_height);
    term.clear_area(0, region_y2*char_height-char_height,
        term_width*char_width, region_y2*char_height);
    term.redraw(0, (region_y1-1)*char_height, term_width*char_width, (region_y2
        -region_y1+1)
        *char_height);
    term.draw_cursor();
  }

  // Save cursor position
  protected void save_cursor(){
    // TODO
    //System.out.println("save current cursor position");
  }

  // Enable alternate character set
  protected void ena_acs(){
    // TODO
    //System.out.println("enable alterate char set");
  }

  protected void exit_alt_charset_mode(){
    // TODO
    //System.out.println("end alternate character set (P)");
  }

  protected void enter_alt_charset_mode(){
    // TODO
    //System.out.println("start alternate character set (P)");
  }

  protected void reset_2string(){
    // TODO
    // rs2(reset string)
  }

  protected void exit_attribute_mode(){
    // TODO
    //System.out.println("turn off all attributes");
    term.resetAllAttributes();
  }

  protected void exit_standout_mode(){
    term.resetAllAttributes();
  }

  protected void exit_underline_mode(){
    // TODO
  }

  protected void enter_bold_mode(){
    term.setBold();
  }

  protected void enter_underline_mode(){
    term.setUnderline();
  }

  protected void enter_reverse_mode(){
    term.setReverse();
  }

  protected void change_scroll_region(int y1, int y2){
    region_y1=y1;
    region_y2=y2;
  }

  protected void cursor_address(int r, int c){
    term.draw_cursor();
    x=(c-1)*char_width;
    y=r*char_height;
    //System.out.println("setCourosr: "+x+" "+y);
    term.setCursor(x, y);
    term.draw_cursor();
  }

  protected void parm_down_cursor(int lines){
    term.draw_cursor();
    y+=(lines)*char_height;
    term.setCursor(x, y);
    term.draw_cursor();
  }

  protected void parm_left_cursor(int chars){
    term.draw_cursor();
    x-=(chars)*char_width;
    term.setCursor(x, y);
    term.draw_cursor();
  }

  protected void parm_right_cursor(int chars){
    term.draw_cursor();
    x+=(chars)*char_width;
    term.setCursor(x, y);
    term.draw_cursor();
  }

  protected void clr_eol(){
    term.draw_cursor();
    term.clear_area(x, y-char_height, term_width*char_width, y);
    term.redraw(x, y-char_height, (term_width)*char_width-x, char_height);
    term.draw_cursor();
  }

  protected void clr_bol(){
    term.draw_cursor();
    term.clear_area(0, y-char_height, x, y);
    term.redraw(0, y-char_height, x, char_height);
    term.draw_cursor();
  }

  protected void clr_eos(){
    term.draw_cursor();
    term.clear_area(x, y-char_height, term_width*char_width, term_height
        *char_height);
    term.redraw(x, y-char_height, term_width*char_width-x, term_height
        *char_height-y+char_height);
    term.draw_cursor();
  }

  protected void parm_up_cursor(int lines){
    term.draw_cursor();
    //	  x=0;
    //	  y-=char_height;
    y-=(lines)*char_height;
    term.setCursor(x, y);
    term.draw_cursor();
  }

  protected void bell(){
    term.beep();
  }

  protected void tab(){
    term.draw_cursor();
    x=(((x/char_width)/tab+1)*tab*char_width);
    if(x>=term_width*char_width){
      x=0;
      y+=char_height;
    }
    term.setCursor(x, y);
    term.draw_cursor();
  }

  protected void carriage_return(){
    term.draw_cursor();
    x=0;
    term.setCursor(x, y);
    term.draw_cursor();
  }

  protected void cursor_left(){
    term.draw_cursor();
    x-=char_width;
    if(x<0){
      y-=char_height;
      x=term_width*char_width-char_width;
    }
    term.setCursor(x, y);
    term.draw_cursor();
  }

  protected void cursor_down(){
    term.draw_cursor();
    y+=char_height;
    term.setCursor(x, y);
    term.draw_cursor();

    check_region();
  }

  private byte[] b2=new byte[2];
  private byte[] b1=new byte[1];

  protected void draw_text() throws java.io.IOException{

    int rx;
    int ry;
    int w;
    int h;

    check_region();

    rx=x;
    ry=y;

    byte b=getChar();
    term.draw_cursor();
    //System.out.print(new Character((char)b)+"["+Integer.toHexString(b&0xff)+"]");
    if((b&0x80)!=0){
      term.clear_area(x, y-char_height, x+char_width*2, y);
      b2[0]=b;
      b2[1]=getChar();
      term.drawString(new String(b2, 0, 2, "EUC-JP"), x, y);
      x+=char_width;
      x+=char_width;
      w=char_width*2;
      h=char_height;
    }
    else{
      pushChar(b);
      int foo=getASCII(term_width-(x/char_width));
      if(foo!=0){
        //System.out.println("foo="+foo+" "+x+", "+(y-char_height)+" "+(x+foo*char_width)+" "+y+" "+buf+" "+bufs+" "+b+" "+buf[bufs-foo]);
        //System.out.println("foo="+foo+" ["+new String(buf, bufs-foo, foo));
        term.clear_area(x, y-char_height, x+foo*char_width, y);
        term.drawBytes(buf, bufs-foo, foo, x, y);
      }
      else{
        foo=1;
        term.clear_area(x, y-char_height, x+foo*char_width, y);
        b1[0]=getChar();
        term.drawBytes(b1, 0, foo, x, y);
        //System.out.print("["+Integer.toHexString(bar[0]&0xff)+"]");
      }
      x+=(char_width*foo);
      w=char_width*foo;
      h=char_height;
    }
    term.redraw(rx, ry-char_height, w, h);
    term.setCursor(x, y);
    term.draw_cursor();
  }

  private void check_region(){
    if(x>=term_width*char_width){
      //System.out.println("!! "+new Character((char)b)+"["+Integer.toHexString(b&0xff)+"]");
      x=0;
      y+=char_height;
      //System.out.println("@1: ry="+ry);
    }

    if(y>region_y2*char_height){
      while(y>region_y2*char_height){
        y-=char_height;
      }
      term.draw_cursor();
      term.scroll_area(0, region_y1*char_height, term_width*char_width,
          (region_y2-region_y1)*char_height, 0, -char_height);
      term.clear_area(0, y-char_height, term_width*char_width, y);
      term.redraw(0, 0, term_width*char_width, region_y2*char_height);
      term.setCursor(x, y);
      term.draw_cursor();
    }
  }
}
