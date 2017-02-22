/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

package org.connectbot;

import org.connectbot.bean.SelectionArea;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Kenny Root
 * 
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class SelectionAreaTest {
	private static final int WIDTH = 80;
	private static final int HEIGHT = 24;

	@Test
	public void createSelectionArea() {
		SelectionArea sa = new SelectionArea();

		assertTrue(sa.getLeft() == 0);
		assertTrue(sa.getRight() == 0);
		assertTrue(sa.getTop() == 0);
		assertTrue(sa.getBottom() == 0);
		assertTrue(sa.isSelectingOrigin());
	}

	@Test
	public void checkMovement() {
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

	@Test
	public void boundsAreCorrect() {
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

	@Test
	public void setThenMove() {
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
