---
name: kos-deploy-debug
description: Prepare and diagnose Dreamcast deployment and remote debugging with dcload-ip, dcload-serial, dc-tool, and GDB-over-dcload.
---

# KOS deployment and debugging

## Intended use

Use this skill after a KOS binary exists and the user needs to transfer, run,
inspect, or debug it on a Dreamcast through Ethernet or serial tooling.

## Do not use when

- The binary does not build; use `kos-build-environment`.
- A missing library is the main issue; use `kos-ports-integration`.
- The failure is an unverified hardware or subsystem behavior; use
  `dreamcast-hardware` after deployment is established.

## Inputs

- Binary path and symbol file.
- KOS and loader revisions.
- Connection type: Broadband Adapter, LAN Adapter, or serial/coders cable.
- Dreamcast IP, host interface, serial device, and `dc-tool` options.
- Emulator or physical-console details.
- GDB version and debug build flags when applicable.

## Outputs

- Loader choice and connection diagnosis.
- Prepared transfer/run command with target and side effects shown.
- Console, PC I/O, exception, and GDB troubleshooting steps.
- Clear distinction between prepared, executed, and verified actions.

## Side effects and permissions

Default capabilities are `read-only` and bounded `code-execution` for local
inspection. Sending a binary, launching it, resetting the console, opening a
remote GDB session, or changing console state is an external write and requires
explicit confirmation immediately before the action. Never flash firmware,
overwrite storage, or burn a disc as part of this workflow.

## Workflow

1. Confirm the binary, symbols, loader revision, target connection, and
   verification method.
2. Select `dcload-ip` for Ethernet or `dcload-serial` for a serial connection;
   do not mix their host tools or assumptions.
3. Verify local tool availability and inspect the command without executing it.
4. Show the exact target, binary, connection, and expected console effect.
5. Ask for confirmation before transfer or launch.
6. Capture console output, exception information, and host-tool logs.
7. For GDB-over-dcload, confirm debug symbols, `gdb_init()`, the target remote
   endpoint, and the selected executable before attaching.
8. Report whether the result was static, emulator-based, or observed on real
   hardware.

## References

- [dcload-ip](https://github.com/KallistiOS/dcload-ip)
- [dcload-serial](https://github.com/KallistiOS/dcload-serial/blob/master/README.md)
- [KOS documentation](https://github.com/KallistiOS/KallistiOS/blob/master/doc/README.md)
