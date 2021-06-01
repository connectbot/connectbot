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
package org.connectbot

import org.connectbot.service.OnHostStatusChangedListener
import org.connectbot.service.TerminalManager
import org.connectbot.data.HostStorage
import android.view.LayoutInflater
import android.view.MenuItem
import android.os.IBinder
import org.connectbot.service.TerminalManager.TerminalBinder
import org.connectbot.util.HostDatabase
import android.util.Log
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import org.connectbot.util.PreferenceConstants
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.Menu
import android.net.Uri
import org.connectbot.bean.HostBean
import org.connectbot.transport.TransportFactory
import android.widget.ImageView
import android.widget.TextView
import android.content.Intent.ShortcutIconResource
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.ViewGroup
import android.annotation.TargetApi
import android.content.*
import android.os.Build
import android.preference.PreferenceManager
import androidx.annotation.StyleRes
import android.text.format.DateUtils
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog

class HostListActivity : AppCompatListActivity(), OnHostStatusChangedListener {
    private var bound: TerminalManager? = null
    private var hostdb: HostStorage? = null
    private var inflater: LayoutInflater? = null
    private var sortedByColor = false
    private var sortcolor: MenuItem? = null
    private var sortlast: MenuItem? = null
    private var disconnectall: MenuItem? = null
    private lateinit var prefs: SharedPreferences
    private var makingShortcut = false
    private var waitingForDisconnectAll = false

    /**
     * Whether to close the activity when disconnectAll is called. True if this activity was
     * only brought to the foreground via the notification button to disconnect all hosts.
     */
    private var closeOnDisconnectAll = true
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            bound = (service as TerminalBinder).service

            // update our listview binder to find the service
            updateList()
            bound?.registerOnHostStatusChangedListener(this@HostListActivity)
            if (waitingForDisconnectAll) {
                disconnectAll()
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            bound?.unregisterOnHostStatusChangedListener(this@HostListActivity)
            updateList()
        }
    }

    public override fun onStart() {
        super.onStart()

        // start the terminal manager service
        this.bindService(Intent(this, TerminalManager::class.java), connection, BIND_AUTO_CREATE)
        hostdb = HostDatabase.get(this)
    }

    public override fun onStop() {
        super.onStop()
        unbindService(connection)
        hostdb = null
        closeOnDisconnectAll = true
    }

    public override fun onResume() {
        super.onResume()

        // Must disconnectAll before setting closeOnDisconnectAll to know whether to keep the
        // activity open after disconnecting.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 && DISCONNECT_ACTION == intent.action) {
            Log.d(TAG, "Got disconnect all request")
            disconnectAll()
        }

        // Still close on disconnect if waiting for a disconnect.
        closeOnDisconnectAll = waitingForDisconnectAll && closeOnDisconnectAll
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT) {
            updateList()
        }
    }

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        setContentView(R.layout.act_hostlist)
        setTitle(R.string.title_hosts_list)
        mListView = findViewById(R.id.list)
        mListView.setHasFixedSize(true)
        mListView.layoutManager = LinearLayoutManager(this)
        mListView.addItemDecoration(ListItemDecoration(this))
        mEmptyView = findViewById(R.id.empty)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // detect HTC Dream and apply special preferences
        if (Build.MANUFACTURER == "HTC" && Build.DEVICE == "dream") {
            val editor = prefs.edit()
            var doCommit = false
            if (!prefs.contains(PreferenceConstants.SHIFT_FKEYS) &&
                    !prefs.contains(PreferenceConstants.CTRL_FKEYS)) {
                editor.putBoolean(PreferenceConstants.SHIFT_FKEYS, true)
                editor.putBoolean(PreferenceConstants.CTRL_FKEYS, true)
                doCommit = true
            }
            if (!prefs.contains(PreferenceConstants.STICKY_MODIFIERS)) {
                editor.putString(PreferenceConstants.STICKY_MODIFIERS, PreferenceConstants.YES)
                doCommit = true
            }
            if (!prefs.contains(PreferenceConstants.KEYMODE)) {
                editor.putString(PreferenceConstants.KEYMODE, PreferenceConstants.KEYMODE_RIGHT)
                doCommit = true
            }
            if (doCommit) {
                editor.apply()
            }
        }
        makingShortcut = Intent.ACTION_CREATE_SHORTCUT == intent.action || Intent.ACTION_PICK == intent.action

        // connect with hosts database and populate list
        hostdb = HostDatabase.get(this)
        sortedByColor = prefs.getBoolean(PreferenceConstants.SORT_BY_COLOR, false)
        registerForContextMenu(mListView)
        val addHostButtonContainer = findViewById<View>(R.id.add_host_button_container)
        addHostButtonContainer.visibility = if (makingShortcut) View.GONE else View.VISIBLE
        val addHostButton = findViewById<FloatingActionButton>(R.id.add_host_button)
        addHostButton.setOnClickListener {
            val intent = EditHostActivity.createIntentForNewHost(this@HostListActivity)
            startActivityForResult(intent, REQUEST_EDIT)
        }
        inflater = LayoutInflater.from(this)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        // don't offer menus when creating shortcut
        if (makingShortcut) return true
        sortcolor!!.isVisible = !sortedByColor
        sortlast!!.isVisible = sortedByColor
        disconnectall!!.isEnabled = bound != null && bound!!.bridges.size > 0
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        // don't offer menus when creating shortcut
        if (makingShortcut) return true

        // add host, ssh keys, about
        sortcolor = menu.add(R.string.list_menu_sortcolor).apply {
            setIcon(android.R.drawable.ic_menu_share)
            setOnMenuItemClickListener {
                sortedByColor = true
                updateList()
                true
            }
        }
        sortlast = menu.add(R.string.list_menu_sortname).apply {
            setIcon(android.R.drawable.ic_menu_share)
            setOnMenuItemClickListener {
                sortedByColor = false
                updateList()
                true
            }
        }

        val keys = menu.add(R.string.list_menu_pubkeys)
        keys.setIcon(android.R.drawable.ic_lock_lock)
        keys.intent = Intent(this@HostListActivity, PubkeyListActivity::class.java)
        val colors = menu.add(R.string.title_colors)
        colors.setIcon(android.R.drawable.ic_menu_slideshow)
        colors.intent = Intent(this@HostListActivity, ColorsActivity::class.java)
        disconnectall = menu.add(R.string.list_menu_disconnect).apply {
            setIcon(android.R.drawable.ic_menu_delete)
            setOnMenuItemClickListener {
                disconnectAll()
                false
            }
        }

        val settings = menu.add(R.string.list_menu_settings)
        settings.setIcon(android.R.drawable.ic_menu_preferences)
        settings.intent = Intent(this@HostListActivity, SettingsActivity::class.java)
        val help = menu.add(R.string.title_help)
        help.setIcon(android.R.drawable.ic_menu_help)
        help.intent = Intent(this@HostListActivity, HelpActivity::class.java)
        return true
    }

    /**
     * Disconnects all active connections and closes the activity if appropriate.
     */
    private fun disconnectAll() {
        if (bound == null) {
            waitingForDisconnectAll = true
            return
        }
        AlertDialog.Builder(
                this@HostListActivity, R.style.AlertDialogTheme)
                .setMessage(getString(R.string.disconnect_all_message))
                .setPositiveButton(R.string.disconnect_all_pos) { dialog, which ->
                    bound!!.disconnectAll(true, false)
                    waitingForDisconnectAll = false

                    // Clear the intent so that the activity can be relaunched without closing.
                    // TODO(jlklein): Find a better way to do this.
                    intent = Intent()
                    if (closeOnDisconnectAll) {
                        finish()
                    }
                }
                .setNegativeButton(R.string.disconnect_all_neg) { dialog, which ->
                    waitingForDisconnectAll = false
                    // Clear the intent so that the activity can be relaunched without closing.
                    // TODO(jlklein): Find a better way to do this.
                    intent = Intent()
                }.create().show()
    }

    /**
     * @return
     */
    private fun startConsoleActivity(uri: Uri): Boolean {
        var host = TransportFactory.findHost(hostdb, uri)
        if (host == null) {
            host = TransportFactory.getTransport(uri.scheme).createHost(uri)
            host.color = HostDatabase.COLOR_GRAY
            host.pubkeyId = HostDatabase.PUBKEYID_ANY
            hostdb!!.saveHost(host)
        }
        val intent = Intent(this@HostListActivity, ConsoleActivity::class.java)
        intent.data = uri
        startActivity(intent)
        return true
    }

    protected fun updateList() {
        if (prefs.getBoolean(PreferenceConstants.SORT_BY_COLOR, false) != sortedByColor) {
            val edit = prefs.edit()
            edit.putBoolean(PreferenceConstants.SORT_BY_COLOR, sortedByColor)
            edit.apply()
        }
        if (hostdb == null) hostdb = HostDatabase.get(this)
        val hosts = hostdb!!.getHosts(sortedByColor)

        // Don't lose hosts that are connected via shortcuts but not in the database.
        if (bound != null) {
            for (bridge in bound!!.bridges) {
                if (!hosts.contains(bridge.host)) hosts.add(0, bridge.host)
            }
        }
        mAdapter = HostAdapter(this, hosts, bound)
        mListView.adapter = mAdapter
        adjustViewVisibility()
    }

    override fun onHostStatusChanged() {
        updateList()
    }

    @VisibleForTesting
    inner class HostViewHolder(v: View) : ItemViewHolder(v) {
        @JvmField
        val icon: ImageView = v.findViewById(android.R.id.icon)

        @JvmField
        val nickname: TextView = v.findViewById(android.R.id.text1)
        val caption: TextView = v.findViewById(android.R.id.text2)

        @JvmField
        var host: HostBean? = null

        override fun onClick(v: View) {
            // launch off to console details
            val uri = host!!.uri
            val contents = Intent(Intent.ACTION_VIEW, uri)
            contents.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (makingShortcut) {
                // create shortcut if requested
                val icon = ShortcutIconResource.fromContext(this@HostListActivity, R.mipmap.icon)
                val intent = Intent()
                intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, contents)
                intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, host!!.nickname)
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon)
                setResult(RESULT_OK, intent)
                finish()
            } else {
                // otherwise just launch activity to show this host
                contents.setClass(this@HostListActivity, ConsoleActivity::class.java)
                this@HostListActivity.startActivity(contents)
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo) {
            menu.setHeaderTitle(host!!.nickname)

            // edit, disconnect, delete
            val connect = menu.add(R.string.list_host_disconnect)
            val bridge = if (bound == null) null else bound!!.getConnectedBridge(host)
            connect.isEnabled = bridge != null
            connect.setOnMenuItemClickListener {
                bridge!!.dispatchDisconnect(true)
                true
            }
            val edit = menu.add(R.string.list_host_edit)
            edit.setOnMenuItemClickListener {
                val intent = EditHostActivity.createIntentForExistingHost(
                        this@HostListActivity, host!!.id)
                this@HostListActivity.startActivityForResult(intent, REQUEST_EDIT)
                true
            }
            val portForwards = menu.add(R.string.list_host_portforwards)
            portForwards.setOnMenuItemClickListener {
                val intent = Intent(this@HostListActivity, PortForwardListActivity::class.java)
                intent.putExtra(Intent.EXTRA_TITLE, host!!.id)
                this@HostListActivity.startActivityForResult(intent, REQUEST_EDIT)
                true
            }
            if (!TransportFactory.canForwardPorts(host!!.protocol)) portForwards.isEnabled = false
            val delete = menu.add(R.string.list_host_delete)
            delete.setOnMenuItemClickListener { // prompt user to make sure they really want this
                AlertDialog.Builder(
                        this@HostListActivity, R.style.AlertDialogTheme)
                        .setMessage(getString(R.string.delete_message, host!!.nickname))
                        .setPositiveButton(R.string.delete_pos) { dialog, which -> // make sure we disconnect
                            bridge?.dispatchDisconnect(true)
                            hostdb!!.deleteHost(host)
                            updateList()
                        }
                        .setNegativeButton(R.string.delete_neg, null).create().show()
                true
            }
        }

    }

    @VisibleForTesting
    private inner class HostAdapter(context: Context?, private val hosts: List<HostBean>, private val manager: TerminalManager?) : ItemAdapter(context) {
        /**
         * Check if we're connected to a terminal with the given host.
         */
        private fun getConnectedState(host: HostBean?): Int {
            // always disconnected if we don't have backend service
            if (manager == null || host == null) {
                return Companion.STATE_UNKNOWN
            }
            if (manager.getConnectedBridge(host) != null) {
                return Companion.STATE_CONNECTED
            }
            return if (manager.disconnected.contains(host)) {
                Companion.STATE_DISCONNECTED
            } else Companion.STATE_UNKNOWN
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HostViewHolder {
            val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_host, parent, false)
            return HostViewHolder(v)
        }

        @TargetApi(16)
        private fun hideFromAccessibility(view: View, hide: Boolean) {
            view.importantForAccessibility = if (hide) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val hostHolder = holder as HostViewHolder
            val host = hosts[position]
            hostHolder.host = host
            hostHolder.nickname.text = host.nickname
            when (getConnectedState(host)) {
                Companion.STATE_UNKNOWN -> {
                    hostHolder.icon.setImageState(intArrayOf(), true)
                    hostHolder.icon.contentDescription = null
                    if (Build.VERSION.SDK_INT >= 16) {
                        hideFromAccessibility(hostHolder.icon, true)
                    }
                }
                Companion.STATE_CONNECTED -> {
                    hostHolder.icon.setImageState(intArrayOf(android.R.attr.state_checked), true)
                    hostHolder.icon.contentDescription = getString(R.string.image_description_connected)
                    if (Build.VERSION.SDK_INT >= 16) {
                        hideFromAccessibility(hostHolder.icon, false)
                    }
                }
                Companion.STATE_DISCONNECTED -> {
                    hostHolder.icon.setImageState(intArrayOf(android.R.attr.state_expanded), true)
                    hostHolder.icon.contentDescription = getString(R.string.image_description_disconnected)
                    if (Build.VERSION.SDK_INT >= 16) {
                        hideFromAccessibility(hostHolder.icon, false)
                    }
                }
                else -> Log.e("HostAdapter", "Unknown host state encountered: " + getConnectedState(host))
            }
            @StyleRes val chosenStyleFirstLine: Int
            @StyleRes val chosenStyleSecondLine: Int
            when {
                HostDatabase.COLOR_RED == host.color -> {
                    chosenStyleFirstLine = R.style.ListItemFirstLineText_Red
                    chosenStyleSecondLine = R.style.ListItemSecondLineText_Red
                }
                HostDatabase.COLOR_GREEN == host.color -> {
                    chosenStyleFirstLine = R.style.ListItemFirstLineText_Green
                    chosenStyleSecondLine = R.style.ListItemSecondLineText_Green
                }
                HostDatabase.COLOR_BLUE == host.color -> {
                    chosenStyleFirstLine = R.style.ListItemFirstLineText_Blue
                    chosenStyleSecondLine = R.style.ListItemSecondLineText_Blue
                }
                else -> {
                    chosenStyleFirstLine = R.style.ListItemFirstLineText
                    chosenStyleSecondLine = R.style.ListItemSecondLineText
                }
            }
            hostHolder.nickname.setTextAppearance(context, chosenStyleFirstLine)
            hostHolder.caption.setTextAppearance(context, chosenStyleSecondLine)
            var nice: CharSequence? = context.getString(R.string.bind_never)
            if (host.lastConnect > 0) {
                nice = DateUtils.getRelativeTimeSpanString(host.lastConnect * 1000)
            }
            hostHolder.caption.text = nice
        }

        override fun getItemId(position: Int) = hosts[position].id

        override fun getItemCount() = hosts.size

    }

    companion object {
        const val TAG = "CB.HostListActivity"
        const val DISCONNECT_ACTION = "org.connectbot.action.DISCONNECT"
        const val REQUEST_EDIT = 1

        const val STATE_UNKNOWN = 1
        const val STATE_CONNECTED = 2
        const val STATE_DISCONNECTED = 3
    }
}