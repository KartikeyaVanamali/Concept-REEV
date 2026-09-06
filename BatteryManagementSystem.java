/**
 * Simulation-grade battery authority for the modular REEV concept.
 *
 * <p>This class deliberately exposes energy and power limits rather than letting
 * the vehicle controller infer permission from displayed SoC. Percentages are
 * HMI anchors; weakest-cell, thermal, isolation, freshness and confidence
 * evidence remain authoritative.</p>
 */
public final class BatteryManagementSystem {

    public record Configuration(
            String variantName,
            double beginningOfLifeUsableCapacityKwh,
            double minimumTurtleEnergyKwh,
            double maximumTurtleEnergyKwh,
            double nominalEmergencyConsumptionKwhPerKm,
            double adverseEmergencyConsumptionKwhPerKm,
            double transientReserveKwh,
            double uncertaintyReserveKwh,
            double upperPhysicalReserveFraction,
            double lowerPhysicalReserveFraction) {

        public Configuration {
            requirePositive(beginningOfLifeUsableCapacityKwh, "usable capacity");
            requirePositive(minimumTurtleEnergyKwh, "minimum Turtle energy");
            requirePositive(maximumTurtleEnergyKwh, "maximum Turtle energy");
            if (maximumTurtleEnergyKwh < minimumTurtleEnergyKwh) {
                throw new IllegalArgumentException(
                        "maximum Turtle energy must not be below minimum");
            }
            requireFraction(upperPhysicalReserveFraction, "upper reserve");
            requireFraction(lowerPhysicalReserveFraction, "lower reserve");
        }

        public static Configuration rwd40Kwh() {
            return new Configuration(
                    "RWD_40_KWH",
                    40.0,
                    3.0,
                    3.2,
                    0.20,
                    0.30,
                    0.45,
                    0.20,
                    0.01,
                    0.03);
        }

        public static Configuration awd50Kwh() {
            return new Configuration(
                    "AWD_50_KWH",
                    50.0,
                    3.5,
                    4.0,
                    0.24,
                    0.36,
                    0.60,
                    0.25,
                    0.01,
                    0.03);
        }
    }

    /**
     * Timestamped sensor evidence supplied to the battery authority.
     *
     * <p>Power values are at the battery HV-DC terminals. The prototype treats
     * 100 ms as the normal freshness limit and fails down on invalid evidence.</p>
     */
    public record SensorFrame(
            long ageMillis,
            double confidence,
            double stateOfHealth,
            double availableDischargePowerKw,
            double availableChargePowerKw,
            double weakestCellVoltage,
            double predictedWeakestCellLoadedVoltage,
            double minimumCellTemperatureC,
            double maximumCellTemperatureC,
            boolean isolationHealthy,
            boolean hvilHealthy,
            boolean contactorsAvailable,
            boolean prechargeAvailable,
            boolean pulseBudgetAvailable) {

        public static SensorFrame nominalRwd() {
            return nominal(187.0, 100.0);
        }

        public static SensorFrame nominalAwd() {
            return nominal(263.0, 125.0);
        }

        private static SensorFrame nominal(
                double dischargePowerKw,
                double chargePowerKw) {
            return new SensorFrame(
                    10L,
                    0.95,
                    1.0,
                    dischargePowerKw,
                    chargePowerKw,
                    3.20,
                    3.05,
                    25.0,
                    32.0,
                    true,
                    true,
                    true,
                    true,
                    true);
        }
    }

    public record EnergyRequest(
            double recentConsumptionKwhPerKm,
            double criticalAuxiliaryPowerKw,
            boolean generatorPowerGuaranteed,
            boolean usableFuelAvailable) {
    }

    public record Status(
            boolean evidenceValid,
            String limitingReason,
            double displayedSoc,
            double physicalSoc,
            double currentUsableCapacityKwh,
            double remainingUsableEnergyKwh,
            double availableDischargePowerKw,
            double availableChargePowerKw,
            double turtleEnergyTargetKwh,
            double turtleEntryDisplayedSoc,
            double recoveryExitEnergyKwh,
            double recoveryProgress,
            boolean earlyInterventionRequired,
            boolean turtleEntryRequired,
            boolean energyDepleted,
            boolean faultControlledStopRequired) {
    }

    private static final double NOMINAL_TURTLE_DISTANCE_KM = 15.0;
    private static final double ADVERSE_TURTLE_DISTANCE_KM = 10.0;
    private static final double EARLY_INTERVENTION_SOC = 0.10;
    private static final double MAX_SIGNAL_AGE_MILLIS = 100.0;
    private static final double MINIMUM_LOADED_CELL_VOLTAGE = 2.80;
    private static final double MINIMUM_NORMAL_CELL_TEMPERATURE_C = -20.0;
    private static final double MAXIMUM_NORMAL_CELL_TEMPERATURE_C = 55.0;

    private final Configuration configuration;
    private double currentUsableCapacityKwh;
    private double remainingUsableEnergyKwh;

    public BatteryManagementSystem(
            Configuration configuration,
            double initialDisplayedSoc) {
        this.configuration = configuration;
        requireFraction(initialDisplayedSoc, "initial displayed SoC");
        currentUsableCapacityKwh =
                configuration.beginningOfLifeUsableCapacityKwh();
        remainingUsableEnergyKwh =
                currentUsableCapacityKwh * initialDisplayedSoc;
    }

    public Configuration configuration() {
        return configuration;
    }

    /**
     * Evaluates the current battery authority without mutating stored energy.
     */
    public Status evaluate(
            SensorFrame sensors,
            EnergyRequest request) {
        if (sensors == null || request == null) {
            throw new IllegalArgumentException("battery evidence is required");
        }
        String validationFailure = validate(sensors, request);
        boolean evidenceValid = validationFailure == null;

        double confidenceMultiplier =
                sensors.confidence() >= 0.80 ? 1.0 : 1.50;
        double observedConsumption = Math.max(
                request.recentConsumptionKwhPerKm(),
                configuration.nominalEmergencyConsumptionKwhPerKm());
        double nominalDistanceEnergy =
                NOMINAL_TURTLE_DISTANCE_KM * observedConsumption;
        double adverseDistanceEnergy =
                ADVERSE_TURTLE_DISTANCE_KM
                        * configuration.adverseEmergencyConsumptionKwhPerKm();
        double transitionEnergy =
                request.criticalAuxiliaryPowerKw() * 0.10
                        + configuration.transientReserveKwh()
                        + configuration.uncertaintyReserveKwh();

        double rawTurtleTarget = Math.max(
                nominalDistanceEnergy,
                Math.max(adverseDistanceEnergy, transitionEnergy));
        double turtleTarget = clamp(
                rawTurtleTarget * confidenceMultiplier,
                configuration.minimumTurtleEnergyKwh(),
                Math.max(
                        configuration.maximumTurtleEnergyKwh(),
                        currentUsableCapacityKwh * 0.12));

        if (!evidenceValid
                || sensors.predictedWeakestCellLoadedVoltage()
                        < MINIMUM_LOADED_CELL_VOLTAGE + 0.10
                || sensors.minimumCellTemperatureC()
                        < MINIMUM_NORMAL_CELL_TEMPERATURE_C
                || sensors.maximumCellTemperatureC()
                        > MAXIMUM_NORMAL_CELL_TEMPERATURE_C) {
            turtleTarget = Math.min(
                    currentUsableCapacityKwh * 0.15,
                    turtleTarget + configuration.uncertaintyReserveKwh());
        }

        double displayedSoc = displayedSoc();
        double recoveryExitEnergy =
                currentUsableCapacityKwh * EARLY_INTERVENTION_SOC
                        + configuration.transientReserveKwh()
                        + configuration.uncertaintyReserveKwh();
        double availableDischarge = safeDischargeLimit(sensors, evidenceValid);
        double availableCharge = safeChargeLimit(sensors, evidenceValid);
        boolean generatorUnavailable =
                !request.generatorPowerGuaranteed()
                        && !request.usableFuelAvailable();

        boolean earlyIntervention =
                remainingUsableEnergyKwh
                                <= Math.max(
                                        turtleTarget,
                                        currentUsableCapacityKwh
                                                * EARLY_INTERVENTION_SOC)
                        || !evidenceValid;
        boolean turtleEntry =
                remainingUsableEnergyKwh <= turtleTarget
                        && generatorUnavailable;
        boolean energyDepleted = remainingUsableEnergyKwh <= 0.0;
        boolean faultControlledStop =
                sensors.predictedWeakestCellLoadedVoltage()
                                < MINIMUM_LOADED_CELL_VOLTAGE
                        || !sensors.isolationHealthy()
                        || !sensors.hvilHealthy();
        double recoveryProgress = clamp(
                remainingUsableEnergyKwh / recoveryExitEnergy,
                0.0,
                1.0);

        return new Status(
                evidenceValid,
                evidenceValid ? limitingReason(sensors) : validationFailure,
                displayedSoc,
                physicalSoc(displayedSoc),
                currentUsableCapacityKwh,
                remainingUsableEnergyKwh,
                availableDischarge,
                availableCharge,
                turtleTarget,
                turtleTarget / currentUsableCapacityKwh,
                recoveryExitEnergy,
                recoveryProgress,
                earlyIntervention,
                turtleEntry,
                energyDepleted,
                faultControlledStop);
    }

    /**
     * Applies average battery-terminal power for one deterministic frame.
     *
     * @param batteryPowerKw positive for discharge, negative for charging
     */
    public void applyPowerFlow(double batteryPowerKw, double deltaSeconds) {
        if (!Double.isFinite(batteryPowerKw)
                || !Double.isFinite(deltaSeconds)
                || deltaSeconds <= 0.0) {
            throw new IllegalArgumentException("invalid battery power frame");
        }
        double energyDeltaKwh = batteryPowerKw * deltaSeconds / 3600.0;
        remainingUsableEnergyKwh = clamp(
                remainingUsableEnergyKwh - energyDeltaKwh,
                0.0,
                currentUsableCapacityKwh);
    }

    public void setStateOfHealth(double stateOfHealth) {
        if (!Double.isFinite(stateOfHealth)
                || stateOfHealth < 0.50
                || stateOfHealth > 1.0) {
            throw new IllegalArgumentException("invalid state of health");
        }
        double previousCapacity = currentUsableCapacityKwh;
        double previousSoc = previousCapacity == 0.0
                ? 0.0
                : remainingUsableEnergyKwh / previousCapacity;
        currentUsableCapacityKwh =
                configuration.beginningOfLifeUsableCapacityKwh()
                        * stateOfHealth;
        remainingUsableEnergyKwh =
                Math.min(currentUsableCapacityKwh,
                        currentUsableCapacityKwh * previousSoc);
    }

    public void setDisplayedSocForSimulation(double displayedSoc) {
        requireFraction(displayedSoc, "displayed SoC");
        remainingUsableEnergyKwh =
                currentUsableCapacityKwh * displayedSoc;
    }

    public boolean canAcceptRegeneration(
            Status status,
            double requestedRegenPowerKw) {
        return status.evidenceValid()
                && requestedRegenPowerKw > 0.0
                && status.availableChargePowerKw() > 0.0
                && status.displayedSoc() < 0.99;
    }

    private double displayedSoc() {
        if (currentUsableCapacityKwh <= 0.0) {
            return 0.0;
        }
        return clamp(
                remainingUsableEnergyKwh / currentUsableCapacityKwh,
                0.0,
                1.0);
    }

    private double physicalSoc(double displayedSoc) {
        double usablePhysicalWindow =
                1.0
                        - configuration.upperPhysicalReserveFraction()
                        - configuration.lowerPhysicalReserveFraction();
        return configuration.lowerPhysicalReserveFraction()
                + displayedSoc * usablePhysicalWindow;
    }

    private static double safeDischargeLimit(
            SensorFrame sensors,
            boolean evidenceValid) {
        if (!evidenceValid
                || !sensors.isolationHealthy()
                || !sensors.hvilHealthy()
                || !sensors.contactorsAvailable()
                || !sensors.prechargeAvailable()) {
            return 0.0;
        }
        double limit = sensors.availableDischargePowerKw();
        if (!sensors.pulseBudgetAvailable()) {
            limit *= 0.50;
        }
        if (sensors.minimumCellTemperatureC() < 0.0
                || sensors.maximumCellTemperatureC() > 50.0) {
            limit *= 0.70;
        }
        return Math.max(0.0, limit);
    }

    private static double safeChargeLimit(
            SensorFrame sensors,
            boolean evidenceValid) {
        if (!evidenceValid
                || !sensors.isolationHealthy()
                || !sensors.hvilHealthy()
                || !sensors.contactorsAvailable()) {
            return 0.0;
        }
        double limit = sensors.availableChargePowerKw();
        if (sensors.minimumCellTemperatureC() < 5.0
                || sensors.maximumCellTemperatureC() > 45.0) {
            limit *= 0.50;
        }
        return Math.max(0.0, limit);
    }

    private static String validate(
            SensorFrame sensors,
            EnergyRequest request) {
        if (sensors == null || request == null) {
            return "missing battery evidence";
        }
        if (sensors.ageMillis() < 0
                || sensors.ageMillis() > MAX_SIGNAL_AGE_MILLIS) {
            return "stale battery evidence";
        }
        if (!finite(
                sensors.confidence(),
                sensors.stateOfHealth(),
                sensors.availableDischargePowerKw(),
                sensors.availableChargePowerKw(),
                sensors.weakestCellVoltage(),
                sensors.predictedWeakestCellLoadedVoltage(),
                sensors.minimumCellTemperatureC(),
                sensors.maximumCellTemperatureC(),
                request.recentConsumptionKwhPerKm(),
                request.criticalAuxiliaryPowerKw())) {
            return "non-finite battery evidence";
        }
        if (sensors.confidence() < 0.0
                || sensors.confidence() > 1.0
                || sensors.stateOfHealth() < 0.50
                || sensors.stateOfHealth() > 1.0
                || sensors.availableDischargePowerKw() < 0.0
                || sensors.availableChargePowerKw() < 0.0
                || request.recentConsumptionKwhPerKm() <= 0.0
                || request.criticalAuxiliaryPowerKw() < 0.0) {
            return "implausible battery evidence";
        }
        if (!sensors.isolationHealthy()) {
            return "isolation fault";
        }
        if (!sensors.hvilHealthy()) {
            return "HVIL fault";
        }
        return null;
    }

    private static String limitingReason(SensorFrame sensors) {
        if (!sensors.pulseBudgetAvailable()) {
            return "battery pulse budget constrained";
        }
        if (sensors.minimumCellTemperatureC() < 0.0) {
            return "cold battery derating";
        }
        if (sensors.maximumCellTemperatureC() > 50.0) {
            return "hot battery derating";
        }
        return "none";
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

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be within 0..1");
        }
    }
}
