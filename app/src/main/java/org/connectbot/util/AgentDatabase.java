/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Jonas Dippel, Michael Perk, Marc Totzke
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

import java.util.ArrayList;
import java.util.List;

import org.connectbot.bean.AgentBean;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

/**
 * The agent database stores necessary data about connected agents. The data is then used to call
 * the agents signing service and to generate the ssh authentication request.
 */
public class AgentDatabase extends RobustSQLiteOpenHelper {

	public static final String TAG = "CB.AgentDatabase";
	public static final String DB_NAME = "agents";

	public static final int DB_VERSION = 1;

	public static final String TABLE_AGENTS = "agents";

	public static final String FIELD_AGENT_KEY_IDENTIFIER = "keyidentifier";
	public static final String FIELD_AGENT_KEY_TYPE = "keytype";
	public static final String FIELD_AGENT_DESCRIPTION = "description";
	public static final String FIELD_AGENT_PACKAGE_NAME = "packagename";
	public static final String FIELD_AGENT_PUBLIC_KEY = "publickey";

	static {
		addTableName(TABLE_AGENTS);
	}

	private static final Object sInstanceLock = new Object();

	private static AgentDatabase sInstance;

	private final SQLiteDatabase mDataBase;

	public static AgentDatabase get(Context context) {
		synchronized (sInstanceLock) {
			if (sInstance != null) {
				return sInstance;
			}

			Context AppContext = context.getApplicationContext();
			sInstance = new AgentDatabase(AppContext);
			return sInstance;
		}
	}

	private AgentDatabase(Context context) {
		super(context, DB_NAME, null, DB_VERSION);

		mDataBase = getWritableDatabase();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		super.onCreate(db);
		createTables(db);
	}

	private void createTables(SQLiteDatabase Database) {
		Database.execSQL("CREATE TABLE " + TABLE_AGENTS + " ( " +
				"_id INTEGER PRIMARY KEY,"
				+ FIELD_AGENT_KEY_IDENTIFIER + " TEXT, "
				+ FIELD_AGENT_KEY_TYPE + " TEXT, "
				+ FIELD_AGENT_DESCRIPTION + " TEXT, "
				+ FIELD_AGENT_PACKAGE_NAME + " TEXT, "
				+ FIELD_AGENT_PUBLIC_KEY + " BLOB)"
		);
	}

	@Override
	public void onRobustUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
			throws SQLiteException {
	}

	/**
	 * Gets an agent by its id.
	 *
	 * @param agentId The agent id.
	 * @return The agent if it is present. Null otherwise.
	 */
	public AgentBean findAgentById(long agentId) {
		Cursor c = mDataBase.query(TABLE_AGENTS, null,
				"_id = ?", new String[] {String.valueOf(agentId)},
				null, null, null);

		return getFirstAgentBean(c);
	}

	/**
	 * Inserts or updates the given agent into the agent database.
	 *
	 * @param agent The agent to insert or update
	 * @return The agent. The field id is now synced with the id property.
	 */
	public AgentBean saveAgent(AgentBean agent) {
		long id = agent.getId();

		mDataBase.beginTransaction();
		try {
			if (id == -1) {
				id = mDataBase.insert(TABLE_AGENTS, null, agent.getValues());
			} else {
				mDataBase.update(TABLE_AGENTS, agent.getValues(), "_id = ?",
						new String[] {String.valueOf(id)});
			}
			mDataBase.setTransactionSuccessful();
		} finally {
			mDataBase.endTransaction();
		}

		agent.setId(id);

		return agent;
	}

	/**
	 * Deletes the agent with the supplied id from the database
	 *
	 * @param agentId Id of agent that should be deleted
	 */
	public void deleteAgentById(long agentId) {
		if (agentId == HostDatabase.AGENTID_NONE) {
			return;
		}
		mDataBase.beginTransaction();
		try {
			mDataBase.delete(TABLE_AGENTS, "_id = ?",
					new String[] {Long.toString(agentId)});
			mDataBase.setTransactionSuccessful();
		} finally {
			mDataBase.endTransaction();
		}
	}

	/**
	 * Creates a list of agent beans from a database cursor.
	 *
	 * @param c cursor to read from
	 */
	private List<AgentBean> createAgentBeans(Cursor c) {
		List<AgentBean> agents = new ArrayList<>();

		final int COL_ID = c.getColumnIndexOrThrow("_id"),
				COL_KEY_IDENTIFIER = c.getColumnIndexOrThrow(FIELD_AGENT_KEY_IDENTIFIER),
				COL_KEY_TYPE = c.getColumnIndexOrThrow(FIELD_AGENT_KEY_TYPE),
				COL_DESCRIPTION = c.getColumnIndexOrThrow(FIELD_AGENT_DESCRIPTION),
				COL_PACKAGE_NAME = c.getColumnIndexOrThrow(FIELD_AGENT_PACKAGE_NAME),
				COL_PUBLIC_KEY = c.getColumnIndexOrThrow(FIELD_AGENT_PUBLIC_KEY);

		while (c.moveToNext()) {
			AgentBean agent = new AgentBean();

			agent.setId(c.getLong(COL_ID));
			agent.setKeyIdentifier(c.getString(COL_KEY_IDENTIFIER));
			agent.setKeyType(c.getString(COL_KEY_TYPE));
			agent.setDescription(c.getString(COL_DESCRIPTION));
			agent.setPackageName(c.getString(COL_PACKAGE_NAME));
			agent.setPublicKey(c.getBlob(COL_PUBLIC_KEY));

			agents.add(agent);
		}

		return agents;
	}

	private AgentBean getFirstAgentBean(Cursor c) {
		AgentBean agent = null;

		List<AgentBean> agents = createAgentBeans(c);

		if (agents.size() > 0) {
			agent = agents.get(0);
		}
		c.close();

		return agent;
	}
}
