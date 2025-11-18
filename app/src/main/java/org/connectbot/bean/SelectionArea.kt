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
package org.connectbot.bean

import de.mud.terminal.VDUBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * @author Kenny Root
 * Keep track of a selection area for the terminal copying mechanism.
 * If the orientation is flipped one way, swap the bottom and top or
 * left and right to keep it in the correct orientation.
 */
class SelectionArea {
    private var top = 0
    private var bottom = 0
    private var left = 0
    private var right = 0
    private var maxColumns = 0
    private var maxRows = 0
    var isSelectingOrigin: Boolean = false
        private set

    init {
        reset()
    }

    fun reset() {
        right = 0
        bottom = right
        left = bottom
        top = left
        this.isSelectingOrigin = true
    }

    /**
     * @param columns
     * @param rows
     */
    fun setBounds(columns: Int, rows: Int) {
        maxColumns = columns - 1
        maxRows = rows - 1
    }

    private fun checkBounds(value: Int, max: Int): Int {
        if (value < 0) return 0
        else if (value > max) return max
        else return value
    }

    fun finishSelectingOrigin() {
        this.isSelectingOrigin = false
    }

    fun decrementRow() {
        if (this.isSelectingOrigin) setTop(top - 1)
        else setBottom(bottom - 1)
    }

    fun incrementRow() {
        if (this.isSelectingOrigin) setTop(top + 1)
        else setBottom(bottom + 1)
    }

    fun setRow(row: Int) {
        if (this.isSelectingOrigin) setTop(row)
        else setBottom(row)
    }

    private fun setTop(top: Int) {
        bottom = checkBounds(top, maxRows)
        this.top = bottom
    }

    fun getTop(): Int {
        return min(top, bottom)
    }

    private fun setBottom(bottom: Int) {
        this.bottom = checkBounds(bottom, maxRows)
    }

    fun getBottom(): Int {
        return max(top, bottom)
    }

    fun decrementColumn() {
        if (this.isSelectingOrigin) setLeft(left - 1)
        else setRight(right - 1)
    }

    fun incrementColumn() {
        if (this.isSelectingOrigin) setLeft(left + 1)
        else setRight(right + 1)
    }

    fun setColumn(column: Int) {
        if (this.isSelectingOrigin) setLeft(column)
        else setRight(column)
    }

    private fun setLeft(left: Int) {
        right = checkBounds(left, maxColumns)
        this.left = right
    }

    fun getLeft(): Int {
        return min(left, right)
    }

    private fun setRight(right: Int) {
        this.right = checkBounds(right, maxColumns)
    }

    fun getRight(): Int {
        return max(left, right)
    }

    fun copyFrom(vb: VDUBuffer): String {
        val size = (getRight() - getLeft() + 1) * (getBottom() - getTop() + 1)

        val buffer = StringBuilder(size)

        for (y in getTop()..getBottom()) {
            var lastNonSpace = buffer.length

            for (x in getLeft()..getRight()) {
                // only copy printable chars
                var c = vb.getChar(x, y)

                if (!Character.isDefined(c) ||
                    (Character.isISOControl(c) && c != '\t')
                ) c = ' '

                if (c != ' ') lastNonSpace = buffer.length

                buffer.append(c)
            }

            // Don't leave a bunch of spaces in our copy buffer.
            if (buffer.length > lastNonSpace) buffer.delete(lastNonSpace + 1, buffer.length)

            if (y != bottom) buffer.append('\n')
        }

        return buffer.toString()
    }

    override fun toString(): String {
        val buffer = StringBuilder()

        buffer.append("SelectionArea[top=")
        buffer.append(top)
        buffer.append(", bottom=")
        buffer.append(bottom)
        buffer.append(", left=")
        buffer.append(left)
        buffer.append(", right=")
        buffer.append(right)
        buffer.append(", maxColumns=")
        buffer.append(maxColumns)
        buffer.append(", maxRows=")
        buffer.append(maxRows)
        buffer.append(", isSelectingOrigin=")
        buffer.append(this.isSelectingOrigin)
        buffer.append(']')

        return buffer.toString()
    }
}
