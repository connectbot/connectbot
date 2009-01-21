/*
	ConnectBot: simple, powerful, open-source SSH client for Android
	Copyright (C) 2007-2008 Kenny Root, Jeffrey Sharkey
	
	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	
	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.
	
	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.connectbot.bean;

import de.mud.terminal.VDUBuffer;

/**
 * @author Kenny Root
 * Keep track of a selection area for the terminal copying mechanism.
 * If the orientation is flipped one way, swap the bottom and top or
 * left and right to keep it in the correct orientation.
 */
public class SelectionArea {
	private int top;
	private int bottom;
	private int left;
	private int right;
	private int maxColumns;
	private int maxRows;
	private boolean selectingOrigin;
	
	public SelectionArea() {
		reset();
	}
	
	public void reset() {
		top = left = bottom = right = 0;
		selectingOrigin = true;
	}
	
	/**
	 * @param columns
	 * @param rows
	 */
	public void setBounds(int columns, int rows) {
		maxColumns = columns - 1;
		maxRows = rows - 1;
	}
	
	private int checkBounds(int value, int max) {
		if (value < 0)
			return 0;
		else if (value > max)
			return max;
		else
			return value;
	}
	
	public boolean isSelectingOrigin() {
		return selectingOrigin;
	}
	
	public void finishSelectingOrigin() {
		selectingOrigin = false;
	}
	
	public void setTop(int top) {
		this.top = bottom = checkBounds(top, maxRows);
	}
	
	public void decrementTop() {
		setTop(--top);
	}
	
	public void incrementTop() {
		setTop(++top);
	}
	
	public int getTop() {
		return Math.min(top, bottom);
	}
	
	public void setBottom(int bottom) {
		this.bottom = checkBounds(bottom, maxRows);
	}
	
	public void decrementBottom() {
		setBottom(--bottom);
	}
	
	public void incrementBottom() {
		setBottom(++bottom);
	}
	
	public int getBottom() {
		return Math.max(top, bottom);
	}
	
	public void setLeft(int left) {
		this.left = right = checkBounds(left, maxColumns);
	}
	
	public void decrementLeft() {
		setLeft(--left);
	}
	
	public void incrementLeft() {
		setLeft(++left);
	}
	
	public int getLeft() {
		return Math.min(left, right);
	}
	
	public void setRight(int right) {
		this.right = checkBounds(right, maxColumns);
	}
	
	public void decrementRight() {
		setRight(--right);
	}
	
	public void incrementRight() {
		setRight(++right);
	}
	
	public int getRight() {
		return Math.max(left, right);
	}
	
	public String copyFrom(VDUBuffer vb) {
		int size = (getRight() - getLeft() + 1) * (getBottom() - getTop() + 1);
		
		StringBuffer buffer = new StringBuffer(size);
		
		for(int y = getTop(); y <= getBottom(); y++) {
			int lastNonSpace = buffer.length();
			
			for (int x = getLeft(); x <= getRight(); x++) {
				// only copy printable chars
				char c = vb.getChar(x, y);
				
				if (!Character.isDefined(c) ||
						(Character.isISOControl(c) && c != '\t'))
					c = ' ';
				
				if (c != ' ')
					lastNonSpace = buffer.length();
				
				buffer.append(c);
			}
			
			// Don't leave a bunch of spaces in our copy buffer.
			if (buffer.length() > lastNonSpace)
				buffer.delete(lastNonSpace + 1, buffer.length());
			
			if (y != bottom)
				buffer.append("\n");
		}
		
		return buffer.toString();
	}
	
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		
		buffer.append("SelectionArea[top=");
		buffer.append(top);
		buffer.append(", bottom=");
		buffer.append(bottom);
		buffer.append(", left=");
		buffer.append(left);
		buffer.append(", right=");
		buffer.append(right);
		buffer.append(", maxColumns=");
		buffer.append(maxColumns);
		buffer.append(", maxRows=");
		buffer.append(maxRows);
		buffer.append(", isSelectingOrigin=");
		buffer.append(isSelectingOrigin());
		buffer.append("]");
		
		return buffer.toString();
	}
}
