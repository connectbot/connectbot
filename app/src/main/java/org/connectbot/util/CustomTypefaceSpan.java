/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Kenny Root, Jeffrey Sharkey
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

package org.connectbot.util;


import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

public class CustomTypefaceSpan extends MetricAffectingSpan {

	private final Typeface typeface;

	public CustomTypefaceSpan(Typeface typeface) {
		this.typeface = typeface;
	}

	@Override
	public void updateDrawState(TextPaint ds) {
		applyCustomTypeFace(ds, typeface);
	}

	@Override
	public void updateMeasureState(TextPaint paint) {
		applyCustomTypeFace(paint, typeface);
	}

	private static void applyCustomTypeFace(Paint paint, Typeface tf) {
		paint.setTypeface(tf);
	}
}
