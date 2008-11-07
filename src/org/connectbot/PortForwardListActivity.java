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

package org.connectbot;

import java.util.List;

import org.connectbot.bean.PortForwardBean;
import org.connectbot.util.HostDatabase;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

/**
 * List all portForwards for a particular host and provide a way for users to add more portForwards,
 * edit existing portForwards, and delete portForwards.
 * 
 * @author Kenny Root
 */
public class PortForwardListActivity extends ListActivity {
	public final static String TAG = PortForwardListActivity.class.toString();

	protected HostDatabase hostdb;

	protected Cursor hosts;
	protected List<PortForwardBean> portForwards;
	
	protected LayoutInflater inflater = null;
	
	private long hostId;

	@Override
    public void onStart() {
		super.onStart();
		
		if(this.hostdb == null)
			this.hostdb = new HostDatabase(this);
	}
	
	@Override
    public void onStop() {
		super.onStop();
	
		if(this.hostdb != null) {
			this.hostdb.close();
			this.hostdb = null;
		}
	}
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		this.hostId = this.getIntent().getLongExtra(Intent.EXTRA_TITLE, -1);

		setContentView(R.layout.act_portforwardlist);
		
		// connect with hosts database and populate list
		this.hostdb = new HostDatabase(this);
		
		this.setTitle(String.format("%s: %s (%s)",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_port_forwards_list), 
				hostdb.findNicknameById(hostId)));
		
		this.updateList();
		
		this.registerForContextMenu(this.getListView());
		
		this.inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem add = menu.add(R.string.portforward_menu_add);
		add.setIcon(android.R.drawable.ic_menu_add);
		add.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// build dialog to prompt user about updating
				final View portForwardView = inflater.inflate(R.layout.dia_portforward, null, false);
				((RadioButton)portForwardView.findViewById(R.id.portforward_local)).setChecked(true);
				new AlertDialog.Builder(PortForwardListActivity.this)
					.setView(portForwardView)
					.setPositiveButton(R.string.portforward_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							try {
								final EditText nicknameEdit = (EditText) portForwardView.findViewById(R.id.nickname);
								final EditText sourcePortEdit = (EditText) portForwardView.findViewById(R.id.portforward_source);
								final EditText destEdit = (EditText) portForwardView.findViewById(R.id.portforward_destination);

								String type = ((RadioButton)portForwardView.findViewById(R.id.portforward_local)).isChecked()
									? HostDatabase.PORTFORWARD_LOCAL : HostDatabase.PORTFORWARD_REMOTE;
								
								PortForwardBean pfb = new PortForwardBean(hostId,
										nicknameEdit.getText().toString(), type,
										sourcePortEdit.getText().toString(),
										destEdit.getText().toString());
								
								if (!hostdb.savePortForward(pfb))
									throw new Exception("Could not save port forward");
								
								updateHandler.sendEmptyMessage(-1);
							} catch (Exception e) {
								Log.e(TAG, "Could not update port forward", e);
								// TODO Show failure dialog.
							}
						}
					})
					.setNegativeButton(R.string.portforward_neg, null).create().show();
				
				return true;
			}
		});
		
		return true;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		// Create menu to handle deleting and editing port forward
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final PortForwardBean pfb = (PortForwardBean) this.getListView().getItemAtPosition(info.position);

		menu.setHeaderTitle(pfb.getNickname());
		
		MenuItem edit = menu.add(R.string.portforward_edit);
		edit.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				final View editTunnelView = inflater.inflate(R.layout.dia_portforward, null, false);
				
				final RadioButton portForwardLocal = (RadioButton) editTunnelView.findViewById(R.id.portforward_local);
				if (HostDatabase.PORTFORWARD_LOCAL.equals(pfb.getType())) {
					portForwardLocal.setChecked(true);
				} else { // if (HostDatabase.PORTFORWARD_REMOTE.equals(type)) {
					((RadioButton) editTunnelView.findViewById(R.id.portforward_remote)).setChecked(true);
				}
				
				final EditText nicknameEdit = (EditText) editTunnelView.findViewById(R.id.nickname);
				nicknameEdit.setText(pfb.getNickname());

				final EditText sourcePortEdit = (EditText) editTunnelView.findViewById(R.id.portforward_source);
				sourcePortEdit.setText(String.valueOf(pfb.getSourcePort()));
				
				final EditText destEdit = (EditText) editTunnelView.findViewById(R.id.portforward_destination);
				destEdit.setText(String.format("%s:%d", pfb.getDestAddr(), pfb.getDestPort()));

				new AlertDialog.Builder(PortForwardListActivity.this)
					.setView(editTunnelView)
					.setPositiveButton(R.string.button_change, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							try {
								pfb.setNickname(nicknameEdit.getText().toString());
								
								if (portForwardLocal.isChecked())
									pfb.setType(HostDatabase.PORTFORWARD_LOCAL);
								else
									pfb.setType(HostDatabase.PORTFORWARD_REMOTE);
								
								pfb.setSourcePort(Integer.parseInt(sourcePortEdit.getText().toString()));
								pfb.setDest(destEdit.getText().toString());
								
								if (!hostdb.savePortForward(pfb))
									throw new Exception("Could not save port forward");
								
								updateHandler.sendEmptyMessage(-1);
							} catch (Exception e) {
								Log.e(TAG, "Could not update port forward", e);
								// TODO Show failure dialog.
							}
						}
					})
					.setNegativeButton(android.R.string.cancel, null).create().show();
				
				return true;
			}
		});
	}
	
	public Handler updateHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			PortForwardListActivity.this.updateList();
		}
	};
	
	protected void updateList() {
		if (this.hostdb == null) return;
		
		this.portForwards = this.hostdb.getPortForwardsForHost(hostId);
		
		PortForwardAdapter adapter = new PortForwardAdapter(this, this.portForwards);
		this.setListAdapter(adapter);

		//this.startManagingCursor(portForwards);
	}
	
	class PortForwardAdapter extends ArrayAdapter<PortForwardBean> {
		private List<PortForwardBean> portForwards;

		public PortForwardAdapter(Context context, List<PortForwardBean> portForwards) {
			super(context, R.layout.item_portforward, portForwards);
			
			this.portForwards = portForwards;
		}

		public View getView(int position, View view, ViewGroup parent) {
			if (view == null)
				view = inflater.inflate(R.layout.item_portforward, null, false);

			TextView nickname = (TextView)view.findViewById(android.R.id.text1);
			TextView caption = (TextView)view.findViewById(android.R.id.text2);

			PortForwardBean pfb = portForwards.get(position);
			nickname.setText(pfb.getNickname());
			caption.setText(pfb.getDescription());
			
			return view;
		}
	}

}
