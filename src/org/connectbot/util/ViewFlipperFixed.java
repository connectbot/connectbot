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
package org.connectbot.util;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ViewFlipper;

/**
 * @author Kenny Root
 *
 * This class simply overrides ViewFlipper until a fix can be released
 * for tracking removal of views from a ViewAnimator.
 * 
 * REMOVE THIS CLASS WHEN ViewAnimator IN ANDROID IS FIXED TO TRACK
 * REMOVAL OF CHILD VIEWS! See also res/layout/act_console.xml
 */
public class ViewFlipperFixed extends ViewFlipper {

	/**
	 * @param context
	 */
	public ViewFlipperFixed(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param context
	 * @param attrs
	 */
	public ViewFlipperFixed(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param index child index
	 */
	@Override
	public void removeViewAt(int index) {
		// TODO Auto-generated method stub
		super.removeViewAt(index);
		
		// Since we can't override removeViewInternal, we might as well do this.
		if (getDisplayedChild() >= getChildCount())
			setDisplayedChild(index - 1);
	}
}
