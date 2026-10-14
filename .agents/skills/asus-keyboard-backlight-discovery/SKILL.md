---
name: asus-keyboard-backlight-discovery
description: Safely inventory and research keyboard-backlight capabilities on an exact ASUS ROG Zephyrus Duo Linux configuration. Use before choosing a backend or claiming model support; inspect read-only LED, driver, kernel, Debian, and daemon evidence without changing hardware.
---

# ASUS keyboard-backlight discovery

## Intended use

Use this skill before designing, implementing, or claiming support for an ASUS
ROG Zephyrus Duo **keyboard-brightness** backend on Linux, Debian first. Inspect
an exact configuration and distinguish observed behavior from documentation,
inference, and unknowns.

Trigger examples:

- "Find the keyboard-backlight interface on this Zephyrus Duo, read-only."
- "Is this exact model and kernel suitable for the CLI MVP?"
- "Review our sysfs versus asusd integration assumptions."

## Non-goals

- Turning the keyboard on/off or writing a brightness level.
- RGB colors/effects, per-key protocols, ASUS performance modes, fan curves,
  firmware/BIOS settings, or primary/secondary display brightness.
- Installing `asusctl`, loading a kernel module, modifying permissions, or
  asserting that an entire Zephyrus Duo family is supported.
- Running hardware experiments or obtaining privileged access as part of a
  read-only inventory.

## Inputs, outputs, and permissions

Inputs: owner's consent for inspection of the target machine, exact model/SKU
and board when available, Debian/kernel/BIOS details, relevant sysfs snapshots,
installed daemon/version, and versioned upstream references.

Outputs: a sanitized evidence inventory, plausible keyboard devices and
conflicts, known and unknown backend behavior, a configuration-specific
compatibility status, and a bounded **proposed** validation plan.

Use `read-only` for local inspection and bounded `code-execution` for safe
read-only queries, when authorized to inspect that machine. Use `web-research`
for versioned public kernel/ASUS/Debian references when permitted. Writing a
report into a repository needs authorized `file-editing`. Read-only discovery
never grants permission to write sysfs, call mutating D-Bus methods, run
commands as root, install services, change system policy, or commit/push.
Keep raw machine identifiers and logs local. Do not send serial numbers,
UUIDs, tokens, or private log content to external services.

## Discovery method

### 1. Identify the target without guessing

Confirm inspection is running on the intended laptop, not a VM, container, or
neighboring machine. Ask the owner for the exact SKU if the product/board name
alone is insufficient. Record sanitized product/board names, BIOS version,
Debian release, kernel version, and active desktop/session if relevant. Do not
collect serial numbers, machine IDs, or a full unreviewed diagnostic archive.

Safe examples on the authorized target are `uname -r` and reading
`/etc/os-release` or the relevant `/sys/class/dmi/id/` product/board/BIOS
fields. Inspect only necessary fields and redact identifying data before
sharing. If the machine is not available, research upstream and label **all
hardware observations unverified**; never present example data as a probe.

### 2. Discover kernel keyboard LEDs read-only

List `/sys/class/leds/` and identify plausible internal keyboard-backlight
entries. `asus::kbd_backlight` is a candidate name, **not** a guaranteed path or
proof of driver/model support. Check the candidate's parent/driver metadata
and sysfs symlink destination to distinguish keyboard LEDs from other lights.
`asus-wmi` and `hid-asus` can both expose keyboard LED interfaces on some
kernel versions. Do not infer transport from the LED name alone.

For each plausible candidate, read only `brightness`, `max_brightness`, and
`trigger` if present. Record whether each attribute exists, its observed
value, and any read errors. `max_brightness` is device-defined; do not assume
0–3 or 0–255. A missing trigger attribute is not equivalent to proving no
trigger exists. An active trigger can control the LED, and writing `0` may
remove it: **do not write to test this**.

If there are no candidates, multiple candidates, contradictory metadata,
missing range information, or insufficient read permission, say so. Do not
pick a device or conclude "unsupported" merely from one missing conventional
filename. `/sys/class/backlight` controls displays, not the keyboard; do not
inspect it as a fallback keyboard backend.

### 3. Check installed daemon and kernel context

Determine whether an ASUS daemon is installed and **already running** using
non-activating, read-only service/bus inspection. Check its installed version
and supported interface from that version's documentation. Do not start,
install, or activate a daemon just to make discovery succeed.

Some `asusd` versions expose keyboard brightness through
`xyz.ljones.Aura`; an interface named `xyz.ljones.Backlight` relates to
displays/ScreenPad in the inspected upstream version. Treat D-Bus interfaces,
object paths, authorization, packaging, and Debian support as **version-specific**;
never assume upstream code or presence on the bus proves it works on this
laptop. Account for possible hotkey and daemon ownership of brightness without
calling mutating methods. Do not silently propose a second competing writer.

### 4. Compare evidence with authoritative sources

Consult the [Linux LED class documentation](https://docs.kernel.org/leds/leds-class.html),
version-matched `asus-wmi`/`hid-asus` kernel source when needed, and the
[asusctl upstream repository](https://github.com/OpenGamingCollective/asusctl)
for the *installed* version. Record source URL, revision/date, which assertion
it supports, and any mismatch with the running kernel or daemon. Verify Debian
package and permission claims against the target release rather than assuming
an upstream-supported distribution is Debian-supported.

An upstream model mapping, LED presence, or successful **read** is not proof
that brightness writes visibly affect a specific keyboard. Only explicitly
consented, controlled tests and owner observation can establish that.

### 5. Report an actionable, bounded result

Provide:

1. **Target:** sanitized SKU/product/board, Debian release, kernel, BIOS, and
   daemon version, each with a source or "unknown".
2. **Evidence:** candidate LED name(s), resolved parent/driver, values and
   bounds, trigger state, relevant daemon ownership, and read errors. Separate
   observed host facts from upstream claims and inference.
3. **Status:** `unverified`, `needs-investigation`, `blocked`, or
   `hardware-validated` for the **exact recorded configuration**. This skill's
   read-only session alone cannot produce `hardware-validated`. Explain what
   is missing; do not turn unknown into unsupported or family-wide support.
4. **Backend recommendation:** whether read-only kernel status is feasible;
   what evidence is still needed before choosing an authorized `asusd` route
   or a narrow helper. Never choose both writers by default.
5. **Next validation gate:** propose a minimal owner-approved write test only
   after access/security review, with recorded starting state, safe bounds,
   trigger handling, visible-level checks, hotkey and daemon coexistence, both
   displays/RGB unaffected, and restoration where possible. **Do not execute
   that plan under this skill's read-only permission.**

Do not publish a compatibility claim for GX550, GX551, GX650, or another Duo
variant without a separate exact-configuration hardware validation record.
