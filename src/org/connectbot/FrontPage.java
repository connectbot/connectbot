package org.connectbot;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.theb.ssh.InteractiveHostKeyVerifier;

import com.trilead.ssh2.Connection;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import org.theb.ssh.R;

public class FrontPage extends Activity {
	
	public TerminalManager bound = null;
	
    private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();
			
			// TODO: update our green bulb icons by checking for existing bridges
			// open up some test sessions
//			try {
//				bound.openConnection("192.168.254.230", 22, "connectbot", "b0tt", "screen", 100);
//				bound.openConnection("192.168.254.230", 22, "connectbot", "b0tt", "screen", 100);
//				bound.openConnection("192.168.254.230", 22, "connectbot", "b0tt", "screen", 100);
//			} catch(Exception e) {
//				e.printStackTrace();
//			}
			
		}

		public void onServiceDisconnected(ComponentName className) {
			bound = null;
		}
	};
	
	
	public final static String ITEM_TITLE = "title";
	public final static String ITEM_CAPTION = "caption";
	public final static String ITEM_IMAGE = "image";

	public Map<String,?> createItem(String title, String caption, int image) {
		Map<String,String> item = new HashMap<String,String>();
		item.put(ITEM_TITLE, title);
		item.put(ITEM_CAPTION, caption);
		item.put(ITEM_IMAGE, Integer.toString(image));
		return item;
	}

	@Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.act_frontpage);
        
        // start the terminal manager service
		this.startService(new Intent(this, TerminalManager.class));
		this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

		
        
        // create some test hostmasks
		List<Map<String,?>> security = new LinkedList<Map<String,?>>();
		security.add(createItem("user@example.org", "20 minutes ago, connected", android.R.drawable.presence_online));
		security.add(createItem("root@home.example.com", "1 hour ago, connected", android.R.drawable.presence_online));
		security.add(createItem("person@192.168.0.1", "12 days ago", android.R.drawable.presence_invisible));
		security.add(createItem("root@google.com", "never", android.R.drawable.presence_invisible));
		security.add(createItem("nobody@example.net", "14 years ago, broken socket", android.R.drawable.presence_busy));
		security.add(createItem("root@home.example.com", "1 hour ago", android.R.drawable.presence_invisible));
		security.add(createItem("person@192.168.0.1", "12 minutes ago", android.R.drawable.presence_invisible));
        
        final ListView list = (ListView)this.findViewById(R.id.front_hostlist);
        list.setAdapter(new SimpleAdapter(this, security, R.layout.item_host, new String[] { ITEM_TITLE, ITEM_CAPTION, ITEM_IMAGE }, new int[] { R.id.host_title, R.id.host_caption, R.id.host_connected }));
        
        list.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				// TODO: actually perform connection process here
				// launch off to console details
				FrontPage.this.startActivity(new Intent(FrontPage.this, Console.class));
				
			}
        	
        });
        
        
//        final TextView text = (TextView)this.findViewById(R.id.front_quickconnect);
//        text.setOnKeyListener(new OnKeyListener() {
//
//			public boolean onKey(View v, int keyCode, KeyEvent event) {
//				
//				// set list filter based on text
//				String filter = text.getText().toString();
//				list.setTextFilterEnabled((filter.length() > 0));
//				list.setFilterText(filter);
//				
//				// TODO Auto-generated method stub
//				return false;
//			}
//        	
//        });
        
        
        
		


        
        
        
	}

}
