package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.i18n.Messages;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel lateral para mostrar evidencia forense de inferencias (FDB, ARP, LLDP).
 */
public class DetailsPanel extends JPanel {
	private JLabel titleLabel;
	private JTextArea detailsArea;

	public DetailsPanel() {
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createTitledBorder(Messages.getString("tab.details")));
		setPreferredSize(new Dimension(250, 0));

		initComponents();
	}

	private void initComponents() {
		titleLabel = new JLabel(" " + Messages.getString("info.evidence_title"));
		titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
		add(titleLabel, BorderLayout.NORTH);

		detailsArea = new JTextArea();
		detailsArea.setEditable(false);
		detailsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
		detailsArea.setLineWrap(true);
		detailsArea.setWrapStyleWord(true);

		add(new JScrollPane(detailsArea), BorderLayout.CENTER);
	}

	public void showNodeDetails(NetworkGraph.GraphNode node, NetworkDevice device) {
		titleLabel.setText(" " + node.getLabel().split("\n")[0]);
		StringBuilder sb = new StringBuilder();
		sb.append(Messages.getString("info.type")).append(": ").append(node.getTypeLabel()).append("\n");
		sb.append(Messages.getString("info.confidence")).append(": ")
				.append(String.format("%.0f%%", node.getConfidence() * 100)).append("\n\n");

		if (device != null) {
			sb.append("--- ").append(Messages.getString("info.evidence_title")).append(" ---\n");
			if (device.getDiscoverySources() != null && !device.getDiscoverySources().isEmpty()) {
				sb.append(Messages.getString("info.discovery_sources")).append(":\n");
				for (String source : device.getDiscoverySources()) {
					sb.append("  - ").append(source).append("\n");
				}
			}

			if (!device.getMacAddressTable().isEmpty()) {
				sb.append("\nFDB Table Insights:\n");
				device.getMacAddressTable().forEach((port, endpoints) -> sb.append(" Port ").append(port).append(": ")
						.append(endpoints.size()).append(" endpoints\n"));
			}
		} else {
			sb.append(Messages.getString("info.shadow_inference_justification")).append("\n");
		}

		detailsArea.setText(sb.toString());
		detailsArea.setCaretPosition(0);
	}

	public void showEdgeDetails(NetworkGraph.GraphEdge edge) {
		titleLabel.setText(" " + Messages.getString("filter.link_types"));
		StringBuilder sb = new StringBuilder();
		sb.append(edge.getSourceId()).append(" <-> ").append(edge.getTargetId()).append("\n\n");
		sb.append(Messages.getString("info.type")).append(": ").append(edge.getType()).append("\n");
		sb.append(Messages.getString("info.confidence")).append(": ")
				.append(String.format("%.0f%%", edge.getConfidence() * 100)).append("\n");

		if (edge.getLabel() != null) {
			sb.append(Messages.getString("info.description")).append(": ").append(edge.getLabel()).append("\n");
		}

		sb.append("\nJustification:\n");
		if (edge.getConfidence() >= 1.0) {
			sb.append("- Verified via LLDP/CDP neighbor discovery.\n");
		} else if (edge.getType() == NetworkGraph.EdgeType.PHYSICAL) {
			sb.append("- Inferred via FDB Triangulation.\n");
			sb.append("- Probable matching port found in source MAC table.\n");
		} else {
			sb.append("- Inferred via L3 Routing Adjacency.\n");
		}

		detailsArea.setText(sb.toString());
		detailsArea.setCaretPosition(0);
	}

	public void clear() {
		titleLabel.setText(" " + Messages.getString("tab.details"));
		detailsArea.setText("");
	}
}
