# REEV Concept — XEV 9S INGLO-derived Range-Extended Electric Vehicle

A concept study for a Range-Extended Electric Vehicle (REEV) on the Mahindra INGLO / XEV 9S skateboard, in XUV 7XO proportions. Daily driving runs entirely on battery. A 1.5 litre mStallion TGDi generator — not the 7XO’s 2.0 petrol — takes over on long trips. The engine never turns a wheel.

> **Status:** Pre-feasibility concept. All figures are transparent design targets and calculation assumptions — not certified test results.

---

## What is a REEV?

A REEV drives like a pure electric vehicle every day. The petrol engine has **no mechanical connection to the wheels** — it only drives a generator to produce electricity when the battery runs low. You get the smoothness of an EV with the freedom of a petrol car on long trips.

---

## Variants

| | RWD | AWD |
|---|---|---|
| Battery | 40 kWh usable | 50 kWh usable |
| Power | 170 kW (231 hp) | 240 kW (326 hp) |
| 0–100 km/h | ~7.8–8.3 s | ~6.1–6.7 s |
| Top speed | 210 km/h | 210 km/h |
| Kerb mass | ~2,300 kg | ~2,500 kg |

**Common to both variants:**
- Mahindra 1.5L mStallion TGDi turbocharged petrol engine (~163 hp)
- 105 kW peak generator HV DC output
- 42 litre petrol tank
- Up to 180 kW DC fast charging
- Combined system range: ~800–885 km on a full charge and full tank

---

## Architecture

- **Series hybrid** — the engine never drives the wheels. Every kilometre is electric.
- **EV-first** — generator stays off when the battery is healthy.
- **Six generator bands** — 30, 40, 55, 70, 90, 105 kW HV DC. The engine operates at fixed, efficient points rather than following the accelerator pedal.
- **Six operating states** — Pure EV → Battery protection → Charge sustaining → Generator limited → Turtle (emergency) → Refuel recovery.
- **V2L (Vehicle-to-Load)** — 230V AC export from the traction battery for home backup or powering devices at camp.

---

## What's in this repository

| File | Description |
|---|---|
| `index.html` | Interactive concept site with live simulator, state machine explorer, charging curves, cost model, and competitor comparison |
| `REEV-Concept-Introduction.docx` / `.pdf` | Concept introduction — executive summary, platform derivation, control law, running costs, GST proposal |
| `pitch/index.html` | Twelve-slide meeting deck |
| `linkedin-post.md` | Draft LinkedIn post, pin comment, and words not to use |
| `REEV-Design-Specification.md` | Full engineering specification — architecture, control laws, cost, tax case (working file) |
| `PowerTrainController.java` | Conceptual powertrain controller implementation |
| `BatteryManagementSystem.java` | Conceptual BMS implementation |
| `GeneratorManagementSystem.java` | Conceptual generator management system |
| `TractionMotorManagement.java` | Conceptual traction motor management |
| `VehicleInstrumentationManagementSystem.java` | Conceptual vehicle instrumentation system |

---

## Live Simulator

Open `index.html` in any modern browser. The simulator lets you:

- Run NH44-style highway drives, city cycles, or custom routes
- Set starting battery charge, fuel level, drive mode, and payload
- Configure charging and refuel stops
- Watch the vehicle move through all six operating states in real time
- See SoC, generator output, wheel power, and speed charts

Stops trigger reactively — if the battery hits the turtle boundary before a planned stop, the simulation pulls over immediately to refuel or recharge, matching real-world driver behaviour.

---

## Key Design Decisions

- No mechanical engine-to-wheel path under any condition
- Engine never runs unattended while parked to accumulate charge
- Generator start is prohibited while plugged in to a charger
- Turtle mode (State 5) caps speed at 60 km/h and sheds comfort loads to preserve mobility
- After a refuel, the vehicle detects fuel automatically — no driver input needed to restart the generator
- V2L home backup mode runs from battery only; generator never starts during an export session

---

## Disclaimer

This is an independent engineering concept study. It is not affiliated with or endorsed by Mahindra & Mahindra or JSW MG Motor. All specifications are preliminary targets derived from publicly available data and first-principles calculations. Nothing has been tested on hardware.

Live site: https://kartikeyavanamali.github.io/Concept-REEV/  
Pitch deck: https://kartikeyavanamali.github.io/Concept-REEV/pitch/
