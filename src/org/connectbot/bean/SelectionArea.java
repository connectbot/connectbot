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

	public final void reset() {
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

	public void decrementRow() {
		if (selectingOrigin)
			setTop(top - 1);
		else
			setBottom(bottom - 1);
	}

	public void incrementRow() {
		if (selectingOrigin)
			setTop(top + 1);
		else
			setBottom(bottom + 1);
	}

	public void setRow(int row) {
		if (selectingOrigin)
			setTop(row);
		else
			setBottom(row);
	}

	private void setTop(int top) {
		this.top = bottom = checkBounds(top, maxRows);
	}

	public int getTop() {
		return Math.min(top, bottom);
	}

	private void setBottom(int bottom) {
		this.bottom = checkBounds(bottom, maxRows);
	}

	public int getBottom() {
		return Math.max(top, bottom);
	}

	public void decrementColumn() {
		if (selectingOrigin)
			setLeft(left - 1);
		else
			setRight(right - 1);
	}

	public void incrementColumn() {
		if (selectingOrigin)
			setLeft(left + 1);
		else
			setRight(right + 1);
	}

	public void setColumn(int column) {
		if (selectingOrigin)
			setLeft(column);
		else
			setRight(column);
	}

	private void setLeft(int left) {
		this.left = right = checkBounds(left, maxColumns);
	}

	public int getLeft() {
		return Math.min(left, right);
	}

	private void setRight(int right) {
		this.right = checkBounds(right, maxColumns);
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

	@Override
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
