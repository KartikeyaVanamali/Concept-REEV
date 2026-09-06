import java.util.Arrays;

/**
 * Engine-generator controller for the modular REEV concept.
 *
 * <p>All public power values are rectified HV-DC power. The six bands are
 * concept-frozen; RPM, BMEP, efficiency and emissions calibrations remain
 * provisional until supplier dyno maps exist.</p>
 */
public final class GeneratorManagementSystem {

    public enum ResponseProfile {
        ECONOMY,
        SPORT,
        HILLS_TERRAIN
    }

    public record Configuration(
            double guaranteedOutputFraction,
            double minimumRunTimeSeconds,
            double minimumStopDwellSeconds,
            double fuelToDcEnergyKwhPerLitre) {

        public Configuration {
            if (!Double.isFinite(guaranteedOutputFraction)
                    || guaranteedOutputFraction < 0.50
                    || guaranteedOutputFraction > 1.0) {
                throw new IllegalArgumentException(
                        "invalid guaranteed output fraction");
            }
            requirePositive(minimumRunTimeSeconds, "minimum run time");
            requirePositive(minimumStopDwellSeconds, "minimum stop dwell");
            requirePositive(fuelToDcEnergyKwhPerLitre, "fuel-to-DC energy");
        }

        public static Configuration provisional() {
            return new Configuration(0.87, 60.0, 20.0, 2.9);
        }
    }

    public record Request(
            ResponseProfile responseProfile,
            double deltaSeconds,
            double sustainedTractionDemandKw,
            double measuredAuxiliaryPowerKw,
            double requestedBatteryRecoveryPowerKw,
            double controlMarginKw,
            double electricalOutputCeilingKw,
            double usableFuelLitres,
            boolean startRequested,
            boolean stopPermitted,
            boolean refuelRecovery,
            boolean brakingMechanicalHold,
            boolean connectorInserted,
            boolean fuelSystemHealthy,
            boolean engineGeneratorHealthy,
            boolean emissionsStartPermitted,
            double thermalAvailabilityFraction,
            double altitudeAvailabilityFraction) {
    }

    public record Status(
            boolean engineRunning,
            boolean startReady,
            double currentOutputKw,
            double targetBandKw,
            double guaranteedOutputKw,
            double estimatedFuelFlowLitresPerHour,
            double predictedFuelRuntimeMinutes,
            int activeBandIndex,
            String transitionReason,
            boolean thermallyDerated) {
    }

    private static final double[] BANDS_KW =
            {30.0, 40.0, 55.0, 70.0, 90.0, 105.0};
    private static final double MINIMUM_USABLE_FUEL_LITRES = 0.20;

    private final Configuration configuration;
    private boolean engineRunning;
    private double currentOutputKw;
    private int activeBandIndex = -1;
    private double timeInBandSeconds;
    private double demandBelowBandSeconds;
    private double engineRunTimeSeconds;
    private double engineStoppedTimeSeconds = Double.POSITIVE_INFINITY;
    private double brakingMechanicalHoldSeconds;

    public GeneratorManagementSystem(Configuration configuration) {
        this.configuration = configuration;
    }

    public static double[] conceptBandsKw() {
        return Arrays.copyOf(BANDS_KW, BANDS_KW.length);
    }

    public static double maximumConceptOutputKw() {
        return BANDS_KW[BANDS_KW.length - 1];
    }

    /**
     * Rejects stale generator output after an invalid controller frame. This is
     * an electrical-safe stop, not a normal emissions-calibrated shutdown.
     */
    public Status emergencyStop(String reason) {
        stopImmediately();
        return stoppedStatus(reason);
    }

    public Status update(Request request) {
        validate(request);

        boolean fuelAvailable =
                request.usableFuelLitres() > MINIMUM_USABLE_FUEL_LITRES;
        boolean startReady = canStart(request, fuelAvailable);

        if (!engineRunning && request.startRequested() && startReady) {
            engineRunning = true;
            activeBandIndex = 0;
            currentOutputKw = 0.0;
            timeInBandSeconds = 0.0;
            demandBelowBandSeconds = 0.0;
            engineRunTimeSeconds = 0.0;
        }

        if (!engineRunning) {
            engineStoppedTimeSeconds += request.deltaSeconds();
            return new Status(
                    false,
                    startReady,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    -1,
                    request.startRequested()
                            ? "generator start blocked"
                            : "generator not requested",
                    false);
        }

        engineRunTimeSeconds += request.deltaSeconds();
        timeInBandSeconds += request.deltaSeconds();
        engineStoppedTimeSeconds = 0.0;

        if (!fuelAvailable
                || !request.fuelSystemHealthy()
                || !request.engineGeneratorHealthy()) {
            stopImmediately();
            return stoppedStatus("generator unavailable");
        }

        double requestedOutputKw =
                Math.max(0.0, request.sustainedTractionDemandKw())
                        + Math.max(0.0, request.measuredAuxiliaryPowerKw())
                        + Math.max(
                                0.0,
                                request.requestedBatteryRecoveryPowerKw())
                        + Math.max(0.0, request.controlMarginKw());

        int desiredBandIndex = findSmallestSufficientBand(requestedOutputKw);
        if (request.refuelRecovery()) {
            desiredBandIndex = requestedOutputKw <= BANDS_KW[0]
                    ? 0
                    : Math.min(1, desiredBandIndex);
        }
        if (request.brakingMechanicalHold() && activeBandIndex >= 0) {
            brakingMechanicalHoldSeconds = 12.0;
        } else {
            brakingMechanicalHoldSeconds = Math.max(
                    0.0,
                    brakingMechanicalHoldSeconds - request.deltaSeconds());
        }
        trackDemandBelowBand(desiredBandIndex, request.deltaSeconds());

        int commandedBandIndex = brakingMechanicalHoldSeconds > 0.0
                && activeBandIndex >= 0
                        ? activeBandIndex
                        : progressBand(
                                desiredBandIndex,
                                request.responseProfile());
        double targetBandKw = BANDS_KW[commandedBandIndex];

        double availabilityFraction = clamp(
                Math.min(
                        request.thermalAvailabilityFraction(),
                        request.altitudeAvailabilityFraction()),
                0.0,
                1.0);
        double availableTargetKw = targetBandKw * availabilityFraction;
        double permittedElectricalOutputKw = Math.min(
                availableTargetKw,
                request.electricalOutputCeilingKw());
        double rampSeconds = switch (request.responseProfile()) {
            case SPORT -> 2.5;
            case ECONOMY -> 5.0;
            case HILLS_TERRAIN -> 5.0;
        };
        double maximumRampKw =
                BANDS_KW[BANDS_KW.length - 1]
                        / rampSeconds
                        * request.deltaSeconds();
        if (currentOutputKw > permittedElectricalOutputKw) {
            // Keep the engine in its current mechanical band for dwell/NVH,
            // but immediately unload the rectifier if the HV link cannot
            // accept its previous electrical output (for example, regen).
            currentOutputKw = permittedElectricalOutputKw;
        } else {
            currentOutputKw = moveTowards(
                    currentOutputKw,
                    permittedElectricalOutputKw,
                    maximumRampKw);
        }

        if (commandedBandIndex != activeBandIndex) {
            activeBandIndex = commandedBandIndex;
            timeInBandSeconds = 0.0;
            demandBelowBandSeconds = 0.0;
        }

        boolean canStop =
                request.stopPermitted()
                        && engineRunTimeSeconds
                                >= configuration.minimumRunTimeSeconds();
        if (canStop) {
            stopImmediately();
            return stoppedStatus("emissions-safe generator stop");
        }

        double guaranteedOutputKw =
                currentOutputKw
                        * configuration.guaranteedOutputFraction();
        double litresPerHour = currentOutputKw <= 0.0
                ? 0.0
                : currentOutputKw
                        / configuration.fuelToDcEnergyKwhPerLitre();
        double runtimeMinutes = litresPerHour <= 0.0
                ? Double.POSITIVE_INFINITY
                : request.usableFuelLitres() / litresPerHour * 60.0;

        return new Status(
                true,
                true,
                currentOutputKw,
                targetBandKw,
                guaranteedOutputKw,
                litresPerHour,
                runtimeMinutes,
                activeBandIndex,
                transitionReason(
                        desiredBandIndex,
                        commandedBandIndex,
                        availabilityFraction),
                availabilityFraction < 0.999);
    }

    /**
     * The downshift dwell measures how long demand has stayed below the active
     * band, not how long the band has been held. A long climb must not
     * pre-expire the dwell and drop the band at the first crest.
     */
    private void trackDemandBelowBand(
            int desiredBandIndex,
            double deltaSeconds) {
        if (activeBandIndex >= 0 && desiredBandIndex < activeBandIndex) {
            demandBelowBandSeconds += deltaSeconds;
        } else {
            demandBelowBandSeconds = 0.0;
        }
    }

    private int progressBand(
            int desiredBandIndex,
            ResponseProfile responseProfile) {
        if (activeBandIndex < 0) {
            return 0;
        }
        if (desiredBandIndex > activeBandIndex) {
            double upshiftDwell = switch (responseProfile) {
                case SPORT -> 0.30;
                case ECONOMY -> 1.50;
                case HILLS_TERRAIN -> 1.50;
            };
            if (timeInBandSeconds < upshiftDwell) {
                return activeBandIndex;
            }
            int maximumStep = switch (responseProfile) {
                case SPORT -> 2;
                case ECONOMY -> 1;
                case HILLS_TERRAIN -> 1;
            };
            return Math.min(desiredBandIndex, activeBandIndex + maximumStep);
        }
        if (desiredBandIndex < activeBandIndex) {
            // Hills/Terrain holds the band well past the crest of a climb so
            // the next climb does not force an immediate upshift. Without the
            // longer dwell, rolling terrain makes the band oscillate.
            double downshiftDwell = switch (responseProfile) {
                case SPORT -> 8.0;
                case ECONOMY -> 16.0;
                case HILLS_TERRAIN -> 24.0;
            };
            if (demandBelowBandSeconds < downshiftDwell) {
                return activeBandIndex;
            }
            return activeBandIndex - 1;
        }
        return activeBandIndex;
    }

    private boolean canStart(Request request, boolean fuelAvailable) {
        return !request.connectorInserted()
                && fuelAvailable
                && request.fuelSystemHealthy()
                && request.engineGeneratorHealthy()
                && request.emissionsStartPermitted()
                && engineStoppedTimeSeconds
                        >= configuration.minimumStopDwellSeconds();
    }

    private static int findSmallestSufficientBand(double requestedOutputKw) {
        for (int index = 0; index < BANDS_KW.length; index++) {
            if (BANDS_KW[index] >= requestedOutputKw) {
                return index;
            }
        }
        return BANDS_KW.length - 1;
    }

    private static String transitionReason(
            int desiredBandIndex,
            int commandedBandIndex,
            double availabilityFraction) {
        if (availabilityFraction < 0.999) {
            return "generator output dynamically derated";
        }
        if (commandedBandIndex < desiredBandIndex) {
            return "progressing toward sufficient band";
        }
        if (commandedBandIndex > desiredBandIndex) {
            return "downshift dwell active";
        }
        return "smallest sufficient band selected";
    }

    private void stopImmediately() {
        engineRunning = false;
        currentOutputKw = 0.0;
        activeBandIndex = -1;
        timeInBandSeconds = 0.0;
        demandBelowBandSeconds = 0.0;
        engineRunTimeSeconds = 0.0;
        engineStoppedTimeSeconds = 0.0;
    }

    private Status stoppedStatus(String reason) {
        return new Status(
                false,
                false,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                -1,
                reason,
                false);
    }

    private static void validate(Request request) {
        if (request == null || request.responseProfile() == null) {
            throw new IllegalArgumentException("generator request is required");
        }
        if (!finite(
                request.deltaSeconds(),
                request.sustainedTractionDemandKw(),
                request.measuredAuxiliaryPowerKw(),
                request.requestedBatteryRecoveryPowerKw(),
                request.controlMarginKw(),
                request.electricalOutputCeilingKw(),
                request.usableFuelLitres(),
                request.thermalAvailabilityFraction(),
                request.altitudeAvailabilityFraction())
                || request.deltaSeconds() <= 0.0
                || request.usableFuelLitres() < 0.0
                || request.electricalOutputCeilingKw() < 0.0
                || request.thermalAvailabilityFraction() < 0.0
                || request.thermalAvailabilityFraction() > 1.0
                || request.altitudeAvailabilityFraction() < 0.0
                || request.altitudeAvailabilityFraction() > 1.0) {
            throw new IllegalArgumentException("invalid generator request");
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static double moveTowards(
            double current,
            double target,
            double maximumStep) {
        if (current < target) {
            return Math.min(target, current + maximumStep);
        }
        return Math.max(target, current - maximumStep);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
