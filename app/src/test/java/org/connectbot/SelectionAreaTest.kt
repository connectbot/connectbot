/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root
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
package org.connectbot

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.connectbot.bean.SelectionArea
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Kenny Root
 */
@RunWith(AndroidJUnit4::class)
class SelectionAreaTest {
    @Test
    fun createSelectionArea() {
        val sa = SelectionArea()

        Assert.assertTrue(sa.getLeft() == 0)
        Assert.assertTrue(sa.getRight() == 0)
        Assert.assertTrue(sa.getTop() == 0)
        Assert.assertTrue(sa.getBottom() == 0)
        Assert.assertTrue(sa.isSelectingOrigin)
    }

    @Test
    fun checkMovement() {
        val sa = SelectionArea()

        sa.setBounds(WIDTH, HEIGHT)

        sa.incrementColumn()

        // Should be (1,0) to (1,0)
        Assert.assertTrue(sa.getLeft() == 1)
        Assert.assertTrue(sa.getTop() == 0)
        Assert.assertTrue(sa.getRight() == 1)
        Assert.assertTrue(sa.getBottom() == 0)

        sa.finishSelectingOrigin()
        Assert.assertFalse(sa.isSelectingOrigin)

        sa.incrementColumn()
        sa.incrementColumn()

        // Should be (1,0) to (3,0)
        Assert.assertTrue(sa.getLeft() == 1)
        Assert.assertTrue(sa.getTop() == 0)
        Assert.assertTrue(sa.getRight() == 3)
        Assert.assertTrue(sa.getBottom() == 0)
    }

    @Test
    fun boundsAreCorrect() {
        val sa = SelectionArea()

        sa.setBounds(WIDTH, HEIGHT)

        for (i in 0..WIDTH) sa.decrementColumn()
        Assert.assertTrue(
            "Left bound should be 0, but instead is " + sa.getLeft(),
            sa.getLeft() == 0
        )

        for (i in 0..HEIGHT) sa.decrementRow()
        Assert.assertTrue(
            "Top bound should be 0, but instead is " + sa.getLeft(),
            sa.getTop() == 0
        )

        sa.finishSelectingOrigin()

        for (i in 0..WIDTH * 2) sa.incrementColumn()

        Assert.assertTrue(
            "Left bound should be 0, but instead is " + sa.getLeft(),
            sa.getLeft() == 0
        )
        Assert.assertTrue(
            "Right bound should be " + (WIDTH - 1) + ", but instead is " + sa.getRight(),
            sa.getRight() == (WIDTH - 1)
        )

        for (i in 0..HEIGHT * 2) sa.incrementRow()

        Assert.assertTrue(
            "Bottom bound should be " + (HEIGHT - 1) + ", but instead is " + sa.getBottom(),
            sa.getBottom() == (HEIGHT - 1)
        )
        Assert.assertTrue(
            "Top bound should be 0, but instead is " + sa.getTop(),
            sa.getTop() == 0
        )
    }

    @Test
    fun setThenMove() {
        val sa = SelectionArea()

        sa.setBounds(WIDTH, HEIGHT)

        val targetColumn: Int = WIDTH / 2
        val targetRow: Int = HEIGHT / 2

        sa.setColumn(targetColumn)
        sa.setRow(targetRow)

        sa.incrementRow()
        Assert.assertTrue(
            "Row should be " + (targetRow + 1) + ", but instead is " + sa.getTop(),
            sa.getTop() == (targetRow + 1)
        )

        sa.decrementColumn()
        Assert.assertTrue(
            "Column shold be " + (targetColumn - 1) + ", but instead is " + sa.getLeft(),
            sa.getLeft() == (targetColumn - 1)
        )

        sa.finishSelectingOrigin()

        sa.setRow(0)
        sa.setColumn(0)

        sa.incrementRow()
        sa.decrementColumn()

        Assert.assertTrue(
            "Top row should be 1, but instead is " + sa.getTop(),
            sa.getTop() == 1
        )

        Assert.assertTrue(
            "Left column shold be 0, but instead is " + sa.getLeft(),
            sa.getLeft() == 0
        )

        Assert.assertTrue(
            "Bottom row should be " + (targetRow + 1) + ", but instead is " + sa.getBottom(),
            sa.getBottom() == (targetRow + 1)
        )

        Assert.assertTrue(
            "Right column shold be " + (targetColumn - 1) + ", but instead is " + sa.getRight(),
            sa.getRight() == (targetColumn - 1)
        )
    }

    companion object {
        private const val WIDTH = 80
        private const val HEIGHT = 24
    }
}
