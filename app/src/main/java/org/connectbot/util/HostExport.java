package org.connectbot.util;

import java.util.ArrayList;
import java.util.List;

import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;

import kotlin.collections.ArrayDeque;

public class HostExport {
	private List<HostWithForwards> hosts;

	public HostExport() {
		this.hosts = new ArrayList<>();
	}
	public HostExport(List<HostWithForwards> hosts) {
		this.hosts = hosts;
	}

	public List<HostWithForwards> getHosts() {
		return hosts;
	}

	public void addHost(HostBean host, List<PortForwardBean> portForwards) {
		HostWithForwards hostExport = new HostWithForwards(host, portForwards);
		hosts.add(hostExport);
	}

	public class HostWithForwards {
		HostBean host;
		List<PortForwardBean> portforwards;

		public HostWithForwards (HostBean host, List<PortForwardBean> portforwards) {
			this.host = host;
			this.portforwards = portforwards;
		}

		public HostBean getHost() {
			return host;
		}

		public List<PortForwardBean> getPortforwards() {
			return portforwards;
		}

	}
}
