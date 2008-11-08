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
package com.trilead.ssh2.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import net.sourceforge.jsocks.Proxy;
import net.sourceforge.jsocks.ProxyMessage;
import net.sourceforge.jsocks.ProxyServer;
import net.sourceforge.jsocks.Socks4Message;
import net.sourceforge.jsocks.Socks5Message;
import net.sourceforge.jsocks.server.ServerAuthenticator;
import net.sourceforge.jsocks.server.ServerAuthenticatorNone;

/**
 * DynamicAcceptThread.
 * 
 * @author Kenny Root
 * @version $Id:
 */
public class DynamicAcceptThread extends Thread implements IChannelWorkerThread {
	private ChannelManager cm;
	private ServerSocket ss;
	
	class DynamicAcceptRunnable extends ProxyServer {
		private ServerAuthenticator auth;
		private Socket sock;
		private InputStream in;
		private OutputStream out;
		private ProxyMessage msg;
		
		public DynamicAcceptRunnable(ServerAuthenticator auth, Socket sock) {
			super(auth, sock);
			this.auth = auth;
			this.sock = sock;
		}

		protected void startSession() throws IOException {
			sock.setSoTimeout(ProxyServer.iddleTimeout);

			try {
				auth = auth.startSession(sock);
			} catch (IOException ioe) {
				System.out.println("Could not start SOCKS session");
				ioe.printStackTrace();
				auth = null;
				return;
			}

			if (auth == null) { // Authentication failed
				System.out.println("SOCKS auth failed");
				return;
			}

			in = auth.getInputStream();
			out = auth.getOutputStream();

			msg = readMsg(in);
			handleRequest(msg);
		}
		
		protected void onConnect(ProxyMessage msg) throws IOException {
			ProxyMessage response = null;
			Channel cn = null;
			StreamForwarder r2l = null;
			StreamForwarder l2r = null;

			try {
				/*
				 * This may fail, e.g., if the remote port is closed (in
				 * optimistic terms: not open yet)
				 */

				cn = cm.openDirectTCPIPChannel(msg.host, msg.port,
						sock.getInetAddress().getHostAddress(),
						sock.getPort());

			} catch (IOException e) {
				/*
				 * Simply close the local socket and wait for the next incoming
				 * connection
				 */

				try {
					sock.close();
				} catch (IOException ignore) {
				}

				return;
			}

			try {
				r2l = new StreamForwarder(cn, null, null, cn.stdoutStream, out, "RemoteToLocal");
				l2r = new StreamForwarder(cn, r2l, sock, in, cn.stdinStream, "LocalToRemote");
			} catch (IOException e) {
				try {
					/*
					 * This message is only visible during debugging, since we
					 * discard the channel immediatelly
					 */
					cn.cm.closeChannel(cn,
							"Weird error during creation of StreamForwarder ("
									+ e.getMessage() + ")", true);
				} catch (IOException ignore) {
				}

				return;
			}

			r2l.setDaemon(true);
			l2r.setDaemon(true);
			r2l.start();
			l2r.start();
			
			if (msg instanceof Socks5Message) {
				response = new Socks5Message(Proxy.SOCKS_SUCCESS, sock
						.getLocalAddress(), sock.getLocalPort());
			} else {
				response = new Socks4Message(Socks4Message.REPLY_OK, sock
						.getLocalAddress(), sock.getLocalPort());

			}
			response.write(out);
		}
	}
	
	public DynamicAcceptThread(ChannelManager cm, int local_port)
			throws IOException {
		this.cm = cm;

		ss = new ServerSocket(local_port);
	}
	
	public DynamicAcceptThread(ChannelManager cm, InetSocketAddress localAddress)
			throws IOException {
		this.cm = cm;

		ss = new ServerSocket();
		ss.bind(localAddress);
	}

	public void run() {
		try {
			cm.registerThread(this);
		} catch (IOException e) {
			stopWorking();
			return;
		}

		while (true) {
			Socket sock = null;

			try {
				sock = ss.accept();
			} catch (IOException e) {
				stopWorking();
				return;
			}

			DynamicAcceptRunnable dar = new DynamicAcceptRunnable(new ServerAuthenticatorNone(), sock);
			Thread t = new Thread(dar);
			t.setDaemon(true);
			t.start();
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.trilead.ssh2.channel.IChannelWorkerThread#stopWorking()
	 */
	public void stopWorking() {
		try {
			/* This will lead to an IOException in the ss.accept() call */
			ss.close();
		} catch (IOException e) {
		}
	}
}
