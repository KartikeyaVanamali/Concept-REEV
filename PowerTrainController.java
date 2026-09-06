import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * XUV 7XO REEV powertrain controller — executable concept simulation.
 * Architecture, design decisions, and numerical rationale:
 * docs/concept/REEV-Design-Specification.md
 */

public class PowerTrainController {

    private static final Logger LOGGER =
            Logger.getLogger(PowerTrainController.class.getName());

    public enum VehicleVariant {
        RWD,
        AWD
    }

    public enum DriveMode {
        ECONOMY,
        SPORT,
        HILLS_TERRAIN
    }

    public enum OperatingState {
        STATE_1_PURE_EV,
        STATE_2_BATTERY_PROTECTION,
        STATE_3_CHARGE_SUSTAINING,
        STATE_4_GENERATOR_LIMITED,
        STATE_5_BATTERY_TURTLE,
        STATE_6_REFUEL_RECOVERY
    }

    public enum Severity {
        INFO,
        ADVISORY,
        WARNING,
        CRITICAL
    }

    public enum AuxiliaryState {
        FULL_SERVICE,
        COMFORT_LIMITED,
        CONVENIENCE_SHED,
        SAFETY_ESSENTIALS_ONLY
    }

    public record HmiEvent(
            String id,
            Severity severity,
            String message,
            boolean acknowledgementAllowed) {
    }

    /**
     * One deterministic controller frame. Power values are explicit:
     * driver/brake requests are at the wheels; auxiliaries are at HV DC.
     */
    public record InputFrame(
            double deltaSeconds,
            DriveMode requestedMode,
            double vehicleSpeedKmph,
            double requestedWheelPowerKw,
            double requestedBrakingWheelPowerKw,
            double pedalPosition,
            double wheelSlipFraction,
            double roadGradeFraction,
            double criticalAuxiliaryPowerKw,
            double comfortAuxiliaryPowerKw,
            double recentConsumptionKwhPerKm,
            boolean connectorInserted,
            boolean nozzleDisconnected,
            boolean vehicleInPark,
            boolean readyRequested,
            boolean v2lStartEnergyRestored,
            boolean fuelSystemHealthy,
            boolean engineGeneratorHealthy,
            boolean emissionsStartPermitted,
            double generatorThermalAvailability,
            double altitudeAvailability,
            BatteryManagementSystem.SensorFrame batterySensors) {
    }

    public record TelemetryFrame(
            VehicleVariant variant,
            OperatingState operatingState,
            DriveMode activeMode,
            double displayedSoc,
            double physicalSoc,
            double remainingBatteryEnergyKwh,
            double turtleEnergyTargetKwh,
            double usableFuelLitres,
            double deliveredWheelPowerKw,
            double rearWheelPowerKw,
            double frontWheelPowerKw,
            double batteryPowerKw,
            double generatorOutputKw,
            double generatorTargetBandKw,
            double generatorToTractionKw,
            double generatorToBatteryKw,
            double regenerativeChargePowerKw,
            double frictionBrakingWheelPowerKw,
            double dynamicMaximumSpeedKmph,
            double estimatedElectricRangeKm,
            double estimatedGeneratorRangeKm,
            double recoveryProgress,
            AuxiliaryState auxiliaryState,
            boolean tractionLimited,
            boolean turtleActive,
            List<HmiEvent> hmiEvents) {

        public TelemetryFrame {
            hmiEvents = List.copyOf(hmiEvents);
        }
    }

    private static final double ECONOMY_START_SOC = 0.15;
    private static final double ECONOMY_STOP_SOC = 0.30;
    private static final double SPORT_HILLS_START_SOC = 0.20;
    private static final double SPORT_HILLS_STOP_SOC = 0.35;
    private static final String SPORT_FALLBACK_EVENT =
            "SPORT_LOW_ENERGY_FALLBACK";
    private static final String SPORT_UNAVAILABLE_EVENT = "SPORT_UNAVAILABLE";
    private static final double MAXIMUM_FUEL_LITRES = 42.0;
    private static final double MINIMUM_USABLE_FUEL_LITRES = 0.20;
    private static final double CONTROL_RESERVE_KW = 3.0;
    private static final double TURTLE_BATTERY_POWER_CAP_KW = 20.0;
    private static final double REFUEL_RECOVERY_BATTERY_CAP_KW = 5.0;

    private final VehicleVariant variant;
    private final BatteryManagementSystem batteryManagementSystem;
    private final GeneratorManagementSystem generatorManagementSystem;
    private final TractionMotorManagement tractionMotorManagement;
    private final VehicleInstrumentationManagementSystem
            instrumentationManagementSystem;

    private DriveMode activeMode = DriveMode.ECONOMY;
    private OperatingState operatingState = OperatingState.STATE_1_PURE_EV;
    private GeneratorManagementSystem.Status lastGeneratorStatus =
            new GeneratorManagementSystem.Status(
                    false,
                    false,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    -1,
                    "generator not requested",
                    false);
    private boolean generatorRecoveryActive;
    private boolean validatedRefillPending;
    private boolean sportLockoutActive;
    private boolean sportRequestRejected;
    private boolean criticalAuxiliaryDeficitLatched;
    private double usableFuelLitres;
    private double filteredSustainedTractionDcKw;

    public PowerTrainController(
            VehicleVariant variant,
            double initialDisplayedSoc,
            double initialFuelLitres) {
        if (variant == null
                || !Double.isFinite(initialFuelLitres)
                || initialFuelLitres < 0.0
                || initialFuelLitres > MAXIMUM_FUEL_LITRES) {
            throw new IllegalArgumentException(
                    "invalid REEV controller configuration");
        }
        this.variant = variant;
        usableFuelLitres = initialFuelLitres;
        batteryManagementSystem = new BatteryManagementSystem(
                variant == VehicleVariant.RWD
                        ? BatteryManagementSystem.Configuration.rwd40Kwh()
                        : BatteryManagementSystem.Configuration.awd50Kwh(),
                initialDisplayedSoc);
        generatorManagementSystem = new GeneratorManagementSystem(
                GeneratorManagementSystem.Configuration.provisional());
        tractionMotorManagement = new TractionMotorManagement(
                variant == VehicleVariant.RWD
                        ? TractionMotorManagement.Configuration.rwd()
                        : TractionMotorManagement.Configuration.awd());
        instrumentationManagementSystem =
                new VehicleInstrumentationManagementSystem();
    }

    /**
     * Represents a refill already validated by tank level, fuel-door history,
     * slosh stabilization, pressure and leak diagnostics.
     */
    public synchronized void observeValidatedRefill(double addedLitres) {
        if (!Double.isFinite(addedLitres) || addedLitres <= 0.0) {
            throw new IllegalArgumentException(
                    "validated refill must be positive");
        }
        usableFuelLitres = Math.min(
                MAXIMUM_FUEL_LITRES,
                usableFuelLitres + addedLitres);
        validatedRefillPending = true;
    }

    public synchronized TelemetryFrame executeControlLoop(InputFrame frame) {
        String frameFailure = validateFrame(frame);
        if (frameFailure != null) {
            return publishFailSafeTelemetry(frame, frameFailure);
        }

        List<HmiEvent> hmiEvents = new ArrayList<>();
        boolean fuelAvailable = usableFuelLitres
                > MINIMUM_USABLE_FUEL_LITRES
                && frame.fuelSystemHealthy();
        BatteryManagementSystem.Status batteryStatus =
                batteryManagementSystem.evaluate(
                        frame.batterySensors(),
                        new BatteryManagementSystem.EnergyRequest(
                                frame.recentConsumptionKwhPerKm(),
                                frame.criticalAuxiliaryPowerKw(),
                                lastGeneratorStatus.guaranteedOutputKw() > 0.0,
                                fuelAvailable));

        applyRequestedMode(frame.requestedMode(), batteryStatus, hmiEvents);
        applySportLowEnergyFallback(batteryStatus, hmiEvents);
        updateGeneratorRecoveryCycle(batteryStatus, fuelAvailable);

        OperatingState previousState = operatingState;
        operatingState = determineState(
                frame,
                batteryStatus,
                fuelAvailable);
        if (operatingState != previousState) {
            hmiEvents.add(new HmiEvent(
                    "STATE_CHANGE",
                    Severity.INFO,
                    previousState + " -> " + operatingState,
                    true));
            if (previousState == OperatingState.STATE_6_REFUEL_RECOVERY
                    && operatingState
                            == OperatingState.STATE_3_CHARGE_SUSTAINING) {
                hmiEvents.add(new HmiEvent(
                        "TURTLE_RECOVERY_COMPLETE",
                        Severity.INFO,
                        "Turtle recovery complete - Economy mode active.",
                        true));
            }
        }

        AuxiliaryState auxiliaryState =
                auxiliaryStateFor(operatingState);
        double permittedComfortAuxiliaryKw = switch (auxiliaryState) {
            case FULL_SERVICE -> frame.comfortAuxiliaryPowerKw();
            case COMFORT_LIMITED ->
                    Math.min(2.0, frame.comfortAuxiliaryPowerKw() * 0.50);
            case CONVENIENCE_SHED, SAFETY_ESSENTIALS_ONLY -> 0.0;
        };
        if (auxiliaryState != AuxiliaryState.FULL_SERVICE) {
            hmiEvents.add(new HmiEvent(
                    "AUXILIARY_LIMIT",
                    Severity.ADVISORY,
                    "Comfort loads limited to preserve safe mobility",
                    true));
        }

        updateFilteredTractionDemand(frame);
        double recoveryPowerKw = requestedRecoveryPower(
                operatingState,
                frame,
                batteryStatus);
        boolean descentRegenPriority =
                activeMode == DriveMode.HILLS_TERRAIN
                        && frame.roadGradeFraction() < -0.03
                        && frame.requestedBrakingWheelPowerKw() > 0.0;
        boolean generatorStartRequested =
                stateRequiresGenerator(operatingState)
                        && !descentRegenPriority;
        boolean generatorStopPermitted =
                !generatorRecoveryActive
                        && (operatingState
                                == OperatingState
                                        .STATE_3_CHARGE_SUSTAINING
                            || operatingState
                                == OperatingState.STATE_1_PURE_EV
                            || operatingState
                                == OperatingState
                                        .STATE_2_BATTERY_PROTECTION)
                        || descentRegenPriority;

        lastGeneratorStatus = generatorManagementSystem.update(
                new GeneratorManagementSystem.Request(
                        generatorProfile(generatorTimingMode()),
                        frame.deltaSeconds(),
                        generatorStartRequested
                                ? filteredSustainedTractionDcKw
                                : 0.0,
                        generatorStartRequested
                                ? frame.criticalAuxiliaryPowerKw()
                                        + permittedComfortAuxiliaryKw
                                : 0.0,
                        recoveryPowerKw,
                        CONTROL_RESERVE_KW,
                        generatorElectricalOutputCeiling(
                                frame,
                                batteryStatus,
                                permittedComfortAuxiliaryKw),
                        usableFuelLitres,
                        generatorStartRequested,
                        generatorStopPermitted,
                        operatingState
                                == OperatingState.STATE_6_REFUEL_RECOVERY,
                        frame.requestedBrakingWheelPowerKw() > 0.0,
                        frame.connectorInserted(),
                        frame.fuelSystemHealthy(),
                        frame.engineGeneratorHealthy(),
                        frame.emissionsStartPermitted(),
                        frame.generatorThermalAvailability(),
                        frame.altitudeAvailability()));

        consumeFuel(lastGeneratorStatus, frame.deltaSeconds());
        addGeneratorHmi(lastGeneratorStatus, hmiEvents);

        double dynamicMaximumSpeed = dynamicMaximumSpeed(
                operatingState,
                lastGeneratorStatus.guaranteedOutputKw(),
                frame.criticalAuxiliaryPowerKw()
                        + permittedComfortAuxiliaryKw);
        double batteryDischargeLimit = switch (operatingState) {
            case STATE_4_GENERATOR_LIMITED -> 0.0;
            case STATE_5_BATTERY_TURTLE -> Math.min(
                    TURTLE_BATTERY_POWER_CAP_KW,
                    batteryStatus.availableDischargePowerKw());
            case STATE_6_REFUEL_RECOVERY -> Math.min(
                    REFUEL_RECOVERY_BATTERY_CAP_KW,
                    batteryStatus.availableDischargePowerKw());
            default -> batteryStatus.availableDischargePowerKw();
        };

        TractionMotorManagement.Status tractionStatus =
                tractionMotorManagement.allocate(
                        new TractionMotorManagement.Request(
                                frame.deltaSeconds(),
                                frame.vehicleSpeedKmph(),
                                frame.requestedWheelPowerKw(),
                                frame.requestedBrakingWheelPowerKw(),
                                batteryDischargeLimit,
                                permittedBatteryChargePower(
                                        frame,
                                        batteryStatus),
                                lastGeneratorStatus.guaranteedOutputKw(),
                                frame.criticalAuxiliaryPowerKw(),
                                permittedComfortAuxiliaryKw,
                                recoveryPowerKw,
                                CONTROL_RESERVE_KW,
                                dynamicMaximumSpeed,
                                frontAxleAssistFraction(frame),
                                stabilityPowerFraction(frame),
                                operatingState
                                        == OperatingState
                                                .STATE_6_REFUEL_RECOVERY,
                                batteryStatus.faultControlledStopRequired()
                                        || criticalAuxiliaryDeficitLatched));
        batteryManagementSystem.applyPowerFlow(
                tractionStatus.batteryPowerKw(),
                frame.deltaSeconds());
        criticalAuxiliaryDeficitLatched =
                tractionStatus.unservedAuxiliaryPowerKw() > 0.01;

        BatteryManagementSystem.Status finalBatteryStatus =
                batteryManagementSystem.evaluate(
                        frame.batterySensors(),
                        new BatteryManagementSystem.EnergyRequest(
                                frame.recentConsumptionKwhPerKm(),
                                frame.criticalAuxiliaryPowerKw(),
                                lastGeneratorStatus.guaranteedOutputKw() > 0.0,
                                usableFuelLitres
                                        > MINIMUM_USABLE_FUEL_LITRES));

        addBatteryAndStateHmi(
                finalBatteryStatus,
                tractionStatus,
                hmiEvents);
        if (operatingState == OperatingState.STATE_6_REFUEL_RECOVERY
                && lastGeneratorStatus.engineRunning()) {
            validatedRefillPending = false;
        }

        double electricRangeKm =
                finalBatteryStatus.remainingUsableEnergyKwh()
                        / frame.recentConsumptionKwhPerKm();
        double generatorRangeKm =
                usableFuelLitres * 2.9
                        / frame.recentConsumptionKwhPerKm();

        TelemetryFrame telemetry = new TelemetryFrame(
                variant,
                operatingState,
                activeMode,
                finalBatteryStatus.displayedSoc(),
                finalBatteryStatus.physicalSoc(),
                finalBatteryStatus.remainingUsableEnergyKwh(),
                finalBatteryStatus.turtleEnergyTargetKwh(),
                usableFuelLitres,
                tractionStatus.deliveredWheelPowerKw(),
                tractionStatus.rearWheelPowerKw(),
                tractionStatus.frontWheelPowerKw(),
                tractionStatus.batteryPowerKw(),
                lastGeneratorStatus.currentOutputKw(),
                lastGeneratorStatus.targetBandKw(),
                tractionStatus.generatorToTractionKw(),
                tractionStatus.generatorToBatteryKw(),
                tractionStatus.regenerativeChargePowerKw(),
                tractionStatus.frictionBrakingWheelPowerKw(),
                dynamicMaximumSpeed,
                electricRangeKm,
                generatorRangeKm,
                finalBatteryStatus.recoveryProgress(),
                auxiliaryState,
                tractionStatus.tractionLimited(),
                operatingState
                        == OperatingState.STATE_5_BATTERY_TURTLE
                        || operatingState
                                == OperatingState.STATE_6_REFUEL_RECOVERY,
                List.copyOf(hmiEvents));
        instrumentationManagementSystem.capture(
                new VehicleInstrumentationManagementSystem
                        .InstrumentationFrame(
                        frame.deltaSeconds(),
                        frame.vehicleSpeedKmph(),
                        frame.requestedWheelPowerKw(),
                        frame.criticalAuxiliaryPowerKw(),
                        permittedComfortAuxiliaryKw,
                        CONTROL_RESERVE_KW,
                        recoveryPowerKw,
                        telemetry,
                        finalBatteryStatus,
                        lastGeneratorStatus,
                        tractionStatus));
        return telemetry;
    }

    public synchronized VehicleInstrumentationManagementSystem
            .DashboardSnapshot latestDashboardSnapshot() {
        return instrumentationManagementSystem.latestSnapshot();
    }

    public synchronized List<VehicleInstrumentationManagementSystem
            .DashboardSnapshot> instrumentationHistory() {
        return instrumentationManagementSystem.history();
    }

    private OperatingState determineState(
            InputFrame frame,
            BatteryManagementSystem.Status batteryStatus,
            boolean fuelAvailable) {
        boolean recoveryStartInterlocks =
                recoveryStartPermitted(frame, batteryStatus, fuelAvailable);

        if (batteryStatus.faultControlledStopRequired()
                || criticalAuxiliaryDeficitLatched) {
            return OperatingState.STATE_2_BATTERY_PROTECTION;
        }
        if (operatingState == OperatingState.STATE_5_BATTERY_TURTLE
                && recoveryStartInterlocks) {
            activeMode = DriveMode.ECONOMY;
            generatorRecoveryActive = true;
            return OperatingState.STATE_6_REFUEL_RECOVERY;
        }
        if (operatingState == OperatingState.STATE_6_REFUEL_RECOVERY) {
            if (batteryStatus.remainingUsableEnergyKwh()
                    >= batteryStatus.recoveryExitEnergyKwh()) {
                activeMode = DriveMode.ECONOMY;
                return OperatingState.STATE_3_CHARGE_SUSTAINING;
            }
            return OperatingState.STATE_6_REFUEL_RECOVERY;
        }
        if (batteryStatus.turtleEntryRequired()) {
            return OperatingState.STATE_5_BATTERY_TURTLE;
        }
        if (!generatorRecoveryActive
                && lastGeneratorStatus.engineRunning()) {
            return OperatingState.STATE_3_CHARGE_SUSTAINING;
        }
        if (generatorRecoveryActive) {
            if (sustainedDeficitThreatensReserve(
                    frame,
                    batteryStatus,
                    fuelAvailable)) {
                return OperatingState.STATE_4_GENERATOR_LIMITED;
            }
            return OperatingState.STATE_3_CHARGE_SUSTAINING;
        }

        double requestedBatteryPower =
                frame.requestedWheelPowerKw() / 0.92
                        + frame.criticalAuxiliaryPowerKw()
                        + frame.comfortAuxiliaryPowerKw();
        return requestedBatteryPower
                        > batteryStatus.availableDischargePowerKw()
                || !batteryStatus.evidenceValid()
                ? OperatingState.STATE_2_BATTERY_PROTECTION
                : OperatingState.STATE_1_PURE_EV;
    }

    private void updateGeneratorRecoveryCycle(
            BatteryManagementSystem.Status batteryStatus,
            boolean fuelAvailable) {
        double startSoc = activeMode == DriveMode.ECONOMY
                ? ECONOMY_START_SOC
                : SPORT_HILLS_START_SOC;
        double stopSoc = activeMode == DriveMode.ECONOMY
                ? ECONOMY_STOP_SOC
                : SPORT_HILLS_STOP_SOC;
        double startEnergyKwh =
                batteryStatus.currentUsableCapacityKwh() * startSoc;
        double stopEnergyKwh =
                batteryStatus.currentUsableCapacityKwh() * stopSoc;
        if (batteryStatus.remainingUsableEnergyKwh() <= startEnergyKwh
                && fuelAvailable) {
            generatorRecoveryActive = true;
        } else if (batteryStatus.remainingUsableEnergyKwh()
                        >= Math.max(
                                stopEnergyKwh,
                                batteryStatus.recoveryExitEnergyKwh())
                && batteryStatus.evidenceValid()
                && batteryStatus.availableDischargePowerKw() > 0.0
                && batteryStatus.availableChargePowerKw() > 0.0
                && operatingState
                        != OperatingState.STATE_6_REFUEL_RECOVERY) {
            generatorRecoveryActive = false;
        }
    }

    private static boolean stateRequiresGenerator(OperatingState state) {
        return state == OperatingState.STATE_3_CHARGE_SUSTAINING
                || state == OperatingState.STATE_4_GENERATOR_LIMITED
                || state == OperatingState.STATE_6_REFUEL_RECOVERY;
    }

    private boolean recoveryStartPermitted(
            InputFrame frame,
            BatteryManagementSystem.Status batteryStatus,
            boolean fuelAvailable) {
        boolean startEnergyAvailable =
                batteryStatus.remainingUsableEnergyKwh() > 0.0
                        || frame.v2lStartEnergyRestored()
                        || (frame.batterySensors().pulseBudgetAvailable()
                            && frame.batterySensors().prechargeAvailable()
                            && frame.batterySensors().contactorsAvailable());
        return validatedRefillPending
                && fuelAvailable
                && frame.nozzleDisconnected()
                && frame.vehicleInPark()
                && frame.readyRequested()
                && !frame.connectorInserted()
                && startEnergyAvailable
                && batteryStatus.evidenceValid();
    }

    private void applyRequestedMode(
            DriveMode requestedMode,
            BatteryManagementSystem.Status batteryStatus,
            List<HmiEvent> hmiEvents) {
        if (requestedMode != DriveMode.SPORT) {
            sportRequestRejected = false;
        }
        if (requestedMode == activeMode) {
            return;
        }
        if (operatingState == OperatingState.STATE_5_BATTERY_TURTLE
                || operatingState
                        == OperatingState.STATE_6_REFUEL_RECOVERY) {
            hmiEvents.add(new HmiEvent(
                    "MODE_DEFERRED",
                    Severity.ADVISORY,
                    "Drive-mode request deferred until Turtle recovery",
                    true));
            return;
        }
        if (requestedMode == DriveMode.SPORT
                && (sportLockoutActive
                        || batteryStatus.displayedSoc()
                            < SPORT_HILLS_START_SOC
                        || !batteryStatus.evidenceValid()
                        || batteryStatus.availableDischargePowerKw() <= 0.0
                        || batteryStatus.faultControlledStopRequired())) {
            // The selector physically stays in Sport, so this branch is
            // reached on every frame. Advise once per rejection, not per frame.
            if (!sportRequestRejected) {
                hmiEvents.add(new HmiEvent(
                        SPORT_UNAVAILABLE_EVENT,
                        Severity.ADVISORY,
                        "Sport unavailable - minimum 20% battery reserve "
                                + "required.",
                        true));
            }
            sportRequestRejected = true;
            return;
        }
        sportRequestRejected = false;
        activeMode = requestedMode;
    }

    /**
     * Sport-only low-energy handover. The boundary is the BMS early
     * intervention flag rather than a fixed percentage: it sits at a nominal
     * 10% displayed SoC, rises on its own when a cold, aged or imbalanced pack
     * pushes the dynamic Turtle target up, and so can never fall below the
     * Turtle gate the way a constant could. It also fires on lost battery
     * evidence at any SoC, matching {@link #applyRequestedMode}, which already
     * refuses a new Sport request without trusted evidence.
     *
     * <p>Hills/Terrain is deliberately absent. Its power management is already
     * Economy-like, so forcing Economy would change nothing about energy use
     * while disabling descent regeneration priority and proactive front-axle
     * preparation at the point they matter most. Terrain therefore keeps its
     * full 20% to Turtle-boundary window.</p>
     */
    private void applySportLowEnergyFallback(
            BatteryManagementSystem.Status batteryStatus,
            List<HmiEvent> hmiEvents) {
        if (batteryStatus.earlyInterventionRequired()) {
            if (activeMode == DriveMode.SPORT) {
                activeMode = DriveMode.ECONOMY;
                hmiEvents.add(new HmiEvent(
                        SPORT_FALLBACK_EVENT,
                        Severity.WARNING,
                        "Low battery reserve: Sport power response replaced by "
                                + "Economy. Sport returns after 20% recovery.",
                        true));
                // The warning already explains the demotion, so suppress the
                // rejection advisory the still-selected Sport position would
                // otherwise raise on the next frame.
                sportRequestRejected = true;
            }
            sportLockoutActive = true;
            return;
        }

        if (sportLockoutActive
                && batteryStatus.displayedSoc() >= SPORT_HILLS_START_SOC
                && batteryStatus.evidenceValid()
                && batteryStatus.availableDischargePowerKw() > 0.0) {
            sportLockoutActive = false;
            hmiEvents.add(new HmiEvent(
                    "SPORT_LOCKOUT_RELEASED",
                    Severity.INFO,
                    "Battery reserve restored: Sport may be selected again.",
                    true));
        }
    }

    private double generatorElectricalOutputCeiling(
            InputFrame frame,
            BatteryManagementSystem.Status batteryStatus,
            double permittedComfortAuxiliaryKw) {
        if (frame.requestedBrakingWheelPowerKw() <= 0.0) {
            return GeneratorManagementSystem.maximumConceptOutputKw();
        }

        double regenerativeCapKw = variant == VehicleVariant.RWD
                ? 30.0
                : 40.0;
        double anticipatedRegenerationKw = Math.min(
                regenerativeCapKw,
                frame.requestedBrakingWheelPowerKw() * 0.85);
        double batteryChargeAvailableAfterRegenKw = Math.max(
                0.0,
                batteryStatus.availableChargePowerKw()
                        - anticipatedRegenerationKw);
        return Math.min(
                GeneratorManagementSystem.maximumConceptOutputKw(),
                frame.criticalAuxiliaryPowerKw()
                        + permittedComfortAuxiliaryKw
                        + batteryChargeAvailableAfterRegenKw);
    }

    private double permittedBatteryChargePower(
            InputFrame frame,
            BatteryManagementSystem.Status batteryStatus) {
        if (frame.requestedBrakingWheelPowerKw() <= 0.0) {
            return batteryStatus.availableChargePowerKw();
        }
        return batteryManagementSystem.canAcceptRegeneration(
                batteryStatus,
                frame.requestedBrakingWheelPowerKw() * 0.85)
                        ? batteryStatus.availableChargePowerKw()
                        : 0.0;
    }

    private boolean sustainedDeficitThreatensReserve(
            InputFrame frame,
            BatteryManagementSystem.Status batteryStatus,
            boolean fuelAvailable) {
        if (!fuelAvailable || !lastGeneratorStatus.engineRunning()) {
            return false;
        }
        double sustainableDcKw = Math.max(
                0.0,
                lastGeneratorStatus.guaranteedOutputKw()
                        - frame.criticalAuxiliaryPowerKw()
                        - CONTROL_RESERVE_KW);
        double requestedDcKw = frame.requestedWheelPowerKw() / 0.92;
        double protectedEnergyKwh = batteryStatus.turtleEnergyTargetKwh()
                + 0.25;
        return requestedDcKw > sustainableDcKw + 0.5
                && (batteryStatus.earlyInterventionRequired()
                        || batteryStatus.remainingUsableEnergyKwh()
                                <= protectedEnergyKwh);
    }

    private void updateFilteredTractionDemand(InputFrame frame) {
        double requestedDcKw = frame.requestedWheelPowerKw() / 0.92;
        double filterSeconds = switch (generatorTimingMode()) {
            case SPORT -> 0.35;
            case ECONOMY -> 1.50;
            case HILLS_TERRAIN -> 2.50;
        };
        double alpha = clamp(frame.deltaSeconds() / filterSeconds, 0.0, 1.0);
        filteredSustainedTractionDcKw +=
                alpha
                        * (requestedDcKw
                                - filteredSustainedTractionDcKw);
    }

    private double requestedRecoveryPower(
            OperatingState state,
            InputFrame frame,
            BatteryManagementSystem.Status batteryStatus) {
        if (state == OperatingState.STATE_6_REFUEL_RECOVERY) {
            double minimumStableRecoveryKw = Math.max(
                    0.0,
                    frame.criticalAuxiliaryPowerKw()
                            + CONTROL_RESERVE_KW
                            - lastGeneratorStatus.guaranteedOutputKw());
            return Math.min(
                    12.0,
                    Math.max(
                            2.0,
                            Math.min(
                                    batteryStatus.availableChargePowerKw(),
                                    minimumStableRecoveryKw + 6.0)));
        }
        if (state != OperatingState.STATE_3_CHARGE_SUSTAINING
                || !generatorRecoveryActive) {
            return 0.0;
        }
        return switch (activeMode) {
            case SPORT -> 12.0;
            case HILLS_TERRAIN -> 9.0;
            case ECONOMY -> 6.0;
        };
    }

    /**
     * Drive mode used for generator band timing, which is not always the mode
     * the driver selected. In generator-limited operation the generator is the
     * only sustained source, so Sport's fast demand filter and two-band steps
     * would make it hunt exactly where band stability matters most. Terrain
     * keeps its own timing there because it is calmer than Economy rather than
     * sharper. Reaching this clamp is rare: the Sport low-energy handover has
     * normally already run, and only the {@code turtleEnergyTargetKwh + 0.25}
     * trigger in {@link #sustainedDeficitThreatensReserve} can arrive first.
     */
    private DriveMode generatorTimingMode() {
        if (operatingState == OperatingState.STATE_4_GENERATOR_LIMITED
                && activeMode == DriveMode.SPORT) {
            return DriveMode.ECONOMY;
        }
        return activeMode;
    }

    private static GeneratorManagementSystem.ResponseProfile generatorProfile(
            DriveMode driveMode) {
        return switch (driveMode) {
            case ECONOMY ->
                    GeneratorManagementSystem.ResponseProfile.ECONOMY;
            case SPORT ->
                    GeneratorManagementSystem.ResponseProfile.SPORT;
            case HILLS_TERRAIN ->
                    GeneratorManagementSystem.ResponseProfile.HILLS_TERRAIN;
        };
    }

    private AuxiliaryState auxiliaryStateFor(OperatingState state) {
        return switch (state) {
            case STATE_4_GENERATOR_LIMITED ->
                    AuxiliaryState.COMFORT_LIMITED;
            case STATE_5_BATTERY_TURTLE ->
                    AuxiliaryState.SAFETY_ESSENTIALS_ONLY;
            case STATE_6_REFUEL_RECOVERY ->
                    AuxiliaryState.CONVENIENCE_SHED;
            default -> AuxiliaryState.FULL_SERVICE;
        };
    }

    private double frontAxleAssistFraction(InputFrame frame) {
        if (variant == VehicleVariant.RWD) {
            return 0.0;
        }
        if (activeMode == DriveMode.HILLS_TERRAIN
                || frame.wheelSlipFraction() > 0.03
                || frame.requestedWheelPowerKw() > 120.0
                || frame.roadGradeFraction() > 0.05) {
            return 70.0 / 240.0;
        }
        return 0.0;
    }

    private static double stabilityPowerFraction(InputFrame frame) {
        return clamp(
                1.0 - frame.wheelSlipFraction() * 3.0,
                0.40,
                1.0);
    }

    private double dynamicMaximumSpeed(
            OperatingState state,
            double guaranteedGeneratorPowerKw,
            double auxiliaryPowerKw) {
        if (state == OperatingState.STATE_5_BATTERY_TURTLE
                || state == OperatingState.STATE_6_REFUEL_RECOVERY) {
            return 60.0;
        }
        if (state != OperatingState.STATE_4_GENERATOR_LIMITED) {
            return 210.0;
        }
        double dcForTraction = Math.max(
                0.0,
                guaranteedGeneratorPowerKw
                        - auxiliaryPowerKw
                        - CONTROL_RESERVE_KW);
        double wheelPowerKw = dcForTraction * 0.92;
        return solveLevelRoadSpeedKmph(wheelPowerKw);
    }

    private double solveLevelRoadSpeedKmph(double availableWheelPowerKw) {
        double massKg = variant == VehicleVariant.RWD ? 2820.0 : 3020.0;
        double lowKmph = 0.0;
        double highKmph = 210.0;
        for (int iteration = 0; iteration < 50; iteration++) {
            double midKmph = (lowKmph + highKmph) / 2.0;
            double speedMps = midKmph / 3.6;
            double rollingForceN = massKg * 9.81 * 0.0095;
            double aeroForceN =
                    0.5 * 1.18 * 0.89 * speedMps * speedMps;
            double requiredPowerKw =
                    (rollingForceN + aeroForceN) * speedMps / 1000.0;
            if (requiredPowerKw <= availableWheelPowerKw) {
                lowKmph = midKmph;
            } else {
                highKmph = midKmph;
            }
        }
        return lowKmph;
    }

    private void consumeFuel(
            GeneratorManagementSystem.Status generatorStatus,
            double deltaSeconds) {
        double consumedLitres =
                generatorStatus.estimatedFuelFlowLitresPerHour()
                        * deltaSeconds / 3600.0;
        usableFuelLitres = Math.max(
                0.0,
                usableFuelLitres - consumedLitres);
    }

    private static void addGeneratorHmi(
            GeneratorManagementSystem.Status status,
            List<HmiEvent> hmiEvents) {
        if (status.engineRunning()) {
            hmiEvents.add(new HmiEvent(
                    "GENERATOR_ACTIVE",
                    Severity.INFO,
                    "Generator active at target "
                            + status.targetBandKw() + " kW band",
                    true));
        }
        if (status.predictedFuelRuntimeMinutes() <= 5.0
                && status.engineRunning()) {
            hmiEvents.add(new HmiEvent(
                    "GENERATOR_RUNTIME_CRITICAL",
                    Severity.CRITICAL,
                    "Usable generator runtime is approximately five minutes",
                    false));
        } else if (status.predictedFuelRuntimeMinutes() <= 10.0
                && status.engineRunning()) {
            hmiEvents.add(new HmiEvent(
                    "GENERATOR_RUNTIME_WARNING",
                    Severity.WARNING,
                    "Usable generator runtime is approximately ten minutes",
                    true));
        }
    }

    private void addBatteryAndStateHmi(
            BatteryManagementSystem.Status batteryStatus,
            TractionMotorManagement.Status tractionStatus,
            List<HmiEvent> hmiEvents) {
        if (!batteryStatus.evidenceValid()) {
            hmiEvents.add(new HmiEvent(
                    "BATTERY_EVIDENCE_INVALID",
                    Severity.CRITICAL,
                    "Battery data invalid - controlled power fallback active",
                    false));
        } else if (batteryStatus.faultControlledStopRequired()) {
            hmiEvents.add(new HmiEvent(
                    "BATTERY_HV_FAULT_CONTROLLED_STOP",
                    Severity.CRITICAL,
                    "Battery HV protection fault - controlled stop active",
                    false));
        } else if (batteryStatus.earlyInterventionRequired()
                && operatingState
                        != OperatingState.STATE_5_BATTERY_TURTLE) {
            hmiEvents.add(new HmiEvent(
                    "BOOST_RESERVE_LOW",
                    Severity.WARNING,
                    "High-power battery support nearing its limit. "
                            + "Vehicle performance is adapting.",
                    true));
        }
        if (operatingState == OperatingState.STATE_5_BATTERY_TURTLE) {
            hmiEvents.add(new HmiEvent(
                    "TURTLE_ACTIVE",
                    Severity.WARNING,
                    "Battery Turtle active - dynamic emergency mobility",
                    true));
        } else if (operatingState
                == OperatingState.STATE_6_REFUEL_RECOVERY) {
            hmiEvents.add(new HmiEvent(
                    "REFUEL_RECOVERY",
                    Severity.WARNING,
                    "Fuel detected - generator running for Turtle recovery",
                    true));
            if (!lastGeneratorStatus.engineRunning()) {
                hmiEvents.add(new HmiEvent(
                        "FUEL_INSUFFICIENT_RECOVERY",
                        Severity.WARNING,
                        "Fuel or generator output is insufficient to restore "
                                + "normal traction.",
                        true));
            }
        }
        if (tractionStatus.unservedAuxiliaryPowerKw() > 0.01) {
            hmiEvents.add(new HmiEvent(
                    "SAFE_STOP_REQUIRED",
                    Severity.CRITICAL,
                    "Source power unavailable for required auxiliaries",
                    false));
        }
    }

    private TelemetryFrame publishFailSafeTelemetry(
            InputFrame frame,
            String reason) {
        lastGeneratorStatus = generatorManagementSystem.emergencyStop(
                "invalid controller frame");
        TelemetryFrame telemetry = failSafeTelemetry(reason);
        double deltaSeconds = frame != null
                        && Double.isFinite(frame.deltaSeconds())
                        && frame.deltaSeconds() > 0.0
                ? frame.deltaSeconds()
                : 0.01;
        instrumentationManagementSystem.capture(
                new VehicleInstrumentationManagementSystem
                        .InstrumentationFrame(
                        deltaSeconds,
                        safeNonNegative(
                                frame == null
                                        ? 0.0
                                        : frame.vehicleSpeedKmph()),
                        safeNonNegative(
                                frame == null
                                        ? 0.0
                                        : frame.requestedWheelPowerKw()),
                        safeNonNegative(
                                frame == null
                                        ? 0.0
                                        : frame.criticalAuxiliaryPowerKw()),
                        0.0,
                        CONTROL_RESERVE_KW,
                        0.0,
                        telemetry,
                        null,
                        lastGeneratorStatus,
                        null));
        return telemetry;
    }

    private TelemetryFrame failSafeTelemetry(String reason) {
        HmiEvent event = new HmiEvent(
                "INVALID_CONTROL_FRAME",
                Severity.CRITICAL,
                reason + " - positive traction request rejected",
                false);
        return new TelemetryFrame(
                variant,
                OperatingState.STATE_2_BATTERY_PROTECTION,
                activeMode,
                0.0,
                0.0,
                0.0,
                0.0,
                usableFuelLitres,
                0.0,
                0.0,
                0.0,
                0.0,
                lastGeneratorStatus.currentOutputKw(),
                lastGeneratorStatus.targetBandKw(),
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                AuxiliaryState.SAFETY_ESSENTIALS_ONLY,
                true,
                false,
                List.of(event));
    }

    private static double safeNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0 ? value : 0.0;
    }

    private static String validateFrame(InputFrame frame) {
        if (frame == null
                || frame.requestedMode() == null
                || frame.batterySensors() == null) {
            return "missing control-frame evidence";
        }
        if (!finite(
                frame.deltaSeconds(),
                frame.vehicleSpeedKmph(),
                frame.requestedWheelPowerKw(),
                frame.requestedBrakingWheelPowerKw(),
                frame.pedalPosition(),
                frame.wheelSlipFraction(),
                frame.roadGradeFraction(),
                frame.criticalAuxiliaryPowerKw(),
                frame.comfortAuxiliaryPowerKw(),
                frame.recentConsumptionKwhPerKm(),
                frame.generatorThermalAvailability(),
                frame.altitudeAvailability())) {
            return "non-finite control-frame evidence";
        }
        if (frame.deltaSeconds() <= 0.0
                || frame.vehicleSpeedKmph() < 0.0
                || frame.requestedWheelPowerKw() < 0.0
                || frame.requestedBrakingWheelPowerKw() < 0.0
                || frame.pedalPosition() < 0.0
                || frame.pedalPosition() > 1.0
                || frame.wheelSlipFraction() < 0.0
                || frame.wheelSlipFraction() > 1.0
                || Math.abs(frame.roadGradeFraction()) > 0.50
                || frame.criticalAuxiliaryPowerKw() < 0.0
                || frame.comfortAuxiliaryPowerKw() < 0.0
                || frame.recentConsumptionKwhPerKm() <= 0.0
                || frame.generatorThermalAvailability() < 0.0
                || frame.generatorThermalAvailability() > 1.0
                || frame.altitudeAvailability() < 0.0
                || frame.altitudeAvailability() > 1.0) {
            return "implausible control-frame evidence";
        }
        return null;
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Demonstrates the concept laws. It is a readable scenario pitch, not HIL.
     */
    public static void main(String[] args) {
        runScenarioDemonstration();
    }

    private static void runScenarioDemonstration() {
        PowerTrainController rwd = new PowerTrainController(
                VehicleVariant.RWD,
                0.80,
                42.0);
        TelemetryFrame pureEv = rwd.executeControlLoop(
                nominalFrame(
                        DriveMode.ECONOMY,
                        120.0,
                        90.0,
                        BatteryManagementSystem.SensorFrame.nominalRwd()));
        VehicleInstrumentationManagementSystem.DashboardSnapshot
                pureEvDashboard = rwd.latestDashboardSnapshot();
        require(
                pureEv.operatingState() == OperatingState.STATE_1_PURE_EV,
                "healthy RWD must remain EV-first");
        require(
                pureEv.generatorOutputKw() == 0.0,
                "hard pedal must not start generator at healthy SoC");

        PowerTrainController awd = new PowerTrainController(
                VehicleVariant.AWD,
                0.14,
                20.0);
        TelemetryFrame chargeSustaining = null;
        for (int frame = 0; frame < 8; frame++) {
            chargeSustaining = awd.executeControlLoop(
                    nominalFrame(
                            DriveMode.SPORT,
                            150.0,
                            110.0,
                            BatteryManagementSystem.SensorFrame.nominalAwd()));
        }
        VehicleInstrumentationManagementSystem.DashboardSnapshot
                chargeDashboard = awd.latestDashboardSnapshot();
        require(
                chargeSustaining.generatorTargetBandKw() >= 40.0,
                "generator must progress through sufficient bands");
        require(
                chargeSustaining.frontWheelPowerKw() > 0.0,
                "AWD high demand must enlist front EDU");

        PowerTrainController turtle = new PowerTrainController(
                VehicleVariant.RWD,
                0.075,
                0.0);
        TelemetryFrame turtleFrame = turtle.executeControlLoop(
                nominalFrame(
                        DriveMode.ECONOMY,
                        20.0,
                        40.0,
                        BatteryManagementSystem.SensorFrame.nominalRwd()));
        VehicleInstrumentationManagementSystem.DashboardSnapshot
                turtleDashboard = turtle.latestDashboardSnapshot();
        require(
                turtleFrame.operatingState()
                        == OperatingState.STATE_5_BATTERY_TURTLE,
                "fuel-exhausted low energy must enter dynamic Turtle");
        require(
                turtleFrame.dynamicMaximumSpeedKmph() <= 60.0,
                "Turtle speed ceiling must be enforced");

        turtle.observeValidatedRefill(5.0);
        TelemetryFrame recovery = turtle.executeControlLoop(
                recoveryFrame(
                        BatteryManagementSystem.SensorFrame.nominalRwd()));
        VehicleInstrumentationManagementSystem.DashboardSnapshot
                recoveryDashboard = turtle.latestDashboardSnapshot();
        require(
                recovery.operatingState()
                        == OperatingState.STATE_6_REFUEL_RECOVERY,
                "validated refill must enter State 6");
        turtle.batteryManagementSystem.setDisplayedSocForSimulation(0.12);
        TelemetryFrame recoveryExit = turtle.executeControlLoop(
                recoveryFrame(
                        BatteryManagementSystem.SensorFrame.nominalRwd()));
        VehicleInstrumentationManagementSystem.DashboardSnapshot
                recoveryExitDashboard = turtle.latestDashboardSnapshot();
        require(
                recoveryExit.operatingState()
                        == OperatingState.STATE_3_CHARGE_SUSTAINING,
                "State 6 must exit through recovery hysteresis");

        PowerTrainController generatorLimited =
                new PowerTrainController(
                        VehicleVariant.AWD,
                        0.095,
                        20.0);
        TelemetryFrame generatorLimitedFrame = null;
        for (int frame = 0; frame < 3; frame++) {
            generatorLimitedFrame =
                    generatorLimited.executeControlLoop(
                            nominalFrame(
                                    DriveMode.ECONOMY,
                                    180.0,
                                    175.0,
                                    BatteryManagementSystem.SensorFrame
                                            .nominalAwd()));
        }
        VehicleInstrumentationManagementSystem.DashboardSnapshot
                generatorLimitedDashboard =
                        generatorLimited.latestDashboardSnapshot();
        require(
                generatorLimitedFrame.operatingState()
                        == OperatingState.STATE_4_GENERATOR_LIMITED,
                "low reserve with running generator must enter State 4");
        require(
                generatorLimitedFrame.dynamicMaximumSpeedKmph() < 210.0,
                "State 4 must derive a sustainable speed");

        PowerTrainController lowEnergyTerrain = new PowerTrainController(
                VehicleVariant.AWD,
                0.095,
                20.0);
        TelemetryFrame lowEnergyTerrainFrame =
                lowEnergyTerrain.executeControlLoop(
                        nominalFrame(
                                DriveMode.HILLS_TERRAIN,
                                30.0,
                                60.0,
                                BatteryManagementSystem.SensorFrame
                                        .nominalAwd()));
        require(
                lowEnergyTerrainFrame.activeMode() == DriveMode.HILLS_TERRAIN,
                "Terrain must keep its own character down to the Turtle gate");
        require(
                lowEnergyTerrainFrame.hmiEvents().stream().noneMatch(
                        event -> event.id().equals(SPORT_FALLBACK_EVENT)),
                "Terrain must not raise the Sport low-energy handover");

        PowerTrainController lowEnergySport = new PowerTrainController(
                VehicleVariant.AWD,
                0.30,
                20.0);
        TelemetryFrame sportAccepted = lowEnergySport.executeControlLoop(
                nominalFrame(
                        DriveMode.SPORT,
                        30.0,
                        60.0,
                        BatteryManagementSystem.SensorFrame.nominalAwd()));
        require(
                sportAccepted.activeMode() == DriveMode.SPORT,
                "Sport must be available with a healthy reserve");
        lowEnergySport.batteryManagementSystem
                .setDisplayedSocForSimulation(0.095);
        TelemetryFrame sportDemoted = lowEnergySport.executeControlLoop(
                nominalFrame(
                        DriveMode.SPORT,
                        30.0,
                        60.0,
                        BatteryManagementSystem.SensorFrame.nominalAwd()));
        require(
                sportDemoted.activeMode() == DriveMode.ECONOMY,
                "Sport must hand over to Economy at early intervention");
        require(
                sportDemoted.hmiEvents().stream().anyMatch(
                        event -> event.id().equals(SPORT_FALLBACK_EVENT)),
                "the Sport handover must be visible to the driver");
        long repeatedSportAdvisories = 0;
        for (int frame = 0; frame < 4; frame++) {
            TelemetryFrame held = lowEnergySport.executeControlLoop(
                    nominalFrame(
                            DriveMode.SPORT,
                            30.0,
                            60.0,
                            BatteryManagementSystem.SensorFrame.nominalAwd()));
            require(
                    held.activeMode() == DriveMode.ECONOMY,
                    "a held Sport selector must not restore Sport below 20%");
            repeatedSportAdvisories += held.hmiEvents().stream()
                    .filter(event -> event.id().equals(
                            SPORT_UNAVAILABLE_EVENT))
                    .count();
        }
        require(
                repeatedSportAdvisories == 0,
                "the Sport rejection advisory must not repeat every frame");

        PowerTrainController sportEvidence = new PowerTrainController(
                VehicleVariant.RWD,
                0.60,
                20.0);
        require(
                sportEvidence.executeControlLoop(
                        nominalFrame(
                                DriveMode.SPORT,
                                30.0,
                                60.0,
                                BatteryManagementSystem.SensorFrame
                                        .nominalRwd()))
                        .activeMode() == DriveMode.SPORT,
                "Sport must engage at healthy SoC with trusted evidence");
        require(
                sportEvidence.executeControlLoop(
                        nominalFrame(
                                DriveMode.SPORT,
                                30.0,
                                60.0,
                                staleSensors()))
                        .activeMode() == DriveMode.ECONOMY,
                "lost battery evidence must drop Sport even at healthy SoC");

        requireTerrainHoldsBandThroughCrest();

        PowerTrainController modeGate = new PowerTrainController(
                VehicleVariant.RWD,
                0.18,
                10.0);
        TelemetryFrame rejectedSport = modeGate.executeControlLoop(
                nominalFrame(
                        DriveMode.SPORT,
                        20.0,
                        50.0,
                        BatteryManagementSystem.SensorFrame.nominalRwd()));
        require(
                rejectedSport.activeMode() == DriveMode.ECONOMY,
                "Sport must remain blocked below safe 20% reserve");

        PowerTrainController staleData = new PowerTrainController(
                VehicleVariant.RWD,
                0.60,
                10.0);
        BatteryManagementSystem.SensorFrame nominalRwd =
                BatteryManagementSystem.SensorFrame.nominalRwd();
        BatteryManagementSystem.SensorFrame staleBattery =
                new BatteryManagementSystem.SensorFrame(
                        500L,
                        nominalRwd.confidence(),
                        nominalRwd.stateOfHealth(),
                        nominalRwd.availableDischargePowerKw(),
                        nominalRwd.availableChargePowerKw(),
                        nominalRwd.weakestCellVoltage(),
                        nominalRwd.predictedWeakestCellLoadedVoltage(),
                        nominalRwd.minimumCellTemperatureC(),
                        nominalRwd.maximumCellTemperatureC(),
                        nominalRwd.isolationHealthy(),
                        nominalRwd.hvilHealthy(),
                        nominalRwd.contactorsAvailable(),
                        nominalRwd.prechargeAvailable(),
                        nominalRwd.pulseBudgetAvailable());
        TelemetryFrame staleFallback = staleData.executeControlLoop(
                nominalFrame(
                        DriveMode.ECONOMY,
                        80.0,
                        80.0,
                        staleBattery));
        VehicleInstrumentationManagementSystem.DashboardSnapshot
                staleDashboard = staleData.latestDashboardSnapshot();
        TelemetryFrame invalidFallback = staleData.executeControlLoop(
                invalidFrame(
                        BatteryManagementSystem.SensorFrame.nominalRwd()));
        VehicleInstrumentationManagementSystem.DashboardSnapshot
                invalidDashboard = staleData.latestDashboardSnapshot();
        require(
                staleFallback.operatingState()
                        == OperatingState.STATE_2_BATTERY_PROTECTION
                        && staleFallback.deliveredWheelPowerKw() == 0.0,
                "stale BMS evidence must fail down");
        require(
                pureEvDashboard.dataQuality()
                        == VehicleInstrumentationManagementSystem
                                .DataQuality.VALID,
                "healthy subsystem evidence must produce valid gauges");
        require(
                residualsWithinTolerance(pureEvDashboard),
                "instrumentation power planes must balance");
        require(
                chargeDashboard.drivetrain()
                                .frontAxleContributionPercent() > 0.0,
                "instrumentation must expose AWD assistance");
        require(
                turtleDashboard.mobility().turtleActive()
                        && turtleDashboard.energy()
                                .turtleEnergyTargetKwh() > 0.0,
                "instrumentation must expose Turtle energy and mobility");
        require(
                recoveryDashboard.energy().recoveryProgressPercent() > 0.0
                        && recoveryExitDashboard.operatingState()
                                == OperatingState
                                        .STATE_3_CHARGE_SUSTAINING,
                "instrumentation must expose State 6 recovery progress");
        require(
                generatorLimitedDashboard.mobility()
                                .dynamicMaximumSpeedKmph() < 210.0,
                "instrumentation must expose sustainable speed");
        require(
                staleDashboard.dataQuality()
                        == VehicleInstrumentationManagementSystem
                                .DataQuality.DEGRADED,
                "stale BMS evidence must mark gauges degraded");
        require(
                invalidFallback.operatingState()
                        == OperatingState.STATE_2_BATTERY_PROTECTION
                        && invalidDashboard.dataQuality()
                                == VehicleInstrumentationManagementSystem
                                        .DataQuality.UNAVAILABLE
                        && Double.isNaN(
                                invalidDashboard.powerFlow()
                                        .tractionDcPowerKw()),
                "invalid control evidence must expose unavailable gauges");
        require(
                alertsArePrioritizedAndUnique(
                        staleDashboard.activeAlerts()),
                "instrumentation alerts must be unique and prioritized");
        require(
                alertsAreImmutable(pureEvDashboard),
                "dashboard alert collections must be immutable");
        PowerTrainController historyController = new PowerTrainController(
                VehicleVariant.RWD,
                0.80,
                10.0);
        for (int frame = 0; frame < 35; frame++) {
            historyController.executeControlLoop(
                    nominalFrame(
                            DriveMode.ECONOMY,
                            20.0,
                            40.0,
                            BatteryManagementSystem.SensorFrame.nominalRwd()));
        }
        require(
                historyController.instrumentationHistory().size() == 30
                        && historyController.latestDashboardSnapshot()
                                .trends().sampleCount() == 30
                        && historyIsImmutable(historyController),
                "instrumentation trend history must remain bounded");

        LOGGER.info("Modular REEV scenarios passed");
        printFrame("RWD EV-first", pureEv);
        printFrame("AWD charge-sustaining", chargeSustaining);
        printFrame("Battery Turtle", turtleFrame);
        printFrame("Refuel recovery", recovery);
        printFrame("Recovery exit", recoveryExit);
        printFrame("Generator limited", generatorLimitedFrame);
        printFrame("Stale-data fallback", staleFallback);
        printDashboard("RWD EV dashboard", pureEvDashboard);
        printDashboard("AWD dashboard", chargeDashboard);
        printDashboard("Turtle dashboard", turtleDashboard);
        printDashboard("Stale-data dashboard", staleDashboard);
        printDashboard("Invalid-data dashboard", invalidDashboard);
    }

    private static InputFrame nominalFrame(
            DriveMode mode,
            double requestedWheelPowerKw,
            double speedKmph,
            BatteryManagementSystem.SensorFrame sensors) {
        return new InputFrame(
                1.0,
                mode,
                speedKmph,
                requestedWheelPowerKw,
                0.0,
                0.70,
                0.0,
                0.0,
                1.5,
                2.0,
                0.21,
                false,
                true,
                false,
                true,
                false,
                true,
                true,
                true,
                1.0,
                1.0,
                sensors);
    }

    private static BatteryManagementSystem.SensorFrame staleSensors() {
        BatteryManagementSystem.SensorFrame nominal =
                BatteryManagementSystem.SensorFrame.nominalRwd();
        return new BatteryManagementSystem.SensorFrame(
                500L,
                nominal.confidence(),
                nominal.stateOfHealth(),
                nominal.availableDischargePowerKw(),
                nominal.availableChargePowerKw(),
                nominal.weakestCellVoltage(),
                nominal.predictedWeakestCellLoadedVoltage(),
                nominal.minimumCellTemperatureC(),
                nominal.maximumCellTemperatureC(),
                nominal.isolationHealthy(),
                nominal.hvilHealthy(),
                nominal.contactorsAvailable(),
                nominal.prechargeAvailable(),
                nominal.pulseBudgetAvailable());
    }

    /**
     * Rolling terrain repeatedly drops demand at each crest. Economy releases
     * the band after 16 s and has to climb back for the next ascent; Terrain
     * holds for 24 s and rides through.
     */
    private static void requireTerrainHoldsBandThroughCrest() {
        int economyFrames = framesToFirstDownshift(
                GeneratorManagementSystem.ResponseProfile.ECONOMY);
        int terrainFrames = framesToFirstDownshift(
                GeneratorManagementSystem.ResponseProfile.HILLS_TERRAIN);
        require(
                economyFrames == 16,
                "Economy must release a band after its 16 s downshift dwell");
        require(
                terrainFrames == 24,
                "Terrain must hold a band for its 24 s downshift dwell");
        require(
                terrainFrames > economyFrames,
                "Terrain must outlast Economy across a crest");
    }

    private static int framesToFirstDownshift(
            GeneratorManagementSystem.ResponseProfile profile) {
        GeneratorManagementSystem generator = new GeneratorManagementSystem(
                GeneratorManagementSystem.Configuration.provisional());
        int peakBandIndex = -1;
        for (int frame = 0; frame < 40; frame++) {
            peakBandIndex = generator
                    .update(crestRequest(profile, 95.0))
                    .activeBandIndex();
        }
        for (int frame = 1; frame <= 60; frame++) {
            int bandIndex = generator
                    .update(crestRequest(profile, 10.0))
                    .activeBandIndex();
            if (bandIndex < peakBandIndex) {
                return frame;
            }
        }
        return -1;
    }

    private static GeneratorManagementSystem.Request crestRequest(
            GeneratorManagementSystem.ResponseProfile profile,
            double tractionDemandKw) {
        return new GeneratorManagementSystem.Request(
                profile,
                1.0,
                tractionDemandKw,
                2.0,
                0.0,
                CONTROL_RESERVE_KW,
                GeneratorManagementSystem.maximumConceptOutputKw(),
                20.0,
                true,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                1.0,
                1.0);
    }

    private static InputFrame recoveryFrame(
            BatteryManagementSystem.SensorFrame sensors) {
        return new InputFrame(
                1.0,
                DriveMode.ECONOMY,
                0.0,
                10.0,
                0.0,
                0.20,
                0.0,
                0.0,
                1.5,
                0.0,
                0.21,
                false,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                1.0,
                1.0,
                sensors);
    }

    private static InputFrame invalidFrame(
            BatteryManagementSystem.SensorFrame sensors) {
        InputFrame nominal = nominalFrame(
                DriveMode.ECONOMY,
                20.0,
                40.0,
                sensors);
        return new InputFrame(
                nominal.deltaSeconds(),
                nominal.requestedMode(),
                nominal.vehicleSpeedKmph(),
                Double.NaN,
                nominal.requestedBrakingWheelPowerKw(),
                nominal.pedalPosition(),
                nominal.wheelSlipFraction(),
                nominal.roadGradeFraction(),
                nominal.criticalAuxiliaryPowerKw(),
                nominal.comfortAuxiliaryPowerKw(),
                nominal.recentConsumptionKwhPerKm(),
                nominal.connectorInserted(),
                nominal.nozzleDisconnected(),
                nominal.vehicleInPark(),
                nominal.readyRequested(),
                nominal.v2lStartEnergyRestored(),
                nominal.fuelSystemHealthy(),
                nominal.engineGeneratorHealthy(),
                nominal.emissionsStartPermitted(),
                nominal.generatorThermalAvailability(),
                nominal.altitudeAvailability(),
                nominal.batterySensors());
    }

    private static void printFrame(String name, TelemetryFrame frame) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        LOGGER.info(String.format(
                "%-24s state=%-32s SoC=%5.1f%% gen=%6.1f kW "
                        + "wheel=%6.1f kW max=%5.1f km/h",
                name,
                frame.operatingState(),
                frame.displayedSoc() * 100.0,
                frame.generatorOutputKw(),
                frame.deliveredWheelPowerKw(),
                frame.dynamicMaximumSpeedKmph()));
    }

    private static void printDashboard(
            String name,
            VehicleInstrumentationManagementSystem.DashboardSnapshot
                    snapshot) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        LOGGER.info(String.format(
                "%-24s seq=%3d quality=%-11s range=%6.1f km "
                        + "battery=%-11s residual=%+.3f kW alerts=%d",
                name,
                snapshot.sequence(),
                snapshot.dataQuality(),
                snapshot.mobility().combinedRangeKm(),
                snapshot.energy().batteryDirection(),
                snapshot.powerFlow().tractionAllocationResidualKw(),
                snapshot.activeAlerts().size()));
    }

    private static boolean residualsWithinTolerance(
            VehicleInstrumentationManagementSystem.DashboardSnapshot
                    snapshot) {
        VehicleInstrumentationManagementSystem.PowerFlowPanel power =
                snapshot.powerFlow();
        return Math.abs(power.tractionAllocationResidualKw()) < 0.10
                && Math.abs(power.batteryAccountingResidualKw()) < 0.10
                && Math.abs(power.generatorAllocationResidualKw()) < 0.10;
    }

    private static boolean alertsArePrioritizedAndUnique(
            List<VehicleInstrumentationManagementSystem.DashboardAlert>
                    alerts) {
        int previousSeverity = Integer.MAX_VALUE;
        for (int index = 0; index < alerts.size(); index++) {
            VehicleInstrumentationManagementSystem.DashboardAlert alert =
                    alerts.get(index);
            int severity = alert.severity().ordinal();
            if (severity > previousSeverity) {
                return false;
            }
            previousSeverity = severity;
            for (int earlier = 0; earlier < index; earlier++) {
                if (alerts.get(earlier).id().equals(alert.id())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean alertsAreImmutable(
            VehicleInstrumentationManagementSystem.DashboardSnapshot
                    snapshot) {
        try {
            snapshot.activeAlerts().add(
                    new VehicleInstrumentationManagementSystem
                            .DashboardAlert(
                            "MUTATION_PROBE",
                            Severity.INFO,
                            "must not be accepted",
                            false));
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }

    private static boolean historyIsImmutable(
            PowerTrainController controller) {
        try {
            controller.instrumentationHistory().clear();
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

    
