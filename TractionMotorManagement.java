/**
 * Electric-traction allocator for the modular REEV concept.
 *
 * <p>The driver requests wheel power. This class keeps wheel, traction-HV-DC,
 * battery-HV-DC and generator-HV-DC planes explicit, allocates generator power
 * only once, and lets the battery provide the immediate permitted deficit.</p>
 */
public final class TractionMotorManagement {

    public record Configuration(
            String variantName,
            double rearMotorMaximumWheelPowerKw,
            double frontMotorMaximumWheelPowerKw,
            double maximumTractionDcPowerKw,
            double nominalDcToWheelEfficiency,
            double nominalRegenWheelToDcEfficiency,
            double maximumRegenerativeChargePowerKw,
            double hardMaximumSpeedKmph) {

        public Configuration {
            requirePositive(
                    rearMotorMaximumWheelPowerKw,
                    "rear motor wheel power");
            if (!Double.isFinite(frontMotorMaximumWheelPowerKw)
                    || frontMotorMaximumWheelPowerKw < 0.0) {
                throw new IllegalArgumentException(
                        "front motor wheel power must not be negative");
            }
            requirePositive(maximumTractionDcPowerKw, "traction DC power");
            requireFraction(
                    nominalDcToWheelEfficiency,
                    "DC-to-wheel efficiency");
            requireFraction(
                    nominalRegenWheelToDcEfficiency,
                    "regen efficiency");
            requirePositive(
                    maximumRegenerativeChargePowerKw,
                    "maximum regenerative charge power");
            requirePositive(hardMaximumSpeedKmph, "maximum speed");
        }

        public static Configuration rwd() {
            return new Configuration(
                    "RWD_170_KW",
                    170.0,
                    0.0,
                    187.0,
                    0.92,
                    0.85,
                    30.0,
                    210.0);
        }

        public static Configuration awd() {
            return new Configuration(
                    "AWD_240_KW",
                    170.0,
                    70.0,
                    263.0,
                    0.92,
                    0.85,
                    40.0,
                    210.0);
        }
    }

    public record Request(
            double deltaSeconds,
            double vehicleSpeedKmph,
            double requestedWheelPowerKw,
            double requestedBrakingWheelPowerKw,
            double batteryDischargeLimitKw,
            double batteryChargeLimitKw,
            double guaranteedGeneratorPowerKw,
            double criticalAuxiliaryPowerKw,
            double permittedComfortAuxiliaryPowerKw,
            double requestedBatteryRecoveryPowerKw,
            double controlReserveKw,
            double dynamicMaximumSpeedKmph,
            double frontAxleAssistFraction,
            double stabilityPowerFraction,
            boolean recoveryHasPriority,
            boolean controlledStopRequested) {
    }

    public record Status(
            double deliveredWheelPowerKw,
            double tractionDcPowerKw,
            double maximumTractionDcPowerKw,
            double rearWheelPowerKw,
            double frontWheelPowerKw,
            double generatorToAuxiliariesKw,
            double generatorToTractionKw,
            double generatorToBatteryKw,
            double batteryToAuxiliariesKw,
            double batteryToTractionKw,
            double batteryPowerKw,
            double regenerativeChargePowerKw,
            double frictionBrakingWheelPowerKw,
            double unservedAuxiliaryPowerKw,
            double unusedGeneratorPowerKw,
            double dynamicMaximumSpeedKmph,
            boolean tractionLimited,
            String limitingReason) {
    }

    private final Configuration configuration;
    private double previousDeliveredWheelPowerKw;

    public TractionMotorManagement(Configuration configuration) {
        this.configuration = configuration;
    }

    public Status allocate(Request request) {
        validate(request);

        double dynamicMaximumSpeed = Math.min(
                configuration.hardMaximumSpeedKmph(),
                Math.max(0.0, request.dynamicMaximumSpeedKmph()));
        double totalAuxiliaryPower =
                request.criticalAuxiliaryPowerKw()
                        + request.permittedComfortAuxiliaryPowerKw();
        double generatorAfterReserve = Math.max(
                0.0,
                request.guaranteedGeneratorPowerKw()
                        - request.controlReserveKw());
        double generatorToAuxiliaries =
                Math.min(totalAuxiliaryPower, generatorAfterReserve);
        double auxiliaryDeficit =
                totalAuxiliaryPower - generatorToAuxiliaries;
        double generatorRemaining =
                generatorAfterReserve - generatorToAuxiliaries;
        double batteryToAuxiliaries = Math.min(
                auxiliaryDeficit,
                request.batteryDischargeLimitKw());
        double unservedAuxiliaryPower =
                auxiliaryDeficit - batteryToAuxiliaries;

        double priorityRecoveryPower = request.recoveryHasPriority()
                ? Math.min(
                        request.requestedBatteryRecoveryPowerKw(),
                        Math.min(
                                request.batteryChargeLimitKw(),
                                generatorRemaining))
                : 0.0;
        generatorRemaining -= priorityRecoveryPower;

        if (request.requestedBrakingWheelPowerKw() > 0.0) {
            return allocateBraking(
                    request,
                    dynamicMaximumSpeed,
                    generatorToAuxiliaries,
                    batteryToAuxiliaries,
                    unservedAuxiliaryPower,
                    generatorRemaining,
                    priorityRecoveryPower);
        }

        double requestedWheelPower = clamp(
                request.requestedWheelPowerKw(),
                0.0,
                maximumWheelPowerKw());
        requestedWheelPower *= request.stabilityPowerFraction();
        if (request.vehicleSpeedKmph() >= dynamicMaximumSpeed
                || request.controlledStopRequested()) {
            requestedWheelPower = 0.0;
        }

        double requestedTractionDc = Math.min(
                configuration.maximumTractionDcPowerKw(),
                requestedWheelPower
                        / configuration.nominalDcToWheelEfficiency());
        double generatorToTraction =
                Math.min(requestedTractionDc, generatorRemaining);
        generatorRemaining -= generatorToTraction;

        double availableBatteryAfterAux = Math.max(
                0.0,
                request.batteryDischargeLimitKw() - batteryToAuxiliaries);
        double tractionDeficit =
                requestedTractionDc - generatorToTraction;
        double batteryToTraction =
                Math.min(tractionDeficit, availableBatteryAfterAux);
        double batteryDischarge =
                batteryToAuxiliaries + batteryToTraction;
        double deliveredTractionDc =
                generatorToTraction + batteryToTraction;
        double targetWheelPower =
                deliveredTractionDc
                        * configuration.nominalDcToWheelEfficiency();

        double rampRateKwPerSecond = request.controlledStopRequested()
                ? 35.0
                : 220.0;
        double deliveredWheelPower = moveTowards(
                previousDeliveredWheelPowerKw,
                targetWheelPower,
                rampRateKwPerSecond * request.deltaSeconds());
        previousDeliveredWheelPowerKw = deliveredWheelPower;

        double normalRecoveryPower = request.recoveryHasPriority()
                ? 0.0
                : Math.min(
                        request.requestedBatteryRecoveryPowerKw(),
                        Math.min(
                                request.batteryChargeLimitKw(),
                                generatorRemaining));
        double generatorToBattery =
                priorityRecoveryPower + normalRecoveryPower;
        generatorRemaining -= normalRecoveryPower;
        double batteryPower = batteryDischarge - generatorToBattery;

        AxlePower axlePower = allocateAxles(
                deliveredWheelPower,
                request.frontAxleAssistFraction());
        boolean tractionLimited =
                deliveredWheelPower + 0.01 < requestedWheelPower;
        String limitingReason = limitingReason(
                request,
                dynamicMaximumSpeed,
                tractionLimited,
                unservedAuxiliaryPower,
                batteryToTraction,
                tractionDeficit);

        return new Status(
                deliveredWheelPower,
                deliveredTractionDc,
                configuration.maximumTractionDcPowerKw(),
                axlePower.rearKw(),
                axlePower.frontKw(),
                generatorToAuxiliaries,
                generatorToTraction,
                generatorToBattery,
                batteryToAuxiliaries,
                batteryToTraction,
                batteryPower,
                0.0,
                0.0,
                unservedAuxiliaryPower,
                generatorRemaining,
                dynamicMaximumSpeed,
                tractionLimited,
                limitingReason);
    }

    private Status allocateBraking(
            Request request,
            double dynamicMaximumSpeed,
            double generatorToAuxiliaries,
            double batteryToAuxiliaries,
            double unservedAuxiliaryPower,
            double generatorRemaining,
            double priorityRecoveryPower) {
        double requestedBrakingWheelPower =
                request.requestedBrakingWheelPowerKw();
        double potentialRegenDc =
                requestedBrakingWheelPower
                        * configuration.nominalRegenWheelToDcEfficiency();
        double remainingChargeAcceptance = Math.max(
                0.0,
                request.batteryChargeLimitKw() - priorityRecoveryPower);
        double regenerativeChargePower =
                Math.min(
                        configuration.maximumRegenerativeChargePowerKw(),
                        Math.min(
                                potentialRegenDc,
                                remainingChargeAcceptance));
        double regenerativeWheelPower =
                regenerativeChargePower
                        / configuration.nominalRegenWheelToDcEfficiency();
        double frictionBrakingPower = Math.max(
                0.0,
                requestedBrakingWheelPower - regenerativeWheelPower);

        double generatorToBattery = priorityRecoveryPower;
        double additionalGeneratorCharge = Math.min(
                Math.max(
                        0.0,
                        request.requestedBatteryRecoveryPowerKw()
                                - priorityRecoveryPower),
                Math.min(
                        Math.max(
                                0.0,
                                remainingChargeAcceptance
                                        - regenerativeChargePower),
                        generatorRemaining));
        generatorToBattery += additionalGeneratorCharge;
        generatorRemaining -= additionalGeneratorCharge;

        double batteryPower =
                batteryToAuxiliaries
                        - regenerativeChargePower
                        - generatorToBattery;
        previousDeliveredWheelPowerKw = moveTowards(
                previousDeliveredWheelPowerKw,
                0.0,
                300.0 * request.deltaSeconds());

        return new Status(
                0.0,
                0.0,
                configuration.maximumTractionDcPowerKw(),
                0.0,
                0.0,
                generatorToAuxiliaries,
                0.0,
                generatorToBattery,
                batteryToAuxiliaries,
                0.0,
                batteryPower,
                regenerativeChargePower,
                frictionBrakingPower,
                unservedAuxiliaryPower,
                generatorRemaining,
                dynamicMaximumSpeed,
                false,
                frictionBrakingPower > 0.0
                        ? "friction braking blended with regeneration"
                        : "regenerative braking");
    }

    private AxlePower allocateAxles(
            double deliveredWheelPowerKw,
            double frontAxleAssistFraction) {
        if (configuration.frontMotorMaximumWheelPowerKw() <= 0.0) {
            return new AxlePower(deliveredWheelPowerKw, 0.0);
        }
        double desiredFront = deliveredWheelPowerKw
                * clamp(frontAxleAssistFraction, 0.0, 1.0);
        double front = Math.min(
                desiredFront,
                configuration.frontMotorMaximumWheelPowerKw());
        double rear = Math.min(
                deliveredWheelPowerKw - front,
                configuration.rearMotorMaximumWheelPowerKw());
        double unallocated = deliveredWheelPowerKw - rear - front;
        if (unallocated > 0.0) {
            double frontHeadroom =
                    configuration.frontMotorMaximumWheelPowerKw() - front;
            double frontAddition = Math.min(unallocated, frontHeadroom);
            front += frontAddition;
            unallocated -= frontAddition;
        }
        if (unallocated > 0.0) {
            rear += Math.min(
                    unallocated,
                    configuration.rearMotorMaximumWheelPowerKw() - rear);
        }
        return new AxlePower(rear, front);
    }

    private double maximumWheelPowerKw() {
        return configuration.rearMotorMaximumWheelPowerKw()
                + configuration.frontMotorMaximumWheelPowerKw();
    }

    private static String limitingReason(
            Request request,
            double dynamicMaximumSpeed,
            boolean tractionLimited,
            double unservedAuxiliaryPower,
            double batteryToTraction,
            double tractionDeficit) {
        if (unservedAuxiliaryPower > 0.01) {
            return "source power unavailable for required auxiliaries";
        }
        if (request.controlledStopRequested()) {
            return "controlled propulsion fade requested";
        }
        if (request.vehicleSpeedKmph() >= dynamicMaximumSpeed) {
            return "dynamic speed ceiling";
        }
        if (tractionLimited && batteryToTraction + 0.01 < tractionDeficit) {
            return "battery discharge authority";
        }
        if (tractionLimited) {
            return "motor or stability envelope";
        }
        return "none";
    }

    private static void validate(Request request) {
        if (request == null
                || !finite(
                        request.deltaSeconds(),
                        request.vehicleSpeedKmph(),
                        request.requestedWheelPowerKw(),
                        request.requestedBrakingWheelPowerKw(),
                        request.batteryDischargeLimitKw(),
                        request.batteryChargeLimitKw(),
                        request.guaranteedGeneratorPowerKw(),
                        request.criticalAuxiliaryPowerKw(),
                        request.permittedComfortAuxiliaryPowerKw(),
                        request.requestedBatteryRecoveryPowerKw(),
                        request.controlReserveKw(),
                        request.dynamicMaximumSpeedKmph(),
                        request.frontAxleAssistFraction(),
                        request.stabilityPowerFraction())
                || request.deltaSeconds() <= 0.0
                || request.vehicleSpeedKmph() < 0.0
                || request.requestedWheelPowerKw() < 0.0
                || request.requestedBrakingWheelPowerKw() < 0.0
                || request.batteryDischargeLimitKw() < 0.0
                || request.batteryChargeLimitKw() < 0.0
                || request.guaranteedGeneratorPowerKw() < 0.0
                || request.criticalAuxiliaryPowerKw() < 0.0
                || request.permittedComfortAuxiliaryPowerKw() < 0.0
                || request.requestedBatteryRecoveryPowerKw() < 0.0
                || request.controlReserveKw() < 0.0
                || request.frontAxleAssistFraction() < 0.0
                || request.frontAxleAssistFraction() > 1.0
                || request.stabilityPowerFraction() < 0.0
                || request.stabilityPowerFraction() > 1.0) {
            throw new IllegalArgumentException("invalid traction request");
        }
    }

    private record AxlePower(double rearKw, double frontKw) {
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

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be within 0..1");
        }
    }
}
