# REEV Design Specification
## XUV 7XO Range-Extended Electric Vehicle — Pre-Feasibility Concept

**Status:** Pre-feasibility concept. Every numerical value is a transparent preliminary design target or calculation assumption, not an asserted certified result. A future programme must validate through supplier data, dynamometer testing, coast-down testing, thermal testing, homologation, HIL/SIL testing, and ISO 26262 safety analysis.

**Publication note:** Section 25 identifies control mechanisms as candidate patentable subject matter, none of which has been filed. India provides no grace period for an inventor's own pre-filing disclosure. Do not publish this file, or Section 25 in any form, until the filing-or-defensive-publication decision in 25.8 has been made deliberately. The HTML concept site is the publication surface; this file is not.

---

## 1. Vehicle Mission and Architecture

- Purpose-built, three-row XUV 7XO Range-Extended Electric Vehicle (REEV).
- Based on a purpose-adapted Mahindra INGLO skateboard, not an ICE donor conversion. The structure, crash paths, floor, thermal circuits, fuel system, range extender, and HV system must be engineered together.
- Series-hybrid architecture: the combustion engine has no mechanical path to either axle. Wheel torque is always electric.
- EV-first operation: when battery energy is healthy, the generator remains off. The vehicle supports home AC and public DC charging.
- The generator supplies the HV DC bus. It can meet traction demand, support auxiliaries, and send genuine surplus to the battery.
- Battery response is immediate. Generator response is deliberately slower because engine, turbocharger, generator, emissions, and thermal systems cannot change output instantaneously.
- Both variants retain the XEV 9e-style single-speed electric reduction drive. There are no stepped gear changes, torque-converter events, or clutch shifts between 0 and 100 km/h.
- "Single-speed automatic" means a fixed-ratio reduction gear with electronic drive/reverse control; it is not a conventional multi-speed automatic transmission.
- Accelerator input requests wheel torque; it does not directly request an engine speed or generator band.
- Driver demand has priority while all BMS, inverter, motor, tyre-adhesion, ESP, thermal, electrical-isolation, and functional-safety limits remain authoritative.
- Maximum electronically permitted vehicle speed is 210 km/h for both RWD and AWD.
- Stationary engine operation is governed by the direction of energy, not by a bare prohibition. While the vehicle is stationary, the engine may run only when the energy it produces is leaving the vehicle or restoring the vehicle's own mobility. It must never run while parked to accumulate charge for later convenience.
- The concerns behind that rule: carbon monoxide and exhaust hazard in enclosed spaces; unattended running with nobody present; extended fixed-band operation outside the certified drive cycle; catalyst and thermal soak behaviour; noise nuisance; and the incentive to burn fuel to inflate an electric range figure.
- The last concern cannot arise while net energy flow is outward, which is why the direction of flow is the criterion. The sign of net flow at the port is measured, so the rule is verified rather than trusted.
- Three cases are therefore permitted: stationary in READY during a traffic halt in State 4 with the pack held flat; the State 6 recovery in Section 11, which starts the engine on a stopped vehicle after a validated refill; and a validated Section 17 export session while net flow remains outward.
- There is no unattended parked self-charging feature.

---

## 2. Proposed Production Variants

### RWD / "2WD"

- **Nominal planning kerb mass:** approximately 2,300 kg.
- **Battery:** 40 kWh usable, future high-power BYD/FinDreams Short Blade 2.0 phosphate cell preferred, subject to production supplier validation.
- **Traction hardware:** Mahindra's existing 210 kW-capable XEV 9e/9S-style rear 3-in-1 PMSM EDU.
- **Concept calibration:** 170 kW (approximately 231 hp) and up to 380 Nm.
- The 210 kW hardware capability is deliberately not exposed as the normal vehicle rating because the 40 kWh pack determines the safe repeatable EV-first power envelope.
- **Realistic preliminary 0–100 km/h target at nominal mass:** approximately 7.8–8.3 seconds.
- **Generator maximum HV DC output:** 105 kW, common with AWD.

### AWD

- **Nominal planning kerb mass:** approximately 2,500 kg.
- **Battery:** 50 kWh usable, future high-power BYD/FinDreams Short Blade 2.0 phosphate cell preferred, subject to production supplier validation.
- **Rear traction hardware:** the same 210 kW-capable / 380 Nm XEV 9e/9S-style 3-in-1 PMSM EDU as RWD, calibrated to 170 kW for this concept.
- **Front traction:** approximately 70 kW independently controlled EDU.
- **Combined target mechanical output:** approximately 240 kW (170 kW rear + 70 kW front), subject to battery, inverter, axle, tyre, thermal, and stability limits.
- **Realistic preliminary 0–100 km/h target at nominal mass:** approximately 6.1–6.7 seconds.
- **Generator maximum HV DC output:** 105 kW, common with RWD.

### Common Hardware

- 1.5 litre mStallion TGDi petrol range-extender engine.
- Reference engine rating: about 163 hp / 121.5 kW peak and 280 Nm, subject to the final generator-specific calibration and durability rating.
- 42 litre petrol tank.
- Common engine, generator, after-treatment, control strategy, service parts, and generator bands wherever packaging permits.
- Generator HV DC bands: nominally 30, 40, 55, 70, 90, and 105 kW.

### Concept-Frozen Architecture Decisions

- Purpose-adapted INGLO series REEV; no mechanical engine-to-wheel path.
- 400–465 V INGLO-class architecture is retained for EDU, inverter, charger, service, and supply-chain reuse; 1,000 V is outside this concept.
- Baseline packs remain 40 kWh usable RWD and 50 kWh usable AWD, provisionally mapped across a 96% window with 1% upper and 3% lower physical reserves.
- Healthy-SoC traction calibrations remain 170 kW RWD and 240 kW AWD.
- Both variants share a 270 kW-class short-duration HV electrical path sized for the AWD ~263 kW peak DC traction demand.
- Both variants use the concept-frozen 30/40/55/70/90/105 kW HV-DC bands.
- Maximum battery-terminal regenerative charge request: 30 kW RWD and 40 kW AWD.
- No supercapacitor, secondary surge battery, or physical auxiliary-source switching.
- The hidden reserve is never advertised, used to rescue a range claim, or released for routine traction.

### AWD Front-Motor Role and Load Sharing

- At maximum calibrated output, 170 kW rear + 70 kW front gives a nominal 71:29 power split. This is a hardware ceiling ratio, not a permanent torque command.
- Normal efficient cruising is rear-led; the front EDU may produce zero torque when traction and thermal conditions do not need it.
- One motor per axle provides fast front/rear allocation, not true independent left/right torque vectoring. Open differentials need brake-based traction control and/or validated electronic/mechanical locking differentials.

---

## 3. Power Reference Planes — Do Not Mix Them

Every signal and calibration must declare its reference plane:

1. Engine crankshaft mechanical power.
2. Generator AC electrical power.
3. Rectified generator HV DC-bus power.
4. Battery HV DC terminal power.
5. Inverter DC input power.
6. Motor shaft mechanical power.
7. Wheel mechanical power after reduction gear and driveline losses.

The generator bands in this specification mean usable HV DC-bus output after generator and rectifier losses, but before traction-inverter, motor, and reduction-gear losses.

A nominal traction-chain efficiency of 92% was used for preliminary road calculations. Production software must use speed-, torque-, voltage-, and temperature-dependent efficiency maps.

**Illustrative battery DC demand:**
- RWD calibrated 170 kW motor output: 170 / 0.92 + ~2 kW auxiliaries = **~187 kW DC**
- AWD calibrated 240 kW combined output: 240 / 0.92 + ~2 kW auxiliaries = **~263 kW DC**

---

## 4. Battery Design, Power, and Voltage Architecture

### Preferred Future Cell

- BYD/FinDreams Short Blade 2.0 high-power phosphate cell is the preferred design direction for PHEV/REEV duty: relatively small energy capacity, frequent shallow cycling, high traction pulse power, strong regenerative-charge acceptance, and repeated engine-generator charging.
- This follows an existing Mahindra supplier and architecture lineage.
- BYD officially announced Blade 2.0 in March 2026. Short Blade 2.0 is described as capable of very high charge/discharge rates, improved low-temperature behaviour, lower resistance, and improved thermal integration.
- Exact production chemistry remains an evidence boundary. Some launch reports describe an LMFP cathode and approximately 3.8 V nominal, while regulatory analysis of initial production vehicles still describes LFP.
- This specification deliberately says "Short Blade 2.0 high-power phosphate cell", not confirmed LMFP. LMFP, 3.8 V nominal, 8–10C charging, 16C pulse discharge, 190–210 Wh/kg, and 4,000-cycle figures remain supplier-confirmation items, not controller constants.

### Capacity Convention

- All customer-facing and control-law capacities are usable capacities.
- **RWD:** 40.0 kWh usable from approximately 41.67 kWh gross.
- **AWD:** 50.0 kWh usable from approximately 52.08 kWh gross.
- Both variants use a provisional 96% physical-SoC operating window: hidden upper reserve = 1%; hidden lower/bootstrap reserve = 3%; driver-usable window = physical 3% through physical 99%.

### C-Rate Summary

| Variant | Usable capacity | Peak DC demand | Peak C-rate |
|---------|----------------|----------------|-------------|
| RWD | 40 kWh | ~187 kW | ~4.67C |
| AWD | 50 kWh | ~263 kW | ~5.26C |
| XEV 9S BEV (reference) | 79 kWh | ~187 kW | ~2.37C |

The 40/50 kWh pair is the smallest round-kWh split at which both variants stay at or below 5.3C with minimal asymmetry. Both use a 108-series 1-parallel string, maintaining the 400 V bus without any step-up converter. A 50 kWh AWD at 45 kWh would require ~5.84C; at 33 kWh it reaches ~8C — thermal and life risk for LFP in Indian summer conditions with front EDU burst loads.

### Voltage Architecture

- Retains the production INGLO/XEV 9S approximately 400–465 V class rather than adopting 1,000 V.
- At illustrative 409.6 V nominal: RWD ~457 A, AWD ~642 A.
- Current rises as loaded pack voltage falls; cables, busbars, fuses, contactors, connectors, cooling, and inverter semiconductors must be sized at the minimum approved loaded voltage.
- Moving to 1,000 V would require a compatible motor/inverter, OBC, DC/DC, compressor, heater, contactors, insulation system, charging inlet, and either a newly wound high-voltage generator or a ~105 kW boost conversion stage — inconsistent with maximum INGLO reuse.

### Illustrative Cell-Count Cases

- If a 3.2 V Short Blade 2.0 cell retained 128s and a 96% usable window: approximately 101.7 Ah cells → 40 kWh usable; approximately 127.2 Ah → 50 kWh, both near 409.6 V nominal.
- If an LMFP cell at 3.8 V nominal: about 108s → 410.4 V nominal; approximately 101.5 Ah RWD or 126.9 Ah AWD. Both pack configs stay near the approximately 409.6 V reference.
- These are electrical target derivations, not proof that BYD offers those Ah variants.

### Battery Cooling

- Cell-to-pack cold plates or equivalently effective direct pack cooling.
- Full pulse power should normally be available below roughly 45°C. Progressive derating may begin around 45–50°C. 55–60°C is not a normal operating target; supplier limits govern.

### Generator Charge-Rate Context

- A theoretical full 105 kW sent only to the battery: ~2.63C for 40 kWh and ~2.10C for 50 kWh.
- Real generator charging is normally lower because traction, critical auxiliaries, comfort loads, conversion losses, and control reserve are supplied first.
- 40 kW genuine surplus is about 1.0C RWD or 0.8C AWD — moderate, repeated, shallow-cycle duty. This is more relevant to the REEV than a 1,500 kW public-charger headline.

---

## 5. Generator and Engine

### Concept-Frozen Output Bands

```
30 → 40 → 55 → 70 → 90 → 105 kW HV DC
```

Exact RPM, BMEP, boost, air/fuel ratio, spark, BSFC, emissions, noise, ramp rate, dwell, and thermal calibration remain supplier/dyno work.

### Public-Data Limit

- Public material confirms approximately 163 hp / 121.5 kW and 280 Nm for the 1.5 litre mStallion TGDi, but no Mahindra BSFC/BTE map is public.
- A comparable production 1.5 litre TGDi benchmark in general ICE application has demonstrated approximately 220 g/kWh minimum BSFC and approximately 38% peak BTE. That is a concept floor, not a claim about the mStallion.
- Jaecoo's Miller-cycle variant of a comparable 1.5 litre TGDi family, used in its Super Hybrid System series-parallel DHT, claims 44.5% peak BTE. That engine must serve both generator duty and direct mechanical wheel drive across a wide road-load range — a dual-duty calibration constraint that this concept's generator-only engine does not carry. A dedicated generator-only calibration, unconstrained by direct-drive traction duty, has a structural BTE advantage over a dual-duty series-parallel engine at equivalent displacement and technology level. The provisional BTE figures in this section reflect that advantage at the lower lean-capable bands while remaining conservative at the upper lambda-1 bands where full-load combustion constraints apply regardless of architecture.

### Provisional Band Operating Points (~89.7% mechanical-to-HV-DC efficiency)

| HV DC | Crankshaft power | RPM | Torque |
|-------|-----------------|-----|--------|
| 30 kW | ~33.4 kW | 1,500 | ~213 Nm |
| 40 kW | ~44.6 kW | 1,800 | ~237 Nm |
| 55 kW | ~61.3 kW | 2,300 | ~255 Nm |
| 70 kW | ~78.0 kW | 2,800 | ~266 Nm |
| 90 kW | ~100.3 kW | 3,500 | ~274 Nm |
| 105 kW | ~117.1 kW | 4,000 | ~280 Nm |

### Provisional Combustion Map

| HV DC | Normal lambda | Validated stretch | Indicative BTE |
|-------|--------------|-------------------|----------------|
| 30 kW | 1.20–1.40 | up to ~1.50 | 40.0–43.0% |
| 40 kW | 1.15–1.35 | up to ~1.45–1.55 | 39.0–42.0% |
| 55 kW | 1.05–1.20 | up to ~1.30 | 38.0–41.0% |
| 70 kW | 1.00–1.08 | up to ~1.15 | 36.5–39.0% |
| 90 kW | 1.00 | no lean claim | 34.0–36.5% |
| 105 kW | 1.00 | no lean claim | 32.0–35.0% |

Lambda cannot be fixed from electrical kW alone. The released target must be scheduled within the band using RPM, BMEP/torque, boost, intake temperature, altitude, fuel octane, knock margin, combustion stability, coolant/oil/turbo/catalyst temperatures, NOx-storage state, and requested battery recovery power.

### Dedicated mStallion Generator-Derivative Concept

This is not a proposal to simply run the production mStallion calibration at fixed throttle. It is a purpose-calibrated 1.5 L TGDi engine-generator derivative for a series REEV: the engine has no mechanical wheel connection, while the battery supplies all immediate traction transients.

The intended derivative: modest lean operation only where demonstrated stable and emissions compliant; expansion-biased Miller/Atkinson-like VVT; cooled EGR; direct injection; optimized fixed-ratio generator coupling; and lambda-one operation whenever high output, combustion stability, catalyst protection, or emissions control requires it.

This specification does NOT assume an active pre-chamber, spark-assisted compression ignition, lambda-2 across the map, or any unverified Mahindra combustion hardware. The revised provisional BTE figures in the combustion table are informed by the Jaecoo 44.5% dual-duty benchmark and the structural generator-only advantage argued in the Public-Data Limit section; they are not claims of a certified or dyno-confirmed outcome.

### Band-Selection and Transition Law

Select the smallest available band satisfying:
```
filteredSustainedTractionDcDemand
  + criticalAuxiliaries
  + requestedBatteryRecoveryPower
  + controlMarginKw
```

Step down progressively only after the demand, recovery, thermal, catalyst, and minimum-dwell reasons for the higher band have cleared.

### Fuel

- Tank nominal capacity: 42 litres.
- Petrol lower-heating-value reference: about 8.9 kWh/litre.
- Warnings should be time/energy based (predicted ten-minute and five-minute generator support remaining), not based only on three litres.

### Long-Term Derivative Direction — Purpose-Built Three-Cylinder Generator Engine

The baseline concept uses the production 1.5 litre mStallion TGDi, a four-cylinder unit with four cylinders of approximately 375 cc each. Mahindra also produces the 2.0 litre mStallion TGDi — a four-cylinder unit with four cylinders of approximately 500 cc each — which currently powers the petrol ICE powertrain variants.

The proposed derivative takes the individual cylinder architecture from the 2.0 litre unit and applies it to a purpose-built three-cylinder configuration: three cylinders of approximately 500 cc each, giving 1.5 litres total displacement. This is not a downsized version of the baseline 1.5 litre engine; it is a different geometry entirely — the same per-cylinder swept volume as the 2.0 litre mStallion, reduced to three cylinders rather than four.

The thermal efficiency argument follows directly from the geometry. A 375 cc cylinder has a higher surface-to-volume ratio than a 500 cc cylinder at the same stroke-to-bore proportion. More combustion chamber surface relative to working volume means more heat rejected to coolant per cycle, lower indicated mean effective pressure, and a harder path to high BTE. The 3-cylinder 1.5 litre derivative inherits the larger, better-proportioned combustion chamber from the 2.0 litre unit, recovers that heat loss advantage, and reduces total friction through fewer reciprocating components simultaneously.

On top of the geometric gain, the generator-only operating regime removes every calibration constraint that a road-following engine carries:

- No cold-start driveability requirement limiting the expansion ratio. Miller cycle valve timing can be pushed further than any production traction engine tolerates.
- No wide road-load sweep requiring combustion stability across partial throttle points. Every operating point is a pre-validated fixed island.
- No NVH requirement across the full RPM band. Active engine mounts and acoustic enclosure can be optimised for six discrete RPM targets rather than the continuous road range.
- Three-cylinder primary imbalance, which is unacceptable in a road-following NVH context, is managed by a balance shaft — a known solution — and does not constrain combustion design choices.

The combustion strategy for this derivative would combine: a dedicated high-expansion-ratio Miller cycle (not a mild VVT offset), split injection with spray-guided stratification, and cooled EGR throughout. Split injection divides the total fuel mass for each cycle into two timed events: an early injection during the intake or early compression stroke lays down a homogeneous base charge, and a late compression injection delivers a locally rich pilot zone near the spark plug. The plug ignites the rich zone reliably; the flame front propagates into the leaner surrounding charge, achieving stable combustion at bulk lambda values — 1.15–1.40 at the lower bands — that a single continuous injection cannot sustain without misfire. At the upper bands (90–105 kW) where lambda 1 and full load are required, a single well-timed early injection producing a fully homogeneous charge is used instead; stratification is neither needed nor desirable at maximum output.

Nissan's e-POWER programme is the clearest production demonstration of exactly this logic. The original Note e-POWER uses a 1.2 litre three-cylinder engine operating purely as a generator; the Qashqai and X-Trail e-POWER use a 1.5 litre three-cylinder VC-Turbo unit. In both cases Nissan deliberately chose a three-cylinder configuration for the generator role — fewer, larger cylinders at the target displacement — and attributes the BTE improvement explicitly to the fixed-point generator-only operating regime. Nissan publicly claims approximately 50% thermal efficiency for the 1.5 litre VC-Turbo in e-POWER generator duty, a figure that relies on the variable compression ratio technology unique to that engine and is not directly replicable here. The relevant comparison is the principle: Nissan confirmed in production that a purpose-designed three-cylinder generator engine operating at fixed points outperforms a general-duty engine of the same displacement on thermal efficiency. That is the same argument this derivative is making, without requiring variable compression ratio hardware.

The 2.0-litre-cylinder-architecture derivative described here, with split injection and spray-guided stratification added, represents a further step in the same direction. Where the Nissan VC-Turbo achieves its headline figure through variable compression ratio, this derivative reaches for the same efficiency region through the combination of larger per-cylinder geometry (from the 2.0L mStallion), a dedicated high-expansion-ratio Miller cycle, and split injection stratified combustion — all within existing combustion hardware categories that do not require a novel mechanism.

For the baseline concept, the revised BTE figures in the combustion table above are the appropriate provisional targets. The three-cylinder 500 cc derivative with split injection lean-burn Miller calibration would move those figures further — particularly at the 30–55 kW bands — and is the natural second-generation engine direction if the programme advances. That development requires a purpose-built engine programme and is outside the current concept scope. It is recorded here as a documented architectural opportunity grounded in existing Mahindra engine technology, not a speculative proposal from outside the family.

---

## 6. Preliminary Road-Load Model

**Reference assumptions:**
- Air density: 1.18 kg/m³
- Cd: 0.32
- Frontal area: ~2.79 m²
- CdA: ~0.89 m²
- Rolling-resistance coefficient: 0.0095
- Nominal DC-to-wheel traction efficiency: 92%
- Baseline auxiliary demand in old calculations: 2 kW (production must use measured dynamic auxiliary demand)

**Road power formula:**
```
Frolling = mass × gravity × Crr
Fgrade   = mass × gravity × sin(roadGrade)
Faero    = 0.5 × airDensity × CdA × relativeAirSpeed²
Pwheel   = (Frolling + Fgrade + Faero) × vehicleSpeed
```

**Mass load cases:**
- M0 agreed kerb: 2,300 kg RWD / 2,500 kg AWD
- M2 nominal touring: M0 + five 80 kg adults + 120 kg luggage = **~2,820 kg RWD / ~3,020 kg AWD**
- M3 seven-occupant structural check: M0 + seven 75 kg adults + 120 kg luggage = **~2,945 kg RWD / ~3,145 kg AWD** (crash/structure check, not routine range case)

**Calm/level at-wheel road power at M0 2,300/2,500 kg kerb:**

| Speed | RWD (kW) | AWD (kW) |
|-------|----------|----------|
| 60 km/h | ~6.0 | ~6.3 |
| 80 km/h | ~10.5 | ~10.9 |
| 95 km/h | ~15.3 | ~15.8 |
| 100 km/h | ~17.2 | ~17.7 |
| 120 km/h | ~26.6 | ~27.2 |
| 150 km/h | ~46.9 | ~47.7 |
| 180 km/h | ~76.4 | ~77.3 |
| 200 km/h | ~102.0 | ~103.0 |
| 210 km/h | ~116.7 | ~117.8 |

**Calm/level DC-bus road demand at M2 nominal touring masses (~2,820/~3,020 kg):**

| Speed | RWD (kW) | AWD (kW) |
|-------|----------|----------|
| 60 km/h | ~9.4 | ~9.8 |
| 80 km/h | ~14.7 | ~15.1 |
| 95 km/h | ~20.1 | ~20.7 |
| 100 km/h | ~22.2 | ~22.8 |
| 120 km/h | ~32.9 | ~33.6 |
| 150 km/h | ~55.5 | ~56.4 |
| 180 km/h | ~88.0 | ~89.0 |
| 200 km/h | ~116.2 | ~117.3 |
| 210 km/h | ~132.4 | ~133.6 |

**Approximate level-road generator-only speed intersections (M2, 2 kW aux):**

| Band | RWD | AWD |
|------|-----|-----|
| 30 kW | ~115 km/h | ~114 km/h |
| 40 kW | ~131 km/h | ~130 km/h |
| 55 kW | ~149 km/h | ~148 km/h |
| 70 kW | ~165 km/h | ~164 km/h |
| 90 kW | ~181 km/h | ~180 km/h |
| 105 kW | ~192 km/h | ~192 km/h |

These are not speed-selected generator commands. Measured demand, battery recovery, FoS, wind/grade, and thermal state select the smallest safe band.

---

## 7. Fundamental Power-Allocation Law

At every control interval:

```
generatorDcAvailable
  - criticalAuxiliaryPower
  - permittedComfortAuxiliaryPower
  - batteryChargeTarget
  - controlReserve
  = generatorPowerAvailableForTraction

batteryDcAvailable =
  BMS-disclosed charge/discharge capability at that instant

requestedTractionDcPower =
  driverWheelTorqueRequest converted through current efficiency maps
```

The controller allocates source power without exceeding: BMS power limits; generator available/derated output; HV-bus and inverter current limits; motor torque and speed envelopes; axle, tyre, ESP, and vehicle-stability limits; thermal and functional-safety constraints.

Generator output first meets current traction and auxiliary requirements. Only actual surplus may charge the battery. The controller must never count the same generator kW simultaneously as traction power and charge power.

### Braking and Generator-Dwell Rule

A short braking event does not command normal generator band hunting. Preserve the mechanical engine/generator band through a calibrated transient hold (~10–15 s) so an immediate re-acceleration can use the already available band.

During braking, grant regeneration only up to the 30 kW RWD / 40 kW AWD cap and the BMS-approved charge power. The generator rectifier must electrically unload fast enough that:

```
generatorToBattery + regenerativeChargePower <= BMS availableChargePower
```

This safety load-shed overrides normal dwell and prevents HV-link overvoltage. Mechanical-band dwell never authorizes uncontrolled electrical injection.

---

## 8. Driver Response — Universal Battery-First Rule

- The battery and inverter answer an accelerator request immediately within their current safe-operating envelope.
- The driver must not wait for the engine or turbocharger to respond.
- Above the active mode's generator-start boundary (15% Economy; 20% Sport/Hills), a fresh propulsion state is pure EV: the generator remains off and hard accelerator input must not start it.
- The advertised healthy-SoC output must therefore be independently supportable by the battery. Generator capacity is not included when calibrating the 170 kW RWD or 240 kW AWD healthy-SoC rating.
- If the BMS cannot support the calibrated request because of temperature, voltage sag, ageing, imbalance, pulse history, or a fault, motor torque is derated immediately. A stopped generator is not used to conceal an undersized or temporarily constrained battery.
- The vehicle must remain predictable: no delayed torque step that surprises occupants after the battery has already accelerated the vehicle.

---

## 9. Drive Modes and Nominal SoC Windows

SoC boundaries below are customer-facing mode anchors, not primary control quantities and not substitutes for dynamic BMS limits. Internally the controller works in measured/predicted kWh, kW, time, weakest-cell voltage, and confidence.

**Nominal beginning-of-life usable-energy equivalents:**
- RWD 40 kWh: 8%=3.2, 10%=4.0, 15%=6.0, 20%=8.0, 30%=12.0, 35%=14.0 kWh
- AWD 50 kWh: 8%=4.0, 10%=5.0, 15%=7.5, 20%=10.0, 30%=15.0, 35%=17.5 kWh

### Onboard-Only Authority

Safety boundaries shall never depend on navigation, weather, cloud, charger availability, destination, or any other external feed. External data may advise the driver or improve a non-authoritative range display, but it may never delay generator start, reduce reserve, increase battery power, postpone Turtle, or override weakest-cell protection.

### Conservative Onboard Energy Calculation

```
usableEnergyToFloor =
  min(coulombCountedEnergy,
      cellModelEnergy,
      weakestCellSagLimitedEnergy)
  - measurementUncertainty
  - criticalAuxiliaryReserve
  - transitionReserve
  - emergencyMobilityReserve
```

Generator start occurs when: `usableEnergyToFloor <= requiredProtectedEnergy`

### Drive Mode Calibration Summary

| Parameter | Economy | Sport | Hills/Terrain |
|-----------|---------|-------|---------------|
| Generator start | 15% | 20% | 20% |
| Generator stop | 30% | 35% | 35% |
| Demand filter | ~1.5 s | ~0.35 s | ~2.5 s |
| Generator ramp | ~4–5 s | ~2–3 s | ~4–5 s |
| Downshift dwell | ~16 s | ~8 s | ~24 s |
| Recovery surplus | ~6 kW | ~12 kW | ~9 kW |

**Hills/Terrain** accelerator behaviour remains Economy-like (smooth, steady, progressive). Generator behaviour is calmer still — the longer downshift dwell and slower filter ride through rolling terrain crests that would otherwise provoke band hunting. During descent, the generator should normally stop to preserve regenerative acceptance. Uses onboard grade measurement/estimation from IMU, wheel acceleration, motor torque, speed, and effective mass.

### Sport Low-Energy Handover

At the BMS early-intervention boundary (nominally ~10% displayed SoC, rises dynamically with cold/aged/imbalanced pack), Sport power management is forced to Economy. Hills/Terrain is deliberately excluded — its power management is already Economy-like, so forcing Economy would disable descent regeneration priority and proactive front-axle preparation at the point they matter most.

The handover boundary is the BMS early-intervention flag, not a fixed percentage. It fires on lost battery evidence at any SoC, matching the rule that a new Sport request is refused without trusted evidence.

**Support windows:**
- Sport support window: 20% → ~10% dynamic early intervention
- Hills/Terrain window: 20% → ~8% dynamic Turtle target
- Economy support window: 15% → ~8% dynamic Turtle target

### Safe Moving Mode Transitions

- Economy → Sport: requires at least 20% displayed SoC plus sufficient onboard safe energy, BMS pulse power, cell voltage/temperature/imbalance margin, and no limiting fault.
- Hills/Terrain → Sport: same 20% and BMS capability gate.
- Economy → Hills/Terrain: always allowed.
- Sport → Economy, Sport → Hills/Terrain, Hills/Terrain → Economy: always allowed.
- Every transition uses hysteresis and bumpless transfer.

---

## 10. Progressive Generator-Band Law

Determine requested generator output from:
```
filteredSustainedTractionDemand
  + measuredAuxiliaryReservation
  + desiredBatteryRecoveryPower
  + controlMargin
```

Select the smallest band capable of meeting that request. Economy/Hills favour slower one-band progression; Sport may progress faster or skip a band when justified by sustained power.

**Downshift:** require filtered demand to remain below the lower threshold for the mode-specific dwell. A fixed maximum-band latch must end when the energy, thermal, emissions, driver-demand, or battery-recovery reason that created it no longer exists.

---

## 11. Operating States

### State 1 — Pure Battery EV

Generator off. Battery supplies traction and auxiliaries. Normal state above the mode's generator-start boundary. A hard pedal event cannot start the generator in this state.

### State 2 — Battery Power Protection

Generator remains off above the active start boundary. If current battery capability is below requested traction power because of temperature, ageing, voltage sag, imbalance, or another BMS constraint, requested motor torque is reduced smoothly. This state prevents generator startup from being used merely to improve a headline acceleration number.

### State 3 — Combined Charge-Sustaining Operation

Enter around 15% Economy or 20% Sport/Hills. Generator supplies sustained demand according to the band law. Battery supplies transients and any deficit. Surplus generator power may restore the battery to 30% Economy or 35% Sport/Hills. Regeneration remains active subject to charge acceptance.

### State 4 — Generator-Limited / Net-Zero-Battery Operation

Enter when dynamic battery energy or discharge-power reserve can no longer support continued traction deficits safely. Battery charging target becomes zero. Critical auxiliaries are reserved first. Generator power remaining for traction determines available wheel torque.

Near the dynamic Turtle-energy target, a battery pulse is allowed only when all of the following are positive: BMS discharge power, energy above the protected Turtle target, predicted weakest-cell voltage margin, and the calibrated generator-ramp FoS.

There is no hard 172 km/h generator-only limit. Sustainable speed varies continuously with generator derating, auxiliaries, wind, grade, mass, rolling resistance, air density, temperature, and drivetrain efficiency.

### State 5 — Battery Turtle / Generator Unavailable

Normal entry invariant:
```
remaining usable energy <= dynamic Turtle-energy target
AND guaranteed generator power == 0
AND reliably usable fuel is exhausted
```

Exceptional entry is permitted for generator, fuel-pressure, thermal, or emissions faults, or battery limits that require earlier protection.

**Provisional beginning-of-life Turtle-energy targets:**
- RWD 40 kWh: ~3.0–3.2 kWh, normally around 7.5–8% displayed
- AWD 50 kWh: ~3.5–4.0 kWh, normally around 7–8% displayed

```
turtleEnergyTarget =
  max(conservativeEnergyForNominal15Km,
      conservativeEnergyForAdverse10Km,
      criticalAuxiliaryEnergy + transitionReserve
        + measurementUncertainty + FoS)
```

Maximum Turtle speed: no more than 60 km/h (ceiling, not a guarantee). Shed comfort loads progressively but retain steering, brakes, hazard lights, lighting, communication, control modules, and mandatory thermal protection.

### State 6 — Refuelled Turtle / Fuel-Recovery

This state applies whenever a vehicle has entered battery-only Turtle and is then refuelled, at any point between dynamic Turtle entry and displayed 0%.

**Entry requirements:**
- Generator must never start while a fuel nozzle may be connected.
- Identify a real refill using onboard evidence only: filtered before/after tank level, a sustained positive fuel delta, fuel-door history, slosh stabilization, tank-pressure/EVAP plausibility, rail-pressure buildup, and leak/fault status.

When a valid refill is confirmed: alert "Fuel detected - generator starting for Turtle recovery"; start at 30 kW after required warm-up/catalyst checks; use 40 kW only when all conditions permit. Keep Turtle active with a hard 60 km/h ceiling.

**Power priority in State 6:**
```
turtleTractionPower =
  guaranteedGeneratorPower
  - criticalAuxiliaryPower
  - minimumBatteryChargePower
  - controlFoS
```

**Recovery exit:** requires energy safely above the nominal 10% boundary:
```
turtleExitEnergy =
  nominal10PercentEnergy
  + transientReserve
  + measurementUncertainty
  + FoS
```

**Depleted-pack bootstrap and V2L rescue:**  
At displayed 0%, the protected physical 3% reserve normally supplies BMS, pre-charge, pumps/controllers, generator-inverter startup, and the brief PMSG motoring pulse needed to crank the engine after a valid refill.

If that reserve cannot provide start power but the BMS confirms every cell remains healthy and inside a validated recoverable-voltage window, the sole vehicle-to-vehicle rescue path is:
```
donor EV with V2L
  → 230 V AC
  → approved protected portable EVSE/rescue cable
  → normal REEV AC charge port and onboard charger
  → REEV traction battery
```

This vehicle qualifies as a donor. Under the Section 17 export rules it can complete a rescue its own pack alone could not, by autonomously engaging its generator once donor SoC reaches its mode boundary and fuel is validated. A charge-only or battery-only donor structurally cannot do this.

At a nominal 3.3 kW V2L supply: 10 minutes provides ~0.55 kWh, 20 minutes ~1.1 kWh before losses. Start release is power/cell/FoS based, not time based.

### Normative State-Transition Summary

| From | To | Condition |
|------|----|-----------|
| State 1 | State 2 | Healthy-SoC battery capability constrains requested torque |
| State 1/2 | State 3 | Protected-energy logic reaches 15%/20% anchor or earlier dynamic boundary |
| State 3 | State 1 | Protected energy/pulse reserve restored; 30%/35% target reached; generator min run/dwell and emissions-safe shutdown satisfied |
| State 3 | State 4 | Sustainable battery deficit or power reserve cannot be guaranteed while usable generator power remains |
| State 4 | State 3 | Battery transient/recovery capability safely restored |
| State 4 | State 5 | Generator power unavailable AND displayed usable energy reaches Turtle boundary |
| State 5 | State 6 | Valid refill AND complete start interlocks, or approved V2L rescue |
| State 6 | State 3/Economy | Dynamic exit energy above nominal 10% met |

All transitions are bumpless: preserve current safe wheel torque initially, then apply calibrated ramps. No state transition alone opens contactors, applies the parking brake, or creates an abrupt positive/negative torque step.

### Battery-Function Invariant

The generator may operate only while the traction battery, BMS, contactors, pre-charge circuit, isolation monitoring, and HV safety controls retain their minimum validated functionality. Battery-isolated generator-only propulsion is rejected. Do not add an independent under-bonnet generator-start port, dedicated engine starter, second traction battery, direct donor-to-HV connection, or special generator-only driving state.

---

## 12. Dynamic Battery-Reserve Alert — No Hard 10.6% Law

Calculate:
```
batteryDeficit =
  tractionDcDemand + auxiliaries - generatorDcAvailable

timeToDynamicSafetyFloor =
  usableBatteryEnergyAboveDynamicFloor / max(batteryDeficit, epsilon)
```

Trigger the high-power-reserve warning/transition when any applies:
- `availableBatteryPower < predictedBatteryDemand + safetyMargin`
- `timeToDynamicSafetyFloor < generator/speed-transition time + required reserve time`
- predicted weakest-cell voltage crosses its safe loaded limit
- temperature, current, SoH, isolation, or imbalance requires derating
- generator is unavailable or cannot reach the predicted required output

Suggested first warning:  
*"High-power battery support nearing its limit. Vehicle performance is adapting to available generator power."*

---

## 13. Dynamic Generator-Limited Speed — No Hard 172 km/h Law

172 km/h was a useful nominal result, not a control constant.

**Preliminary equilibrium checks with 105 kW output (M2, 2 kW aux):**

| Condition | Equilibrium speed |
|-----------|-----------------|
| Calm/level | ~192 km/h |
| 20 km/h headwind | ~180 km/h |
| 40 km/h headwind | ~168 km/h |
| 1% grade + 20 km/h headwind | ~171 km/h |
| 1% grade + 40 km/h headwind | ~159 km/h |
| 2% grade + 20 km/h headwind | ~161 km/h |
| 2% grade + 40 km/h headwind | ~151 km/h |

A control reserve lowers those values further. The production controller should calculate available wheel torque/power and cap requested torque, not command a fixed road speed.

**Dynamic transition sequence:**
1. Predict battery support exhaustion.
2. Confirm available generator output and auxiliary demand.
3. Set battery charge target to zero.
4. Reserve critical auxiliary power.
5. Calculate available traction/wheel power.
6. Reduce positive motor torque smoothly; do not command abrupt braking.
7. Recalculate continuously as road load and derating change.

---

## 14. Auxiliary Power

Auxiliary load cannot be a fixed 2 kW:
- Critical pumps/controllers: ~0.5–1.5 kW
- Lighting and low-voltage systems: ~0.3–1.0 kW
- Cabin AC: ~1.5–5 kW
- Battery cooling: ~1–4 kW
- Engine-generator cooling: ~0.5–2 kW
- Combined hot-weather thermal event: ~7–10 kW

### Allocation Priority
1. Safety-critical controls, steering/braking support, required cooling.
2. Traction required to maintain safe road operation.
3. Legally/safety-required lighting and communications.
4. Comfort auxiliaries.
5. Battery charging.

### Auxiliary Shedding Matrix

| Class | Description | Shed policy |
|-------|-------------|-------------|
| A0 | Brake/steering, BMS/VCU, contactors/HVIL, hazard lights, e-call, mandatory pumps | Never shed; reduce propulsion first |
| A1 | Battery, inverter, motor, generator, engine, catalyst, rectifier cooling | Never switch off; request torque/generator derating |
| A2 | Required exterior lighting, indicators, wipers, cluster, diagnostics | Reduce to legal/safe minimum |
| A3 | Cabin compressor, heater, rear-zone HVAC, seat heating | Progressively cap in State 4; strongly cap in State 5/6 |
| A4 | High-power audio, accessory outlets, nonessential USB, ambient lighting | Shed first when energy/power reserve constrained |
| A5 | Battery recovery charging surplus | Zero in State 4 when necessary |

Re-enable in reverse order only after available source power exceeds measured active load plus recovery target plus calibrated hysteresis for a minimum dwell. Do not cycle HVAC/convenience loads rapidly.

---

## 15. Regenerative and Friction Braking

- Regen request cannot be a fixed multiplier of brake pressure.
- Blend driver deceleration request across front/rear motors and friction brakes using speed, axle load, tyre slip, yaw, SoC, cell charge power, temperature, motor speed, inverter limits, and stability requests.
- Battery-terminal regeneration capped at 30 kW RWD and 40 kW AWD before applying the lower real-time BMS charge-power limit.
- During a short brake event with an active generator, preserve the mechanical generator band for the transient hold but reduce rectifier electrical load as required to keep combined generator charging plus regen within BMS-approved charge power.
- Friction brakes must independently satisfy stopping, fade, failure, and homologation requirements.
- At high SoC, low temperature, high temperature, or a BMS charge limit, friction braking must replace unavailable regen without changing pedal feel.

---

## 16. Low-Energy Warnings and Safe Degradation

### Warning Sequence

1. Mode boundary reached: generator starts normally.
2. Dynamic battery traction reserve declining: generator support and performance adaptation shown.
3. Battery net-discharge capability exhausted: generator-limited operation.
4. Generator runtime/usable fuel critical: predictive ten/five-minute alert, optional navigation assistance to fuel/charging location (advisory only).
5. Around 10% with generator unavailable: progressive Turtle preparation; full Turtle at the dynamic energy target, nominally around 7–8%.
6. Displayed usable 0% approaching: controlled pull-over request.
7. Petrol added at 0%: refuelled Turtle recovery, 60 km/h maximum, low acceleration, generator charging toward safe exit above 10%.

### HMI Information Model

Always distinguish three quantities: displayed usable battery SoC, available high-power traction/boost reserve, and predicted usable generator energy/runtime. Never combine them into one ambiguous range gauge.

| Severity | Examples |
|----------|---------|
| INFO | Generator start/stop, current mode, active charging/recovery |
| ADVISORY | Sport unavailable, comfort load limited, generator band/thermal derating |
| WARNING | High-power reserve declining, generator runtime ~10 minutes, Turtle entered |
| CRITICAL | ~5 minutes usable generator runtime, safe pull-over request, isolation/thermal danger |

**Required messages:**
- "Sport unavailable - minimum 20% battery reserve required."
- "High-power battery support nearing its limit. Vehicle performance is adapting to available generator power."
- "Fuel detected - generator starting for Turtle recovery."
- "Fuel insufficient to restore normal traction - emergency mobility only."
- "Turtle recovery complete - Economy mode active."

---

## 17. SoC Display, Physical Reserve, and Charging

```
physicalSoC = 3% + (displayedSoC × 96%)
```

| Displayed SoC | Physical SoC |
|---------------|-------------|
| 100% | ~99% |
| 35% | ~36.6% |
| 30% | ~31.8% |
| 20% | ~22.2% |
| 15% | ~17.4% |
| 10% | ~12.6% |
| 8% | ~10.68% |
| 0% | ~3% |

The upper 1% protects high-voltage/cell life and provides manufacturing and estimation margin. The lower 3% protects bootstrap/restart capability and is not part of Turtle range.

### Charging

- Home charging through a 15/16 A outlet or approved EVSE.
- The onboard charger (OBC) is bidirectional. The same physical charge port accepts inbound AC charging in one session and delivers 230 V AC export in another. No second port or separate export inverter is required. This retains the V2L capability already present on the INGLO platform (XEV 9e) without additional hardware development.
- Reuse the INGLO-class 11.2 kW three-phase onboard AC charger where supply and packaging permit; automatically limit to ~3.0–3.2 kW battery input from a 230 V 15/16 A domestic source after losses.
- Public DC fast charging supported.
- No unattended petrol-engine self-charging feature.
- Inbound charging and export are mutually exclusive on the same port. The OBC current direction is set at session start and is not changed mid-session.

### V2L Export — Operating Modes

Two distinct export operating modes share the same port, the same OBC, and the same six mandatory interlocks. They differ only in floor condition and generator engagement rule.

| | Home Battery Backup | Emergency Vehicle Rescue |
|---|---|---|
| Trigger | Grid loss detected while vehicle is connected and at healthy SoC | Stranded EV needs energy to enable generator start |
| Generator | Never starts | Starts automatically when donor SoC reaches the active mode boundary and fuel is validated |
| Export floor | 15% displayed SoC (Economy generator-start gate) | Turtle boundary plus protected physical reserve |
| Occupant required | No — session may run unattended once established | Yes — driver acknowledges exhaust and enclosed-space warning before generator starts |
| Session end | 15% SoC reached, or grid restored, or any interlock lost | Recipient pack reaches generator-start capability, or donor floor reached, or any interlock lost |

**Home Battery Backup detail:** When the vehicle is connected and the household grid drops, the OBC reverses current direction and the vehicle presents as a 230 V / 15 A AC island supply. The battery discharges at up to 3.3 kW to supply connected household loads. The generator never starts. The session stops automatically when displayed SoC reaches 15% — the same boundary used as the Economy generator-start gate — leaving the pack in a state from which normal generator-assisted driving can resume. At a typical Indian household load of 1.0–1.5 kW (refrigerator, fans, lights, phone charging), a 40 kWh RWD pack charged to 100% provides approximately 17–25 hours of backup before the 15% floor. At full 3.3 kW export the available window is approximately 10 hours. Because the generator does not start, there is no exhaust, no thermal soak outside a certified drive pattern, no fuel consumption, and no requirement for the occupant to be present. This is a bounded UPS function drawing from a known energy store — a category regulators and consumers already understand.

**Emergency Vehicle Rescue detail:** Described in Section 11 (State 6 bootstrap) and repeated here for completeness. A stranded REEV at displayed 0% that cannot self-start after a valid refill can receive energy from a donor vehicle's V2L port via an approved portable EVSE cable to the normal AC charge port. Start release is battery pulse-power and factor-of-safety based, not time or SoC based.

### V2L Export — Session and Interlocks

- Export is nominally 3.3 kW single-phase at 15/16 A.
- **Six mandatory interlocks** re-evaluated every frame: charge-port flap open and connector latched; external demand present; measured net current in export direction; vehicle stationary; transmission in Park; parking brake engaged.
- Losing any single interlock ends the session through a controlled ramp.
- Output must present neutral-earth bonding so a portable EVSE's earth-continuity and residual-current checks pass.
- Island operation only. Export and inbound charging are mutually exclusive on the same port and cannot overlap mid-session.

### V2L Export — Generator Engagement (Emergency Rescue Mode Only)

- Generator engagement applies to emergency rescue mode only. It never applies to home battery backup mode.
- Engagement is automatic and based on measured donor state, never on a declared purpose.
- The control system starts the engine once donor SoC falls to its active mode generator-start boundary and validated fuel sits above reserve, after the driver acknowledges the exhaust and enclosed-space warning.
- Net outward flow is the continuous licence to run. If external demand collapses so net flow is no longer outward for a calibrated dwell, the engine stops. This single rule subsumes completion handling.
- Donor floors are absolute. Export must never reach the Turtle boundary or the protected physical reserve, and must leave the donor able to reach a fuel station or charger independently.

---

## 18. Preliminary Range and Homologation Context

These are model estimates, not certified claims. Calibrated against the XEV 9S ground truth: 79 kWh / 679 km MIDC claimed (116.3 Wh/km effective), calibration factor ~0.8495 accounting for regenerative braking recovery and MIDC speed distribution.

**RWD 40 kWh usable:**
- MIDC P1+P2 battery-only estimate: **~340 km** (calibrated)
- Real city with AC to the Economy boundary: ~200–260 km
- 95–100 km/h battery-only highway: ~175–210 km

**AWD 50 kWh usable:**
- MIDC P1+P2 battery-only estimate: **~408 km** (calibrated)
- Real city with AC to the Economy boundary: ~240–310 km
- 95–100 km/h battery-only highway: ~220–260 km

Both variants comfortably exceed the proposed 270 km policy threshold. This headroom is a consequence of correct pack sizing (driven by C-rate requirements), not a design target.

### Policy Homologation Requirements

For the hypothetical over-4-metre REEV GST class requiring ≥270 km certified all-electric MIDC range:
- The generator is prohibited from starting throughout the charge-depleting all-electric test.
- The 15%/20% boundaries are generator-management anchors; battery Turtle begins at its dynamic energy target, provisionally ~7–8%, and continues to displayed 0%.
- The protected physical 3% remains excluded from measured/advertised range.
- Programme targets should be at least 270 km certified RWD so tyres, options, temperature, production variation, battery ageing rules, and conformity-of-production margin do not defeat eligibility.
- If coast-down/cycle testing cannot guarantee the RWD margin, consider approximately 42–43 kWh usable rather than manipulating hidden reserves or exposing safety energy.

---

## 19. Purpose-Built Mass Planning Envelopes

**XEV 9S battery substitution:**
- 79 kWh INGLO LFP pack at ~141.5 Wh/kg: approximately 550–560 kg.
- RWD 40 kWh Short Blade 2.0 high-power pack planning expectation: ~240–270 kg.
- AWD 50 kWh Short Blade 2.0 high-power pack planning expectation: ~295–330 kg.
- Indicative battery-system saving vs 79 kWh: RWD ~290–320 kg; AWD ~230–265 kg.

**Range-extender mass expectation:**
- 1.5T engine, fluids, and ancillaries: ~115–135 kg
- 105 kW generator and coupling: ~45–65 kg
- Rectifier/generator inverter: ~10–15 kg
- Intake, turbo plumbing, and mounts: ~12–20 kg
- Exhaust and after-treatment: ~22–35 kg
- Dedicated cooling hardware: ~20–30 kg
- Shields, isolation, and NVH treatment: ~12–20 kg
- **Deeply integrated engine-generator system target: ~230–290 kg**
- 42L tank, fuel, pump, lines, and vapour system: ~40–50 kg
- Additional thermal/structural/NVH integration: ~15–30 kg

**Grounded concept mass outcome:**

| Component | RWD Δ kg | AWD Δ kg |
|-----------|----------|----------|
| Remove: 79 kWh Blade 1.0 pack | −550 to −560 | −550 to −560 |
| Add: RWD 40 kWh Short Blade 2.0 | +240 to +270 | — |
| Add: AWD 50 kWh Short Blade 2.0 | — | +295 to +330 |
| Add: Engine-generator/fuel system | +285 to +370 | +285 to +370 |
| Add: AWD front EDU + subframe + HV | — | +145 to +195 |
| BIW/floor optimisation opportunity | −20 to −70 | −20 to −70 |
| **Planning range vs XEV 9S 2,240 kg** | **~2,175–2,330 kg** | **~2,365–2,545 kg** |
| **Programme kerb target** | **2,300 kg** | **2,500 kg** |

**Mass and load-case register:**
- M0 agreed kerb: 2,300 kg RWD / 2,500 kg AWD
- M2 nominal touring: ~2,820 kg RWD / ~3,020 kg AWD (M0 + five 80 kg adults + 120 kg luggage)
- M3 seven-occupant structural check: ~2,945 kg RWD / ~3,145 kg AWD (M0 + seven 75 kg adults + 120 kg luggage; crash/structure check, not routine payload)

**AWD delta over equivalent RWD:**
- Additional 10 kWh battery: ~55–65 kg
- Front 70 kW EDU/inverter: ~55–70 kg
- Half-shafts and mounts: ~12–18 kg
- Front structure/subframe adaptation: ~8–15 kg
- Additional HV and cooling hardware: ~8–15 kg
- Brake/suspension/tyre differences: ~5–12 kg
- **Realistic total delta: ~145–195 kg**

---

## 20. How to Achieve the Mass Targets Without Compromising Safety

**Optimization range:**
- Conservative recoverable mass: approximately 70–120 kg
- Aggressive recoverable mass: approximately 120–170 kg
- Larger claims usually double-count battery right-sizing or trade away safety, durability, NVH, cost, repairability, or production tolerance.

**Key principles:**
- Use a purpose-built REEV floor; do not retain redundant ICE and BEV load paths.
- Avoid carrying an empty 79 kWh enclosure around a 40/50 kWh cell set.
- Generator-specific engine calibration allows removal of traction gearbox, broad transient torque hardware, starter/alternator duplication where safely replaced, and unnecessary engine ancillaries.
- Use flow-formed 19-inch wheels as the mass/range baseline. Large 20-inch wheels must carry an explicit unsprung-mass, range, ride, and cost penalty.
- Retain the panoramic roof as a product requirement and account for its mass honestly; offset it through surrounding roof-frame optimization, not weaker rollover structure.
- Avoid acoustic over-treatment: apply targeted barriers, local damping, active noise control where appropriate.

**Safety boundaries for mass reduction:**
- No reduction to occupant cage, roof-crush, side pole, battery intrusion, seat anchorage, third-row survival space, fuel-system shielding, braking, steering, thermal propagation, or electrical isolation merely to meet a marketing mass.
- Five adults at 80 kg plus 120 kg luggage produce nominal road masses near 2,820 kg RWD and 3,020 kg AWD when kerb mass already includes fuel.
- Exact load cases must be frozen rather than mixing conventions.

---

## 21. Commercial and Regulatory Context

*(Not a control requirement)*

**Manufacturing cost envelope (mature, localised production ~75,000–100,000 units/year):**
- Indicative RWD loaded factory cost: ~Rs 17.0–18.5 lakh
- Indicative AWD loaded factory cost: ~Rs 18.5–20.5 lakh

Manufacturing cost must never be presented as retail price.

**Current-tax reference (August 2026 planning basis):**
A >4 m hybrid/REEV is assumed to attract 40% GST, while a pure EV attracts 5%.

At 40% GST with sustainable pre-GST revenue of ~Rs 21.5–23.0 lakh RWD and ~Rs 23.0–25.0 lakh AWD:
- RWD: ~Rs 30.1–32.2 lakh ex-showroom / ~Rs 35.6–38.2 lakh Mumbai on-road
- AWD: ~Rs 32.2–35.0 lakh ex-showroom / ~Rs 38.2–41.5 lakh Mumbai on-road

**Hypothetical capability-based REEV GST policy:**

| Class | Proposed GST | Generator displacement | Min certified MIDC EV range |
|-------|-------------|----------------------|----------------------------|
| Above 4 metres | 18% | Above 1,000 cc, below 1,500 cc | At least 270 km |
| Sub-4 metres | 9% | 500 cc to below 1,000 cc | At least 210 km |
| At/above 1,500 cc | 40% | 1,500 cc and above | No preferential rate |

The fiscal case: GST forgone is ~Rs 0.86 lakh net delta per vehicle (Rs 4.73L forgone minus Rs 3.87L still collected at 18%). At 18% GST, the engineering target is 270 km (>4m) / 210 km (sub-4m) with a genuine 42–43 kWh fallback only if evidence requires.

**Pricing under hypothetical 18% REEV GST:**
- RWD: ~Rs 25.5–27.1 lakh ex-showroom / ~Rs 30–32 lakh Mumbai on-road
- AWD: ~Rs 27.1–29.5 lakh ex-showroom / ~Rs 32–35 lakh Mumbai on-road

**Illustrative August 2026 energy-price basis:**
- Petrol: Rs 110.93/litre
- Home electricity: Rs 8.50/kWh effective marginal energy cost
- Commercial highway DC charging: Rs 22/kWh
- Generator yield: 3.0 kWh usable HV DC/litre as base case (range: 2.7–3.1 kWh/litre)

---

## 22. Safety, Software, and Validation Requirements

Production development requires at minimum:
- ISO 26262 HARA, safety goals, ASIL decomposition, and safety case.
- Redundant/plausibility-checked accelerator, brake, speed, current, voltage, temperature, fuel-pressure, generator-output, and torque signals.
- Watchdogs, alive counters, CAN authentication/integrity strategy, timeout behaviour, and defined degraded modes.
- Torque arbitration with ESP, ABS, brake control, BMS, inverter, motor, generator, thermal, charging, and ADAS controllers.
- SIL, MIL, HIL, dyno, climatic chamber, altitude, hot soak, cold start, corrosion, vibration, EMC, crash, abuse, and vehicle durability tests.
- Generator emissions certification across starts, ramps, bands, altitude, ambient conditions, ageing, catalyst light-off, and abrupt shutdowns.
- Coast-down testing to replace assumed CdA/Crr.
- Measured motor/inverter/generator/rectifier/gear efficiency maps.
- Tyre speed/load ratings suitable for 210 km/h and final GVWR.

---

## 22A. First-Principles Calculation Annex

**M2 grade sensitivity — additional HV-DC traction power per 1% grade:**

| Speed | RWD (kW) | AWD (kW) |
|-------|----------|----------|
| 60 km/h | ~5.1 | ~5.5 |
| 100 km/h | ~8.6 | ~9.2 |
| 120 km/h | ~10.3 | ~11.0 |
| 150 km/h | ~12.9 | ~13.8 |
| 180 km/h | ~15.4 | ~16.5 |

**Additional HV-DC demand from headwind at 100/120/150/180 km/h (approx.):**
- 20 km/h headwind: 5.4 / 7.6 / 11.7 / 16.7 kW
- 40 km/h headwind: 11.7 / 16.4 / 25.0 / 35.2 kW

**Displayed-window energy at beginning of life:**
- RWD: each displayed 1% is 0.40 kWh; Economy 15→30% restores 6 kWh; Sport/Hills 20→35% restores 6 kWh; provisional 8→0% Turtle provides 3.2 kWh.
- AWD: each 1% is 0.50 kWh; both normal recovery windows restore 7.5 kWh; provisional 8→0% Turtle provides 4.0 kWh.

**Ideal M2 Turtle checkpoints (2 kW auxiliaries, no reserve/FoS):**
- RWD 3.2 kWh: ~32 min/21 km at 40 km/h or ~22 min/22 km at 60 km/h.
- AWD 4.0 kWh: ~38 min/25 km at 40 km/h or ~25 min/25 km at 60 km/h.
- Conservative real-world planning targets: ~12–18 km RWD and ~15–20 km AWD.

**Charging/rescue checkpoints:**
- 230 V × 15 A = 3.45 kW wall maximum; ~3.0–3.2 kW reaches the pack after conversion.
- Donor V2L at 3.3 kW: 0.55 kWh in 10 min or 1.1 kWh in 20 min before losses. Start release is power/cell/FoS based, not time based.
- Restoring displayed 0→10% requires ~4 kWh RWD or ~5 kWh AWD before traction/auxiliary energy. Approximately 1 litre petrol provides only ~2.7–3.1 kWh HV DC.

---

## 22B. Requirement-to-Validation Traceability

Initial concept matrix:

| ID | Requirement | Test evidence |
|----|-------------|---------------|
| ARC-001 | No mechanical engine-wheel path | Inspect CAD/vehicle; zero torque path in every configuration |
| PWR-001 | Healthy-SoC EV-first 170/240 kW | Pack/EDU dyno and HIL; generator off and delivered torque within limits |
| BAT-001 | 187/263 kW pulse capability | Cell/module/pack tests across SoC, temperature and aged state |
| GEN-001 | Released 30/40/55/70/90/105 kW bands | Dyno; measured HV-DC output within approved maps |
| GEN-002 | Progressive band law | SIL/HIL/vehicle; smallest sufficient band, no unjustified maximum jump |
| MOD-001 | Moving mode changes | HIL/driver-in-loop; no discontinuous wheel torque, Sport gate enforced |
| EST-001 | Road-load/effective-mass estimate | Proving ground; confidence and residual valid across M0–M3 |
| AUX-001 | A0/A1 protection | Fault-injection/HIL; safety cooling never shed, propulsion derates first |
| TUR-001 | Dynamic 7–8% nominal Turtle entry | HIL/vehicle; 10% intervention and energy-based entry smooth |
| TUR-002 | State 5/6 recovery | Cold/hot/aged-pack HIL and vehicle; interlocks, recovery, messages match |
| RES-001 | V2L rescue | Electrical/HIL/vehicle; only safe recoverable packs charge |
| EXP-001 | V2L export | Electrical/HIL/vehicle; all six interlocks, directional metering, donor floor |
| BRK-001 | Friction-independent stopping | GVWR fade/failure test; legal stop performance with zero regen |
| AWD-001 | Axle allocation/terrain | Split-mu, grade, descent, wading, and thermal tests |
| HOM-001 | All-electric MIDC | Certification dry-run; generator consumes zero fuel, range retains COP margin |
| HMI-001 | Warning comprehension | Human-factors test; severity, cause, and action understood |
| SAF-001 | Input/data integrity | HIL fault injection; stale/invalid signals enter safe fallback |
| THM-001 | 210 km/h and generator high bands | Dyno/proving ground/climatic; no battery, EDU, generator, or tyre limit exceeded |
| FUE-001 | Usable fuel/runtime | Tank rig/CFD/vehicle attitude; prediction conservative through slosh and ageing |
| SER-001 | Fuel-age/exercise/service | Durability/fleet trial; no-start, emissions, lubrication risks controlled |

---

## 23. Engineering Decision Register

### Concept Frozen

- Series REEV, no mechanical engine-wheel path, EV-first battery response, battery-coupled generator, States 1–6, and no generator-only battery bypass.
- 400–465 V INGLO-class reuse; 1,000 V is outside this concept.
- Baseline 40/50 kWh usable packs and provisional 1% upper/3% lower reserve convention; reserve is excluded from customer range and normal traction.
- 170 kW RWD and 240 kW AWD healthy-SoC calibrations, conditional on BMS and supplier limits rather than generator contribution.
- Common 30/40/55/70/90/105 kW HV-DC bands, smallest-sufficient-band law, mode-specific progression, and 105 kW maximum reference-condition target.
- Economy 15→30%, Sport/Hills 20→35%, dynamic energy authority, safe mode gates, 10% early intervention, energy-based Turtle entry normally around 7–8%, complete use to displayed 0%, and energy/FoS-based recovery exit.
- BMS/VCU information contract, onboard-only authority, estimator structure, degraded-data principle, and prohibition on optimistic stale-data fallback.
- Auxiliary A0–A5 priority/shedding policy and progressive re-enable order.
- M0–M3 mass/load conventions: **M0 2,300/2,500 kg; M2 ~2,820/~3,020 kg; M3 ~2,945/~3,145 kg.**
- HMI three-quantity model, severity framework, and mandatory recovery/safety message intent.
- One export session class with six mandatory interlocks re-evaluated every frame, and measured net outward flow as the continuous condition for generator engagement.
- Stationary engine operation governed by the direction of energy.
- Condition/hour/calendar-based service tracking and the attended maintenance-exercise principle.
- Hypothetical capability-based GST policy: 18% for >4 m, >1,000 cc/<1,500 cc petrol-generator REEVs with ≥270 km combined MIDC all-electric range; 9% for sub-4 m, ≥500 cc/<1,000 cc equivalents with ≥210 km; no proposed preferential rate at ≥1,500 cc. Engineering target is ~340 km RWD / ~408 km AWD.

### Provisional — Validation Pending

- 41.67/52.08 kWh gross values, 96% usable window, exact reserve sufficiency, and pack mass.
- Approximate 128s topology; freeze series/parallel count after nominated-cell and inverter limits.
- 105 kW continuous-capable reference envelope, thermal/altitude derating, and provisional engine RPM/BMEP/torque points.
- Liquid-cooled HV PMSG generator, active rectifier, integrated PMSG start, coupling ratio, and 30–70 kW weighted-efficiency target.
- BMS confidence/timeouts, estimator filters, FoS multipliers, generator guarantee, ramp/dwell/hysteresis, torque blending, and recovery calibration.
- **Agreed M0 kerb targets: 2,300 kg RWD / 2,500 kg AWD** — pending any future physical programme work.
- AC timing checkpoints, thermal preconditioning states, V2L start-release inequality, and DC charge-curve placeholders.
- Bidirectional OBC confirmed as the resolved architecture. One port, one OBC, two export operating modes differentiated by floor condition and generator engagement rule. A dedicated export inverter or second port is not required.

### External Evidence Required

- Nominate the exact Short Blade 2.0 cell and confirm chemistry, voltage, Ah, resistance, pulse/continuous charge-discharge limits, dimensions, mass, temperature/ageing behavior, and cycle-life maps.
- Prove 187/263 kW pack pulse capability across SoC, temperature, repetition, imbalance, sag, ageing, fuses, contactors, busbars, cooling, and faults.
- Obtain Mahindra engine BSFC/BTE, emissions/durability maps and measured generator/rectifier/EDU/gear efficiency maps.
- Confirm rear/front EDU identity, inverter absolute limits, final-drive ratio, maximum motor speed, field weakening, and 210 km/h durability.
- Replace CdA/Crr assumptions through coast-down.
- Complete pack/generator/fuel/third-row CAD, CAE, crash, side-pole, rear impact, roof, underbody, wading, cooling, NVH, and serviceability evidence.
- Confirm ARAI OVC-HEV test mass, fuel loading, all-electric break-off, variant-family/COP treatment, tax classification, and certified range.
- Close ISO 26262 safety case, cybersecurity/CAN integrity, HIL/SIL/dyno/climatic/durability/homologation evidence, and production warranty release.

---

## 24. Modular Rewrite Conformance and Remaining Limits

The public simulation in `PowerTrainController.java` maps the major requirement families across ARC-001/PWR-001/BAT-001, GEN-001/GEN-002, MOD-001, TUR-001/TUR-002, AUX-001, BRK-001, SAF-001, AWD-001/HMI-001/FUE-001, and INS-001.

**Simulation limitations that must not be mistaken for production closure:**
- Cell chemistry, series/parallel topology, voltage/current maps, pulse durability, balancing, contactor control, and pack thermal models remain supplier/HIL/physical-test work.
- Generator RPM/BMEP, BSFC/BTE, catalyst, emissions-safe start/stop, altitude/thermal maps, coupling, rectifier, and CAN interfaces are represented by provisional power-level abstractions only.
- The level-road sustainable-speed solver uses assumed CdA/Crr/mass. It does not replace coast-down identification, IMU grade fusion, tyre/surface, payload, headwind, towing, or production effective-mass estimation.
- AWD assistance is an axle-level allocation illustration, not ESP, yaw, brake-vectoring, e-differential, split-mu, or terrain-control software.
- Regeneration accepts requested wheel-braking power but does not replace certified brake-by-wire, ABS/ESC arbitration, pedal-feel, fade, or friction-only stopping validation.
- Tank slosh, pickup reserve, pressure, EVAP and leak validation occur outside this model; `observeValidatedRefill` represents that completed trust boundary.
- V2L export is specified in Section 17 but is not modelled at all. The six interlocks, directional metering, net-export collapse dwell, session cap and acknowledgement flow exist as requirements only and must not be treated as verified behaviour.
- Scenario assertions are explanatory checks, not ISO 26262, MISRA, cybersecurity, HIL, durability, crash, emissions, or homologation evidence.

---

## 25. Candidate Patentable Subject Matter and Disclosure Sequencing

*(Commercial and legal strategy context; not a control requirement. Written by an engineer, not a patent attorney. No novelty search, prior-art search, or freedom-to-operate analysis has been performed. Every statement below must be confirmed with a qualified Indian patent attorney before any money is spent or any application is filed.)*

### 25.1 The Disclosure Rule

India provides no general grace period for an inventor's own pre-filing publication. Public disclosure includes a social-media post, a published build of the concept site, a public repository, a conference talk, a demonstration, and any sharing without a non-disclosure agreement. Anything intended for protection must be filed first, at minimum as a provisional application establishing a priority date.

**Consequence for this repository:** this section must be removed from any public build of the concept material.

### 25.2 Ownership Must Be Resolved Before Filing

This work was authored on employer-managed equipment inside an employer repository. Standard Indian technology-employment agreements assign inventions created using employer time, equipment, or resources. Automotive powertrain control sitting far outside the employer's field helps the argument but does not automatically settle it. Required before filing: read the invention-assignment clause; recreate the work on personal equipment; retain dated records establishing authorship; and where doubt remains, obtain a written waiver or no-interest letter.

### 25.3 Ranked Candidates

**C1 — Braking-time separation of a generator's mechanical band from its electrical output** *(strongest candidate)*
- Mechanism: during a regeneration event the engine and generator hold their mechanically selected band and dwell timer, while the rectifier collapses electrical output so the pack's charge-acceptance budget is reserved for regeneration. Electrical output is restored when regeneration ends, with no band restart.
- Possible novelty: holding one machine in two different states — mechanical and electrical — in a defined time relationship.
- Detectable: yes. Engine speed stays flat while generator DC current falls, visible on a CAN trace or a dynamometer.

**C2 — Reserve protection as a time co-arrival problem**
- Mechanism: predict when stored energy and fuel will each be exhausted, and act on the predicted relationship between those arrival times instead of acting when SoC crosses a fixed number.
- Novelty caution: predictive energy management is a crowded field. Claim narrowly.
- Detectable: weakly. To an outside observer the behavior resembles a threshold.

**C3 — Refuel confirmation by evidence fusion driving autonomous recovery**
- Mechanism: confirm a refill from multiple independent signals rather than a single level sender, then autonomously start the engine on a stopped energy-depleted vehicle, hold Turtle, recover to a computed exit threshold, and only then restore full capability, with connector, Park and brake interlocks throughout.
- Detectable: yes. A tester refuels a stopped vehicle and observes the sequence.

**C4 — Depleted-pack bootstrap and donor-side generator engagement**
- Mechanism, recipient side: recovery over an EVSE-mediated chain, with the engine-start release computed from available battery pulse power against cranking demand, auxiliaries and a factor of safety rather than from an SoC value.
- Mechanism, donor side *(stronger)*: a donor that autonomously starts its own range extender, gated on donor SoC and validated fuel, to complete a rescue its pack alone could not.
- Third mechanism, possibly claimable independently: using the measured sign of net energy flow at the port as the continuous authorisation for stationary engine operation.
- Detectable: yes. Instrument the port and observe the engine start as the donor reaches its boundary.

**C5 — Aftertreatment state constraining electrical band selection**
- Mechanism: catalyst light-off state, lean or stoichiometric mode, and NOx trap regeneration status gate which electrical power band the generator is permitted to occupy, with the traction battery absorbing the resulting mismatch.
- Detectable: partially, through emissions bench testing.

**C6 — Demand-referenced downshift timing**
- Mechanism: time the downshift from how long demand has been below the current band, rather than from how long the band has been held. This prevents band hunting on rolling terrain.
- Best used as a dependent claim within C1 rather than as a separate filing.
- Detectable: yes, by driving a rolling-terrain profile.

### 25.4 What Is Not Patentable Here

- The range-extended architecture itself. Series hybrids are extensively documented.
- Principles and objectives: EV-first operation, battery as first responder, removing range anxiety. Aims are not claimable.
- The specific numbers. The 15, 20, 30, 35 and 8 percent gates, the six power bands, the 105 kW ceiling, the 30 and 40 kW regen caps. Numbers are calibration; a competitor changes them trivially.
- Mass targets, cost estimates, the hypothetical GST proposal, styling, cabin-enhancement list, range and economy figures, and the calculator user interface.
- The document itself. Its text is protected by copyright, which prevents copying the document and does nothing to prevent anyone building the system it describes.

### 25.5 India-Specific Drafting Constraint: Section 3(k)

Section 3(k) of the Patents Act, 1970 excludes a mathematical or business method, a computer programme per se, and algorithms from patentability. Control logic is nevertheless patentable in India when claimed as producing a technical effect in a physical system. Every candidate above must therefore be drafted as a method of controlling the powertrain, naming the physical consequence.

### 25.6 Search Before Spending

Search Espacenet, Google Patents, and the Indian Patent Advanced Search System before drafting anything, concentrating on: Nissan, BMW, General Motors, Li Auto, Great Wall, Geely, Honda, Mahindra, and Tata Motors.

### 25.7 Indicative Cost and Route

An Indian provisional application buys a priority date plus twelve months. Do not file all six. File one strong application, or none.

### 25.8 The Honest Fork

- **Route A, protect first:** resolve ownership, file a provisional covering C1 with C6 as a dependent claim, then publish.
- **Route B, defensive publication:** publish now with a clear verifiable date. Nobody can then patent the disclosed matter.
- Route B is the correct choice unless a commercial conversation is genuinely in prospect.
- The failure mode to avoid: publish, then decide to file, and discover the disclosure has already destroyed the novelty. Choose deliberately, and choose before publishing anything.
