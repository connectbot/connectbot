/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2015 Kenny Root, Jeffrey Sharkey
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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

public abstract class AppCompatListActivity extends AppCompatActivity {
	protected ItemAdapter mAdapter;

	protected View mEmptyView;
	protected RecyclerView mListView;

	/**
	 * If the list is empty, hides the list and shows the empty message; otherwise, shows
	 * the list and hides the empty message.
	 */
	protected void adjustViewVisibility() {
		boolean isEmpty = mAdapter.getItemCount() == 0;
		mListView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
		mEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
	}

	/**
	 * Item decorations for list items, which adds a divider between items and leaves a
	 * small offset at the top of the list to adhere to the Material Design spec.
	 */
	protected class ListItemDecoration extends RecyclerView.ItemDecoration {
		private final int[] ATTRS = new int[] { android.R.attr.listDivider };

		private final int TOP_LIST_OFFSET = 8;

		private Drawable mDivider;

		public ListItemDecoration(Context c) {
			final TypedArray a = c.obtainStyledAttributes(ATTRS);
			mDivider = a.getDrawable(0);
			a.recycle();
		}

		@Override
		public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
			final int left = parent.getPaddingLeft();
			final int right = parent.getWidth() - parent.getPaddingRight();

			final int childCount = parent.getChildCount();
			for (int i = 0; i < childCount; i++) {
				final View child = parent.getChildAt(i);
				final RecyclerView.LayoutParams params =
						(RecyclerView.LayoutParams) child.getLayoutParams();
				final int top = child.getBottom() + params.bottomMargin;
				final int bottom = top + mDivider.getIntrinsicHeight();
				mDivider.setBounds(left, top, right, bottom);
				mDivider.draw(c);
			}
		}

		@Override
		public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
				RecyclerView.State state) {
			int top = parent.getChildAdapterPosition(view) == 0 ? TOP_LIST_OFFSET : 0;
			outRect.set(0, top, 0, mDivider.getIntrinsicHeight());
		}
	}

	protected abstract class ItemViewHolder extends RecyclerView.ViewHolder
			implements View.OnClickListener, View.OnCreateContextMenuListener {
		public ItemViewHolder(View v) {
			super(v);
			v.setOnClickListener(this);
			v.setOnCreateContextMenuListener(this);
		}
	}

	@VisibleForTesting
	protected static abstract class ItemAdapter extends RecyclerView.Adapter<ItemViewHolder> {
		protected final Context context;

		public ItemAdapter(Context context/*, List<AbstractBean> items*/) {
			this.context = context;
		}
	}
}
