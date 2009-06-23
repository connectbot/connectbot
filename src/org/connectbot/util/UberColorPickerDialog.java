/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * 090408
 * Keith Wiley
 * kwiley@keithwiley.com
 * http://keithwiley.com
 *
 * UberColorPickerDialog v1.1
 *
 * This color picker was implemented as a (significant) extension of the
 * ColorPickerDialog class provided in the Android API Demos.  You are free
 * to drop it unchanged into your own projects or to modify it as you see
 * fit.  I would appreciate it if this comment block were let intact,
 * merely for credit's sake.
 *
 * Enjoy!
 */

package org.connectbot.util;

import java.util.Calendar;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

/**
 * UberColorPickerDialog is a seriously enhanced version of the UberColorPickerDialog class provided in the Android API Demos.
 * Improvements include:
 * <ul>
 * 		<li> Multiple color spaces and chooser methods (dimension combinations) for manipulating those color spaces, including:
 * 		<ul>
 * 			<li> HSV with angular H and radial S combined in 2D and a 1D V slider.
 * 			<li> HSV with angular H and radial V combined in 2D and a 1D S slider (this one's kinda silly).
 * 			<li> HSV with cardinal S and V combined in 2D and a 1D H slider.
 * 			<li> YUV with cardinal U and V combined in 2D and a 1D Y slider.
 * 			<li> RGB with three 1D sliders.
 * 			<li> HSV with three 1D sliders.
 *		</ul>
 *		<li> Simple switch-based compile-time configuration of which chooser methods are provided (search for ENABLED_METHODS near the top of the code).
 * 		<li> Numerical feedback of precise color values.
 * 		<li> Two sample swatches, one the original which can be used to revert to the initial color, the other to show the currently chosen color.
 * 		<li> Trackball input for precise color control.
 * 		<li> Automatic detection of portrait/landscape orientation and adjustment of the widget layout to make best use of that orientation.
 * 		<li> The option of showing or hiding the window title.  Showing it wastes a lot of space of course, hiding it is augmented with an introductory toast message.
 *		<li> Realtime feedback of color changes, not only in the sample swatch but also in all relevant palettes and sliders.
 *		<li> Hilighted borders to show which widget has trackball focus.
 *		<li> Position markers on the palettes and sliders to show the current value in each dimension (the value of each parameter).
 * </ul>
 * <p>
 * Version History:
 * <ul>
 * 		<li>v1.1, 090408
 * 		<ul>
 * 			<li>Added hex numerical output (HTML colors).
 * 			<li>All colorspace parameters (HSV, RGB, YUV, Hex) are now updated and shown at all times.
 * 			<li>Converted to GradientDrawable Bitmaps for drawing the 1D sliders.  They're much smoother, less blocky.  Note that 2D palettes are still constructed from the less smooth gradients.
 * 			<li>Did some general refactoring.
 * 			<li>Made the UV palette slightly more color-accurate (a little ligher and darker at extreme Y values).
 * 			<li>Added a "hilighted" border around the currently selected color chooser method.
 * 		</ul>
 * 		<li>v1.0, 090405
 * 		<ul>
 * 			<li>First public release
 * 		</ul>
 * </ul>
 *
 * @author Keith Wiley, kwiley@keithwiley.com, http://keithwiley.com
 */
public class UberColorPickerDialog extends Dialog {
	private OnColorChangedListener mListener;
	private int mInitialColor;
	private boolean mShowTitle;

	/**
	 * Callback to the creator of the dialog, informing the creator of a new color and notifying that the dialog is about to dismiss.
	 */
	public interface OnColorChangedListener {
		void colorChanged(int color);
	}

	/**
	 * Ctor
	 * @param context
	 * @param listener
	 * @param initialColor
	 * @param showTitle If true, a title is shown across the top of the dialog.  If false a toast is shown instead.
	 */
	public UberColorPickerDialog(Context context,
							OnColorChangedListener listener,
							int initialColor,
							boolean showTitle) {
		super(context);

		mListener = listener;
		mInitialColor = initialColor;
		mShowTitle = showTitle;
	}

	/**
	 * Activity entry point
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		OnColorChangedListener l = new OnColorChangedListener() {
			public void colorChanged(int color) {
				mListener.colorChanged(color);
				dismiss();
			}
		};

		DisplayMetrics dm = new DisplayMetrics();
		getWindow().getWindowManager().getDefaultDisplay().getMetrics(dm);
		int screenWidth = dm.widthPixels;
		int screenHeight = dm.heightPixels;

		if (!mShowTitle) {
			getWindow().requestFeature(Window.FEATURE_NO_TITLE);
			Toast.makeText(getContext(), "Pick a color (try the trackball)", Toast.LENGTH_SHORT).show();
		}
		else setTitle("Pick a color (try the trackball)");

		try {
			setContentView(new ColorPickerView(getContext(), l, screenWidth, screenHeight, mInitialColor));
		}
		catch (Exception e) {
			//There is currently only one kind of ctor exception, that where no methods are enabled.
			dismiss();	//This doesn't work!  The dialog is still shown (its title at least, the layout is empty from the exception being thrown).  <sigh>
		}
	}

	/**
	 * Android's Color.colorToHSV() has what I assume is a bug, such that on a desaturated color it sets H,S,V all to V.
	 * While ambiguous w.r.t. hue, saturation should certainly be 0 in such a case.  Detect and fix.
	 * @param color 4-byte ARGB
	 * @return true if fully desaturated, indicating that if this color was passed to Color.colorToHSV(), then the resulting HSV's S should be explicitly set to 0
	 */
	static public boolean isGray(int color) {
		return (((color >> 16) & 0x00000000FF) == (color & 0x000000FF)
			&& ((color >> 8) & 0x00000000FF) == (color & 0x000000FF));
	}

	/**
	 * Android's Color.colorToHSV() has what I assume is a bug, such that on a desaturated color it sets H,S,V all to V.
	 * While ambiguous w.r.t. hue, saturation should certainly be 0 in such a case.  Detect and fix.
	 * @param color 4-elm rgb of indeterminate range
	 * @return true if fully desaturated, indicating that if this color was passed to Color.colorToHSV(), then the resulting HSV's S should be explicitly set to 0
	 */
	static public boolean isGray(int[] rgb) {
		return (rgb[1] == rgb[0] && rgb[2] == rgb[0]);
	}

	/**
	 * ColorPickerView is the meat of this color picker (as opposed to the enclosing class).
	 * All the heavy lifting is done directly by this View subclass.
	 * <P>
	 * You can enable/disable whichever color chooser methods you want by modifying the ENABLED_METHODS switches.  They *should*
	 * do all the work required to properly enable/disable methods without losing track of what goes with what and what maps to what.
	 * <P>
	 * If you add a new color chooser method, do a text search for "NEW_METHOD_WORK_NEEDED_HERE".  That tag indicates all
	 * the locations in the code that will have to be amended in order to properly add a new color chooser method.
	 * I highly recommend adding new methods to the end of the list.  If you want to try to reorder the list, you're on your own.
	 */
	private static class ColorPickerView extends View {
		private static int SWATCH_WIDTH = 95;
		private static final int SWATCH_HEIGHT = 60;

		private static int PALETTE_POS_X = 0;
		private static int PALETTE_POS_Y = SWATCH_HEIGHT;
		private static final int PALETTE_DIM = SWATCH_WIDTH * 2;
		private static final int PALETTE_RADIUS = PALETTE_DIM / 2;
		private static final int PALETTE_CENTER_X = PALETTE_RADIUS;
		private static final int PALETTE_CENTER_Y = PALETTE_RADIUS;

		private static final int FIRST_HOR_SLIDER_POS_Y = 20;

		private static final int SLIDER_THICKNESS = 40;

		private static final int METHOD_SELECTOR_SIZE = 40;
		private static final int METHOD_SELECTOR_SPACING = 10;
		private static int METHOD_SELECTOR_POS_X = PALETTE_DIM + METHOD_SELECTOR_SPACING;

		private static int VIEW_DIM_X = PALETTE_DIM + METHOD_SELECTOR_SPACING + METHOD_SELECTOR_SIZE;
		private static int VIEW_DIM_Y = METHOD_SELECTOR_SIZE * 5 + METHOD_SELECTOR_SIZE * 2;

		//NEW_METHOD_WORK_NEEDED_HERE
		private static final int METHOD_HS_V_PALETTE = 0;
		/**
		 * METHOD_HV_S_PALETTE is pretty silly in that it violates the dimension-interdependence of HSV,
		 * thus making it difficult to use (as the user moves through the 2D palette its coloration actually changes!
		 * It's just thrown in for fun.
		 */
		private static final int METHOD_HV_S_PALETTE = 1;
		private static final int METHOD_SV_H_PALETTE = 2;
		/**
		 * Please read the important note at setUVPalette() about the visual portrayal of the UV palette.
		 */
		private static final int METHOD_UV_Y_PALETTE = 3;
		private static final int METHOD_RGB_SLIDERS = 4;
		private static final int METHOD_HSV_SLIDERS = 5;
		private static final int METHOD_YUV_SLIDERS = 6;

		/**
		 * Edit these switches to show/hide each method's icon in the method selector list and thus to enable/disable access to that method.
		 */
		//NEW_METHOD_WORK_NEEDED_HERE
		//Add a new entry to the list, make sure you keep the order correct w.r.t. to the METHOD_ consts.
		private static final boolean[] ENABLED_METHODS = {
			true,	//METHOD_HS_V_ENABLED
			true,	//METHOD_HV_S_ENABLED
			true,	//METHOD_SV_H_ENABLED
			true,	//METHOD_UV_Y_ENABLED
			true,	//METHOD_RGB_ENABLED
			true,	//METHOD_HSV_ENABLED
			true	//METHOD_YUV_SLIDERS
		};

		//No need to manually keep this in sync with the switches above, it will be adjusted automatically during setup.
		private static int NUM_ENABLED_METHODS = ENABLED_METHODS.length;

		//NEW_METHOD_WORK_NEEDED_HERE
		//Add a new entry to the list for each controller in the new method
		private static final int TRACKED_NONE = -1;	//No object on screen is currently being tracked
		private static final int TRACK_SWATCH_OLD = 10;
		private static final int TRACK_SWATCH_NEW = 11;
		private static final int TRACK_HV_PALETTE = 20;
		private static final int TRACK_VER_S_SLIDER = 21;
		private static final int TRACK_HS_PALETTE = 30;
		private static final int TRACK_VER_VALUE_SLIDER = 31;
		private static final int TRACK_SV_PALETTE = 40;
		private static final int TRACK_VER_H_SLIDER = 41;
		private static final int TRACK_UV_PALETTE = 50;
		private static final int TRACK_VER_Y_SLIDER = 51;
		private static final int TRACK_R_SLIDER = 60;
		private static final int TRACK_G_SLIDER = 61;
		private static final int TRACK_B_SLIDER = 62;
		private static final int TRACK_H_SLIDER = 70;
		private static final int TRACK_S_SLIDER = 71;
		private static final int TRACK_HOR_VALUE_SLIDER = 72;
		private static final int TRACK_HOR_Y_SLIDER = 80;
		private static final int TRACK_U_SLIDER = 81;
		private static final int TRACK_V_SLIDER = 82;

		private static final int TEXT_SIZE = 12;
		private static final int TEXT_HALF_SIZE = TEXT_SIZE / 2;	//Can be used to vertically center text (sorta, it's approximate)
		private static int[] TEXT_HSV_POS = new int[2];
		private static int[] TEXT_RGB_POS = new int[2];
		private static int[] TEXT_YUV_POS = new int[2];
		private static int[] TEXT_HEX_POS = new int[2];

		private static final float PI = 3.141592653589793f;

		private int mMethod = METHOD_HS_V_PALETTE;
		private int mTracking = TRACKED_NONE;	//What object on screen is currently being tracked for movement

		//Zillions of persistant Paint objecs for drawing the View

		private Paint mSwatchOld, mSwatchNew;

		private Shader mFadeInLeft, mFadeInTop, mFadeInRight, mFadeInBottom;

		//NEW_METHOD_WORK_NEEDED_HERE
		//Add Paints to represent the palettes of the new method's UI controllers
		private Paint mOvalHueSat;

		private Paint mOvalHueVal;

		private Shader mSatValMask;
		private Paint mSatValPalette;

		private Paint mUVPalette;

		private Bitmap mVerSliderBM;
		private Canvas mVerSliderCv;

		private Bitmap[] mHorSlidersBM = new Bitmap[3];
		private Canvas[] mHorSlidersCv = new Canvas[3];

		private Paint mSatFader;
		private Paint mValDimmer;

		//NEW_METHOD_WORK_NEEDED_HERE
		//Add Paints to represent the icon for the new method
		private Paint mOvalHueSatSmall;
		private Paint mOvalHueValSmall;
		private Paint mSVSmall;
		private Paint mUVSmall;
		private Paint[] mRGBSmall = new Paint[3];
		private Paint[] mHSSmall = new Paint[2];
		private Paint[] mYUVSmall = new Paint[3];

		private Paint mPosMarker;
		private Paint mText;

		private Rect mOldSwatchRect = new Rect();
		private Rect mNewSwatchRect = new Rect();
		private Rect mPaletteRect = new Rect();
		private Rect mVerSliderRect = new Rect();
		private Rect[] mHorSliderRects = new Rect[3];
		private Rect[] mMethodSelectorRects = null;	//The Rects where the icons are drawn.  This will be assigned during setup.
		private int[] mMethodSelectRectMap = null;	//Which method corresponds to which icon Rect.  This will be assigned during setup.

		private int[] mSpectrumColors, mSpectrumColorsRev;
		private int mOriginalColor = 0;	//The color passed in at the beginning, which can be reverted to at any time by tapping the old swatch.
		private float[] mHSV = new float[3];
		private int[] mRGB = new int[3];
		private float[] mYUV = new float[3];
		private String mHexStr = "";
		private boolean mHSVenabled = true;	//Only true if an HSV method is enabled
		private boolean mRGBenabled = true;	//Only true if an RGB method is enabled
		private boolean mYUVenabled = true;	//Only true if a YUV method is enabled
		private boolean mHexenabled = true;	//Only true if an RGB method is enabled
		private int[] mCoord = new int[3];		//For drawing slider/palette markers
		private int mFocusedControl = -1;	//Which control receives trackball events.
		private OnColorChangedListener mListener;
		private long mTimeOfLastSliderSwitch = 0;		//To prevent slider switches from occurring too rapidly.
		private boolean mShownYUVWarnedAlready = false;	//Only show the YUV toast warning once.

		/**
		 * Ctor.
		 * @param c
		 * @param l
		 * @param width Used to determine orientation and adjust layout accordingly
		 * @param height Used to determine orientation and adjust layout accordingly
		 * @param color The initial color
		 * @throws Exception
		 */
		ColorPickerView(Context c, OnColorChangedListener l, int width, int height, int color)
		throws Exception {
			super(c);

			//We need to make the dialog focusable to retrieve trackball events.
			setFocusable(true);
			boolean focusable = isFocusable();
			boolean gotFocus = requestFocus();

			mListener = l;

			mOriginalColor = color;

			Color.colorToHSV(color, mHSV);
			if (isGray(color))		//Ugh, I think there's a bug in android's Color routines.  Read the longer description at isGray().
				mHSV[1] = 0;

			updateAllFromHSV();

			//Gather the number of enabled methods and allocate Rects to represent their icon locations in the method selector list.
			NUM_ENABLED_METHODS = 0;
			for (int i = 0; i < ENABLED_METHODS.length; i++)
				if (ENABLED_METHODS[i])
					NUM_ENABLED_METHODS++;
			if (NUM_ENABLED_METHODS == 0) {
				Toast.makeText(getContext(), "No color picker methods enabled.", Toast.LENGTH_SHORT).show();
				throw new Exception("At least one method must be enabled");
			}
			mMethodSelectorRects = new Rect[NUM_ENABLED_METHODS];
			mMethodSelectRectMap = new int[NUM_ENABLED_METHODS];

			//Setup the layout based on whether this is a portrait or landscape orientation.
			if (width <= height) {	//Portrait layout
				SWATCH_WIDTH = (PALETTE_DIM + SLIDER_THICKNESS) / 2;

				PALETTE_POS_X = 0;
				PALETTE_POS_Y = TEXT_SIZE * 4 + SWATCH_HEIGHT;

				METHOD_SELECTOR_POS_X = PALETTE_POS_X + PALETTE_DIM + SLIDER_THICKNESS + METHOD_SELECTOR_SPACING;

				//NEW_METHOD_WORK_NEEDED_HERE
				//Follow the pattern here
				mHSVenabled = ENABLED_METHODS[METHOD_HS_V_PALETTE] || ENABLED_METHODS[METHOD_HV_S_PALETTE] || ENABLED_METHODS[METHOD_SV_H_PALETTE] || ENABLED_METHODS[METHOD_HSV_SLIDERS];
				mRGBenabled = ENABLED_METHODS[METHOD_RGB_SLIDERS];
				mYUVenabled = ENABLED_METHODS[METHOD_UV_Y_PALETTE] || ENABLED_METHODS[METHOD_YUV_SLIDERS];
				mHexenabled = ENABLED_METHODS[METHOD_RGB_SLIDERS];

				//Set the method chooser icon rects
				int prevEnabledMethod = -1;
				for (int i = 0; i < NUM_ENABLED_METHODS; i++) {
					mMethodSelectorRects[i] = new Rect(
						METHOD_SELECTOR_POS_X,
						(METHOD_SELECTOR_SIZE + METHOD_SELECTOR_SPACING) * i,
						METHOD_SELECTOR_POS_X + METHOD_SELECTOR_SIZE,
						(METHOD_SELECTOR_SIZE + METHOD_SELECTOR_SPACING) * i + METHOD_SELECTOR_SIZE);

					for (int j = prevEnabledMethod + 1; j < ENABLED_METHODS.length; j++) {
						if (ENABLED_METHODS[j]) {
							mMethodSelectRectMap[i] = j;
							prevEnabledMethod = j;
							break;
						}
					}
				}

				//Set more rects, lots of rects
				mOldSwatchRect.set(0, TEXT_SIZE * 4, SWATCH_WIDTH, TEXT_SIZE * 4 + SWATCH_HEIGHT);
				mNewSwatchRect.set(SWATCH_WIDTH, TEXT_SIZE * 4, SWATCH_WIDTH * 2, TEXT_SIZE * 4 + SWATCH_HEIGHT);
				mPaletteRect.set(0, PALETTE_POS_Y, PALETTE_DIM, PALETTE_POS_Y + PALETTE_DIM);
				mVerSliderRect.set(PALETTE_DIM, PALETTE_POS_Y, PALETTE_DIM + SLIDER_THICKNESS, PALETTE_POS_Y + PALETTE_DIM);
				mHorSliderRects[0] = new Rect(
					0,
					PALETTE_POS_Y + FIRST_HOR_SLIDER_POS_Y,
					PALETTE_DIM,
					PALETTE_POS_Y + FIRST_HOR_SLIDER_POS_Y + SLIDER_THICKNESS);
				mHorSliderRects[1] = new Rect(
					0,
					PALETTE_POS_Y + FIRST_HOR_SLIDER_POS_Y + (int)(SLIDER_THICKNESS * 1.25),
					PALETTE_DIM,
					PALETTE_POS_Y + FIRST_HOR_SLIDER_POS_Y + (int)(SLIDER_THICKNESS * 1.25) + SLIDER_THICKNESS);
				mHorSliderRects[2] = new Rect(
					0,
					PALETTE_POS_Y + FIRST_HOR_SLIDER_POS_Y + (int)(SLIDER_THICKNESS * 2.5),
					PALETTE_DIM,
					PALETTE_POS_Y + FIRST_HOR_SLIDER_POS_Y + (int)(SLIDER_THICKNESS * 2.5) + SLIDER_THICKNESS);

				TEXT_HSV_POS[0] = 3;
				TEXT_HSV_POS[1] = 0;
				TEXT_RGB_POS[0] = TEXT_HSV_POS[0] + 50;
				TEXT_RGB_POS[1] = TEXT_HSV_POS[1];
				TEXT_YUV_POS[0] = TEXT_HSV_POS[0] + 100;
				TEXT_YUV_POS[1] = TEXT_HSV_POS[1];
				TEXT_HEX_POS[0] = TEXT_HSV_POS[0] + 150;
				TEXT_HEX_POS[1] = TEXT_HSV_POS[1];

				VIEW_DIM_X = PALETTE_DIM + SLIDER_THICKNESS + METHOD_SELECTOR_SPACING + METHOD_SELECTOR_SIZE;
				VIEW_DIM_Y = Math.max(SWATCH_HEIGHT + PALETTE_DIM + TEXT_SIZE * 4,
					METHOD_SELECTOR_SIZE * NUM_ENABLED_METHODS + METHOD_SELECTOR_SPACING * (NUM_ENABLED_METHODS - 1));
			}
			else {	//Landscape layout
				SWATCH_WIDTH = 110;

				PALETTE_POS_X = SWATCH_WIDTH;
				PALETTE_POS_Y = 0;

				METHOD_SELECTOR_POS_X = PALETTE_POS_X + PALETTE_DIM + SLIDER_THICKNESS + METHOD_SELECTOR_SPACING;

				//NEW_METHOD_WORK_NEEDED_HERE
				//Follow the pattern here
				mHSVenabled = ENABLED_METHODS[METHOD_HS_V_PALETTE] || ENABLED_METHODS[METHOD_HV_S_PALETTE] || ENABLED_METHODS[METHOD_SV_H_PALETTE] || ENABLED_METHODS[METHOD_HSV_SLIDERS];
				mRGBenabled = ENABLED_METHODS[METHOD_RGB_SLIDERS];
				mYUVenabled = ENABLED_METHODS[METHOD_UV_Y_PALETTE] || ENABLED_METHODS[METHOD_YUV_SLIDERS];
				mHexenabled = ENABLED_METHODS[METHOD_RGB_SLIDERS];

				//The maximum number of method selector icons per column is hard-coded to 4.
				//Changing this parameter would require some care, especially in calculation of VIEW_DIM_Y.

				//Set the method chooser icon rects
				int prevEnabledMethod = -1;
				for (int i = 0; i < NUM_ENABLED_METHODS; i++) {
					int xOffset = (METHOD_SELECTOR_SIZE + METHOD_SELECTOR_SPACING) * (i / 4);
					mMethodSelectorRects[i] = new Rect(
						METHOD_SELECTOR_POS_X + xOffset,
						(METHOD_SELECTOR_SIZE + METHOD_SELECTOR_SPACING) * (i % 4),
						METHOD_SELECTOR_POS_X + xOffset + METHOD_SELECTOR_SIZE,
						(METHOD_SELECTOR_SIZE + METHOD_SELECTOR_SPACING) * (i % 4) + METHOD_SELECTOR_SIZE);

					for (int j = prevEnabledMethod + 1; j < ENABLED_METHODS.length; j++) {
						if (ENABLED_METHODS[j]) {
							mMethodSelectRectMap[i] = j;
							prevEnabledMethod = j;
							break;
						}
					}
				}
				int numMethodSelectorColumns = (int)Math.ceil(NUM_ENABLED_METHODS / 4.0f);

				//Set more rects, lots of rects
				mOldSwatchRect.set(0, TEXT_SIZE * 7, SWATCH_WIDTH, TEXT_SIZE * 7 + SWATCH_HEIGHT);
				mNewSwatchRect.set(0, TEXT_SIZE * 7 + SWATCH_HEIGHT, SWATCH_WIDTH, TEXT_SIZE * 7 + SWATCH_HEIGHT * 2);
				mPaletteRect.set(SWATCH_WIDTH, PALETTE_POS_Y, SWATCH_WIDTH + PALETTE_DIM, PALETTE_POS_Y + PALETTE_DIM);
				mVerSliderRect.set(SWATCH_WIDTH + PALETTE_DIM, PALETTE_POS_Y, SWATCH_WIDTH + PALETTE_DIM + SLIDER_THICKNESS, PALETTE_POS_Y + PALETTE_DIM);
				mHorSliderRects[0] = new Rect(
					SWATCH_WIDTH,
					FIRST_HOR_SLIDER_POS_Y,
					SWATCH_WIDTH + PALETTE_DIM,
					FIRST_HOR_SLIDER_POS_Y + SLIDER_THICKNESS);
				mHorSliderRects[1] = new Rect(
					SWATCH_WIDTH,
					FIRST_HOR_SLIDER_POS_Y + (int)(SLIDER_THICKNESS * 1.25),
					SWATCH_WIDTH + PALETTE_DIM,
					FIRST_HOR_SLIDER_POS_Y + (int)(SLIDER_THICKNESS * 1.25) + SLIDER_THICKNESS);
				mHorSliderRects[2] = new Rect(
					SWATCH_WIDTH,
					FIRST_HOR_SLIDER_POS_Y + (int)(SLIDER_THICKNESS * 2.5),
					SWATCH_WIDTH + PALETTE_DIM,
					FIRST_HOR_SLIDER_POS_Y + (int)(SLIDER_THICKNESS * 2.5) + SLIDER_THICKNESS);

				TEXT_HSV_POS[0] = 3;
				TEXT_HSV_POS[1] = 0;
				TEXT_RGB_POS[0] = TEXT_HSV_POS[0];
				TEXT_RGB_POS[1] = (int)(TEXT_HSV_POS[1] + TEXT_SIZE * 3.5);
				TEXT_YUV_POS[0] = TEXT_HSV_POS[0] + 50;
				TEXT_YUV_POS[1] = (int)(TEXT_HSV_POS[1] + TEXT_SIZE * 3.5);
				TEXT_HEX_POS[0] = TEXT_HSV_POS[0] + 50;
				TEXT_HEX_POS[1] = TEXT_HSV_POS[1];

				VIEW_DIM_X = PALETTE_POS_X + PALETTE_DIM + SLIDER_THICKNESS +
					(METHOD_SELECTOR_SPACING + METHOD_SELECTOR_SIZE) * numMethodSelectorColumns;
				VIEW_DIM_Y = Math.max(mNewSwatchRect.bottom, Math.max(PALETTE_DIM, METHOD_SELECTOR_SIZE * 4 + METHOD_SELECTOR_SPACING * 3));
			}

			//Rainbows make everybody happy!
			mSpectrumColors = new int[] {
				0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
				0xFF0000FF, 0xFFFF00FF, 0xFFFF0000,
			};
			mSpectrumColorsRev = new int[] {
				0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF,
				0xFF00FF00, 0xFFFFFF00, 0xFFFF0000,
			};

			//Setup all the Paint and Shader objects.  There are lots of them!

			//NEW_METHOD_WORK_NEEDED_HERE
			//Add Paints to represent the palettes of the new method's UI controllers

			mSwatchOld = new Paint(Paint.ANTI_ALIAS_FLAG);
			mSwatchOld.setStyle(Paint.Style.FILL);
			mSwatchOld.setColor(Color.HSVToColor(mHSV));

			mSwatchNew = new Paint(Paint.ANTI_ALIAS_FLAG);
			mSwatchNew.setStyle(Paint.Style.FILL);
			mSwatchNew.setColor(Color.HSVToColor(mHSV));

			mFadeInLeft = new LinearGradient(0, 0, PALETTE_DIM, 0, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
			mFadeInRight = new LinearGradient(0, 0, PALETTE_DIM, 0, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
			mFadeInTop = new LinearGradient(0, 0, 0, PALETTE_DIM, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
			mFadeInBottom = new LinearGradient(0, 0, 0, PALETTE_DIM, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
			Shader fadeInTopSmall = new LinearGradient(0, 0, 0, METHOD_SELECTOR_SIZE, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
			Shader fadeInBottomSmall = new LinearGradient(0, 0, 0, METHOD_SELECTOR_SIZE, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);

			Shader shader = new SweepGradient(0, 0, mSpectrumColorsRev, null);

			Shader shaderA = new SweepGradient(0, 0, mSpectrumColorsRev, null);
			Shader shaderB = new RadialGradient(0, 0, PALETTE_CENTER_X, 0xFFFFFFFF, 0xFF000000, Shader.TileMode.CLAMP);
			shader = new ComposeShader(shaderA, shaderB, PorterDuff.Mode.SCREEN);
			mOvalHueSat = new Paint(Paint.ANTI_ALIAS_FLAG);
			mOvalHueSat.setShader(shader);
			mOvalHueSat.setStyle(Paint.Style.FILL);
			mOvalHueSat.setDither(true);

			shaderB = new RadialGradient(0, 0, PALETTE_CENTER_X, 0xFF000000, 0xFFFFFFFF, Shader.TileMode.CLAMP);
			shader = new ComposeShader(shaderA, shaderB, PorterDuff.Mode.MULTIPLY);
			mOvalHueVal = new Paint(Paint.ANTI_ALIAS_FLAG);
			mOvalHueVal.setShader(shader);
			mOvalHueVal.setStyle(Paint.Style.FILL);
			mOvalHueVal.setDither(true);

			shaderA = new LinearGradient(0, 0, 0, PALETTE_DIM, 0xFF000000, 0xFFFFFFFF, Shader.TileMode.CLAMP);
			mSatValMask = new ComposeShader(shaderA, mFadeInRight, PorterDuff.Mode.DST_IN);	//DST_ATOP also works

			mSatValPalette = new Paint(Paint.ANTI_ALIAS_FLAG);
			mSatValPalette.setStyle(Paint.Style.FILL);
			mSatValPalette.setDither(true);

			mUVPalette = new Paint(Paint.ANTI_ALIAS_FLAG);
			mUVPalette.setStyle(Paint.Style.FILL);
			mUVPalette.setDither(true);

			mVerSliderBM = Bitmap.createBitmap(SLIDER_THICKNESS, PALETTE_DIM, Bitmap.Config.RGB_565);
			mVerSliderCv = new Canvas(mVerSliderBM);

			for (int i = 0; i < 3; i++) {
				mHorSlidersBM[i] = Bitmap.createBitmap(PALETTE_DIM, SLIDER_THICKNESS, Bitmap.Config.RGB_565);
				mHorSlidersCv[i] = new Canvas(mHorSlidersBM[i]);
			}

			mSatFader = new Paint(Paint.ANTI_ALIAS_FLAG);
			mSatFader.setStyle(Paint.Style.FILL);
			mSatFader.setDither(true);
			mSatFader.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

			mValDimmer = new Paint(Paint.ANTI_ALIAS_FLAG);
			mValDimmer.setStyle(Paint.Style.FILL);
			mValDimmer.setDither(true);
			mValDimmer.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));

			//Whew, we're done making the big Paints and Shaders for the swatches, palettes, and sliders.
			//Now we need to make the Paints and Shaders that will draw the little method icons in the method selector list.

			//NEW_METHOD_WORK_NEEDED_HERE
			//Add Paints to represent the icon for the new method

			shaderA = new SweepGradient(0, 0, mSpectrumColorsRev, null);
			shaderB = new RadialGradient(0, 0, METHOD_SELECTOR_SIZE / 2, 0xFFFFFFFF, 0xFF000000, Shader.TileMode.CLAMP);
			shader = new ComposeShader(shaderA, shaderB, PorterDuff.Mode.SCREEN);
			mOvalHueSatSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
			mOvalHueSatSmall.setShader(shader);
			mOvalHueSatSmall.setStyle(Paint.Style.FILL);

			shaderB = new RadialGradient(0, 0, METHOD_SELECTOR_SIZE / 2, 0xFF000000, 0xFFFFFFFF, Shader.TileMode.CLAMP);
			shader = new ComposeShader(shaderA, shaderB, PorterDuff.Mode.MULTIPLY);
			mOvalHueValSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
			mOvalHueValSmall.setShader(shader);
			mOvalHueValSmall.setStyle(Paint.Style.FILL);

			shaderA = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, 0xFF000000, 0xFFFF0000, Shader.TileMode.CLAMP);
			shaderB = new LinearGradient(0, 0, 0, METHOD_SELECTOR_SIZE, 0xFF000000, 0xFFFFFFFF, Shader.TileMode.CLAMP);
			Shader shaderC = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
			Shader shaderD = new ComposeShader(shaderB, shaderC, PorterDuff.Mode.DST_IN);
			shader = new ComposeShader(shaderA, shaderD, PorterDuff.Mode.SCREEN);

			mSVSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
			mSVSmall.setShader(shader);
			mSVSmall.setStyle(Paint.Style.FILL);

			//A UV palette (U across, V up) for a given Y value is estimated by painting a U gradient across
			//the top for maximum V, faded out at the bottom, and painting a U gradient across the bottom for
			//minimum V, faded out at the top, then blending them.  This is pretty accurate, except for the
			//center of the palette for extreme values of Y (very low or very high), in which the true darkness
			//or lightness is not properly represented.
			ColorMatrix cm = new ColorMatrix();
			cm.setYUV2RGB();
			final float[] a = cm.getArray();

			int col1, col2;
			float[] yuv = new float[3];
			int[] rgb = new int[3];

			yuv[0] = .5f;

			//Top U, faded out at bottom
			yuv[1] = -.5f;
			yuv[2] = .5f;
			matrixProductToByte(a, yuv, rgb);
			col1 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			yuv[1] = .5f;
			matrixProductToByte(a, yuv, rgb);
			col2 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			shaderA = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, col1, col2, Shader.TileMode.CLAMP);
			Shader shaderA2 = new ComposeShader(shaderA, fadeInTopSmall, PorterDuff.Mode.DST_IN);

			//Bottom U, faded out at top
			yuv[1] = -.5f;
			yuv[2] = -.5f;
			matrixProductToByte(a, yuv, rgb);
			col1 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			yuv[1] = .5f;
			matrixProductToByte(a, yuv, rgb);
			col2 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			shaderB = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, col1, col2, Shader.TileMode.CLAMP);
			Shader shaderB2 = new ComposeShader(shaderB, fadeInBottomSmall, PorterDuff.Mode.DST_IN);

			shader = new ComposeShader(shaderA2, shaderB2, PorterDuff.Mode.SCREEN);

			mUVSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
			mUVSmall.setShader(shader);
			mUVSmall.setStyle(Paint.Style.FILL);

			shader = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, 0xFF000000, 0xFFFF0000, Shader.TileMode.CLAMP);
			mRGBSmall[0] = new Paint(Paint.ANTI_ALIAS_FLAG);
			mRGBSmall[0].setShader(shader);
			mRGBSmall[0].setStyle(Paint.Style.FILL);

			shader = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, 0xFF000000, 0xFF00FF00, Shader.TileMode.CLAMP);
			mRGBSmall[1] = new Paint(Paint.ANTI_ALIAS_FLAG);
			mRGBSmall[1].setShader(shader);
			mRGBSmall[1].setStyle(Paint.Style.FILL);

			shader = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, 0xFF000000, 0xFF0000FF, Shader.TileMode.CLAMP);
			mRGBSmall[2] = new Paint(Paint.ANTI_ALIAS_FLAG);
			mRGBSmall[2].setShader(shader);
			mRGBSmall[2].setStyle(Paint.Style.FILL);

			shader = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, mSpectrumColors, null, Shader.TileMode.CLAMP);
			mHSSmall[0] = new Paint(Paint.ANTI_ALIAS_FLAG);
			mHSSmall[0].setShader(shader);
			mHSSmall[0].setStyle(Paint.Style.FILL);

			shader = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, 0xFFFFFFFF, 0xFFFF0000, Shader.TileMode.CLAMP);
			mHSSmall[1] = new Paint(Paint.ANTI_ALIAS_FLAG);
			mHSSmall[1].setShader(shader);
			mHSSmall[1].setStyle(Paint.Style.FILL);

			yuv[0] = 0;
			yuv[1] = 0;
			yuv[2] = 0;
			matrixProductToByte(a, yuv, rgb);
			col1 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			yuv[0] = 1;
			matrixProductToByte(a, yuv, rgb);
			col2 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			shader = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, col1, col2, Shader.TileMode.CLAMP);
			mYUVSmall[0] = new Paint(Paint.ANTI_ALIAS_FLAG);
			mYUVSmall[0].setShader(shader);
			mYUVSmall[0].setStyle(Paint.Style.FILL);

			yuv[0] = .5f;
			yuv[1] = -.5f;
			yuv[2] = 0;
			matrixProductToByte(a, yuv, rgb);
			col1 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			yuv[1] = .5f;
			matrixProductToByte(a, yuv, rgb);
			col2 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			shader = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, col1, col2, Shader.TileMode.CLAMP);
			mYUVSmall[1] = new Paint(Paint.ANTI_ALIAS_FLAG);
			mYUVSmall[1].setShader(shader);
			mYUVSmall[1].setStyle(Paint.Style.FILL);

			yuv[0] = .5f;
			yuv[1] = 0;
			yuv[2] = -.5f;
			matrixProductToByte(a, yuv, rgb);
			col1 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			yuv[2] = .5f;
			matrixProductToByte(a, yuv, rgb);
			col2 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			shader = new LinearGradient(0, 0, METHOD_SELECTOR_SIZE, 0, col1, col2, Shader.TileMode.CLAMP);
			mYUVSmall[2] = new Paint(Paint.ANTI_ALIAS_FLAG);
			mYUVSmall[2].setShader(shader);
			mYUVSmall[2].setStyle(Paint.Style.FILL);

			//Make a simple stroking Paint for drawing markers and borders and stuff like that.
			mPosMarker = new Paint(Paint.ANTI_ALIAS_FLAG);
			mPosMarker.setStyle(Paint.Style.STROKE);
			mPosMarker.setStrokeWidth(2);

			//Make a basic text Paint.
			mText = new Paint(Paint.ANTI_ALIAS_FLAG);
			mText.setTextSize(TEXT_SIZE);
			mText.setColor(Color.WHITE);

			//Kickstart
			initUI();
		}

		/**
		 * Draw the entire view (the entire dialog).
		 */
		@Override
		protected void onDraw(Canvas canvas) {
			//Draw the old and new swatches
			drawSwatches(canvas);

			//Write the text
			writeColorParams(canvas);

			//Draw the palette and sliders (the UI)
			//NEW_METHOD_WORK_NEEDED_HERE
			//To add a new method, replicate and extend the last entry in this list
			if (mMethod == METHOD_HS_V_PALETTE)
				drawHSV1Palette(canvas);
			else if (mMethod == METHOD_HV_S_PALETTE)
				drawHSV2Palette(canvas);
			else if (mMethod == METHOD_SV_H_PALETTE)
				drawHSV3Palette(canvas);
			else if (mMethod == METHOD_UV_Y_PALETTE)
				drawYUVPalette(canvas);
			else if (mMethod == METHOD_RGB_SLIDERS)
				drawRGBSliders(canvas);
			else if (mMethod == METHOD_HSV_SLIDERS)
				drawHSVSliders(canvas);
			else if (mMethod == METHOD_YUV_SLIDERS)
				drawYUVSliders(canvas);

			//Draw the method selector icons
			drawMethodSelectors(canvas);
		}

		/**
		 * Draw the old and new swatches.
		 * @param canvas
		 */
		private void drawSwatches(Canvas canvas) {
			float[] hsv = new float[3];

			mText.setTextSize(16);

			//Draw the original swatch
			canvas.drawRect(mOldSwatchRect, mSwatchOld);
			Color.colorToHSV(mOriginalColor, hsv);
			//if (UberColorPickerDialog.isGray(mColor))	//Don't need this right here, but imp't to note
			//	hsv[1] = 0;
			if (hsv[2] > .5)
				mText.setColor(Color.BLACK);
			canvas.drawText("Revert", mOldSwatchRect.left + SWATCH_WIDTH / 2 - mText.measureText("Revert") / 2, mOldSwatchRect.top + 16, mText);
			mText.setColor(Color.WHITE);

			//Draw the new swatch
			canvas.drawRect(mNewSwatchRect, mSwatchNew);
			if (mHSV[2] > .5)
				mText.setColor(Color.BLACK);
			canvas.drawText("Accept", mNewSwatchRect.left + SWATCH_WIDTH / 2 - mText.measureText("Accept") / 2, mNewSwatchRect.top + 16, mText);
			mText.setColor(Color.WHITE);

			mText.setTextSize(TEXT_SIZE);
		}

		/**
		 * Write the color parametes (HSV, RGB, YUV, Hex, etc.).
		 * @param canvas
		 */
		private void writeColorParams(Canvas canvas) {
			if (mHSVenabled) {
				canvas.drawText("H: " + Integer.toString((int)(mHSV[0] / 360.0f * 255)), TEXT_HSV_POS[0], TEXT_HSV_POS[1] + TEXT_SIZE, mText);
				canvas.drawText("S: " + Integer.toString((int)(mHSV[1] * 255)), TEXT_HSV_POS[0], TEXT_HSV_POS[1] + TEXT_SIZE * 2, mText);
				canvas.drawText("V: " + Integer.toString((int)(mHSV[2] * 255)), TEXT_HSV_POS[0], TEXT_HSV_POS[1] + TEXT_SIZE * 3, mText);
			}

			if (mRGBenabled) {
				canvas.drawText("R: " + mRGB[0], TEXT_RGB_POS[0], TEXT_RGB_POS[1] + TEXT_SIZE, mText);
				canvas.drawText("G: " + mRGB[1], TEXT_RGB_POS[0], TEXT_RGB_POS[1] + TEXT_SIZE * 2, mText);
				canvas.drawText("B: " + mRGB[2], TEXT_RGB_POS[0], TEXT_RGB_POS[1] + TEXT_SIZE * 3, mText);
			}

			if (mYUVenabled) {
				canvas.drawText("Y: " + Integer.toString((int)(mYUV[0] * 255)), TEXT_YUV_POS[0], TEXT_YUV_POS[1] + TEXT_SIZE, mText);
				canvas.drawText("U: " + Integer.toString((int)((mYUV[1] + .5f) * 255)), TEXT_YUV_POS[0], TEXT_YUV_POS[1] + TEXT_SIZE * 2, mText);
				canvas.drawText("V: " + Integer.toString((int)((mYUV[2] + .5f) * 255)), TEXT_YUV_POS[0], TEXT_YUV_POS[1] + TEXT_SIZE * 3, mText);
			}

			if (mHexenabled)
				canvas.drawText("#" + mHexStr, TEXT_HEX_POS[0], TEXT_HEX_POS[1] + TEXT_SIZE, mText);
		}

		/**
		 * Place a small circle on the 2D palette to indicate the current values.
		 * @param canvas
		 * @param markerPosX
		 * @param markerPosY
		 */
		private void mark2DPalette(Canvas canvas, int markerPosX, int markerPosY) {
			mPosMarker.setColor(Color.BLACK);
			canvas.drawOval(new RectF(markerPosX - 5, markerPosY - 5, markerPosX + 5, markerPosY + 5), mPosMarker);
			mPosMarker.setColor(Color.WHITE);
			canvas.drawOval(new RectF(markerPosX - 3, markerPosY - 3, markerPosX + 3, markerPosY + 3), mPosMarker);
		}

		/**
		 * Draw a line across the slider to indicate its current value.
		 * @param canvas
		 * @param markerPos
		 */
		private void markVerSlider(Canvas canvas, int markerPos) {
			mPosMarker.setColor(Color.BLACK);
			canvas.drawRect(new Rect(0, markerPos - 2, SLIDER_THICKNESS, markerPos + 3), mPosMarker);
			mPosMarker.setColor(Color.WHITE);
			canvas.drawRect(new Rect(0, markerPos, SLIDER_THICKNESS, markerPos + 1), mPosMarker);
		}

		/**
		 * Draw a line across the slider to indicate its current value.
		 * @param canvas
		 * @param markerPos
		 */
		private void markHorSlider(Canvas canvas, int markerPos) {
			mPosMarker.setColor(Color.BLACK);
			canvas.drawRect(new Rect(markerPos - 2, 0, markerPos + 3, SLIDER_THICKNESS), mPosMarker);
			mPosMarker.setColor(Color.WHITE);
			canvas.drawRect(new Rect(markerPos, 0, markerPos + 1, SLIDER_THICKNESS), mPosMarker);
		}

		/**
		 * Frame the slider to indicate that it has trackball focus.
		 * @param canvas
		 */
		private void hilightFocusedVerSlider(Canvas canvas) {
			mPosMarker.setColor(Color.WHITE);
			canvas.drawRect(new Rect(0, 0, SLIDER_THICKNESS, PALETTE_DIM), mPosMarker);
			mPosMarker.setColor(Color.BLACK);
			canvas.drawRect(new Rect(2, 2, SLIDER_THICKNESS - 2, PALETTE_DIM - 2), mPosMarker);
		}

		/**
		 * Frame the slider to indicate that it has trackball focus.
		 * @param canvas
		 */
		private void hilightFocusedHorSlider(Canvas canvas) {
			mPosMarker.setColor(Color.WHITE);
			canvas.drawRect(new Rect(0, 0, PALETTE_DIM, SLIDER_THICKNESS), mPosMarker);
			mPosMarker.setColor(Color.BLACK);
			canvas.drawRect(new Rect(2, 2, PALETTE_DIM - 2, SLIDER_THICKNESS - 2), mPosMarker);
		}

		/**
		 * Frame the 2D palette to indicate that it has trackball focus.
		 * @param canvas
		 */
		private void hilightFocusedOvalPalette(Canvas canvas) {
			mPosMarker.setColor(Color.WHITE);
			canvas.drawOval(new RectF(-PALETTE_RADIUS, -PALETTE_RADIUS, PALETTE_RADIUS, PALETTE_RADIUS), mPosMarker);
			mPosMarker.setColor(Color.BLACK);
			canvas.drawOval(new RectF(-PALETTE_RADIUS + 2, -PALETTE_RADIUS + 2, PALETTE_RADIUS - 2, PALETTE_RADIUS - 2), mPosMarker);
		}

		/**
		 * Frame the 2D palette to indicate that it has trackball focus.
		 * @param canvas
		 */
		private void hilightFocusedSquarePalette(Canvas canvas) {
			mPosMarker.setColor(Color.WHITE);
			canvas.drawRect(new Rect(0, 0, PALETTE_DIM, PALETTE_DIM), mPosMarker);
			mPosMarker.setColor(Color.BLACK);
			canvas.drawRect(new Rect(2, 2, PALETTE_DIM - 2, PALETTE_DIM - 2), mPosMarker);
		}

		/**
		 * Frame the 2D palette to indicate that it has trackball focus.
		 * @param canvas
		 */
		private void hilightMethodSelectorOval(Canvas canvas) {
			mPosMarker.setColor(Color.WHITE);
			canvas.drawOval(new RectF(-METHOD_SELECTOR_SIZE / 2, -METHOD_SELECTOR_SIZE / 2, METHOD_SELECTOR_SIZE / 2, METHOD_SELECTOR_SIZE / 2), mPosMarker);
			mPosMarker.setColor(Color.BLACK);
			canvas.drawOval(new RectF(-METHOD_SELECTOR_SIZE / 2 + 2, -METHOD_SELECTOR_SIZE / 2 + 2, METHOD_SELECTOR_SIZE / 2 - 2, METHOD_SELECTOR_SIZE / 2 - 2), mPosMarker);
		}

		/**
		 * Frame the 2D palette to indicate that it has trackball focus.
		 * @param canvas
		 */
		private void hilightMethodSelectorRect(Canvas canvas) {
			mPosMarker.setColor(Color.WHITE);
			canvas.drawRect(new Rect(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE), mPosMarker);
			mPosMarker.setColor(Color.BLACK);
			canvas.drawRect(new Rect(2, 2, METHOD_SELECTOR_SIZE - 2, METHOD_SELECTOR_SIZE - 2), mPosMarker);
		}

		//NEW_METHOD_WORK_NEEDED_HERE
		//To add a new method, replicate the basic draw functions here.  Use the 2D palette or 1D sliders as templates for the new method.
		/**
		 * Draw the UI for HSV with angular H and radial S combined in 2D and a 1D V slider.
		 * @param canvas
		 */
		private void drawHSV1Palette(Canvas canvas) {
			canvas.save();

			canvas.translate(PALETTE_POS_X, PALETTE_POS_Y);

			//Draw the 2D palette
			canvas.translate(PALETTE_CENTER_X, PALETTE_CENTER_Y);
			canvas.drawOval(new RectF(-PALETTE_RADIUS, -PALETTE_RADIUS, PALETTE_RADIUS, PALETTE_RADIUS), mOvalHueSat);
			canvas.drawOval(new RectF(-PALETTE_RADIUS, -PALETTE_RADIUS, PALETTE_RADIUS, PALETTE_RADIUS), mValDimmer);
			if (mFocusedControl == 0)
				hilightFocusedOvalPalette(canvas);
			mark2DPalette(canvas, mCoord[0], mCoord[1]);
			canvas.translate(-PALETTE_CENTER_X, -PALETTE_CENTER_Y);

			//Draw the 1D slider
			canvas.translate(PALETTE_DIM, 0);
			canvas.drawBitmap(mVerSliderBM, 0, 0, null);
			if (mFocusedControl == 1)
				hilightFocusedVerSlider(canvas);
			markVerSlider(canvas, mCoord[2]);

			canvas.restore();
		}

		/**
		 * Draw the UI for HSV with angular H and radial V combined in 2D and a 1D S slider.
		 * @param canvas
		 */
		private void drawHSV2Palette(Canvas canvas) {
			canvas.save();

			canvas.translate(PALETTE_POS_X, PALETTE_POS_Y);

			canvas.translate(PALETTE_CENTER_X, PALETTE_CENTER_Y);
			canvas.drawOval(new RectF(-PALETTE_RADIUS, -PALETTE_RADIUS, PALETTE_RADIUS, PALETTE_RADIUS), mOvalHueVal);
			canvas.drawOval(new RectF(-PALETTE_RADIUS, -PALETTE_RADIUS, PALETTE_RADIUS, PALETTE_RADIUS), mSatFader);
			if (mFocusedControl == 0)
				hilightFocusedOvalPalette(canvas);
			mark2DPalette(canvas, mCoord[0], mCoord[2]);
			canvas.translate(-PALETTE_CENTER_X, -PALETTE_CENTER_Y);

			canvas.translate(PALETTE_DIM, 0);
			canvas.drawBitmap(mVerSliderBM, 0, 0, null);
			if (mFocusedControl == 1)
				hilightFocusedVerSlider(canvas);
			if (mFocusedControl == 1)
				hilightFocusedVerSlider(canvas);
			markVerSlider(canvas, mCoord[1]);

			canvas.restore();
		}

		/**
		 * Draw the UI for HSV with cardinal S and V combined in 2D and a 1D H slider.
		 * @param canvas
		 */
		private void drawHSV3Palette(Canvas canvas) {
			canvas.save();

			canvas.translate(PALETTE_POS_X, PALETTE_POS_Y);

			canvas.drawRect(new Rect(0, 0, PALETTE_DIM, PALETTE_DIM), mSatValPalette);
			if (mFocusedControl == 0)
				hilightFocusedSquarePalette(canvas);
			mark2DPalette(canvas, mCoord[2], mCoord[1]);

			canvas.translate(PALETTE_DIM, 0);
			canvas.drawBitmap(mVerSliderBM, 0, 0, null);
			if (mFocusedControl == 1)
				hilightFocusedVerSlider(canvas);
			canvas.drawRect(new Rect(0, 0, SLIDER_THICKNESS, PALETTE_DIM), mSatFader);
			canvas.drawRect(new Rect(0, 0, SLIDER_THICKNESS, PALETTE_DIM), mValDimmer);
			if (mFocusedControl == 1)
				hilightFocusedVerSlider(canvas);
			markVerSlider(canvas, mCoord[0]);

			canvas.restore();
		}

		/**
		 * Draw the UI for YUV with cardinal U and V combined in 2D and a 1D Y slider.
		 * @param canvas
		 */
		private void drawYUVPalette(Canvas canvas) {
			canvas.save();

			canvas.translate(PALETTE_POS_X, PALETTE_POS_Y);

			Paint black = new Paint(Paint.ANTI_ALIAS_FLAG);
			black.setStyle(Paint.Style.FILL);
			black.setColor(Color.BLACK);
			canvas.drawRect(new Rect(0, 0, PALETTE_DIM, PALETTE_DIM), black);
			canvas.drawRect(new Rect(0, 0, PALETTE_DIM, PALETTE_DIM), mUVPalette);
			if (mFocusedControl == 0)
				hilightFocusedSquarePalette(canvas);
			mark2DPalette(canvas, mCoord[1], mCoord[2]);

			canvas.translate(PALETTE_DIM, 0);
			canvas.drawBitmap(mVerSliderBM, 0, 0, null);
			if (mFocusedControl == 1)
				hilightFocusedVerSlider(canvas);
			if (mFocusedControl == 1)
				hilightFocusedVerSlider(canvas);
			markVerSlider(canvas, mCoord[0]);

			canvas.restore();
		}

		/**
		 * Draw the UI for RGB with three 1D sliders.
		 * @param canvas
		 */
		private void drawRGBSliders(Canvas canvas) {
			canvas.save();

			canvas.translate(PALETTE_POS_X, PALETTE_POS_Y);

			for (int i = 0; i < 3; i++) {
				if (i == 0)
					canvas.translate(0, FIRST_HOR_SLIDER_POS_Y);
				else canvas.translate(0, (int)(SLIDER_THICKNESS * 1.25));

				canvas.drawBitmap(mHorSlidersBM[i], 0, 0, null);

				if (mFocusedControl == i)
					hilightFocusedHorSlider(canvas);
				markHorSlider(canvas, mCoord[i]);

				if (i == 0)
					canvas.drawText("R", PALETTE_DIM + 5, SLIDER_THICKNESS / 2 + TEXT_HALF_SIZE, mText);
				else if (i == 1)
					canvas.drawText("G", PALETTE_DIM + 5, SLIDER_THICKNESS / 2 + TEXT_HALF_SIZE, mText);
				else canvas.drawText("B", PALETTE_DIM + 5, SLIDER_THICKNESS / 2 + TEXT_HALF_SIZE, mText);
			}

			canvas.restore();
		}

		/**
		 * Draw the UI for HSV with three 1D sliders.
		 * @param canvas
		 */
		private void drawHSVSliders(Canvas canvas) {
			canvas.save();

			canvas.translate(PALETTE_POS_X, PALETTE_POS_Y);

			for (int i = 0; i < 3; i++) {
				if (i == 0)
					canvas.translate(0, FIRST_HOR_SLIDER_POS_Y);
				else canvas.translate(0, (int)(SLIDER_THICKNESS * 1.25));

				canvas.drawBitmap(mHorSlidersBM[i], 0, 0, null);

				if (i == 0) {
					canvas.drawRect(new Rect(0, 0, PALETTE_DIM, SLIDER_THICKNESS), mSatFader);
					canvas.drawRect(new Rect(0, 0, PALETTE_DIM, SLIDER_THICKNESS), mValDimmer);
				}

				if (mFocusedControl == i)
					hilightFocusedHorSlider(canvas);
				markHorSlider(canvas, mCoord[i]);

				if (i == 0)
					canvas.drawText("H", PALETTE_DIM + 5, SLIDER_THICKNESS / 2 + TEXT_HALF_SIZE, mText);
				else if (i == 1)
					canvas.drawText("S", PALETTE_DIM + 5, SLIDER_THICKNESS / 2 + TEXT_HALF_SIZE, mText);
				else canvas.drawText("V", PALETTE_DIM + 5, SLIDER_THICKNESS / 2 + TEXT_HALF_SIZE, mText);
			}

			canvas.restore();
		}

		/**
		 * Draw the UI for RGB with three 1D sliders.
		 * @param canvas
		 */
		private void drawYUVSliders(Canvas canvas) {
			canvas.save();

			canvas.translate(PALETTE_POS_X, PALETTE_POS_Y);

			for (int i = 0; i < 3; i++) {
				if (i == 0)
					canvas.translate(0, FIRST_HOR_SLIDER_POS_Y);
				else canvas.translate(0, (int)(SLIDER_THICKNESS * 1.25));

				canvas.drawBitmap(mHorSlidersBM[i], 0, 0, null);

				if (mFocusedControl == i)
					hilightFocusedHorSlider(canvas);
				markHorSlider(canvas, mCoord[i]);

				if (i == 0)
					canvas.drawText("Y", PALETTE_DIM + 5, SLIDER_THICKNESS / 2 + TEXT_HALF_SIZE, mText);
				else if (i == 1)
					canvas.drawText("U", PALETTE_DIM + 5, SLIDER_THICKNESS / 2 + TEXT_HALF_SIZE, mText);
				else canvas.drawText("V", PALETTE_DIM + 5, SLIDER_THICKNESS / 2 + TEXT_HALF_SIZE, mText);
			}

			canvas.restore();
		}

		/**
		 * Draw the method selector icons
		 * @param canvas
		 */
		private void drawMethodSelectors(Canvas canvas) {
			for (int i = 0; i < NUM_ENABLED_METHODS; i++) {
				canvas.save();

				//NEW_METHOD_WORK_NEEDED_HERE
				//To add a new method, replicate and extend the last entry in this list
				switch (mMethodSelectRectMap[i]) {
					case METHOD_HS_V_PALETTE: {
							canvas.translate(mMethodSelectorRects[i].left + METHOD_SELECTOR_SIZE / 2, mMethodSelectorRects[i].top + METHOD_SELECTOR_SIZE / 2);
							canvas.drawOval(new RectF(-METHOD_SELECTOR_SIZE / 2, -METHOD_SELECTOR_SIZE / 2, METHOD_SELECTOR_SIZE / 2, METHOD_SELECTOR_SIZE / 2), mOvalHueSatSmall);

							if (mMethod == i)
								hilightMethodSelectorOval(canvas);
						}
						break;
					case METHOD_HV_S_PALETTE: {
							canvas.translate(mMethodSelectorRects[i].left + METHOD_SELECTOR_SIZE / 2, mMethodSelectorRects[i].top + METHOD_SELECTOR_SIZE / 2);
							canvas.drawOval(new RectF(-METHOD_SELECTOR_SIZE / 2, -METHOD_SELECTOR_SIZE / 2, METHOD_SELECTOR_SIZE / 2, METHOD_SELECTOR_SIZE / 2), mOvalHueValSmall);

							if (mMethod == i)
								hilightMethodSelectorOval(canvas);
						}
						break;
					case METHOD_SV_H_PALETTE: {
							canvas.translate(mMethodSelectorRects[i].left, mMethodSelectorRects[i].top);
							canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE), mSVSmall);

							if (mMethod == i)
								hilightMethodSelectorRect(canvas);
						}
						break;
					case METHOD_UV_Y_PALETTE: {
							canvas.translate(mMethodSelectorRects[i].left, mMethodSelectorRects[i].top);
							canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE), mUVSmall);

							if (mMethod == i)
								hilightMethodSelectorRect(canvas);
						}
						break;
					case METHOD_RGB_SLIDERS: {
							canvas.translate(mMethodSelectorRects[i].left, mMethodSelectorRects[i].top + METHOD_SELECTOR_SIZE / 16);
							canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE / 4), mRGBSmall[0]);
							canvas.translate(0, METHOD_SELECTOR_SIZE / 3);
							canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE / 4), mRGBSmall[1]);
							canvas.translate(0, METHOD_SELECTOR_SIZE / 3);
							canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE / 4), mRGBSmall[2]);

							canvas.translate(0, -(2 * (METHOD_SELECTOR_SIZE / 3) + (METHOD_SELECTOR_SIZE / 16)));
							if (mMethod == i)
								hilightMethodSelectorRect(canvas);
						}
						break;
					case METHOD_HSV_SLIDERS: {
							canvas.translate(mMethodSelectorRects[i].left, mMethodSelectorRects[i].top + METHOD_SELECTOR_SIZE / 16);
							canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE / 4), mHSSmall[0]);
							canvas.translate(0, METHOD_SELECTOR_SIZE / 3);
							canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE / 4), mHSSmall[1]);
							canvas.translate(0, METHOD_SELECTOR_SIZE / 3);
							canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE / 4), mRGBSmall[0]);

							canvas.translate(0, -(2 * (METHOD_SELECTOR_SIZE / 3) + (METHOD_SELECTOR_SIZE / 16)));
							if (mMethod == i)
								hilightMethodSelectorRect(canvas);
						}
						break;
					case METHOD_YUV_SLIDERS: {
						canvas.translate(mMethodSelectorRects[i].left, mMethodSelectorRects[i].top + METHOD_SELECTOR_SIZE / 16);
						canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE / 4), mYUVSmall[0]);
						canvas.translate(0, METHOD_SELECTOR_SIZE / 3);
						canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE / 4), mYUVSmall[1]);
						canvas.translate(0, METHOD_SELECTOR_SIZE / 3);
						canvas.drawRect(new RectF(0, 0, METHOD_SELECTOR_SIZE, METHOD_SELECTOR_SIZE / 4), mYUVSmall[2]);

						canvas.translate(0, -(2 * (METHOD_SELECTOR_SIZE / 3) + (METHOD_SELECTOR_SIZE / 16)));
						if (mMethod == i)
							hilightMethodSelectorRect(canvas);
					}
					break;
				}

				canvas.restore();
			}
		}

		/**
		 * Initialize the current color chooser's UI (set its color parameters and set its palette and slider values accordingly).
		 */
		private void initUI() {
			//NEW_METHOD_WORK_NEEDED_HERE
			//To add a new method, replicate and extend the last entry in this list
			switch (mMethod) {
				case METHOD_HS_V_PALETTE:
					initHSV1Palette();
					break;
				case METHOD_HV_S_PALETTE:
					initHSV2Palette();
					break;
				case METHOD_SV_H_PALETTE:
					initHSV3Palette();
					break;
				case METHOD_UV_Y_PALETTE:
					initYUVPalette();
					break;
				case METHOD_RGB_SLIDERS:
					initRGBSliders();
					break;
				case METHOD_HSV_SLIDERS:
					initHSVSliders();
					break;
				case METHOD_YUV_SLIDERS:
					initYUVSliders();
					break;
			}

			//Focus on the first controller (arbitrary).
			mFocusedControl = 0;
		}

		//NEW_METHOD_WORK_NEEDED_HERE
		//To add a new method, replicate and extend the last init function shown below
		/**
		 * Initialize a color chooser.
		 */
		private void initHSV1Palette() {
			setOvalValDimmer();
			setVerValSlider();

			float angle = 2*PI - mHSV[0] / (180 / 3.1415927f);
			float radius = mHSV[1] * PALETTE_RADIUS;
			mCoord[0] = (int)(Math.cos(angle) * radius);
			mCoord[1] = (int)(Math.sin(angle) * radius);

			mCoord[2] = PALETTE_DIM - (int)(mHSV[2] * PALETTE_DIM);
		}

		/**
		 * Initialize a color chooser.
		 */
		private void initHSV2Palette() {
			setOvalSatFader();
			setVerSatSlider();

			float angle = 2*PI - mHSV[0] / (180 / 3.1415927f);
			float radius = mHSV[1] * PALETTE_RADIUS;
			mCoord[0] = (int)(Math.cos(angle) * radius);
			mCoord[2] = (int)(Math.sin(angle) * radius);

			mCoord[1] = PALETTE_DIM - (int)(mHSV[1] * PALETTE_DIM);
		}

		/**
		 * Initialize a color chooser.
		 */
		private void initHSV3Palette() {
			Shader shader = new LinearGradient(0, PALETTE_DIM, 0, 0, mSpectrumColors, null, Shader.TileMode.CLAMP);

			GradientDrawable gradDraw = new GradientDrawable(Orientation.TOP_BOTTOM, mSpectrumColorsRev);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, SLIDER_THICKNESS, PALETTE_DIM);
			gradDraw.draw(mVerSliderCv);

			setHorSatFader();
			setHorValDimmer();
			setSatValPalette();
			setVerHueSlider();

			mCoord[1] = PALETTE_DIM - (int)(mHSV[1] * PALETTE_DIM);
			mCoord[2] = (int)(mHSV[2] * PALETTE_DIM);

			mCoord[0] = PALETTE_DIM - (int)((mHSV[0] / 360.0f) * PALETTE_DIM);
		}

		/**
		 * Initialize a color chooser.
		 */
		private void initYUVPalette() {
			int color = Color.HSVToColor(mHSV);
			float r = Color.red(color) / 255.0f;
			float g = Color.green(color) / 255.0f;
			float b = Color.blue(color) / 255.0f;

			ColorMatrix cm = new ColorMatrix();
			cm.setRGB2YUV();
			final float[] a = cm.getArray();

			mYUV[0] = a[0] * r + a[1] * g + a[2] * b;
			mYUV[0] = pinToUnit(mYUV[0]);
			mYUV[1] = a[5] * r + a[6] * g + a[7] * b;
			mYUV[1] = pin(mYUV[1], -.5f, .5f);
			mYUV[2] = a[10] * r + a[11] * g + a[12] * b;
			mYUV[2] = pin(mYUV[2], -.5f, .5f);

			setUVPalette();
			setVerYSlider();

			mCoord[1] = (int)((mYUV[1] + .5f) * PALETTE_DIM);
			mCoord[2] = PALETTE_DIM - (int)((mYUV[2] + .5f) * PALETTE_DIM);

			mCoord[0] = PALETTE_DIM - (int)(mYUV[0] * PALETTE_DIM);

			//Warn the user that the UV 2D palette is only an estimate, but that the swatch is correct.
			if (!mShownYUVWarnedAlready)
				Toast.makeText(getContext(), "Note that the UV 2D palette only shows an estimate " +
					"but the swatch is correct.", Toast.LENGTH_LONG).show();
			mShownYUVWarnedAlready = true;
		}

		/**
		 * Initialize a color chooser.
		 */
		private void initRGBSliders() {
			int color = Color.HSVToColor(mHSV);
			mRGB[0] = Color.red(color);
			mRGB[1] = Color.green(color);
			mRGB[2] = Color.blue(color);

			setHorRSlider();
			setHorGSlider();
			setHorBSlider();

			int col = Color.HSVToColor(mHSV);
			mCoord[0] = (int)(PALETTE_DIM * (Color.red(col) / 255.0f));
			mCoord[1] = (int)(PALETTE_DIM * (Color.green(col) / 255.0f));
			mCoord[2] = (int)(PALETTE_DIM * (Color.blue(col) / 255.0f));
		}

		/**
		 * Initialize a color chooser.
		 */
		private void initHSVSliders() {
			Shader shader = new LinearGradient(0, 0, PALETTE_DIM, 0, mSpectrumColors, null, Shader.TileMode.CLAMP);

			GradientDrawable gradDraw = new GradientDrawable(Orientation.LEFT_RIGHT, mSpectrumColors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, PALETTE_DIM, SLIDER_THICKNESS);
			gradDraw.draw(mHorSlidersCv[0]);

			setHorSatFader();
			setHorValDimmer();
			setHorSatSlider();
			setHorValSlider();

			mCoord[0] = (int)((mHSV[0] / 360.0f) * PALETTE_DIM);
			mCoord[1] = (int)(mHSV[1] * PALETTE_DIM);
			mCoord[2] = (int)(mHSV[2] * PALETTE_DIM);
		}

		/**
		 * Initialize a color chooser.
		 */
		private void initYUVSliders() {
			int color = Color.HSVToColor(mHSV);
			float r = Color.red(color) / 255.0f;
			float g = Color.green(color) / 255.0f;
			float b = Color.blue(color) / 255.0f;

			ColorMatrix cm = new ColorMatrix();
			cm.setRGB2YUV();
			final float[] a = cm.getArray();

			mYUV[0] = a[0] * r + a[1] * g + a[2] * b;
			mYUV[0] = pinToUnit(mYUV[0]);
			mYUV[1] = a[5] * r + a[6] * g + a[7] * b;
			mYUV[1] = pin(mYUV[1], -.5f, .5f);
			mYUV[2] = a[10] * r + a[11] * g + a[12] * b;
			mYUV[2] = pin(mYUV[2], -.5f, .5f);

			setHorYSlider();
			setHorUSlider();
			setHorVSlider();

			mCoord[0] = (int)(mYUV[0] * PALETTE_DIM);
			mCoord[1] = (int)((mYUV[1] + .5f) * PALETTE_DIM);
			mCoord[2] = (int)((mYUV[2] + .5f) * PALETTE_DIM);
		}

		//NEW_METHOD_WORK_NEEDED_HERE
		//To add a new method, replicate and extend the set functions below, one per UI controller in the new method
		/**
		 * Adjust a Paint which, when painted, dims its underlying object to show the effects of varying value (brightness).
		 */
		private void setOvalValDimmer() {
			float[] hsv = new float[3];
			hsv[0] = mHSV[0];
			hsv[1] = 0;
			hsv[2] = mHSV[2];
			int gray = Color.HSVToColor(hsv);
			mValDimmer.setColor(gray);
		}

		/**
		 * Create a linear gradient shader to show variations in value.
		 */
		private void setVerValSlider() {
			float[] hsv = new float[3];
			hsv[0] = mHSV[0];
			hsv[1] = mHSV[1];
			hsv[2] = 1;
			int col = Color.HSVToColor(hsv);

			int colors[] = new int[2];
			colors[0] = col;
			colors[1] = 0xFF000000;
			GradientDrawable gradDraw = new GradientDrawable(Orientation.TOP_BOTTOM, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, SLIDER_THICKNESS, PALETTE_DIM);
			gradDraw.draw(mVerSliderCv);
		}

		/**
		 * Adjust a Paint which, when painted, fades its underlying object to show the effects of varying saturation.
		 */
		private void setOvalSatFader() {
			float[] hsv = new float[3];
			hsv[0] = mHSV[0];
			hsv[1] = 0;
			hsv[2] = (1 - mHSV[1]) * mHSV[2];
			int gray = Color.HSVToColor(hsv);
			mSatFader.setColor(gray);
		}

		/**
		 * Create a linear gradient shader to show variations in saturation.
		 */
		private void setVerSatSlider() {
			float[] hsv = new float[3];
			hsv[0] = mHSV[0];
			hsv[1] = 1;
			hsv[2] = mHSV[2];
			int col1 = Color.HSVToColor(hsv);
			hsv[0] = mHSV[0];
			hsv[1] = 0;
			hsv[2] = mHSV[2];
			int col2 = Color.HSVToColor(hsv);

			int colors[] = new int[2];
			colors[0] = col1;
			colors[1] = col2;
			GradientDrawable gradDraw = new GradientDrawable(Orientation.TOP_BOTTOM, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, SLIDER_THICKNESS, PALETTE_DIM);
			gradDraw.draw(mVerSliderCv);
		}

		/**
		 * Create a cardinal 2D palette with increasing value to the right and increasing saturation upwards, for a given hue.
		 */
		private void setSatValPalette() {
			float[] hsv = new float[3];
			hsv[0] = mHSV[0];
			hsv[1] = 1;
			hsv[2] = 1;
			int hue = Color.HSVToColor(hsv);

			Shader shaderA = new LinearGradient(0, 0, PALETTE_DIM, 0, 0xFF000000, hue, Shader.TileMode.CLAMP);
			Shader shaderB = new ComposeShader(shaderA, mSatValMask, PorterDuff.Mode.SCREEN);
			mSatValPalette.setShader(shaderB);
		}

		/**
		 * Place holder to keep the "setSlider" pattern, but there's nothing to do here.
		 */
		private void setVerHueSlider() {
			//Nothing to do
		}

		/**
		 * Adjust a Paint which, when painted, fades its underlying object to show the effects of varying saturation.
		 */
		private void setHorSatFader() {
			float[] hsv = new float[3];
			hsv[0] = mHSV[0];
			hsv[1] = 0;
			hsv[2] = 1.0f - mHSV[1];
			int gray = Color.HSVToColor(hsv);
			mSatFader.setColor(gray);
		}

		/**
		 * Adjust a Paint which, when painted, dims its underlying object to show the effects of varying value (brightness).
		 */
		private void setHorValDimmer() {
			float[] hsv = new float[3];
			hsv[0] = mHSV[0];
			hsv[1] = 0;
			hsv[2] = mHSV[2];
			int gray = Color.HSVToColor(hsv);
			mValDimmer.setColor(gray);
		}

		/**
		 * Create a cardinal 2D palette (in YUV space) with increasing U to the right and increasing V upwards, for a given Y.
		 * <P>
		 * This UV palette (U across, V up) for a given Y value is estimated by painting a U gradient across
		 * the top for maximum V, alpha-faded out at the bottom, and painting a U gradient across the bottom for
		 * minimum V, alpha-faded out at the top, then blending them.  This fairly accurately simulates the UV palette,
		 * except for the center of the palette for extreme values of Y (very low or very high),
		 * in which the true darkness or lightness is not properly represented.
		 */
		private void setUVPalette() {
			ColorMatrix cm = new ColorMatrix();
			cm.setYUV2RGB();
			final float[] a = cm.getArray();

			int col1, col2;
			float[] yuv = new float[3];
			int[] rgb = new int[3];

			yuv[0] = mYUV[0];

			//Top U, alpha-faded out at bottom
			yuv[1] = -.5f;
			yuv[2] = .5f;
			matrixProductToByte(a, yuv, rgb);
			col1 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			yuv[1] = .5f;
			matrixProductToByte(a, yuv, rgb);
			col2 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			Shader shaderA = new LinearGradient(0, 0, PALETTE_DIM, 0, col1, col2, Shader.TileMode.CLAMP);
			Shader shaderA2 = new ComposeShader(shaderA, mFadeInTop, PorterDuff.Mode.DST_IN);

			//Bottom U, alpha-faded out at top
			yuv[1] = -.5f;
			yuv[2] = -.5f;
			matrixProductToByte(a, yuv, rgb);
			col1 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			yuv[1] = .5f;
			matrixProductToByte(a, yuv, rgb);
			col2 = Color.rgb(rgb[0], rgb[1], rgb[2]);

			Shader shaderB = new LinearGradient(0, 0, PALETTE_DIM, 0, col1, col2, Shader.TileMode.CLAMP);
			Shader shaderB2 = new ComposeShader(shaderB, mFadeInBottom, PorterDuff.Mode.DST_IN);

			Shader shaderC = new ComposeShader(shaderA2, shaderB2, PorterDuff.Mode.SCREEN);

			//The center of the palette will be too saturated and not bright or dark enough at extreme Y values.
			//Let's compensate a bit here.  Yes, this is a hack.
			Shader shaderD = null;
			Shader shader = null;
			if (mYUV[0] >= .5) {
				int gray = pinToByte((int)((mYUV[0] - .5f) * 512));
				int trans = pinToByte((int)((mYUV[0] - .5f) * 480));
				col1 = Color.argb(trans, gray, gray, gray);
				shaderD = new RadialGradient(PALETTE_CENTER_X, PALETTE_CENTER_Y, PALETTE_RADIUS, col1, 0x00000000, Shader.TileMode.CLAMP);
				shader = new ComposeShader(shaderC, shaderD, PorterDuff.Mode.SCREEN);
			}
			else {
				int gray = pinToByte((int)((mYUV[0] + .5f) * 512));
				int trans = pinToByte((int)((1.0f - (mYUV[0] + .5f)) * 448));
				col1 = Color.argb(trans, gray, gray, gray);
				shaderD = new RadialGradient(PALETTE_CENTER_X, PALETTE_CENTER_Y, PALETTE_RADIUS, col1, 0x00000000, Shader.TileMode.CLAMP);
				shader = new ComposeShader(shaderC, shaderD, PorterDuff.Mode.DST_OUT);
			}

			mUVPalette.setShader(shader);
		}

		/**
		 * Create a linear gradient shader to show variations in Y (in YUV).
		 */
		private void setVerYSlider() {
			ColorMatrix cm = new ColorMatrix();
			cm.setYUV2RGB();
			final float[] a = cm.getArray();

			float[] yuv = new float[3];
			int[] rgb = new int[3];

			yuv[1] = mYUV[1];
			yuv[2] = mYUV[2];
			int colors[] = new int[11];
			for (int i = 0; i <= 10; i++) {
				yuv[0] = i / 10.0f;
				matrixProductToByte(a, yuv, rgb);
				colors[10 - i] = Color.rgb(rgb[0], rgb[1], rgb[2]);
			}

			GradientDrawable gradDraw = new GradientDrawable(Orientation.TOP_BOTTOM, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, SLIDER_THICKNESS, PALETTE_DIM);
			gradDraw.draw(mVerSliderCv);
		}

		/**
		 * Create a linear gradient shader to show variations in red.
		 */
		private void setHorRSlider() {
			int col1 = Color.rgb(0, mRGB[1], mRGB[2]);
			int col2 = Color.rgb(255, mRGB[1], mRGB[2]);

			int colors[] = new int[2];
			colors[0] = col1;
			colors[1] = col2;
			GradientDrawable gradDraw = new GradientDrawable(Orientation.LEFT_RIGHT, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, PALETTE_DIM, SLIDER_THICKNESS);
			gradDraw.draw(mHorSlidersCv[0]);
		}

		/**
		 * Create a linear gradient shader to show variations in green.
		 */
		private void setHorGSlider() {
			int col1 = Color.rgb(mRGB[0], 0, mRGB[2]);
			int col2 = Color.rgb(mRGB[0], 255, mRGB[2]);

			int colors[] = new int[2];
			colors[0] = col1;
			colors[1] = col2;
			GradientDrawable gradDraw = new GradientDrawable(Orientation.LEFT_RIGHT, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, PALETTE_DIM, SLIDER_THICKNESS);
			gradDraw.draw(mHorSlidersCv[1]);
		}

		/**
		 * Create a linear gradient shader to show variations in blue.
		 */
		private void setHorBSlider() {
			int col1 = Color.rgb(mRGB[0], mRGB[1], 0);
			int col2 = Color.rgb(mRGB[0], mRGB[1], 255);

			int colors[] = new int[2];
			colors[0] = col1;
			colors[1] = col2;
			GradientDrawable gradDraw = new GradientDrawable(Orientation.LEFT_RIGHT, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, PALETTE_DIM, SLIDER_THICKNESS);
			gradDraw.draw(mHorSlidersCv[2]);
		}

		/**
		 * Create a linear gradient shader to show variations in saturation.
		 */
		private void setHorSatSlider() {
			float[] hsv = new float[3];
			hsv[0] = mHSV[0];
			hsv[1] = 0;
			hsv[2] = mHSV[2];
			int col1 = Color.HSVToColor(hsv);
			hsv[0] = mHSV[0];
			hsv[1] = 1;
			hsv[2] = mHSV[2];
			int col2 = Color.HSVToColor(hsv);

			int colors[] = new int[2];
			colors[0] = col1;
			colors[1] = col2;
			GradientDrawable gradDraw = new GradientDrawable(Orientation.LEFT_RIGHT, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, PALETTE_DIM, SLIDER_THICKNESS);
			gradDraw.draw(mHorSlidersCv[1]);
		}

		/**
		 * Create a linear gradient shader to show variations in value.
		 */
		private void setHorValSlider() {
			float[] hsv = new float[3];
			hsv[0] = mHSV[0];
			hsv[1] = mHSV[1];
			hsv[2] = 1;
			int col = Color.HSVToColor(hsv);

			int colors[] = new int[2];
			colors[0] = 0xFF000000;
			colors[1] = col;
			GradientDrawable gradDraw = new GradientDrawable(Orientation.LEFT_RIGHT, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, PALETTE_DIM, SLIDER_THICKNESS);
			gradDraw.draw(mHorSlidersCv[2]);
		}

		/**
		 * Create a linear gradient shader to show variations in Y (in YUV).
		 */
		private void setHorYSlider() {
			ColorMatrix cm = new ColorMatrix();
			cm.setYUV2RGB();
			final float[] a = cm.getArray();

			float[] yuv = new float[3];
			int[] rgb = new int[3];

			yuv[1] = mYUV[1];
			yuv[2] = mYUV[2];
			int colors[] = new int[11];
			for (int i = 0; i <= 10; i++) {
				yuv[0] = i / 10.0f;
				matrixProductToByte(a, yuv, rgb);
				colors[i] = Color.rgb(rgb[0], rgb[1], rgb[2]);
			}

			GradientDrawable gradDraw = new GradientDrawable(Orientation.LEFT_RIGHT, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, PALETTE_DIM, SLIDER_THICKNESS);
			gradDraw.draw(mHorSlidersCv[0]);
		}

		/**
		 * Create a linear gradient shader to show variations in U (in YUV).
		 */
		private void setHorUSlider() {
			ColorMatrix cm = new ColorMatrix();
			cm.setYUV2RGB();
			final float[] a = cm.getArray();

			float[] yuv = new float[3];
			int[] rgb = new int[3];

			yuv[0] = mYUV[0];
			yuv[2] = mYUV[2];
			int colors[] = new int[11];
			for (int i = -5; i <= 5; i++) {
				yuv[1] = i / 10.0f;
				matrixProductToByte(a, yuv, rgb);
				colors[i + 5] = Color.rgb(rgb[0], rgb[1], rgb[2]);
			}

			GradientDrawable gradDraw = new GradientDrawable(Orientation.LEFT_RIGHT, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, PALETTE_DIM, SLIDER_THICKNESS);
			gradDraw.draw(mHorSlidersCv[1]);
		}

		/**
		 * Create a linear gradient shader to show variations in V (in YUV).
		 */
		private void setHorVSlider() {
			ColorMatrix cm = new ColorMatrix();
			cm.setYUV2RGB();
			final float[] a = cm.getArray();

			float[] yuv = new float[3];
			int[] rgb = new int[3];

			yuv[0] = mYUV[0];
			yuv[1] = mYUV[1];
			int colors[] = new int[11];
			for (int i = -5; i <= 5; i++) {
				yuv[2] = i / 10.0f;
				matrixProductToByte(a, yuv, rgb);
				colors[i + 5] = Color.rgb(rgb[0], rgb[1], rgb[2]);
			}

			GradientDrawable gradDraw = new GradientDrawable(Orientation.LEFT_RIGHT, colors);
			gradDraw.setDither(true);
			gradDraw.setLevel(10000);
			gradDraw.setBounds(0, 0, PALETTE_DIM, SLIDER_THICKNESS);
			gradDraw.draw(mHorSlidersCv[2]);
		}

		/**
		 * Report the correct tightly bounded dimensions of the view.
		 */
		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			setMeasuredDimension(VIEW_DIM_X, VIEW_DIM_Y);
		}

		/**
		 * Convert a slider position in the range [0,PALETTE_DIM] to a byte value in the range [0,255].
		 * @param sliderPos in the range [0,PALETTE_DIM].
		 * @return
		 */
		public int sliderPosTo255(int sliderPos) {
			int int255 = (int)(255.0f * ((float)sliderPos / (float)PALETTE_DIM));
			int255 = pinToByte(int255);
			return int255;
		}

		/**
		 * Wrap Math.round().  I'm not a Java expert.  Is this the only way to avoid writing "(int)Math.round" everywhere?
		 * @param x
		 * @return
		 */
		private int round(double x) {
			return (int)Math.round(x);
		}

		/**
		 * Limit a value to a min and max range.
		 * @param n
		 * @return
		 */
		private int pinToByte(int n) {
			if (n < 0) {
				n = 0;
			} else if (n > 255) {
				n = 255;
			}
			return n;
		}

		/**
		 * Limit a value to the range [0,1].
		 * @param n
		 * @return
		 */
		private float pinToUnit(float n) {
			if (n < 0) {
				n = 0;
			} else if (n > 1) {
				n = 1;
			}
			return n;
		}

		/**
		 * Limit a value to the range [0,max].
		 * @param n
		 * @param max
		 * @return
		 */
		private float pin(float n, float max) {
			if (n < 0) {
				n = 0;
			} else if (n > max) {
				n = max;
			}
			return n;
		}

		/**
		 * Limit a value to the range [min,max].
		 * @param n
		 * @param min
		 * @param max
		 * @return
		 */
		private float pin(float n, float min, float max) {
			if (n < min) {
				n = min;
			} else if (n > max) {
				n = max;
			}
			return n;
		}

		/**
		 * Perform a matrix multiplication to convert between colorspaces, then scale the results by 255 on the assumption
		 * that the original output range was [0,1] and the required output range is [0,255].
		 * @param a
		 * @param in
		 * @param out
		 */
		private void matrixProductToByte(float[] a, float[] in, int[] out) {
			out[0] = pinToByte(round((a[0] * in[0] + a[1] * in[1] + a[2] * in[2]) * 255.0f));
			out[1] = pinToByte(round((a[5] * in[0] + a[6] * in[1] + a[7] * in[2]) * 255.0f));
			out[2] = pinToByte(round((a[10] * in[0] + a[11] * in[1] + a[12] * in[2]) * 255.0f));
		}

		/**
		 * No clue what this does (some sort of average/mean I presume).  It came with the original UberColorPickerDialog
		 * in the API Demos and wasn't documented.  I don't feel like spending any time figuring it out, I haven't looked at it at all.
		 * @param s
		 * @param d
		 * @param p
		 * @return
		 */
		private int ave(int s, int d, float p) {
			return s + round(p * (d - s));
		}

		/**
		 * Came with the original UberColorPickerDialog in the API Demos, wasn't documented.  I believe it takes an array of
		 * colors and a value in the range [0,1] and interpolates a resulting color in a seemingly predictable manner.
		 * I haven't looked at it at all.
		 * @param colors
		 * @param unit
		 * @return
		 */
		private int interpColor(int colors[], float unit) {
			if (unit <= 0) {
				return colors[0];
			}
			if (unit >= 1) {
				return colors[colors.length - 1];
			}

			float p = unit * (colors.length - 1);
			int i = (int)p;
			p -= i;

			// now p is just the fractional part [0...1) and i is the index
			int c0 = colors[i];
			int c1 = colors[i+1];
			int a = ave(Color.alpha(c0), Color.alpha(c1), p);
			int r = ave(Color.red(c0), Color.red(c1), p);
			int g = ave(Color.green(c0), Color.green(c1), p);
			int b = ave(Color.blue(c0), Color.blue(c1), p);

			return Color.argb(a, r, g, b);
		}

		/**
		 * Unused, thought I might need this at one point.
		 * <P>
		 * If the integer quantization inherent in the android Color HSB/RGB conversions is problematic
		 * use this function instead.  Problems can occur when converting back and forth, especially when done repeatedly.
		 * @param hsv in the range[0,360][0,1][0,1].
		 * @param rgb in the range[0,1][0,1][0,1].
		 */
		private void HSV2RGB(float[] hsv, float[] rgb) {
			float f = hsv[0] / 60.0f - (int)hsv[0] / 60;
			float p = hsv[2] * (1 - hsv[1]);
			float q = hsv[2] * (1 - f * hsv[1]);
			float t = hsv[2] * (1 - (1 - f) * hsv[1]);
			switch (((int)hsv[0] / 60) % 6) {
				case 0:
					rgb[0] = hsv[2];
					rgb[1] = t;
					rgb[2] = p;
					break;
				case 1:
					rgb[0] = q;
					rgb[1] = hsv[2];
					rgb[2] = p;
					break;
				case 2:
					rgb[0] = p;
					rgb[1] = hsv[2];
					rgb[2] = t;
					break;
				case 3:
					rgb[0] = p;
					rgb[1] = q;
					rgb[2] = hsv[2];
					break;
				case 4:
					rgb[0] = t;
					rgb[1] = p;
					rgb[2] = hsv[2];
					break;
				case 5:
					rgb[0] = hsv[2];
					rgb[1] = p;
					rgb[2] = q;
					break;
			}
		}

		/**
		 * A standard point-in-rect routine.
		 * @param x
		 * @param y
		 * @param r
		 * @return true if point x,y is in rect r
		 */
		public boolean ptInRect(int x, int y, Rect r) {
			return x > r.left && x < r.right && y > r.top && y < r.bottom;
		}

		/**
		 * Process trackball events.  Used mainly for fine-tuned color adjustment, or alternatively to switch between slider controls.
		 */
		@Override
		public boolean dispatchTrackballEvent(MotionEvent event) {
			float x = event.getX();
			float y = event.getY();

			//Track the time so we don't switch between sliders too quickly
			long currTime = Calendar.getInstance().getTimeInMillis();

			//A longer event history implies faster trackball movement.
			//Use it to infer a larger jump and therefore faster palette/slider adjustment.
			int jump = event.getHistorySize() + 1;

			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN: {
					}
					break;
				case MotionEvent.ACTION_MOVE: {
						//NEW_METHOD_WORK_NEEDED_HERE
						//To add a new method, replicate and extend the appropriate entry in this list,
						//depending on whether you use 1D or 2D controllers
						switch (mMethod) {
							case METHOD_HS_V_PALETTE:
								if (mFocusedControl == 0) {
									changeHSPalette(x, y, jump);
								}
								else if (mFocusedControl == 1) {
									if (y < 0)
										changeSlider(mFocusedControl, true, jump);
									else if (y > 0)
										changeSlider(mFocusedControl, false, jump);
								}
								break;
							case METHOD_HV_S_PALETTE:
								if (mFocusedControl == 0) {
									changeHVPalette(x, y, jump);
								}
								else if (mFocusedControl == 1) {
									if (y < 0)
										changeSlider(mFocusedControl, true, jump);
									else if (y > 0)
										changeSlider(mFocusedControl, false, jump);
								}
								break;
							case METHOD_SV_H_PALETTE:
								if (mFocusedControl == 0) {
									changeSVPalette(x, y, jump);
								}
								else if (mFocusedControl == 1) {
									if (y < 0)
										changeSlider(mFocusedControl, true, jump);
									else if (y > 0)
										changeSlider(mFocusedControl, false, jump);
								}
								break;
							case METHOD_UV_Y_PALETTE:
								if (mFocusedControl == 0) {
									changeUVPalette(x, y, jump);
								}
								else if (mFocusedControl == 1) {
									if (y < 0)
										changeSlider(mFocusedControl, true, jump);
									else if (y > 0)
										changeSlider(mFocusedControl, false, jump);
								}
								break;
							case METHOD_RGB_SLIDERS:
							case METHOD_HSV_SLIDERS:
							case METHOD_YUV_SLIDERS:
								if (y < 0) {
									if (mFocusedControl == -1) {
										mFocusedControl = 2;
										invalidate();
									}
									else if (mFocusedControl > 0 && currTime - mTimeOfLastSliderSwitch > 200) {
										mTimeOfLastSliderSwitch = currTime;
										mFocusedControl--;
										invalidate();
									}
								}
								else if (y > 0) {
									if (mFocusedControl == -1) {
										mFocusedControl = 0;
										invalidate();
									}
									else if (mFocusedControl < 2 && currTime - mTimeOfLastSliderSwitch > 200) {
										mTimeOfLastSliderSwitch = currTime;
										mFocusedControl++;
										invalidate();
									}
								}
								else if (x < 0 && mFocusedControl != -1) {
									changeSlider(mFocusedControl, false, jump);
								}
								else if (x > 0 && mFocusedControl != -1) {
									changeSlider(mFocusedControl, true, jump);
								}
								break;
						}
					}
					break;
				case MotionEvent.ACTION_UP: {
					}
					break;
			}

			return true;
		}

		//NEW_METHOD_WORK_NEEDED_HERE
		//To add a new method, replicate and extend the appropriate functions below,
		//one per UI controller in the new method
		/**
		 * Effect a trackball change to a 2D palette.
		 * @param x -1: negative x change, 0: no x change, +1: positive x change.
		 * @param y -1: negative y change, 0, no y change, +1: positive y change.
		 * @param jump the amount by which to change.
		 */
		private void changeHSPalette(float x, float y, int jump) {
			int x2 = 0, y2 = 0;
			if (x < 0)
				x2 = -jump;
			else if (x > 0)
				x2 = jump;
			if (y < 0)
				y2 = -jump;
			else if (y > 0)
				y2 = jump;

		 	mCoord[0] += x2;
		 	mCoord[1] += y2;

		 	if (mCoord[0] < -PALETTE_RADIUS)
		 		mCoord[0] = -PALETTE_RADIUS;
		 	else if (mCoord[0] > PALETTE_RADIUS)
		 		mCoord[0] = PALETTE_RADIUS;
		 	if (mCoord[1] < -PALETTE_RADIUS)
		 		mCoord[1] = -PALETTE_RADIUS;
		 	else if (mCoord[1] > PALETTE_RADIUS)
		 		mCoord[1] = PALETTE_RADIUS;

			float radius = (float)java.lang.Math.sqrt(mCoord[0] * mCoord[0] + mCoord[1] * mCoord[1]);
			if (radius > PALETTE_RADIUS)
				radius = PALETTE_RADIUS;

			float angle = (float)java.lang.Math.atan2(mCoord[1], mCoord[0]);
			// need to turn angle [-PI ... PI] into unit [0....1]
			float unit = angle/(2*PI);
			if (unit < 0) {
				unit += 1;
			}

			mCoord[0] = round(Math.cos(angle) * radius);
			mCoord[1] = round(Math.sin(angle) * radius);

			int c = interpColor(mSpectrumColorsRev, unit);
			float[] hsv = new float[3];
			Color.colorToHSV(c, hsv);
			mHSV[0] = hsv[0];
			mHSV[1] = radius / PALETTE_RADIUS;
			updateAllFromHSV();
			mSwatchNew.setColor(Color.HSVToColor(mHSV));

			setVerValSlider();

			invalidate();
		}

		/**
		 * Effect a trackball change to a 2D palette.
		 * @param x -1: negative x change, 0: no x change, +1: positive x change.
		 * @param y -1: negative y change, 0, no y change, +1: positive y change.
		 * @param jump the amount by which to change.
		 */
		private void changeHVPalette(float x, float y, int jump) {
			int x2 = 0, y2 = 0;
			if (x < 0)
				x2 = -jump;
			else if (x > 0)
				x2 = jump;
			if (y < 0)
				y2 = -jump;
			else if (y > 0)
				y2 = jump;

		 	mCoord[0] += x2;
		 	mCoord[2] += y2;

		 	if (mCoord[0] < -PALETTE_RADIUS)
		 		mCoord[0] = -PALETTE_RADIUS;
		 	else if (mCoord[0] > PALETTE_RADIUS)
		 		mCoord[0] = PALETTE_RADIUS;
		 	if (mCoord[2] < -PALETTE_RADIUS)
		 		mCoord[2] = -PALETTE_RADIUS;
		 	else if (mCoord[2] > PALETTE_RADIUS)
		 		mCoord[2] = PALETTE_RADIUS;

			float radius = (float)java.lang.Math.sqrt(mCoord[0] * mCoord[0] + mCoord[2] * mCoord[2]);
			if (radius > PALETTE_RADIUS)
				radius = PALETTE_RADIUS;

			float angle = (float)java.lang.Math.atan2(mCoord[2], mCoord[0]);
			// need to turn angle [-PI ... PI] into unit [0....1]
			float unit = angle/(2*PI);
			if (unit < 0) {
				unit += 1;
			}

			mCoord[0] = round(Math.cos(angle) * radius);
			mCoord[2] = round(Math.sin(angle) * radius);

			int c = interpColor(mSpectrumColorsRev, unit);
			float[] hsv = new float[3];
			Color.colorToHSV(c, hsv);
			mHSV[0] = hsv[0];
			mHSV[2] = radius / PALETTE_RADIUS;
			updateAllFromHSV();
			mSwatchNew.setColor(Color.HSVToColor(mHSV));

			setOvalSatFader();
			setVerSatSlider();

			invalidate();
		}

		/**
		 * Effect a trackball change to a 2D palette.
		 * @param x -1: negative x change, 0: no x change, +1: positive x change.
		 * @param y -1: negative y change, 0, no y change, +1: positive y change.
		 * @param jump the amount by which to change.
		 */
		private void changeSVPalette(float x, float y, int jump) {
			int x2 = 0, y2 = 0;
			if (x < 0)
				x2 = -jump;
			else if (x > 0)
				x2 = jump;
			if (y < 0)
				y2 = -jump;
			else if (y > 0)
				y2 = jump;

		 	mCoord[1] += y2;
		 	mCoord[2] += x2;

		 	mCoord[1] = (int)pin(mCoord[1], PALETTE_DIM);
		 	mCoord[2] = (int)pin(mCoord[2], PALETTE_DIM);

			mHSV[1] = (float)(PALETTE_DIM - mCoord[1]) / (float)PALETTE_DIM;
			mHSV[2] = (float)mCoord[2] / (float)PALETTE_DIM;
			updateAllFromHSV();
			mSwatchNew.setColor(Color.HSVToColor(mHSV));

			setHorSatFader();
			setHorValDimmer();

			invalidate();
		}

		/**
		 * Effect a trackball change to a 2D palette.
		 * @param x -1: negative x change, 0: no x change, +1: positive x change.
		 * @param y -1: negative y change, 0, no y change, +1: positive y change.
		 * @param jump the amount by which to change.
		 */
		private void changeUVPalette(float x, float y, int jump) {
			int x2 = 0, y2 = 0;
			if (x < 0)
				x2 = -jump;
			else if (x > 0)
				x2 = jump;
			if (y < 0)
				y2 = -jump;
			else if (y > 0)
				y2 = jump;

		 	mCoord[1] += x2;
		 	mCoord[2] += y2;

		 	mCoord[1] = (int)pin(mCoord[1], PALETTE_DIM);
		 	mCoord[2] = (int)pin(mCoord[2], PALETTE_DIM);

			mYUV[1] = ((float)mCoord[1] / (float)PALETTE_DIM) - .5f;
			mYUV[2] = ((float)(PALETTE_DIM - mCoord[2]) / (float)PALETTE_DIM) - .5f;
			updateAllFromYUV();

			ColorMatrix cm = new ColorMatrix();
			cm.setYUV2RGB();
			final float[] a = cm.getArray();
			int[] rgb = new int[3];
			matrixProductToByte(a, mYUV, rgb);
			mSwatchNew.setColor(Color.rgb(rgb[0], rgb[1], rgb[2]));

			setVerYSlider();

			invalidate();
		}

		/**
		 * Effect a trackball change to a 1D slider.
		 * @param slider id of the slider to be effected
		 * @param increase true if the change is an increase, false if a decrease
		 * @param jump the amount by which to change in units of the range [0,255]
		 */
		private void changeSlider(int slider, boolean increase, int jump) {
			//NEW_METHOD_WORK_NEEDED_HERE
			//It is only necessary to add an entry here for a new method if the new method uses a 1D slider.
			//Note, some sliders are horizontal and others are vertical.
			//They differ a bit, especially in a sign flip on the vertical axis.
			if (mMethod == METHOD_HS_V_PALETTE) {
				//slider *must* equal 1

				mHSV[2] += (increase ? jump : -jump) / 256.0f;
				mHSV[2] = pinToUnit(mHSV[2]);
				updateAllFromHSV();
				mCoord[2] = PALETTE_DIM - (int)(mHSV[2] * PALETTE_DIM);

				mSwatchNew.setColor(Color.HSVToColor(mHSV));

				setOvalValDimmer();

				invalidate();
			}
			else if (mMethod == METHOD_HV_S_PALETTE) {
				//slider *must* equal 1

				mHSV[1] += (increase ? jump : -jump) / 256.0f;
				mHSV[1] = pinToUnit(mHSV[1]);
				updateAllFromHSV();
				mCoord[1] = PALETTE_DIM - (int)(mHSV[1] * PALETTE_DIM);

				mSwatchNew.setColor(Color.HSVToColor(mHSV));

				setOvalSatFader();

				invalidate();
			}
			else if (mMethod == METHOD_SV_H_PALETTE) {
				//slider *must* equal 1

				mHSV[0] += ((increase ? jump : -jump) / 256.0f) * 360.0f;
				mHSV[0] = pin(mHSV[0], 360);
				updateAllFromHSV();
				mCoord[0] = PALETTE_DIM - (int)((mHSV[0] / 360.0f) * PALETTE_DIM);

				mSwatchNew.setColor(Color.HSVToColor(mHSV));

				setSatValPalette();

				invalidate();
			}
			else if (mMethod == METHOD_UV_Y_PALETTE) {
				//slider *must* equal 1

				mYUV[0] += (increase ? jump : -jump) / 256.0f;
				mYUV[0] = pinToUnit(mYUV[0]);
				updateAllFromYUV();
				mCoord[0] = PALETTE_DIM - (int)(mYUV[0]  * PALETTE_DIM);

				mSwatchNew.setColor(Color.HSVToColor(mHSV));

				setUVPalette();

				invalidate();
			}
			else if (mMethod == METHOD_RGB_SLIDERS) {
				int color = Color.HSVToColor(mHSV);
				if (slider == 0)
					mRGB[slider] = Color.red(color) + (increase ? jump : -jump);
				else if (slider == 1)
					mRGB[slider] = Color.green(color) + (increase ? jump : -jump);
				else mRGB[slider] = Color.blue(color) + (increase ? jump : -jump);

				mRGB[slider] = pinToByte(mRGB[slider]);
				updateAllFromRGB();
				mCoord[slider] = (int)(PALETTE_DIM * (mRGB[slider] / 255.0f));

				mSwatchNew.setColor(Color.HSVToColor(mHSV));

				if (slider != 0)
					setHorRSlider();
				if (slider != 1)
					setHorGSlider();
				if (slider != 2)
					setHorBSlider();

				invalidate();
			}
			else if (mMethod == METHOD_HSV_SLIDERS) {
				if (slider == 0) {
					mHSV[slider] += ((increase ? jump : -jump) / 256.0f) * 360.0f;
					mHSV[slider] = pin(mHSV[slider], 360);
					mCoord[slider] = (int)((mHSV[slider] / 360.0f) * PALETTE_DIM);
				}
				else {
					mHSV[slider] += (increase ? jump : -jump) / 256.0f;
					mHSV[slider] = pinToUnit(mHSV[slider]);
					mCoord[slider] = (int)(mHSV[slider] * PALETTE_DIM);
				}

				updateAllFromHSV();
				mSwatchNew.setColor(Color.HSVToColor(mHSV));

				if (slider == 0) {
					setHorSatSlider();
					setHorValSlider();
				}
				else if (slider == 1) {
					setHorSatFader();
					setHorValSlider();
				}
				else if (slider == 2) {
					setHorValDimmer();
					setHorSatSlider();
				}

				invalidate();
			}
			else if (mMethod == METHOD_YUV_SLIDERS) {
				mYUV[slider] += (increase ? jump : -jump) / 256.0f;
				if (slider == 0)
					mYUV[slider] = pinToUnit(mYUV[slider]);
				else mYUV[slider] = pin(mYUV[slider], -.5f, .5f);

				updateAllFromYUV();

				if (slider == 0)
					mCoord[slider] = (int)(mYUV[slider]  * PALETTE_DIM);
				else mCoord[slider] = (int)((mYUV[slider] + .5f)  * PALETTE_DIM);

				mSwatchNew.setColor(Color.HSVToColor(mHSV));

				if (slider != 0)
					setHorYSlider();
				if (slider != 1)
					setHorUSlider();
				if (slider != 2)
					setHorVSlider();

				invalidate();
			}
		}

		//NEW_METHOD_WORK_NEEDED_HERE
		//If the new method doesn't operate on HSV (specifically, on the variable mHSV), and also doesn't operate on
		//mRGB or mYUV, which are already implemented here, then the pattern below needs to be replicated for the additional colorspace.
		//Namely, it is critical that all representations (HSV, RGB, YUV, Hex, etc.) be maintained at all times.
		/**
		 * Keep all colorspace representations in sync.
		 */
		private void updateHSVfromRGB() {
			Color.RGBToHSV(mRGB[0], mRGB[1], mRGB[2], mHSV);
			if (isGray(mRGB))
				mHSV[1] = 0;
		}

		/**
		 * Keep all colorspace representations in sync.
		 */
		private void updateHSVfromYUV() {
			ColorMatrix cm = new ColorMatrix();
			cm.setYUV2RGB();
			final float[] a = cm.getArray();
			matrixProductToByte(a, mYUV, mRGB);
			updateHSVfromRGB();
		}

		/**
		 * Keep all colorspace representations in sync.
		 */
		private void updateRGBfromHSV() {
			int color = Color.HSVToColor(mHSV);
			mRGB[0] = Color.red(color);
			mRGB[1] = Color.green(color);
			mRGB[2] = Color.blue(color);
		}

		/**
		 * Keep all colorspace representations in sync.
		 */
		private void updateRGBfromYUV() {
			ColorMatrix cm = new ColorMatrix();
			cm.setYUV2RGB();
			final float[] a = cm.getArray();
			matrixProductToByte(a, mYUV, mRGB);
		}

		/**
		 * Keep all colorspace representations in sync.
		 */
		private void updateYUVfromRGB() {
			float r = mRGB[0] / 255.0f;
			float g = mRGB[1] / 255.0f;
			float b = mRGB[2] / 255.0f;

			ColorMatrix cm = new ColorMatrix();
			cm.setRGB2YUV();
			final float[] a = cm.getArray();

			mYUV[0] = a[0] * r + a[1] * g + a[2] * b;
			mYUV[0] = pinToUnit(mYUV[0]);
			mYUV[1] = a[5] * r + a[6] * g + a[7] * b;
			mYUV[1] = pin(mYUV[1], -.5f, .5f);
			mYUV[2] = a[10] * r + a[11] * g + a[12] * b;
			mYUV[2] = pin(mYUV[2], -.5f, .5f);
		}

		/**
		 * Keep all colorspace representations in sync.
		 */
		private void updateHexFromHSV() {
			//For now, assume 100% opacity
			mHexStr = Integer.toHexString(Color.HSVToColor(mHSV)).toUpperCase();
			mHexStr = mHexStr.substring(2, mHexStr.length());
		}

		/**
		 * Keep all colorspace representations in sync.
		 */
		private void updateAllFromHSV() {
			//Update mRGB
			if (mRGBenabled || mYUVenabled)
				updateRGBfromHSV();

			//Update mYUV
			if (mYUVenabled)
				updateYUVfromRGB();

			//Update mHexStr
			if (mRGBenabled)
				updateHexFromHSV();
		}

		/**
		 * Keep all colorspace representations in sync.
		 */
		private void updateAllFromRGB() {
			//Update mHSV
			if (mHSVenabled || mHexenabled)
				updateHSVfromRGB();

			//Update mYUV
			if (mYUVenabled)
				updateYUVfromRGB();

			//Update mHexStr
			if (mHexenabled)
				updateHexFromHSV();
		}

		/**
		 * Keep all colorspace representations in sync.
		 */
		private void updateAllFromYUV() {
			//Update mRGB
			if (mRGBenabled || mHSVenabled || mHexenabled)
				updateRGBfromYUV();

			//Update mYUV
			if (mHSVenabled)
				updateHSVfromRGB();

			//Update mHexStr
			if (mHexenabled)
				updateHexFromHSV();
		}

		/**
		 * Process touch events: down, move, and up
		 */
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			float x = event.getX();
			float y = event.getY();

			//Generate coordinates which are palette=local with the origin at the upper left of the main 2D palette
			int x2 = (int)(pin(round(x - PALETTE_POS_X), PALETTE_DIM));
			int y2 = (int)(pin(round(y - PALETTE_POS_Y), PALETTE_DIM));

			//Generate coordinates which are palette-local with the origin at the center of the main 2D palette
			float circlePinnedX = x - PALETTE_POS_X - PALETTE_CENTER_X;
			float circlePinnedY = y - PALETTE_POS_Y - PALETTE_CENTER_Y;

			//Is the event in a swatch?
			boolean inSwatchOld = ptInRect(round(x), round(y), mOldSwatchRect);
			boolean inSwatchNew = ptInRect(round(x), round(y), mNewSwatchRect);

			//Is the event in a method selector icon?
			boolean inMethodSelector[] = new boolean[NUM_ENABLED_METHODS];
			for (int i = 0; i < NUM_ENABLED_METHODS; i++)
				inMethodSelector[i] = false;
			for (int i = 0; i < NUM_ENABLED_METHODS; i++) {
				if (ptInRect(round(x), round(y), mMethodSelectorRects[i])) {
					inMethodSelector[i] = true;
					break;
				}
			}

			//Get the event's distance from the center of the main 2D palette
			float radius = (float)java.lang.Math.sqrt(circlePinnedX * circlePinnedX + circlePinnedY * circlePinnedY);

			//Is the event in a circle-pinned 2D palette?
			boolean inOvalPalette = radius <= PALETTE_RADIUS;

			//Pin the radius
			if (radius > PALETTE_RADIUS)
				radius = PALETTE_RADIUS;

			//Is the event in a square palette
			boolean inSquarePalette = ptInRect(round(x), round(y), mPaletteRect);

			//Is the event in a vertical slider to the right of the main 2D palette
			boolean inVerSlider = ptInRect(round(x), round(y), mVerSliderRect);

			//Is the event in a horizontal slider within the main "palette's" region
			boolean inFirstHorSlider = ptInRect(round(x), round(y), mHorSliderRects[0]);
			boolean inSecondHorSlider = ptInRect(round(x), round(y), mHorSliderRects[1]);
			boolean inThirdHorSlider = ptInRect(round(x), round(y), mHorSliderRects[2]);

			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					mTracking = TRACKED_NONE;

					if (inSwatchOld)
						mTracking = TRACK_SWATCH_OLD;
					else if (inSwatchNew)
						mTracking = TRACK_SWATCH_NEW;

					//NEW_METHOD_WORK_NEEDED_HERE
					//To add a new method, replicate and extend the last entry in this list
					else if (NUM_ENABLED_METHODS > 0 && inMethodSelector[0])
						mTracking = mMethodSelectRectMap[0];
					else if (NUM_ENABLED_METHODS > 1 && inMethodSelector[1])
						mTracking = mMethodSelectRectMap[1];
					else if (NUM_ENABLED_METHODS > 2 && inMethodSelector[2])
						mTracking = mMethodSelectRectMap[2];
					else if (NUM_ENABLED_METHODS > 3 && inMethodSelector[3])
						mTracking = mMethodSelectRectMap[3];
					else if (NUM_ENABLED_METHODS > 4 && inMethodSelector[4])
						mTracking = mMethodSelectRectMap[4];
					else if (NUM_ENABLED_METHODS > 5 && inMethodSelector[5])
						mTracking = mMethodSelectRectMap[5];
					else if (NUM_ENABLED_METHODS > 6 && inMethodSelector[6])
						mTracking = mMethodSelectRectMap[6];

					//NEW_METHOD_WORK_NEEDED_HERE
					//To add a new method, replicate and extend the last entry in this list
					else if (mMethod == METHOD_HS_V_PALETTE) {
						if (inOvalPalette) {
							mTracking = TRACK_HS_PALETTE;
							mFocusedControl = 0;
						}
						else if (inVerSlider) {
							mTracking = TRACK_VER_VALUE_SLIDER;
							mFocusedControl = 1;
						}
					}
					else if (mMethod == METHOD_HV_S_PALETTE) {
						if (inOvalPalette) {
							mTracking = TRACK_HV_PALETTE;
							mFocusedControl = 0;
						}
						else if (inVerSlider) {
							mTracking = TRACK_VER_S_SLIDER;
							mFocusedControl = 1;
						}
					}
					else if (mMethod == METHOD_SV_H_PALETTE) {
						if (inSquarePalette) {
							mTracking = TRACK_SV_PALETTE;
							mFocusedControl = 0;
						}
						else if (inVerSlider) {
							mTracking = TRACK_VER_H_SLIDER;
							mFocusedControl = 1;
						}
					}
					else if (mMethod == METHOD_UV_Y_PALETTE) {
						if (inSquarePalette) {
							mTracking = TRACK_UV_PALETTE;
							mFocusedControl = 0;
						}
						else if (inVerSlider) {
							mTracking = TRACK_VER_Y_SLIDER;
							mFocusedControl = 1;
						}
					}
					else if (mMethod == METHOD_RGB_SLIDERS) {
						if (inFirstHorSlider) {
							mTracking = TRACK_R_SLIDER;
							mFocusedControl = 0;
						}
						else if (inSecondHorSlider) {
							mTracking = TRACK_G_SLIDER;
							mFocusedControl = 1;
						}
						else if (inThirdHorSlider) {
							mTracking = TRACK_B_SLIDER;
							mFocusedControl = 2;
						}
					}
					else if (mMethod == METHOD_HSV_SLIDERS) {
						if (inFirstHorSlider) {
							mTracking = TRACK_H_SLIDER;
							mFocusedControl = 0;
						}
						else if (inSecondHorSlider) {
							mTracking = TRACK_S_SLIDER;
							mFocusedControl = 1;
						}
						else if (inThirdHorSlider) {
							mTracking = TRACK_HOR_VALUE_SLIDER;
							mFocusedControl = 2;
						}
					}
					else if (mMethod == METHOD_YUV_SLIDERS) {
						if (inFirstHorSlider) {
							mTracking = TRACK_HOR_Y_SLIDER;
							mFocusedControl = 0;
						}
						else if (inSecondHorSlider) {
							mTracking = TRACK_U_SLIDER;
							mFocusedControl = 1;
						}
						else if (inThirdHorSlider) {
							mTracking = TRACK_V_SLIDER;
							mFocusedControl = 2;
						}
					}
				case MotionEvent.ACTION_MOVE:
					//NEW_METHOD_WORK_NEEDED_HERE
					//To add a new method, replicate and extend the entries in this list,
					//one per UI controller the new method requires.
					if (mTracking == TRACK_HS_PALETTE) {
						float angle = (float)java.lang.Math.atan2(circlePinnedY, circlePinnedX);
						// need to turn angle [-PI ... PI] into unit [0....1]
						float unit = angle/(2*PI);
						if (unit < 0) {
							unit += 1;
						}

						mCoord[0] = round(Math.cos(angle) * radius);
						mCoord[1] = round(Math.sin(angle) * radius);

						int c = interpColor(mSpectrumColorsRev, unit);
						float[] hsv = new float[3];
						Color.colorToHSV(c, hsv);
						mHSV[0] = hsv[0];
						mHSV[1] = radius / PALETTE_RADIUS;
						updateAllFromHSV();
						mSwatchNew.setColor(Color.HSVToColor(mHSV));

						setVerValSlider();

						invalidate();
					}
					else if (mTracking == TRACK_VER_VALUE_SLIDER) {
						if (mCoord[2] != y2) {
							mCoord[2] = y2;
							float value = 1.0f - (float)y2 / (float)PALETTE_DIM;

							mHSV[2] = value;
							updateAllFromHSV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setOvalValDimmer();

							invalidate();
						}
					}
					else if (mTracking == TRACK_HV_PALETTE) {
						float angle = (float)java.lang.Math.atan2(circlePinnedY, circlePinnedX);
						// need to turn angle [-PI ... PI] into unit [0....1]
						float unit = angle/(2*PI);
						if (unit < 0) {
							unit += 1;
						}

						mCoord[0] = round(Math.cos(angle) * radius);
						mCoord[2] = round(Math.sin(angle) * radius);

						int c = interpColor(mSpectrumColorsRev, unit);
						float[] hsv = new float[3];
						Color.colorToHSV(c, hsv);
						mHSV[0] = hsv[0];
						mHSV[2] = radius / PALETTE_RADIUS;
						updateAllFromHSV();
						mSwatchNew.setColor(Color.HSVToColor(mHSV));

						setOvalSatFader();
						setVerSatSlider();

						invalidate();
					}
					else if (mTracking == TRACK_VER_S_SLIDER) {
						if (mCoord[1] != y2) {
							mCoord[1] = y2;
							float value = 1.0f - (float)y2 / (float)PALETTE_DIM;

							mHSV[1] = value;
							updateAllFromHSV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setOvalSatFader();

							invalidate();
						}
					}
					 else if (mTracking == TRACK_SV_PALETTE) {
						if (mCoord[1] != y2 || mCoord[2] != x2) {
						 	mCoord[1] = y2;
						 	mCoord[2] = x2;

							mHSV[1] = (float)(PALETTE_DIM - mCoord[1]) / (float)PALETTE_DIM;
	 						mHSV[2] = (float)mCoord[2] / (float)PALETTE_DIM;
							updateAllFromHSV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorSatFader();
							setHorValDimmer();

	 						invalidate();
						}
					 }
					 else if (mTracking == TRACK_VER_H_SLIDER) {
						if (mCoord[0] != y2) {
							mCoord[0] = y2;
							float hue = 360.0f - 360.0f * ((float)y2 / (float)PALETTE_DIM);

							mHSV[0] = hue;
							updateAllFromHSV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setSatValPalette();

							invalidate();
						}
					}
					 else if (mTracking == TRACK_UV_PALETTE) {
						if (mCoord[1] != y2 || mCoord[2] != x2) {
						 	mCoord[1] = x2;
						 	mCoord[2] = y2;

							mYUV[1] = ((float)mCoord[1] / (float)PALETTE_DIM) - .5f;
							mYUV[2] = ((float)(PALETTE_DIM - mCoord[2]) / (float)PALETTE_DIM) - .5f;
							updateAllFromYUV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setVerYSlider();

	 						invalidate();
						}
					 }
					 else if (mTracking == TRACK_VER_Y_SLIDER) {
						if (mCoord[0] != y2) {
							mCoord[0] = y2;

							mYUV[0] = 1.0f - (float)mCoord[0] / (float)PALETTE_DIM;
							updateAllFromYUV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setUVPalette();

							invalidate();
						}
					}
					else if (mTracking == TRACK_R_SLIDER) {
						if (mCoord[0] != x2) {
							mCoord[0] = x2;
							int int255 = sliderPosTo255(mCoord[0]);

							mRGB[0] = int255;
							updateAllFromRGB();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorGSlider();
							setHorBSlider();

							invalidate();
						}
					}
					else if (mTracking == TRACK_G_SLIDER) {
						if (mCoord[1] != x2) {
							mCoord[1] = x2;
							int int255 = sliderPosTo255(mCoord[1]);

							mRGB[1] = int255;
							updateAllFromRGB();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorRSlider();
							setHorBSlider();

							invalidate();
						}
					}
					else if (mTracking == TRACK_B_SLIDER) {
						if (mCoord[2] != x2) {
							mCoord[2] = x2;
							int int255 = sliderPosTo255(mCoord[2]);

							mRGB[2] = int255;
							updateAllFromRGB();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorRSlider();
							setHorGSlider();

							invalidate();
						}
					}
					else if (mTracking == TRACK_H_SLIDER) {
						if (mCoord[0] != x2) {
							mCoord[0] = x2;
							float hue = 360.0f * ((float)mCoord[0] / (float)PALETTE_DIM);

							mHSV[0] = hue;
							updateAllFromHSV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorSatSlider();
							setHorValSlider();

							invalidate();
						}
					}
					else if (mTracking == TRACK_S_SLIDER) {
						if (mCoord[1] != x2) {
							mCoord[1] = x2;
							float sat = (float)mCoord[1] / (float)PALETTE_DIM;

							mHSV[1] = sat;
							updateAllFromHSV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorSatFader();
							setHorValSlider();

							invalidate();
						}
					}
					else if (mTracking == TRACK_HOR_VALUE_SLIDER) {
						if (mCoord[2] != x2) {
							mCoord[2] = x2;
							float val = (float)mCoord[2] / (float)PALETTE_DIM;

							mHSV[2] = val;
							updateAllFromHSV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorValDimmer();
							setHorSatSlider();

							invalidate();
						}
					}
					else if (mTracking == TRACK_HOR_Y_SLIDER) {
						if (mCoord[0] != x2) {
							mCoord[0] = x2;

							mYUV[0] = (float)mCoord[0] / (float)PALETTE_DIM;
							updateAllFromYUV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorUSlider();
							setHorVSlider();

							invalidate();
						}
					}
					else if (mTracking == TRACK_U_SLIDER) {
						if (mCoord[1] != x2) {
							mCoord[1] = x2;

							mYUV[1] = ((float)mCoord[1] / (float)PALETTE_DIM) - .5f;
							updateAllFromYUV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorYSlider();
							setHorVSlider();

							invalidate();
						}
					}
					else if (mTracking == TRACK_V_SLIDER) {
						if (mCoord[2] != x2) {
							mCoord[2] = x2;

							mYUV[2] = ((float)mCoord[2] / (float)PALETTE_DIM) - .5f;
							updateAllFromYUV();
							mSwatchNew.setColor(Color.HSVToColor(mHSV));

							setHorYSlider();
							setHorUSlider();

							invalidate();
						}
					}
					break;
				case MotionEvent.ACTION_UP:
					//NEW_METHOD_WORK_NEEDED_HERE
					//To add a new method, replicate and extend the last entry in this list.
					if (mTracking == TRACK_SWATCH_OLD && inSwatchOld) {
						Color.colorToHSV(mOriginalColor, mHSV);
						if (isGray(mOriginalColor))
							mHSV[1] = 0;
						mSwatchNew.setColor(mOriginalColor);
						initUI();
						invalidate();
					}
					else if (mTracking == TRACK_SWATCH_NEW && inSwatchNew) {
						mListener.colorChanged(mSwatchNew.getColor());
						invalidate();
					}
					else if (NUM_ENABLED_METHODS > 0 && mTracking == mMethodSelectRectMap[0] && inMethodSelector[0]) {
						mMethod = mMethodSelectRectMap[0];
						initUI();
						invalidate();
					}
					else if (NUM_ENABLED_METHODS > 1 && mTracking == mMethodSelectRectMap[1] && inMethodSelector[1]) {
						mMethod = mMethodSelectRectMap[1];
						initUI();
						invalidate();
					}
					else if (NUM_ENABLED_METHODS > 2 && mTracking == mMethodSelectRectMap[2] && inMethodSelector[2]) {
						mMethod = mMethodSelectRectMap[2];
						initUI();
						invalidate();
					}
					else if (NUM_ENABLED_METHODS > 3 && mTracking == mMethodSelectRectMap[3] && inMethodSelector[3]) {
						mMethod = mMethodSelectRectMap[3];
						initUI();
						invalidate();
					}
					else if (NUM_ENABLED_METHODS > 4 && mTracking == mMethodSelectRectMap[4] && inMethodSelector[4]) {
						mMethod = mMethodSelectRectMap[4];
						initUI();
						invalidate();
					}
					else if (NUM_ENABLED_METHODS > 5 && mTracking == mMethodSelectRectMap[5] && inMethodSelector[5]) {
						mMethod = mMethodSelectRectMap[5];
						initUI();
						invalidate();
					}
					else if (NUM_ENABLED_METHODS > 6 && mTracking == mMethodSelectRectMap[6] && inMethodSelector[6]) {
						mMethod = mMethodSelectRectMap[6];
						initUI();
						invalidate();
					}

					mTracking= TRACKED_NONE;
					break;
			}

			return true;
		}
	}
}
