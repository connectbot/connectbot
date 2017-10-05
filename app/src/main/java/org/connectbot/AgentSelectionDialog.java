/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright (C) 2017 Christian Hagau <ach@hagau.se>
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

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class AgentSelectionDialog extends DialogFragment {
	public static final String TAG = "CB.AgentSelectionDialog";
	private static final String AGENTLIST = "agentList";
	private static final String AGENTNAMELIST = "agentNameList";
	private Button mSelect;
	private Button mCancel;
	private RecyclerView mAgentRecyclerView;

	private int mSelection;

	private List<Drawable> mAgentIcons;
	private List<String> mAgentList;
	private List<String> mAgentNameList;
	private AgentAdapter mAgentAdapter;

	private HostEditorFragment mHostEditorFragment;

	public static AgentSelectionDialog newInstance(List<String> agentList,
			List<String> agentNameList) {
		Bundle args = new Bundle();
		args.putStringArrayList(AGENTLIST, new ArrayList<>(agentList));
		args.putStringArrayList(AGENTNAMELIST, new ArrayList<>(agentNameList));

		AgentSelectionDialog fragment = new AgentSelectionDialog();
		fragment.setArguments(args);
		return fragment;
	}

	private List<Drawable> loadIcons(Context context, List<String> agentList) {
		List<Drawable> agentIcons = new ArrayList<>(agentList.size());
		for (String agent : agentList) {
			try {
				agentIcons.add(context.getPackageManager().getApplicationIcon(agent));
			} catch (PackageManager.NameNotFoundException e) {
				throw new IllegalArgumentException("Couldn't load app icon for " + agent, e);
			}
		}
		return agentIcons;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		Activity activity = getActivity();
		View view = inflater.inflate(R.layout.agent_selection_dialog, container, false);

		mHostEditorFragment = (HostEditorFragment) getFragmentManager()
				.findFragmentById(R.id.fragment_container);

		mSelect = (Button) view.findViewById(R.id.button_select);
		mSelect.setEnabled(false);
		mCancel = (Button) view.findViewById(R.id.button_cancel);

		mAgentRecyclerView = (RecyclerView) view.findViewById(R.id.agent_recycler);

		mAgentList = getArguments().getStringArrayList(AGENTLIST);
		mAgentNameList = getArguments().getStringArrayList(AGENTNAMELIST);

		mAgentIcons = loadIcons(getContext(), mAgentList);

		mAgentAdapter = new AgentAdapter(mAgentNameList, mAgentIcons, getResources());
		mAgentRecyclerView.setAdapter(mAgentAdapter);

		mAgentRecyclerView.setLayoutManager(new LinearLayoutManager(activity));

		mCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mHostEditorFragment.onAgentSelected(null);
				dismiss();
			}
		});
		mSelect.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mHostEditorFragment.onAgentSelected(mAgentList.get(mSelection));
				dismiss();
			}
		});

		mAgentRecyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
			GestureDetector mTapDetector = new GestureDetector(getContext(),
					new GestureDetector.SimpleOnGestureListener() {
						@Override
						public boolean onSingleTapUp(MotionEvent e) {
							return true;
						}
					});

			@Override
			public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent e) {
				View itemView = view.findChildViewUnder(e.getX(), e.getY());
				if (itemView != null && mTapDetector.onTouchEvent(e)) {
					int position = view.getChildAdapterPosition(itemView);
					mSelection = position;
					mAgentAdapter.select(position);
					mSelect.setEnabled(true);
					return true;
				}
				return false;
			}
		});

		return view;
	}

	public static class AgentAdapter extends RecyclerView.Adapter<AgentViewHolder> {
		private List<String> mAgentList;
		private List<Drawable> mAgentIcons;
		private int mSelectedItem = -1;
		private Resources mResources;

		public AgentAdapter(List<String> agentList, List<Drawable> agentIcons, Resources resources) {
			mAgentList = agentList;
			mAgentIcons = agentIcons;
			mResources = resources;
		}

		@Override
		public AgentViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			final View agentItemView = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.agent_selection_item, parent, false);
			final AgentViewHolder viewHolder = new AgentViewHolder(agentItemView);

			return viewHolder;
		}

		@Override
		public void onBindViewHolder(AgentViewHolder holder, int position) {
			holder.vName.setText(mAgentList.get(position));
			if (mSelectedItem != position) {
				Drawable icon = mAgentIcons.get(position);
				Drawable.ConstantState constantState = icon.getConstantState();
				if (constantState != null) {
					Drawable tintedIcon = constantState.newDrawable(mResources);
					DrawableCompat.setTint(tintedIcon.mutate(),
							ResourcesCompat.getColor(mResources, R.color.key_background_normal,
									null));
					holder.vIcon.setImageDrawable(tintedIcon);
				}
			} else {
				holder.vIcon.setImageDrawable(mAgentIcons.get(position));
			}
		}

		@Override
		public int getItemCount() {
			return mAgentList.size();
		}

		public void select(int position) {
			mSelectedItem = position;
			notifyDataSetChanged();
		}
	}

	private static class AgentViewHolder extends RecyclerView.ViewHolder {
		public final TextView vName;
		public final ImageView vIcon;

		AgentViewHolder(View itemView) {
			super(itemView);

			vName = (TextView) itemView.findViewById(R.id.agentName);
			vIcon = (ImageView) itemView.findViewById(R.id.agentIcon);
		}
	}
}
