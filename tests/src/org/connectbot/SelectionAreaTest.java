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
package org.connectbot;

import org.connectbot.bean.SelectionArea;

import android.test.AndroidTestCase;

/**
 * @author Kenny Root
 * 
 */
public class SelectionAreaTest extends AndroidTestCase {
	private static final int WIDTH = 80;
	private static final int HEIGHT = 24;

	public void testCreate() {
		SelectionArea sa = new SelectionArea();

		assertTrue(sa.getLeft() == 0);
		assertTrue(sa.getRight() == 0);
		assertTrue(sa.getTop() == 0);
		assertTrue(sa.getBottom() == 0);
		assertTrue(sa.isSelectingOrigin());
	}

	public void testCheckMovement() {
		SelectionArea sa = new SelectionArea();

		sa.setBounds(WIDTH, HEIGHT);

		sa.incrementColumn();

		// Should be (1,0) to (1,0)
		assertTrue(sa.getLeft() == 1);
		assertTrue(sa.getTop() == 0);
		assertTrue(sa.getRight() == 1);
		assertTrue(sa.getBottom() == 0);

		sa.finishSelectingOrigin();
		assertFalse(sa.isSelectingOrigin());

		sa.incrementColumn();
		sa.incrementColumn();

		// Should be (1,0) to (3,0)
		assertTrue(sa.getLeft() == 1);
		assertTrue(sa.getTop() == 0);
		assertTrue(sa.getRight() == 3);
		assertTrue(sa.getBottom() == 0);
	}

	public void testBounds() {
		SelectionArea sa = new SelectionArea();

		sa.setBounds(WIDTH, HEIGHT);

		for (int i = 0; i <= WIDTH; i++)
			sa.decrementColumn();
		assertTrue("Left bound should be 0, but instead is " + sa.getLeft(),
				sa.getLeft() == 0);

		for (int i = 0; i <= HEIGHT; i++)
			sa.decrementRow();
		assertTrue("Top bound should be 0, but instead is " + sa.getLeft(),
				sa.getTop() == 0);

		sa.finishSelectingOrigin();

		for (int i = 0; i <= WIDTH * 2; i++)
			sa.incrementColumn();

		assertTrue("Left bound should be 0, but instead is " + sa.getLeft(),
				sa.getLeft() == 0);
		assertTrue("Right bound should be " + (WIDTH - 1) + ", but instead is " + sa.getRight(),
				sa.getRight() == (WIDTH - 1));

		for (int i = 0; i <= HEIGHT * 2; i++)
			sa.incrementRow();

		assertTrue("Bottom bound should be " + (HEIGHT - 1) + ", but instead is " + sa.getBottom(),
				sa.getBottom() == (HEIGHT - 1));
		assertTrue("Top bound should be 0, but instead is " + sa.getTop(),
				sa.getTop() == 0);
	}

	public void testSetThenMove() {
		SelectionArea sa = new SelectionArea();

		sa.setBounds(WIDTH, HEIGHT);

		int targetColumn = WIDTH / 2;
		int targetRow = HEIGHT / 2;

		sa.setColumn(targetColumn);
		sa.setRow(targetRow);

		sa.incrementRow();
		assertTrue("Row should be " + (targetRow + 1) + ", but instead is " + sa.getTop(),
				sa.getTop() == (targetRow + 1));

		sa.decrementColumn();
		assertTrue("Column shold be " + (targetColumn - 1) + ", but instead is " + sa.getLeft(), 
				sa.getLeft() == (targetColumn - 1));

		sa.finishSelectingOrigin();

		sa.setRow(0);
		sa.setColumn(0);

		sa.incrementRow();
		sa.decrementColumn();

		assertTrue("Top row should be 1, but instead is " + sa.getTop(),
				sa.getTop() == 1);

		assertTrue("Left column shold be 0, but instead is " + sa.getLeft(), 
				sa.getLeft() == 0);

		assertTrue("Bottom row should be " + (targetRow + 1) + ", but instead is " + sa.getBottom(),
				sa.getBottom() == (targetRow + 1));

		assertTrue("Right column shold be " + (targetColumn - 1) + ", but instead is " + sa.getRight(), 
				sa.getRight() == (targetColumn - 1));
	}
}
