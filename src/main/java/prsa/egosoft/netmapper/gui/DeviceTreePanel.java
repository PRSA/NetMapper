package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.i18n.Messages;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkInterface;
import prsa.egosoft.netmapper.model.DetectedEndpoint;
import prsa.egosoft.netmapper.util.SubnetUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DeviceTreePanel extends JPanel {
	private JTree resultsTree;
	private DefaultTreeModel treeModel;
	private DefaultMutableTreeNode rootNode;
	private Map<String, DefaultMutableTreeNode> deviceNodeMap;
	private java.util.function.Consumer<NetworkDevice> selectionListener;

	public DeviceTreePanel() {
		setLayout(new BorderLayout());
		deviceNodeMap = new HashMap<>();

		initComponents();
		resultsTree.setCellRenderer(new DeviceTreeCellRenderer());
		updateUITexts();
	}

	private void initComponents() {
		rootNode = new DefaultMutableTreeNode(Messages.getString("tree.root"));
		treeModel = new DefaultTreeModel(rootNode);
		resultsTree = new JTree(treeModel);

		add(new JScrollPane(resultsTree), BorderLayout.CENTER);

		resultsTree.addTreeSelectionListener(e -> {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) resultsTree.getLastSelectedPathComponent();
			if (node != null && selectionListener != null) {
				Object userObject = node.getUserObject();
				if (userObject instanceof NetworkDevice) {
					selectionListener.accept((NetworkDevice) userObject);
				}
			}
		});
	}

	public void setSelectionListener(java.util.function.Consumer<NetworkDevice> selectionListener) {
		this.selectionListener = selectionListener;
	}

	public void updateUITexts() {
		rootNode.setUserObject(Messages.getString("tree.root"));
		treeModel.nodeChanged(rootNode);

		// Re-display existing devices to update their group labels if necessary
		// (though model objects won't change strings magically,
		// static parts of the tree like "Interfaces (3)" are generated in
		// displayDevice)
		// For simplicity, we only update the root here.
	}

	public void clear() {
		rootNode.removeAllChildren();
		treeModel.reload();
		deviceNodeMap.clear();
	}

	public void addOrUpdateDevice(NetworkDevice device) {
		DefaultMutableTreeNode deviceNode;
		boolean isNew = false;

		if (deviceNodeMap.containsKey(device.getIpAddress())) {
			deviceNode = deviceNodeMap.get(device.getIpAddress());
			deviceNode.setUserObject(device);
			deviceNode.removeAllChildren();
		} else {
			deviceNode = new DefaultMutableTreeNode(device);
			deviceNodeMap.put(device.getIpAddress(), deviceNode);
			isNew = true;
		}

		buildDeviceNodes(device, deviceNode);

		if (isNew) {
			int index = findInsertionIndex(device.getIpAddress());
			treeModel.insertNodeInto(deviceNode, rootNode, index);
			resultsTree.scrollPathToVisible(new TreePath(deviceNode.getPath()));
		} else {
			treeModel.nodeStructureChanged(deviceNode);
		}
	}

	private void buildDeviceNodes(NetworkDevice device, DefaultMutableTreeNode deviceNode) {
		// System Info Node
		deviceNode.add(createSystemInfoNode(device));

		// Interfaces Node
		deviceNode.add(createInterfacesNode(device));

		// Routing Node
		if (!device.getRoutingTable().isEmpty()) {
			deviceNode.add(createRoutingNode(device));
		}

		// VLANs Node
		if (!device.getVlans().isEmpty()) {
			deviceNode.add(createVlanNode(device));
		}
	}

	private DefaultMutableTreeNode createSystemInfoNode(NetworkDevice device) {
		DefaultMutableTreeNode node = new DefaultMutableTreeNode(Messages.getString("tree.system_info"));
		node.add(new DefaultMutableTreeNode(Messages.getString("info.vendor") + ": " + device.getVendor()));
		node.add(new DefaultMutableTreeNode(Messages.getString("info.model") + ": " + device.getModel()));

		String typeDisplay = device.getDeviceType();
		if (device.getFormattedServices() != null && !device.getFormattedServices().isEmpty()) {
			typeDisplay += " (" + device.getFormattedServices() + ")";
		}
		node.add(new DefaultMutableTreeNode(Messages.getString("info.type") + ": " + typeDisplay));
		node.add(new DefaultMutableTreeNode(Messages.getString("info.description") + ": " + device.getSysDescr()));
		node.add(new DefaultMutableTreeNode(Messages.getString("info.location") + ": " + device.getSysLocation()));
		node.add(new DefaultMutableTreeNode(Messages.getString("info.contact") + ": " + device.getSysContact()));
		node.add(new DefaultMutableTreeNode(Messages.getString("info.uptime") + ": " + device.getSysUpTime()));

		// MUDFR Fields
		if (device.getDiscoveryMethod() != null) {
			node.add(new DefaultMutableTreeNode(
					Messages.getString("info.discovery_method") + ": " + device.getDiscoveryMethod()));
		}
		if (device.getMgmtState() != null) {
			node.add(new DefaultMutableTreeNode(Messages.getString("info.mgmt_state") + ": " + device.getMgmtState()));
		}

		if (device.getDiscoverySources() != null && !device.getDiscoverySources().isEmpty()) {
			String sources = String.join(", ", device.getDiscoverySources());
			node.add(new DefaultMutableTreeNode(Messages.getString("info.discovery_sources") + ": [" + sources + "]"));
		}

		node.add(new DefaultMutableTreeNode(
				Messages.getString("info.confidence") + ": " + String.format("%.0f%%", device.getConfidence() * 100)));

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		node.add(new DefaultMutableTreeNode(
				Messages.getString("info.last_seen") + ": " + sdf.format(new Date(device.getLastSeenTimestamp()))));

		return node;
	}

	private DefaultMutableTreeNode createInterfacesNode(NetworkDevice device) {
		List<NetworkInterface> interfaces = new ArrayList<>(device.getInterfaces());
		sortInterfaces(interfaces); // Ordenación

		DefaultMutableTreeNode ifNode = new DefaultMutableTreeNode(
				Messages.getString("tree.interfaces", interfaces.size()));

		for (NetworkInterface ni : interfaces) {
			ifNode.add(createSingleInterfaceNode(ni, device));
		}
		return ifNode;
	}

	private DefaultMutableTreeNode createSingleInterfaceNode(NetworkInterface ni, NetworkDevice device) {
		DefaultMutableTreeNode niNode = new DefaultMutableTreeNode(ni.toString());

		// Detalles de configuración
		niNode.add(
				new DefaultMutableTreeNode(Messages.getString("interface.admin_status") + ": " + ni.getAdminStatus()));
		niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.oper_status") + ": " + ni.getOperStatus()));

		// Display MAC address with vendor information
		addMacAddressNode(niNode, ni);

		// Display interface type
		addIfNotNull(niNode, Messages.getString("interface.type"), ni.getType());

		// Display MTU if available
		if (ni.getMtu() > 0)
			niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.mtu") + ": " + ni.getMtu()));

		// Display speed if available
		if (ni.getSpeed() != null) {
			niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.speed") + ": " + ni.getSpeed() + " "
					+ Messages.getString("interface.speed_unit")));
		}

		// Display duplex mode if available
		if (ni.getDuplexMode() != null && !ni.getDuplexMode().equalsIgnoreCase("Unknown")) {
			niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.duplex") + ": " + ni.getDuplexMode()));
		}

		// Display STP state if available
		if (ni.getStpState() != null) {
			niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.stp_state") + ": " + ni.getStpState()));
		}
		if (ni.getStpRole() != null) {
			niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.stp_role") + ": " + ni.getStpRole()));
		}

		// Display Neighbor info if available
		if (ni.getNeighborInfo() != null && !ni.getNeighborInfo().isEmpty()) {
			niNode.add(new DefaultMutableTreeNode(
					Messages.getString("interface.neighbors") + ": " + ni.getNeighborInfo()));
		}

		// Counters (Phase 6)
		if (ni.getInErrors() > 0)
			niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.in_errors") + ": " + ni.getInErrors()));
		if (ni.getOutErrors() > 0)
			niNode.add(
					new DefaultMutableTreeNode(Messages.getString("interface.out_errors") + ": " + ni.getOutErrors()));
		if (ni.getInDiscards() > 0)
			niNode.add(new DefaultMutableTreeNode(
					Messages.getString("interface.in_discards") + ": " + ni.getInDiscards()));
		if (ni.getOutDiscards() > 0)
			niNode.add(new DefaultMutableTreeNode(
					Messages.getString("interface.out_discards") + ": " + ni.getOutDiscards()));

		// Display IP address and subnet mask if available
		if (ni.getIpAddress() != null) {
			niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.ip") + ": " + ni.getIpAddress()));
			niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.mask") + ": " + ni.getSubnetMask()));
		}

		// Display untagged VLANs if available
		if (ni.getUntaggedVlanId() > 0) {
			niNode.add(new DefaultMutableTreeNode(
					Messages.getString("interface.vlan_native_label") + ": " + ni.getUntaggedVlanId()));
		}

		// Display tagged VLANs if available
		if (!ni.getTaggedVlans().isEmpty()) {
			niNode.add(new DefaultMutableTreeNode(
					Messages.getString("interface.vlans_tagged_label") + ": " + ni.getTaggedVlans()));
		}

		// Learned Endpoints
		addLearnedEndpointsNode(niNode, ni, device);

		return niNode;
	}

	private void addMacAddressNode(DefaultMutableTreeNode parent, NetworkInterface ni) {
		if (ni.getMacAddress() != null) {
			String macDisplay = Messages.getString("interface.mac") + ": " + ni.getMacAddress();
			String vendor = prsa.egosoft.netmapper.util.MacVendorUtils.getVendor(ni.getMacAddress());
			if (vendor != null && !vendor.isEmpty()
					&& !prsa.egosoft.netmapper.i18n.Messages.getString("vendor.unknown").equals(vendor)) {
				macDisplay += " (" + vendor + ")";
			}
			parent.add(new DefaultMutableTreeNode(macDisplay));
		} else {
			parent.add(new DefaultMutableTreeNode(Messages.getString("interface.mac_na")));
		}
	}

	private void addLearnedEndpointsNode(DefaultMutableTreeNode parent, NetworkInterface ni, NetworkDevice device) {
		java.util.List<DetectedEndpoint> learned = device.getMacAddressTable().get(ni.getIndex());
		if (learned != null && !learned.isEmpty()) {
			DefaultMutableTreeNode learnedNode = new DefaultMutableTreeNode(
					Messages.getString("interface.learned_endpoints", learned.size()));
			for (DetectedEndpoint endpoint : learned) {
				learnedNode.add(new DefaultMutableTreeNode(endpoint.toString()));
			}
			parent.add(learnedNode);
		}
	}

	private DefaultMutableTreeNode createRoutingNode(NetworkDevice device) {
		DefaultMutableTreeNode routeNode = new DefaultMutableTreeNode(Messages.getString("tree.routes"));
		for (Map.Entry<String, String> route : device.getRoutingTable().entrySet()) {
			String dest = route.getKey();
			String gw = route.getValue();
			String proto = device.getRouteProtocols().get(dest);
			String label = Messages.getString("route.destination_label") + ": " + dest + " -> "
					+ Messages.getString("route.gateway_label") + ": " + gw;
			if (proto != null) {
				label += " [" + Messages.getString("route.protocol") + ": " + proto + "]";
			}
			routeNode.add(new DefaultMutableTreeNode(label));
		}
		return routeNode;
	}

	private DefaultMutableTreeNode createVlanNode(NetworkDevice device) {
		DefaultMutableTreeNode vlanNode = new DefaultMutableTreeNode(
				Messages.getString("tree.vlans", device.getVlans().size()));
		for (String vlan : device.getVlans()) {
			vlanNode.add(new DefaultMutableTreeNode(vlan));
		}
		return vlanNode;
	}

	private void addIfNotNull(DefaultMutableTreeNode node, String label, String value) {
		if (value != null) {
			node.add(new DefaultMutableTreeNode(label + ": " + value));
		}
	}

	private void sortInterfaces(List<NetworkInterface> interfaces) {
		// La lógica compleja de comparación
		interfaces.sort((n1, n2) -> {
			String s1 = (n1.getDescription() == null) ? "" : n1.getDescription();
			String s2 = (n2.getDescription() == null) ? "" : n2.getDescription();

			boolean isN1Num = s1.matches("\\d+");
			boolean isN2Num = s2.matches("\\d+");

			if (isN1Num && isN2Num) {
				try {
					return Long.compare(Long.parseLong(s1), Long.parseLong(s2));
				} catch (NumberFormatException e) {
					/* Fallback */ }
			}
			if (isN1Num)
				return -1;
			if (isN2Num)
				return 1;
			return s1.compareToIgnoreCase(s2);
		});
	}

	private int findInsertionIndex(String ip) {
		int count = rootNode.getChildCount();
		for (int i = 0; i < count; i++) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
			NetworkDevice cd = (NetworkDevice) child.getUserObject();
			if (SubnetUtils.compareIps(ip, cd.getIpAddress()) < 0) {
				return i;
			}
		}
		return count;
	}

	private class DeviceTreeCellRenderer extends javax.swing.tree.DefaultTreeCellRenderer {
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			if (value instanceof DefaultMutableTreeNode) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
				Object userObject = node.getUserObject();

				if (userObject instanceof NetworkDevice) {
					NetworkDevice device = (NetworkDevice) userObject;
					setIcon(getStatusIcon(device.getMgmtState()));
				}
			}
			return this;
		}

		private Icon getStatusIcon(NetworkDevice.ManagementState state) {
			int size = 12;
			BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = img.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			Color color;
			if (state == null) {
				color = Color.GRAY;
			} else {
				switch (state) {
					case REACHABLE:
						color = new Color(40, 167, 69); // Green
						break;
					case AUTH_FAILED:
						color = new Color(255, 193, 7); // Yellow/Orange
						break;
					case UNREACHABLE:
						color = new Color(220, 53, 69); // Red
						break;
					case POLICIED_SILENCE:
						color = new Color(108, 117, 125); // Gray/Muted
						break;
					default:
						color = Color.LIGHT_GRAY;
				}
			}

			g2.setColor(color);
			g2.fillOval(1, 1, size - 2, size - 2);
			g2.setColor(color.darker());
			g2.drawOval(1, 1, size - 2, size - 2);
			g2.dispose();

			return new ImageIcon(img);
		}
	}
}