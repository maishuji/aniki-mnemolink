---
name: dreamcast-hardware
description: Analyze Sega Dreamcast hardware-specific code and behavior across SH-4, PowerVR, AICA, Maple, VMU, storage, networking, and video systems.
---

# Dreamcast hardware analysis

## Intended use

Use this skill for architecture questions, hardware-specific bugs, performance
reasoning, register or DMA concerns, graphics/audio behavior, and peripheral
integration in KOS projects.

## Do not use when

- The problem is only a host build or linker failure.
- A missing third-party library is the primary issue.
- The user is targeting a different Sega SDK or a non-Dreamcast platform unless
  the comparison is explicitly requested.

## Inputs

- Hardware subsystem and target model or modification.
- KOS commit and relevant headers/examples.
- Source code, logs, frame timing, memory addresses, or register values.
- Emulator or real-console setup and observed symptoms.
- Performance or correctness goal.

## Outputs

- Hardware and KOS subsystem map.
- Evidence-backed explanation with relevant symbols, headers, examples, or
  documentation.
- Safe diagnostic plan and expected observations.
- Performance, memory, cache, DMA, synchronization, or format risks.
- Explicit uncertainty where hardware verification is unavailable.

## Side effects and permissions

Default capabilities are `read-only` and bounded `code-execution`. Do not write
hardware registers, reset devices, flash firmware, overwrite storage, or run
console-side experiments without explicit authorization and a clearly stated
target.

## Workflow

1. Identify the subsystem: SH-4, PowerVR/PVR, AICA, Maple, VMU, storage,
   network, video, or another device.
2. Locate the matching KOS headers, driver code, examples, and versioned
   documentation.
3. Separate documented behavior, source-derived behavior, emulator behavior,
   and real-hardware observations.
4. Check alignment, cache/store-queue behavior, DMA ownership, timing,
   synchronization, memory limits, and data formats where relevant.
5. Propose the smallest safe diagnostic or code change.
6. State what evidence would confirm or falsify the diagnosis.

Do not fill gaps with generic desktop or modern-console assumptions. Prefer a
small source-backed claim over an attractive but unverified explanation.

## References

- [KallistiOS hardware support and examples](https://github.com/KallistiOS/KallistiOS)
- [KOS architecture documentation](https://github.com/KallistiOS/KallistiOS/blob/master/doc/README.md)
