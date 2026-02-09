package prsa.egosoft.netmapper;

import prsa.egosoft.netmapper.gui.MainWindow;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.service.ExportService;
import prsa.egosoft.netmapper.service.NetworkController;
import prsa.egosoft.netmapper.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

import javax.swing.SwingUtilities;

public class Main {
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	public static final boolean IS_ADMIN;

	static {
		boolean isAdmin = false;
		String os = System.getProperty("os.name").toLowerCase();

		try {
			if (os.contains("win")) {
				// En Windows, 'net session' devuelve error (código 1) si no eres admin
				Process process = Runtime.getRuntime().exec("net session");
				isAdmin = process.waitFor() == 0;
			} else {
				// En Unix (Linux/macOS), el ID de usuario 0 siempre es root
				Process process = Runtime.getRuntime().exec("id -u");
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String uid = reader.readLine();
				isAdmin = "0".equals(uid);
			}
		} catch (Exception e) {
			isAdmin = false;
		}

		IS_ADMIN = isAdmin;
	}

	public static void main(String[] args) {
		if (args.length > 0) {
			runHeadless(args);
		} else {
			SwingUtilities.invokeLater(() -> {
				new MainWindow().setVisible(true);
			});
		}
	}

	private static void runHeadless(String[] args) {
		CliArguments cliArgs = parseArguments(args);

		if (cliArgs.showHelp) {
			displayHelp();
			return;
		}

		if (cliArgs.jsonPath != null && (cliArgs.targets != null || cliArgs.autoDiscovery)) {
			logger.error(Messages.getString("cli.error.exclusive_map"));
			return;
		}

		if (cliArgs.targets != null && cliArgs.autoDiscovery) {
			logger.error(Messages.getString("cli.error.exclusive"));
			return;
		}

		if (cliArgs.jsonPath == null) {
			if (cliArgs.targets == null && !cliArgs.autoDiscovery) {
				logger.error(Messages.getString("cli.error.missing_target"));
				return;
			}
			if (cliArgs.community == null) {
				logger.error(Messages.getString("cli.error.missing_community"));
				return;
			}
		}

		NetworkController controller = new NetworkController();
		controller.setVerbose(cliArgs.verbose);
		controller.setForensics(cliArgs.forensics);

		if (cliArgs.jsonPath != null && cliArgs.targets == null && !cliArgs.autoDiscovery) {
			logger.info(Messages.getString("cli.msg.loading_map", cliArgs.jsonPath));
			try {
				controller.loadDevicesFromJson(new File(cliArgs.jsonPath));
			} catch (Exception e) {
				logger.error(Messages.getString("cli.error.load_map", e.getMessage()));
				return;
			}
		} else {
			logger.info(Messages.getString("cli.msg.scan_start"));
			if (cliArgs.autoDiscovery) {
				controller.autoDiscoverBlocking(cliArgs.community,
						device -> logger.info(Messages.getString("message.scan_complete", device.toString())));
			} else {
				controller.scanNetworkBlocking(cliArgs.targets, cliArgs.community,
						device -> logger.info(Messages.getString("message.scan_complete", device.toString())));
			}
		}

		Map<String, NetworkDevice> devices = controller.getDiscoveredDevices();
		logger.info(Messages.getString("cli.msg.scan_complete", devices.size()));

		if (devices.isEmpty()) {
			logger.info(Messages.getString("cli.msg.no_devices"));
			controller.shutdown();
			return;
		}

		ExportService exportService = new ExportService();
		NetworkGraph graph = NetworkGraph.buildFromDevices(devices);

		if (cliArgs.minConfidence > 0) {
			exportService.filterGraphByConfidence(graph, cliArgs.minConfidence);
		}

		if (cliArgs.verbose) {
			logger.info("Forensic Methodology Trace (GTR/MUDFR):");
		}

		if (cliArgs.forensics) {
			displayForensicDump(devices);
		}

		executeExports(cliArgs, devices, graph, exportService);

		controller.shutdown();
		logger.info(Messages.getString("cli.msg.done"));
	}

	private static CliArguments parseArguments(String[] args) {
		CliArguments cliArgs = new CliArguments();
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-c":
					if (i + 1 < args.length)
						cliArgs.community = args[++i];
					break;
				case "-t":
					if (i + 1 < args.length)
						cliArgs.targets = args[++i];
					break;
				case "-a":
					cliArgs.autoDiscovery = true;
					break;
				case "-png":
					if (i + 1 < args.length)
						cliArgs.pngPath = args[++i];
					break;
				case "-pdf":
					if (i + 1 < args.length)
						cliArgs.pdfPath = args[++i];
					break;
				case "-json":
				case "-m":
					if (i + 1 < args.length)
						cliArgs.jsonPath = args[++i];
					break;
				case "-h":
					cliArgs.showHelp = true;
					break;
				case "-v":
					cliArgs.verbose = true;
					break;
				case "--forensics":
					cliArgs.forensics = true;
					break;
				case "--min-confidence":
					if (i + 1 < args.length) {
						try {
							cliArgs.minConfidence = Double.parseDouble(args[++i]);
						} catch (NumberFormatException e) {
							logger.warn("Invalid confidence value: " + args[i]);
						}
					}
					break;
			}
		}
		return cliArgs;
	}

	private static void displayHelp() {
		logger.info(Messages.getString("cli.usage"));
		logger.info(Messages.getString("cli.options"));
		logger.info("  -c <community>  " + Messages.getString("cli.opt.community"));
		logger.info("  -t <targets>	" + Messages.getString("cli.opt.targets"));
		logger.info("  -a			  " + Messages.getString("cli.opt.autodiscover"));
		logger.info("  -m <path>	   " + Messages.getString("cli.opt.load_map"));
		logger.info("  -png <path>	 " + Messages.getString("cli.opt.png"));
		logger.info("  -pdf <path>	 " + Messages.getString("cli.opt.pdf"));
		logger.info("  -json <path>	" + Messages.getString("cli.opt.json"));
		logger.info("  -v			  " + Messages.getString("cli.opt.verbose"));
		logger.info("  --forensics	 " + Messages.getString("cli.opt.forensics"));
		logger.info("  --min-confidence <val> " + Messages.getString("cli.opt.min_confidence"));
		logger.info("  -h			  " + Messages.getString("cli.opt.help"));
	}

	private static void displayForensicDump(Map<String, NetworkDevice> devices) {
		logger.info("Forensic Data Dump:");
		for (NetworkDevice dev : devices.values()) {
			String deviceId = dev.getIpAddress() != null ? dev.getIpAddress() : dev.getSysName();
			logger.info("Device: " + deviceId);
			if (!dev.getMacAddressTable().isEmpty()) {
				logger.info("  FDB: " + dev.getMacAddressTable());
			}
			if (!dev.getRoutingTable().isEmpty()) {
				logger.info("  ARP/Route: " + dev.getRoutingTable());
			}
		}
	}

	private static void executeExports(CliArguments cliArgs, Map<String, NetworkDevice> devices, NetworkGraph graph,
			ExportService exportService) {
		try {
			boolean loadedFromMap = (cliArgs.jsonPath != null && cliArgs.targets == null && !cliArgs.autoDiscovery);

			if (cliArgs.jsonPath != null && !loadedFromMap) {
				logger.info(Messages.getString("cli.export.json", cliArgs.jsonPath));
				exportService.exportToJSON(new File(cliArgs.jsonPath), devices);
			}

			if (cliArgs.pngPath != null) {
				logger.info(Messages.getString("cli.export.png", cliArgs.pngPath));
				exportService.exportToPNG(new File(cliArgs.pngPath), graph);
			}
			if (cliArgs.pdfPath != null) {
				logger.info(Messages.getString("cli.export.pdf", cliArgs.pdfPath));
				exportService.exportToPDF(new File(cliArgs.pdfPath), graph);
			}
		} catch (Exception e) {
			logger.error(Messages.getString("cli.error.export", e.getMessage()));
		}
	}

	private static class CliArguments {
		String community = null;
		String targets = null;
		boolean autoDiscovery = false;
		String pngPath = null;
		String pdfPath = null;
		String jsonPath = null;
		boolean showHelp = false;
		boolean verbose = false;
		boolean forensics = false;
		double minConfidence = 0.0;
	}
}
