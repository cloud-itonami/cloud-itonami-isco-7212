# cloud-itonami-isco-7212

Open Occupation Blueprint for **ISCO-08 7212**: Welders and Flame Cutters.

This repository designs a forkable OSS business for an independent welder: a weld-prep and inspection robot performs joint-alignment checks and post-weld inspection scans under a governor-gated actor, so the practice keeps its own job and inspection records instead of renting a closed trades-management SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a weld-prep and inspection robot performs joint alignment checks and post-weld inspection scans under an actor that proposes
actions and an independent **Welding Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating near open flame, arc welding, or flammable materials) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
job order + material spec + safety plan
        |
        v
Welding Advisor -> Welding Governor -> weld-support/finish, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7212`). Required capabilities:

- :robotics
- :forms
- :telemetry
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
