---
name: kos-ports-integration
description: Find, build, link, and diagnose kos-ports libraries and other optional dependencies in KallistiOS projects.
---

# kos-ports integration

## Intended use

Use this skill when a KOS project needs an external library from `kos-ports`,
when a port fails to build, or when headers and libraries are not being found.

## Do not use when

- The failure is in the base KOS toolchain or `environ.sh`; use
  `kos-build-environment`.
- The issue is a KOS hardware API or rendering/audio bug.
- The user wants to publish or maintain an upstream port.

## Inputs

- KOS commit and sourced environment.
- kos-ports revision and port name.
- Project Makefile and failing compiler/linker output.
- Required license and distribution constraints.
- Host network and download permissions.

## Outputs

- Dependency and compatibility assessment.
- Required port build/install steps.
- Makefile or include/library configuration draft.
- License and attribution notes.
- Verification command and expected result.

## Side effects and permissions

Default capabilities are `read-only` and bounded `code-execution`. Port builds
may download source archives, write into the ports tree, and create libraries.
Show those commands and request authorization before downloads or installation.
Never silently add a dependency or change a lock, manifest, or release asset.

## Workflow

1. Confirm the KOS and kos-ports revisions; do not assume `master` compatibility.
2. Search the ports tree for the exact port and its declared dependencies.
3. Check whether the port is already installed and whether headers and
   libraries come from the expected revision.
4. Review the port's build instructions, patches, and license files.
5. Prepare the smallest install/build sequence, including the required sourced
   KOS environment.
6. Update the project Makefile only when explicitly authorized, preserving the
   repository's existing conventions.
7. Build a minimal example or target and distinguish compile, link, and runtime
   failures.

## References

- [KOS ports repository](https://github.com/KallistiOS/kos-ports)
- [KOS documentation](https://github.com/KallistiOS/KallistiOS/blob/master/doc/README.md)
