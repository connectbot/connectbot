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

package org.connectbot.data;

import java.util.List;
import java.util.Map;

import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;

import com.trilead.ssh2.KnownHosts;

import androidx.annotation.VisibleForTesting;

/**
 * Interface that defines the operation used to interact with the storage layer.
 */
public interface HostStorage {
	/**
	 * Resets the database during testing.
	 */
	@VisibleForTesting
	void resetDatabase();

	/**
	 * Finds the host that is represented by the given selection.
	 * @param selection all fields to be considered
	 * @return the matching host
	 */
	HostBean findHost(Map<String, String> selection);

	/**
	 * Deletes the given {@code host} from the storage layer.
	 */
	void deleteHost(HostBean host);

	/**
	 * Saves the given {@code host} to the storage layer.
	 */
	HostBean saveHost(HostBean host);

	/**
	 * Returns a list of all the hosts as a list of {@link HostBean}.
	 * @param sortedByColor if hosts should be grouped by color.
	 */
	List<HostBean> getHosts(boolean sortedByColor);

	/**
	 * Updates the last connected time for {@code host}.
	 */
	void touchHost(HostBean host);

	/**
	 * Finds a {@link HostBean} based on the given {@code hostId}.
	 */
	HostBean findHostById(long hostId);

	/**
	 * Returns the list of known hosts.
	 *
	 * @see #saveKnownHost(String, int, String, byte[])
	 */
	KnownHosts getKnownHosts();

	/**
	 * Returns the list of host key algorithms known for the host.
	 */
	List<String> getHostKeyAlgorithmsForHost(String hostname, int port);

	/**
	 * Adds a known host to the database for later retrieval using {@link #getKnownHosts()}.
	 */
	void saveKnownHost(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey);

	/**
	 * Removes a known host from the database.
	 */
	void removeKnownHost(String host, int port, String serverHostKeyAlgorithm, byte[] serverHostKey);

	/**
	 * Return all port forwards for the given {@code host}.
	 */
	List<PortForwardBean> getPortForwardsForHost(HostBean host);
}
