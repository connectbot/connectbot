/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2016 Kenny Root, Jeffrey Sharkey
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

package org.connectbot.views;

import java.util.List;

import org.connectbot.R;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import androidx.appcompat.widget.SwitchCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Created by kenny on 5/31/16.
 */
public class CheckableMenuItem extends RelativeLayout {
	private static final String ACCESSIBILITY_EVENT_CLASS_NAME = "android.widget.Switch";

	private final Rect mPlaceHolderRect = new Rect(0, 0, 1, 1);
	private static final String PLACEHOLDER_STRING = "";

	private final View mRootView;
	private final TextView mTitle;
	private final TextView mSummary;
	private final SwitchCompat mSwitch;
	private final ExploreByTouchHelper mAccessHelper;

	public CheckableMenuItem(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CheckableMenuItem(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		mRootView = inflate(context, R.layout.view_checkablemenuitem, this);

		mTitle = mRootView.findViewById(R.id.title);
		mSummary = mRootView.findViewById(R.id.summary);
		mSwitch = findViewById(R.id.checkbox_switch);

		setFocusable(true);

		mAccessHelper = new ExploreByTouchHelper(this) {
			private final Rect mTmpRect = new Rect();

			@Override
			protected int getVirtualViewAt(float x, float y) {
				return HOST_ID;
			}

			@Override
			protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
			}

			@Override
			protected void onPopulateEventForVirtualView(int virtualViewId, AccessibilityEvent event) {
				if (virtualViewId != HOST_ID) {
					// TODO(kroot): remove this when the bug is fixed.
					event.setContentDescription(PLACEHOLDER_STRING);
					return;
				}

				event.setContentDescription(mTitle.getText() + " " + mSummary.getText());
				event.setClassName(ACCESSIBILITY_EVENT_CLASS_NAME);
				event.setChecked(isChecked());
			}

			@Override
			protected void onPopulateNodeForVirtualView(int virtualViewId, AccessibilityNodeInfoCompat node) {
				if (virtualViewId != HOST_ID) {
					// TODO(kroot): remove this when the bug is fixed.
					node.setBoundsInParent(mPlaceHolderRect);
					node.setContentDescription(PLACEHOLDER_STRING);
					return;
				}

				mTmpRect.set(0, 0, CheckableMenuItem.this.getWidth(), CheckableMenuItem.this.getHeight());
				node.setBoundsInParent(mTmpRect);

				node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
				node.setClassName(ACCESSIBILITY_EVENT_CLASS_NAME);
				node.setCheckable(true);
				node.setChecked(isChecked());

				node.addChild(mTitle);
				node.addChild(mSummary);
			}

			@Override
			protected boolean onPerformActionForVirtualView(int virtualViewId, int action, Bundle arguments) {
				if (virtualViewId != HOST_ID) {
					return false;
				}

				if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
					mSwitch.toggle();
					sendAccessibilityEvent(mRootView,
							AccessibilityEventCompat.CONTENT_CHANGE_TYPE_UNDEFINED);
					return true;
				}

				return false;
			}
		};
		ViewCompat.setAccessibilityDelegate(mRootView, mAccessHelper);

		setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mSwitch.toggle();
			}
		});

		if (attrs != null) {
			TypedArray typedArray = getContext().obtainStyledAttributes(
					attrs, R.styleable.CheckableMenuItem, 0, 0);

			@DrawableRes int iconRes = typedArray.getResourceId(
					R.styleable.CheckableMenuItem_android_icon, 0);
			@StringRes int titleRes = typedArray.getResourceId(
					R.styleable.CheckableMenuItem_android_title, 0);
			@StringRes int summaryRes = typedArray.getResourceId(
					R.styleable.CheckableMenuItem_summary, 0);

			typedArray.recycle();

			ImageView icon = mRootView.findViewById(R.id.icon);
			mTitle.setText(titleRes);
			if (iconRes != 0) {
				Resources resources = context.getResources();
				Resources.Theme theme = context.getTheme();
				Drawable iconDrawable = VectorDrawableCompat.create(resources, iconRes, theme);

				icon.setImageDrawable(iconDrawable);
			}
			if (summaryRes != 0) {
				mSummary.setText(summaryRes);
			}
		}
	}

	public boolean isChecked() {
		return mSwitch.isChecked();
	}

	public void setChecked(boolean checked) {
		mAccessHelper.sendAccessibilityEvent(mRootView,
				AccessibilityEventCompat.CONTENT_CHANGE_TYPE_UNDEFINED);
		mSwitch.setChecked(checked);
	}

	public void toggle() {
		mAccessHelper.sendAccessibilityEvent(mRootView,
				AccessibilityEventCompat.CONTENT_CHANGE_TYPE_UNDEFINED);
		mSwitch.toggle();
	}

	public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
		mSwitch.setOnCheckedChangeListener(listener);
	}
}
