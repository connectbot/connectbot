/*
 * Copyright (C) 2007 Kenny Root (kenny at the-b.org)
 * 
 * This file is part of Connectbot.
 *
 *  Connectbot is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Connectbot is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Connectbot.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.theb.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.Session;

public class TrileadConnectionThread extends ConnectionThread {
	private String hostname;
	private String username;
	private String password;
	private int port;

	private Connection connection;
	private Session session;

	private InputStream stdOut;
	private OutputStream stdIn;

	private Semaphore sPass;
	
	//protected FeedbackUI ui;
	protected Terminal term;
	
	public TrileadConnectionThread(FeedbackUI ui, Terminal term, String hostname, String username, int port) {
		super(ui, hostname, username, port);
		//this.ui = ui;
		this.term = term;
		this.hostname = hostname;
		this.username = username;
		this.port = port;
	}

	@Override
	public void finish() {
		if (session != null) {
			session.close();
			session = null;
		}

		if (connection != null) {
			connection.close();
			connection = null;
		}
	}

	@Override
	public InputStream getReader() {
		return stdOut;
	}

	@Override
	public OutputStream getWriter() {
		return stdIn;
	}

	@Override
	public void run() {
		connection = new Connection(hostname, port);

		//connection.addConnectionMonitor((ConnectionMonitor) ui);
		//ui.setWaiting(true, "Connection", "Connecting to " + hostname + "...");

		try {
			connection.connect(new InteractiveHostKeyVerifier());

			//ui.setWaiting(true, "Authenticating", "Trying to authenticate...");

			// boolean enableKeyboardInteractive = true;
			// boolean enableDSA = true;
			// boolean enableRSA = true;

			while (true) {
				/*
				 * if ((enableDSA || enableRSA ) &&
				 * mConn.isAuthMethodAvailable(username, "publickey");
				 */

				if (connection.isAuthMethodAvailable(username, "password")) {
					//ui.setWaiting(true, "Authenticating","Trying to authenticate using password...");

					// Set a semaphore that is unset by the returning dialog.
//					sPass = new Semaphore(0);
//					ui.askPassword();
//
//					// Wait for the user to answer.
//					sPass.acquire();
//					sPass = null;
//					if (password == null)
//						continue;
					
					password = "b0tt";

					boolean res = connection.authenticateWithPassword(username, password);
					password = null;
					if (res == true)
						break;

					continue;
				}

				throw new IOException(
						"No supported authentication methods available.");
			}

			//ui.setWaiting(true, "Session", "Requesting shell...");

			session = connection.openSession();
			
			session.requestPTY("xterm",  // BUGFIX: allow colors with xterm instead of vt100
					term.getColumnCount(), term.getRowCount(),
					term.getWidth(), term.getHeight(),
					null);

			session.startShell();

			stdIn = session.getStdin();
			// stderr = session.getStderr();
			stdOut = session.getStdout();

			//ui.setWaiting(false, null, null);
		} catch (IOException e) {
			//ui.setWaiting(false, null, null);
			return;
		}

		term.start(stdOut, stdIn);
	}

	@Override
	public void setPassword(String password) {
		if (password == null)
			this.password = "";
		else
			this.password = password;
		sPass.release();
	}
}
