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

package com.trilead.ssh2;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.trilead.ssh2.channel.ChannelManager;
import com.trilead.ssh2.channel.DynamicAcceptThread;

/**
 * A <code>DynamicPortForwarder</code> forwards TCP/IP connections to a local
 * port via the secure tunnel to another host which is selected via the
 * SOCKS protocol. Checkout {@link Connection#createDynamicPortForwarder(int)}
 * on how to create one.
 *
 * @author Kenny Root
 * @version $Id: $
 */
public class DynamicPortForwarder {
	ChannelManager cm;

	DynamicAcceptThread dat;

	DynamicPortForwarder(ChannelManager cm, int local_port)
			throws IOException
	{
		this.cm = cm;

		dat = new DynamicAcceptThread(cm, local_port);
		dat.setDaemon(true);
		dat.start();
	}

	DynamicPortForwarder(ChannelManager cm, InetSocketAddress addr) throws IOException {
		this.cm = cm;

		dat = new DynamicAcceptThread(cm, addr);
		dat.setDaemon(true);
		dat.start();
	}

	/**
	 * Stop TCP/IP forwarding of newly arriving connections.
	 *
	 * @throws IOException
	 */
	public void close() throws IOException
	{
		dat.stopWorking();
	}
}
