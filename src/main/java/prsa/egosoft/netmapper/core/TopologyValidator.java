package prsa.egosoft.netmapper.core;

import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Validador de topología para detectar inconsistencias físicas y lógicas.
 */
public class TopologyValidator {

    public static class ValidationIssue {
        public enum Severity {
            WARNING, ERROR
        }

        public final Severity severity;
        public final String message;
        public final String sourceDevice;
        public final String targetDevice;

        public ValidationIssue(Severity severity, String message, String source, String target) {
            this.severity = severity;
            this.message = message;
            this.sourceDevice = source;
            this.targetDevice = target;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s -> %s: %s", severity, sourceDevice, targetDevice, message);
        }
    }

    /**
     * Detecta "Duplex Mismatch" entre dos interfaces conectadas.
     * Problema común: Un extremo en Full-Duplex y otro en Half-Duplex (o Auto
     * fallido).
     */
    public List<ValidationIssue> validateDuplexMismatch(NetworkDevice source, NetworkInterface sourceIf,
            NetworkDevice target, NetworkInterface targetIf) {
        List<ValidationIssue> issues = new ArrayList<>();

        String srcDuplex = sourceIf.getDuplexMode();
        String dstDuplex = targetIf.getDuplexMode();

        if (srcDuplex != null && dstDuplex != null &&
                !srcDuplex.equalsIgnoreCase("Unknown") && !dstDuplex.equalsIgnoreCase("Unknown") &&
                !srcDuplex.equalsIgnoreCase(dstDuplex)) {

            issues.add(new ValidationIssue(
                    ValidationIssue.Severity.WARNING,
                    String.format("Duplex Mismatch: %s (%s) vs %s (%s)",
                            sourceIf.getDescription(), srcDuplex, targetIf.getDescription(), dstDuplex),
                    source.getSysName(),
                    target.getSysName()));
        }
        return issues;
    }

    /**
     * Detecta "MTU Mismatch".
     * Si los MTU no coinciden, paquetes grandes se perderán (Black Hole).
     */
    public List<ValidationIssue> validateMtuMismatch(NetworkDevice source, NetworkInterface sourceIf,
            NetworkDevice target, NetworkInterface targetIf) {
        List<ValidationIssue> issues = new ArrayList<>();

        int srcMtu = sourceIf.getMtu();
        int dstMtu = targetIf.getMtu();

        if (srcMtu > 0 && dstMtu > 0 && srcMtu != dstMtu) {
            issues.add(new ValidationIssue(
                    ValidationIssue.Severity.ERROR,
                    String.format("MTU Mismatch: %s (%d) vs %s (%d)",
                            sourceIf.getDescription(), srcMtu, targetIf.getDescription(), dstMtu),
                    source.getSysName(),
                    target.getSysName()));
        }
        return issues;
    }

    /**
     * Valida consistencia de velocidad.
     */
    public List<ValidationIssue> validateSpeedMismatch(NetworkDevice source, NetworkInterface sourceIf,
            NetworkDevice target, NetworkInterface targetIf) {
        List<ValidationIssue> issues = new ArrayList<>();

        String srcSpeed = sourceIf.getSpeed();
        String dstSpeed = targetIf.getSpeed();

        if (srcSpeed != null && dstSpeed != null && !srcSpeed.equals(dstSpeed)) {
            issues.add(new ValidationIssue(
                    ValidationIssue.Severity.WARNING,
                    String.format("Speed Mismatch: %s (%s bps) vs %s (%s bps)",
                            sourceIf.getDescription(), srcSpeed, targetIf.getDescription(), dstSpeed),
                    source.getSysName(),
                    target.getSysName()));
        }
        return issues;
    }
}
