---
name: kos-build-environment
description: Set up and diagnose KallistiOS cross-compilation environments, toolchains, environ.sh, Makefiles, and Dreamcast build failures.
---

# KOS build environment

## Intended use

Use this skill when a user needs to install, configure, inspect, or debug a
KallistiOS build environment or a KOS project that fails before deployment.

## Do not use when

- The problem is primarily a kos-ports dependency; use `kos-ports-integration`.
- The problem is transferring or debugging a binary on a console; use
  `kos-deploy-debug`.
- The problem is hardware behavior after a successful build; use
  `dreamcast-hardware`.

## Inputs

- KOS source path and commit, tag, or branch.
- Project path and build command.
- Host OS and shell.
- Toolchain location and target tuple.
- `environ.sh`, Makefiles, linker output, and complete error logs.
- Target platform such as Dreamcast or Naomi when relevant.

## Outputs

- Root cause classification.
- Minimal diagnostic commands.
- A corrected environment or Makefile draft when requested.
- Expected output and stopping conditions.
- Exact validation performed and unresolved assumptions.

## Side effects and permissions

Default capabilities are `read-only` and bounded `code-execution`. Building may
create local artifacts. Do not install packages, download toolchains, delete
build trees, or modify shell startup files without explicit authorization.

## Workflow

1. Record KOS and toolchain versions before diagnosing.
2. Inspect `environ.sh`, `KOS_BASE`, toolchain variables, target selection, and
   project Makefiles.
3. Confirm that the compiler target and linker match the intended Dreamcast
   target.
4. Reproduce the smallest failing command with the environment explicitly
   sourced.
5. Classify the failure as environment, compiler, Makefile, linker, header,
   library, or project-code related.
6. Check the nearest KOS example or official source before proposing an API or
   flag change.
7. Validate with a bounded rebuild or a dry-run where possible.

Do not treat a successful host compilation as proof that the binary will run on
Dreamcast hardware.

## References

- [KallistiOS build and architecture documentation](https://github.com/KallistiOS/KallistiOS/blob/master/doc/README.md)
- [KallistiOS repository](https://github.com/KallistiOS/KallistiOS)
