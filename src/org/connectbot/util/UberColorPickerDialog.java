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

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ComposeShader;
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

/**
 * UberColorPickerDialog is a seriously enhanced version of the UberColorPickerDialog
 * class provided in the Android API Demos.<p>
 *
 * NOTE (from Kenny Root): This is a VERY slimmed down version custom for ConnectBot.
 * Visit Keith's site for the full version at the URL listed in the author line.<p>
 *
 * @author Keith Wiley, kwiley@keithwiley.com, http://keithwiley.com
 */
public class UberColorPickerDialog extends Dialog {
	private OnColorChangedListener mListener;
	private int mInitialColor;

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
							int initialColor) {
		super(context);

		mListener = listener;
		mInitialColor = initialColor;
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

		setTitle("Pick a color (try the trackball)");

		try {
			setContentView(new ColorPickerView(getContext(), l, screenWidth, screenHeight, mInitialColor));
		}
		catch (Exception e) {
			//There is currently only one kind of ctor exception, that where no methods are enabled.
			dismiss();	//This doesn't work!  The dialog is still shown (its title at least, the layout is empty from the exception being thrown).  <sigh>
		}
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

		private static final int SLIDER_THICKNESS = 40;

		private static int VIEW_DIM_X = PALETTE_DIM;
		private static int VIEW_DIM_Y = SWATCH_HEIGHT;

		//NEW_METHOD_WORK_NEEDED_HERE
		private static final int METHOD_HS_V_PALETTE = 0;

		//NEW_METHOD_WORK_NEEDED_HERE
		//Add a new entry to the list for each controller in the new method
		private static final int TRACKED_NONE = -1;	//No object on screen is currently being tracked
		private static final int TRACK_SWATCH_OLD = 10;
		private static final int TRACK_SWATCH_NEW = 11;
		private static final int TRACK_HS_PALETTE = 30;
		private static final int TRACK_VER_VALUE_SLIDER = 31;

		private static final int TEXT_SIZE = 12;
		private static int[] TEXT_HSV_POS = new int[2];
		private static int[] TEXT_RGB_POS = new int[2];
		private static int[] TEXT_YUV_POS = new int[2];
		private static int[] TEXT_HEX_POS = new int[2];

		private static final float PI = 3.141592653589793f;

		private int mMethod = METHOD_HS_V_PALETTE;
		private int mTracking = TRACKED_NONE;	//What object on screen is currently being tracked for movement

		//Zillions of persistant Paint objecs for drawing the View

		private Paint mSwatchOld, mSwatchNew;

		//NEW_METHOD_WORK_NEEDED_HERE
		//Add Paints to represent the palettes of the new method's UI controllers
		private Paint mOvalHueSat;

		private Bitmap mVerSliderBM;
		private Canvas mVerSliderCv;

		private Bitmap[] mHorSlidersBM = new Bitmap[3];
		private Canvas[] mHorSlidersCv = new Canvas[3];

		private Paint mValDimmer;

		//NEW_METHOD_WORK_NEEDED_HERE
		//Add Paints to represent the icon for the new method
		private Paint mOvalHueSatSmall;

		private Paint mPosMarker;
		private Paint mText;

		private Rect mOldSwatchRect = new Rect();
		private Rect mNewSwatchRect = new Rect();
		private Rect mPaletteRect = new Rect();
		private Rect mVerSliderRect = new Rect();

		private int[] mSpectrumColorsRev;
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

			mListener = l;

			mOriginalColor = color;

			Color.colorToHSV(color, mHSV);

			updateAllFromHSV();

			//Setup the layout based on whether this is a portrait or landscape orientation.
			if (width <= height) {	//Portrait layout
				SWATCH_WIDTH = (PALETTE_DIM + SLIDER_THICKNESS) / 2;

				PALETTE_POS_X = 0;
				PALETTE_POS_Y = TEXT_SIZE * 4 + SWATCH_HEIGHT;

				//Set more rects, lots of rects
				mOldSwatchRect.set(0, TEXT_SIZE * 4, SWATCH_WIDTH, TEXT_SIZE * 4 + SWATCH_HEIGHT);
				mNewSwatchRect.set(SWATCH_WIDTH, TEXT_SIZE * 4, SWATCH_WIDTH * 2, TEXT_SIZE * 4 + SWATCH_HEIGHT);
				mPaletteRect.set(0, PALETTE_POS_Y, PALETTE_DIM, PALETTE_POS_Y + PALETTE_DIM);
				mVerSliderRect.set(PALETTE_DIM, PALETTE_POS_Y, PALETTE_DIM + SLIDER_THICKNESS, PALETTE_POS_Y + PALETTE_DIM);

				TEXT_HSV_POS[0] = 3;
				TEXT_HSV_POS[1] = 0;
				TEXT_RGB_POS[0] = TEXT_HSV_POS[0] + 50;
				TEXT_RGB_POS[1] = TEXT_HSV_POS[1];
				TEXT_YUV_POS[0] = TEXT_HSV_POS[0] + 100;
				TEXT_YUV_POS[1] = TEXT_HSV_POS[1];
				TEXT_HEX_POS[0] = TEXT_HSV_POS[0] + 150;
				TEXT_HEX_POS[1] = TEXT_HSV_POS[1];

				VIEW_DIM_X = PALETTE_DIM + SLIDER_THICKNESS;
				VIEW_DIM_Y = SWATCH_HEIGHT + PALETTE_DIM + TEXT_SIZE * 4;
			}
			else {	//Landscape layout
				SWATCH_WIDTH = 110;

				PALETTE_POS_X = SWATCH_WIDTH;
				PALETTE_POS_Y = 0;

				//Set more rects, lots of rects
				mOldSwatchRect.set(0, TEXT_SIZE * 7, SWATCH_WIDTH, TEXT_SIZE * 7 + SWATCH_HEIGHT);
				mNewSwatchRect.set(0, TEXT_SIZE * 7 + SWATCH_HEIGHT, SWATCH_WIDTH, TEXT_SIZE * 7 + SWATCH_HEIGHT * 2);
				mPaletteRect.set(SWATCH_WIDTH, PALETTE_POS_Y, SWATCH_WIDTH + PALETTE_DIM, PALETTE_POS_Y + PALETTE_DIM);
				mVerSliderRect.set(SWATCH_WIDTH + PALETTE_DIM, PALETTE_POS_Y, SWATCH_WIDTH + PALETTE_DIM + SLIDER_THICKNESS, PALETTE_POS_Y + PALETTE_DIM);

				TEXT_HSV_POS[0] = 3;
				TEXT_HSV_POS[1] = 0;
				TEXT_RGB_POS[0] = TEXT_HSV_POS[0];
				TEXT_RGB_POS[1] = (int)(TEXT_HSV_POS[1] + TEXT_SIZE * 3.5);
				TEXT_YUV_POS[0] = TEXT_HSV_POS[0] + 50;
				TEXT_YUV_POS[1] = (int)(TEXT_HSV_POS[1] + TEXT_SIZE * 3.5);
				TEXT_HEX_POS[0] = TEXT_HSV_POS[0] + 50;
				TEXT_HEX_POS[1] = TEXT_HSV_POS[1];

				VIEW_DIM_X = PALETTE_POS_X + PALETTE_DIM + SLIDER_THICKNESS;
				VIEW_DIM_Y = Math.max(mNewSwatchRect.bottom, PALETTE_DIM);
			}

			//Rainbows make everybody happy!
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

			Shader shaderA = new SweepGradient(0, 0, mSpectrumColorsRev, null);
			Shader shaderB = new RadialGradient(0, 0, PALETTE_CENTER_X, 0xFFFFFFFF, 0xFF000000, Shader.TileMode.CLAMP);
			Shader shader = new ComposeShader(shaderA, shaderB, PorterDuff.Mode.SCREEN);
			mOvalHueSat = new Paint(Paint.ANTI_ALIAS_FLAG);
			mOvalHueSat.setShader(shader);
			mOvalHueSat.setStyle(Paint.Style.FILL);
			mOvalHueSat.setDither(true);

			mVerSliderBM = Bitmap.createBitmap(SLIDER_THICKNESS, PALETTE_DIM, Bitmap.Config.RGB_565);
			mVerSliderCv = new Canvas(mVerSliderBM);

			for (int i = 0; i < 3; i++) {
				mHorSlidersBM[i] = Bitmap.createBitmap(PALETTE_DIM, SLIDER_THICKNESS, Bitmap.Config.RGB_565);
				mHorSlidersCv[i] = new Canvas(mHorSlidersBM[i]);
			}

			mValDimmer = new Paint(Paint.ANTI_ALIAS_FLAG);
			mValDimmer.setStyle(Paint.Style.FILL);
			mValDimmer.setDither(true);
			mValDimmer.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));

			//Whew, we're done making the big Paints and Shaders for the swatches, palettes, and sliders.
			//Now we need to make the Paints and Shaders that will draw the little method icons in the method selector list.

			//NEW_METHOD_WORK_NEEDED_HERE
			//Add Paints to represent the icon for the new method

			shaderA = new SweepGradient(0, 0, mSpectrumColorsRev, null);
			shaderB = new RadialGradient(0, 0, PALETTE_DIM / 2, 0xFFFFFFFF, 0xFF000000, Shader.TileMode.CLAMP);
			shader = new ComposeShader(shaderA, shaderB, PorterDuff.Mode.SCREEN);
			mOvalHueSatSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
			mOvalHueSatSmall.setShader(shader);
			mOvalHueSatSmall.setStyle(Paint.Style.FILL);

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
			if (mMethod == METHOD_HS_V_PALETTE)
				drawHSV1Palette(canvas);
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
		 * Frame the 2D palette to indicate that it has trackball focus.
		 * @param canvas
		 */
		private void hilightFocusedOvalPalette(Canvas canvas) {
			mPosMarker.setColor(Color.WHITE);
			canvas.drawOval(new RectF(-PALETTE_RADIUS, -PALETTE_RADIUS, PALETTE_RADIUS, PALETTE_RADIUS), mPosMarker);
			mPosMarker.setColor(Color.BLACK);
			canvas.drawOval(new RectF(-PALETTE_RADIUS + 2, -PALETTE_RADIUS + 2, PALETTE_RADIUS - 2, PALETTE_RADIUS - 2), mPosMarker);
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
		 * Initialize the current color chooser's UI (set its color parameters and set its palette and slider values accordingly).
		 */
		private void initUI() {
			initHSV1Palette();

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
		 * Report the correct tightly bounded dimensions of the view.
		 */
		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			setMeasuredDimension(VIEW_DIM_X, VIEW_DIM_Y);
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
		 * Process touch events: down, move, and up
		 */
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			float x = event.getX();
			float y = event.getY();

			//Generate coordinates which are palette=local with the origin at the upper left of the main 2D palette
			int y2 = (int)(pin(round(y - PALETTE_POS_Y), PALETTE_DIM));

			//Generate coordinates which are palette-local with the origin at the center of the main 2D palette
			float circlePinnedX = x - PALETTE_POS_X - PALETTE_CENTER_X;
			float circlePinnedY = y - PALETTE_POS_Y - PALETTE_CENTER_Y;

			//Is the event in a swatch?
			boolean inSwatchOld = ptInRect(round(x), round(y), mOldSwatchRect);
			boolean inSwatchNew = ptInRect(round(x), round(y), mNewSwatchRect);

			//Get the event's distance from the center of the main 2D palette
			float radius = (float)java.lang.Math.sqrt(circlePinnedX * circlePinnedX + circlePinnedY * circlePinnedY);

			//Is the event in a circle-pinned 2D palette?
			boolean inOvalPalette = radius <= PALETTE_RADIUS;

			//Pin the radius
			if (radius > PALETTE_RADIUS)
				radius = PALETTE_RADIUS;

			//Is the event in a vertical slider to the right of the main 2D palette
			boolean inVerSlider = ptInRect(round(x), round(y), mVerSliderRect);

			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					mTracking = TRACKED_NONE;

					if (inSwatchOld)
						mTracking = TRACK_SWATCH_OLD;
					else if (inSwatchNew)
						mTracking = TRACK_SWATCH_NEW;

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
					break;
				case MotionEvent.ACTION_UP:
					//NEW_METHOD_WORK_NEEDED_HERE
					//To add a new method, replicate and extend the last entry in this list.
					if (mTracking == TRACK_SWATCH_OLD && inSwatchOld) {
						Color.colorToHSV(mOriginalColor, mHSV);
						mSwatchNew.setColor(mOriginalColor);
						initUI();
						invalidate();
					}
					else if (mTracking == TRACK_SWATCH_NEW && inSwatchNew) {
						mListener.colorChanged(mSwatchNew.getColor());
						invalidate();
					}

					mTracking= TRACKED_NONE;
					break;
			}

			return true;
		}
	}
}
