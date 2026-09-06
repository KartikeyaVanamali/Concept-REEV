import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only instrumentation boundary for the modular REEV simulation.
 *
 * <p>This class observes completed controller frames after power arbitration.
 * It cannot command torque, start the generator, alter battery limits, change a
 * drive mode, or participate in a safety decision. Its only responsibility is
 * to turn subsystem evidence into immutable, explicitly-unitized snapshots for
 * a future instrument cluster or infotainment UI.</p>
 */
public final class VehicleInstrumentationManagementSystem {

    public enum DataQuality {
        VALID,
        DEGRADED,
        UNAVAILABLE
    }

    public enum BatteryDirection {
        DISCHARGING,
        CHARGING,
        IDLE,
        UNAVAILABLE
    }

    /**
     * Input assembled by PowerTrainController after the actuator command is
     * resolved. Power values retain their subsystem reference planes.
     */
    public record InstrumentationFrame(
            double deltaSeconds,
            double vehicleSpeedKmph,
            double requestedWheelPowerKw,
            double criticalAuxiliaryPowerKw,
            double permittedComfortAuxiliaryPowerKw,
            double generatorControlReserveKw,
            double reservedBatteryRecoveryPowerKw,
            PowerTrainController.TelemetryFrame controller,
            BatteryManagementSystem.Status battery,
            GeneratorManagementSystem.Status generator,
            TractionMotorManagement.Status traction) {
    }

    public record DashboardAlert(
            String id,
            PowerTrainController.Severity severity,
            String message,
            boolean acknowledgementAllowed) {
    }

    public record EnergyPanel(
            double displayedSocPercent,
            double physicalSocPercent,
            double usableBatteryEnergyKwh,
            double turtleEnergyTargetKwh,
            double turtleEntrySocPercent,
            double recoveryExitEnergyKwh,
            double recoveryProgressPercent,
            double usableFuelLitres,
            BatteryDirection batteryDirection) {
    }

    /**
     * Generator values are rectified HV DC. Battery power is at battery HV
     * terminals and is positive while discharging. Wheel values are mechanical.
     */
    public record PowerFlowPanel(
            double requestedWheelPowerKw,
            double deliveredWheelPowerKw,
            double tractionDcPowerKw,
            double batteryTerminalPowerKw,
            double generatorCurrentDcPowerKw,
            double generatorGuaranteedDcPowerKw,
            double generatorTargetBandDcPowerKw,
            double estimatedFuelFlowLitresPerHour,
            double generatorToAuxiliariesKw,
            double generatorToTractionKw,
            double generatorToBatteryKw,
            double batteryToAuxiliariesKw,
            double batteryToTractionKw,
            double regenerativeChargePowerKw,
            double frictionBrakingWheelPowerKw,
            double totalActiveAuxiliaryPowerKw,
            double combinedAvailableTractionDcPowerKw,
            double tractionAllocationResidualKw,
            double batteryAccountingResidualKw,
            double generatorAllocationResidualKw) {
    }

    public record MobilityPanel(
            double vehicleSpeedKmph,
            double dynamicMaximumSpeedKmph,
            double electricRangeKm,
            double generatorRangeKm,
            double combinedRangeKm,
            double predictedGeneratorRuntimeMinutes,
            boolean turtleActive) {
    }

    public record DrivetrainPanel(
            double rearWheelPowerKw,
            double frontWheelPowerKw,
            double frontAxleContributionPercent,
            double generatorUtilizationPercent,
            int activeGeneratorBandIndex,
            boolean generatorRunning,
            boolean tractionLimited,
            String batteryLimitingReason,
            String generatorTransitionReason,
            String tractionLimitingReason) {
    }

    public record TrendPanel(
            int sampleCount,
            double windowSeconds,
            double averageVehicleSpeedKmph,
            double averageWheelPowerKw,
            double averageBatteryPowerKw,
            double averageGeneratorPowerKw,
            double displayedSocChangePercentPerMinute,
            double estimatedFuelUseLitresPerHour) {
    }

    public record DashboardSnapshot(
            long sequence,
            double elapsedSimulationSeconds,
            PowerTrainController.VehicleVariant variant,
            PowerTrainController.OperatingState operatingState,
            PowerTrainController.DriveMode driveMode,
            DataQuality dataQuality,
            EnergyPanel energy,
            PowerFlowPanel powerFlow,
            MobilityPanel mobility,
            DrivetrainPanel drivetrain,
            TrendPanel trends,
            PowerTrainController.AuxiliaryState auxiliaryState,
            List<DashboardAlert> activeAlerts) {

        public DashboardSnapshot {
            activeAlerts = List.copyOf(activeAlerts);
        }
    }

    private static final int MAXIMUM_HISTORY_SAMPLES = 30;
    private static final double RESIDUAL_TOLERANCE_KW = 0.10;

    private final ArrayDeque<DashboardSnapshot> history = new ArrayDeque<>();
    private long sequence;
    private double elapsedSimulationSeconds;
    private DashboardSnapshot latestSnapshot;

    public synchronized DashboardSnapshot capture(
            InstrumentationFrame frame) {
        validate(frame);
        sequence++;
        elapsedSimulationSeconds += frame.deltaSeconds();
        while (history.size() >= MAXIMUM_HISTORY_SAMPLES) {
            history.removeFirst();
        }

        DataQuality quality = dataQuality(frame);
        List<DashboardAlert> alerts = prioritizedAlerts(frame, quality);
        EnergyPanel energy = energyPanel(frame, quality);
        PowerFlowPanel powerFlow = powerFlowPanel(frame);
        MobilityPanel mobility = mobilityPanel(frame);
        DrivetrainPanel drivetrain = drivetrainPanel(frame);
        TrendPanel trends = trendPanel(
                frame,
                energy,
                powerFlow,
                mobility);

        DashboardSnapshot snapshot = new DashboardSnapshot(
                sequence,
                elapsedSimulationSeconds,
                frame.controller().variant(),
                frame.controller().operatingState(),
                frame.controller().activeMode(),
                quality,
                energy,
                powerFlow,
                mobility,
                drivetrain,
                trends,
                frame.controller().auxiliaryState(),
                alerts);
        history.addLast(snapshot);
        latestSnapshot = snapshot;
        return snapshot;
    }

    public synchronized DashboardSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    public synchronized List<DashboardSnapshot> history() {
        return List.copyOf(history);
    }

    public int maximumHistorySamples() {
        return MAXIMUM_HISTORY_SAMPLES;
    }

    private static EnergyPanel energyPanel(
            InstrumentationFrame frame,
            DataQuality quality) {
        BatteryManagementSystem.Status battery = frame.battery();
        PowerTrainController.TelemetryFrame controller = frame.controller();
        if (battery == null) {
            return new EnergyPanel(
                    controller.displayedSoc() * 100.0,
                    controller.physicalSoc() * 100.0,
                    controller.remainingBatteryEnergyKwh(),
                    controller.turtleEnergyTargetKwh(),
                    Double.NaN,
                    Double.NaN,
                    controller.recoveryProgress() * 100.0,
                    controller.usableFuelLitres(),
                    BatteryDirection.UNAVAILABLE);
        }
        return new EnergyPanel(
                battery.displayedSoc() * 100.0,
                battery.physicalSoc() * 100.0,
                battery.remainingUsableEnergyKwh(),
                battery.turtleEnergyTargetKwh(),
                battery.turtleEntryDisplayedSoc() * 100.0,
                battery.recoveryExitEnergyKwh(),
                battery.recoveryProgress() * 100.0,
                controller.usableFuelLitres(),
                quality == DataQuality.UNAVAILABLE
                        ? BatteryDirection.UNAVAILABLE
                        : batteryDirection(controller.batteryPowerKw()));
    }

    private static PowerFlowPanel powerFlowPanel(
            InstrumentationFrame frame) {
        TractionMotorManagement.Status traction = frame.traction();
        GeneratorManagementSystem.Status generator = frame.generator();
        BatteryManagementSystem.Status battery = frame.battery();
        if (traction == null || generator == null || battery == null) {
            return unavailablePowerFlow(frame);
        }

        double tractionResidual =
                traction.tractionDcPowerKw()
                        - traction.generatorToTractionKw()
                        - traction.batteryToTractionKw();
        double expectedBatteryPower =
                traction.batteryToAuxiliariesKw()
                        + traction.batteryToTractionKw()
                        - traction.generatorToBatteryKw()
                        - traction.regenerativeChargePowerKw();
        double batteryResidual =
                traction.batteryPowerKw() - expectedBatteryPower;
        double generatorAfterReserve = Math.max(
                0.0,
                generator.guaranteedOutputKw()
                        - frame.generatorControlReserveKw());
        double allocatedGenerator =
                traction.generatorToAuxiliariesKw()
                        + traction.generatorToTractionKw()
                        + traction.generatorToBatteryKw()
                        + traction.unusedGeneratorPowerKw();
        double generatorResidual =
                generatorAfterReserve - allocatedGenerator;
        double activeAuxiliaryPower =
                traction.generatorToAuxiliariesKw()
                        + traction.batteryToAuxiliariesKw()
                        + traction.unservedAuxiliaryPowerKw();
        double combinedAvailableTractionDc = Math.min(
                traction.maximumTractionDcPowerKw(),
                Math.max(
                        0.0,
                        battery.availableDischargePowerKw()
                                + generatorAfterReserve
                                - frame.criticalAuxiliaryPowerKw()
                                - frame.permittedComfortAuxiliaryPowerKw()
                                - frame.reservedBatteryRecoveryPowerKw()));

        return new PowerFlowPanel(
                frame.requestedWheelPowerKw(),
                traction.deliveredWheelPowerKw(),
                traction.tractionDcPowerKw(),
                traction.batteryPowerKw(),
                generator.currentOutputKw(),
                generator.guaranteedOutputKw(),
                generator.targetBandKw(),
                generator.estimatedFuelFlowLitresPerHour(),
                traction.generatorToAuxiliariesKw(),
                traction.generatorToTractionKw(),
                traction.generatorToBatteryKw(),
                traction.batteryToAuxiliariesKw(),
                traction.batteryToTractionKw(),
                traction.regenerativeChargePowerKw(),
                traction.frictionBrakingWheelPowerKw(),
                activeAuxiliaryPower,
                combinedAvailableTractionDc,
                normalizeResidual(tractionResidual),
                normalizeResidual(batteryResidual),
                normalizeResidual(generatorResidual));
    }

    private static PowerFlowPanel unavailablePowerFlow(
            InstrumentationFrame frame) {
        double unavailable = Double.NaN;
        return new PowerFlowPanel(
                frame.requestedWheelPowerKw(),
                frame.controller().deliveredWheelPowerKw(),
                unavailable,
                frame.controller().batteryPowerKw(),
                frame.controller().generatorOutputKw(),
                unavailable,
                frame.controller().generatorTargetBandKw(),
                unavailable,
                unavailable,
                frame.controller().generatorToTractionKw(),
                frame.controller().generatorToBatteryKw(),
                unavailable,
                unavailable,
                frame.controller().regenerativeChargePowerKw(),
                frame.controller().frictionBrakingWheelPowerKw(),
                frame.criticalAuxiliaryPowerKw()
                        + frame.permittedComfortAuxiliaryPowerKw(),
                unavailable,
                unavailable,
                unavailable,
                unavailable);
    }

    private static MobilityPanel mobilityPanel(
            InstrumentationFrame frame) {
        PowerTrainController.TelemetryFrame controller = frame.controller();
        GeneratorManagementSystem.Status generator = frame.generator();
        double runtime = generator == null
                ? Double.NaN
                : generator.predictedFuelRuntimeMinutes();
        return new MobilityPanel(
                frame.vehicleSpeedKmph(),
                controller.dynamicMaximumSpeedKmph(),
                controller.estimatedElectricRangeKm(),
                controller.estimatedGeneratorRangeKm(),
                controller.estimatedElectricRangeKm()
                        + controller.estimatedGeneratorRangeKm(),
                runtime,
                controller.turtleActive());
    }

    private static DrivetrainPanel drivetrainPanel(
            InstrumentationFrame frame) {
        PowerTrainController.TelemetryFrame controller = frame.controller();
        BatteryManagementSystem.Status battery = frame.battery();
        GeneratorManagementSystem.Status generator = frame.generator();
        TractionMotorManagement.Status traction = frame.traction();
        double frontContribution = controller.deliveredWheelPowerKw() <= 0.0
                ? 0.0
                : controller.frontWheelPowerKw()
                        / controller.deliveredWheelPowerKw() * 100.0;
        double generatorUtilization =
                generator == null
                        ? Double.NaN
                        : generator.currentOutputKw()
                                / GeneratorManagementSystem
                                        .maximumConceptOutputKw() * 100.0;
        return new DrivetrainPanel(
                controller.rearWheelPowerKw(),
                controller.frontWheelPowerKw(),
                frontContribution,
                generatorUtilization,
                generator == null ? -1 : generator.activeBandIndex(),
                generator != null && generator.engineRunning(),
                controller.tractionLimited(),
                battery == null
                        ? "battery status unavailable"
                        : battery.limitingReason(),
                generator == null
                        ? "generator status unavailable"
                        : generator.transitionReason(),
                traction == null
                        ? "traction status unavailable"
                        : traction.limitingReason());
    }

    private TrendPanel trendPanel(
            InstrumentationFrame currentFrame,
            EnergyPanel currentEnergy,
            PowerFlowPanel currentPower,
            MobilityPanel currentMobility) {
        int samples = history.size() + 1;
        double windowSeconds = history.isEmpty()
                ? currentFrame.deltaSeconds()
                : elapsedSimulationSeconds
                        - history.getFirst().elapsedSimulationSeconds()
                        + currentFrame.deltaSeconds();
        double speedSum = currentMobility.vehicleSpeedKmph();
        double wheelPowerSum = currentPower.deliveredWheelPowerKw();
        double batteryPowerSum = finiteOrZero(
                currentPower.batteryTerminalPowerKw());
        double generatorPowerSum = finiteOrZero(
                currentPower.generatorCurrentDcPowerKw());
        double fuelFlowSum = finiteOrZero(
                currentPower.estimatedFuelFlowLitresPerHour());

        for (DashboardSnapshot snapshot : history) {
            speedSum += snapshot.mobility().vehicleSpeedKmph();
            wheelPowerSum += snapshot.powerFlow().deliveredWheelPowerKw();
            batteryPowerSum += finiteOrZero(
                    snapshot.powerFlow().batteryTerminalPowerKw());
            generatorPowerSum += finiteOrZero(
                    snapshot.powerFlow().generatorCurrentDcPowerKw());
            fuelFlowSum += finiteOrZero(
                    snapshot.powerFlow()
                            .estimatedFuelFlowLitresPerHour());
        }

        double socChangePerMinute = 0.0;
        double changeSeconds = history.isEmpty()
                ? 0.0
                : elapsedSimulationSeconds
                        - history.getFirst().elapsedSimulationSeconds();
        if (!history.isEmpty() && changeSeconds > 0.0) {
            DashboardSnapshot oldest = history.getFirst();
            socChangePerMinute =
                    (currentEnergy.displayedSocPercent()
                            - oldest.energy().displayedSocPercent())
                            / changeSeconds * 60.0;
        }
        return new TrendPanel(
                samples,
                windowSeconds,
                speedSum / samples,
                wheelPowerSum / samples,
                batteryPowerSum / samples,
                generatorPowerSum / samples,
                socChangePerMinute,
                fuelFlowSum / samples);
    }

    private static List<DashboardAlert> prioritizedAlerts(
            InstrumentationFrame frame,
            DataQuality quality) {
        Map<String, DashboardAlert> alertsById = new LinkedHashMap<>();
        for (PowerTrainController.HmiEvent event
                : frame.controller().hmiEvents()) {
            DashboardAlert alert = new DashboardAlert(
                    event.id(),
                    event.severity(),
                    event.message(),
                    event.acknowledgementAllowed());
            alertsById.merge(
                    alert.id(),
                    alert,
                    VehicleInstrumentationManagementSystem
                            ::higherSeverity);
        }
        if (quality == DataQuality.UNAVAILABLE) {
            alertsById.put(
                    "INSTRUMENTATION_DATA_UNAVAILABLE",
                    new DashboardAlert(
                            "INSTRUMENTATION_DATA_UNAVAILABLE",
                            PowerTrainController.Severity.CRITICAL,
                            "Powertrain instrumentation data unavailable",
                            false));
        } else if (quality == DataQuality.DEGRADED) {
            alertsById.put(
                    "INSTRUMENTATION_DATA_DEGRADED",
                    new DashboardAlert(
                            "INSTRUMENTATION_DATA_DEGRADED",
                            PowerTrainController.Severity.ADVISORY,
                            "Some powertrain gauges are degraded",
                            true));
        }
        List<DashboardAlert> alerts =
                new ArrayList<>(alertsById.values());
        alerts.sort(
                Comparator.comparingInt(
                                (DashboardAlert alert) ->
                                        alert.severity().ordinal())
                        .reversed()
                        .thenComparing(DashboardAlert::id));
        return List.copyOf(alerts);
    }

    private static DashboardAlert higherSeverity(
            DashboardAlert first,
            DashboardAlert second) {
        return first.severity().ordinal() >= second.severity().ordinal()
                ? first
                : second;
    }

    private static DataQuality dataQuality(InstrumentationFrame frame) {
        if (frame.battery() == null
                || frame.generator() == null
                || frame.traction() == null) {
            return DataQuality.UNAVAILABLE;
        }
        PowerFlowPanel powerFlow = powerFlowPanel(frame);
        if (!frame.battery().evidenceValid()
                || frame.generator().thermallyDerated()
                || frame.traction().unservedAuxiliaryPowerKw() > 0.01
                || Math.abs(
                        powerFlow.tractionAllocationResidualKw())
                        > RESIDUAL_TOLERANCE_KW
                || Math.abs(
                        powerFlow.batteryAccountingResidualKw())
                        > RESIDUAL_TOLERANCE_KW
                || Math.abs(
                        powerFlow.generatorAllocationResidualKw())
                        > RESIDUAL_TOLERANCE_KW) {
            return DataQuality.DEGRADED;
        }
        return DataQuality.VALID;
    }

    private static BatteryDirection batteryDirection(
            double batteryPowerKw) {
        if (batteryPowerKw > 0.10) {
            return BatteryDirection.DISCHARGING;
        }
        if (batteryPowerKw < -0.10) {
            return BatteryDirection.CHARGING;
        }
        return BatteryDirection.IDLE;
    }

    private static double normalizeResidual(double residualKw) {
        return Math.abs(residualKw) <= 1.0e-9 ? 0.0 : residualKw;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static void validate(InstrumentationFrame frame) {
        if (frame == null || frame.controller() == null) {
            throw new IllegalArgumentException(
                    "controller telemetry is required");
        }
        if (!Double.isFinite(frame.deltaSeconds())
                || frame.deltaSeconds() <= 0.0
                || !Double.isFinite(frame.vehicleSpeedKmph())
                || !Double.isFinite(frame.requestedWheelPowerKw())
                || !Double.isFinite(frame.criticalAuxiliaryPowerKw())
                || !Double.isFinite(frame.permittedComfortAuxiliaryPowerKw())
                || !Double.isFinite(frame.generatorControlReserveKw())
                || !Double.isFinite(
                        frame.reservedBatteryRecoveryPowerKw())
                || frame.vehicleSpeedKmph() < 0.0
                || frame.requestedWheelPowerKw() < 0.0
                || frame.criticalAuxiliaryPowerKw() < 0.0
                || frame.permittedComfortAuxiliaryPowerKw() < 0.0
                || frame.generatorControlReserveKw() < 0.0
                || frame.reservedBatteryRecoveryPowerKw() < 0.0) {
            throw new IllegalArgumentException(
                    "invalid instrumentation frame");
        }
    }
}
