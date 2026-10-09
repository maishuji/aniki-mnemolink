---
name: rust-tdd
description: "Use test-driven development for Rust behavior changes: establish a runnable baseline, write a meaningful failing test, implement the smallest passing change, refactor, and report exact validation. Use for features, bug fixes, and regression coverage in Rust projects."
---

# Rust test-driven development

## Intended use

Use this skill when implementing or reviewing a Rust behavior change, especially
a bug fix, new CLI/API feature, or regression test. It guides the validation
engineer and implementer through **red → green → refactor**, not a separate
"write tests after the feature" pass.

Trigger examples:

- "Add regression coverage before fixing this Rust bug."
- "Develop this CLI behavior with TDD."
- "Review whether these tests actually prove the requested change."

## Non-goals

- Creating tests for implementation details merely to increase coverage counts.
- Redesigning unrelated modules, CI, or toolchains to make a test pass.
- Replacing real hardware/desktop acceptance checks with fake-backed tests.
- Running tests that write to host hardware, require root, or mutate remote
  systems in the ordinary test suite.

## Inputs, outputs, and permissions

Inputs: observable acceptance criteria, repository instructions, toolchain,
existing tests, module/API boundaries, safe test fixtures, and available checks.

Outputs: a small acceptance/test matrix; the failing test and its failure
reason; the minimal passing change when authorized; refactor and validation
results; and explicit gaps or environmental blockers. Provide commands and
results, not invented pass/fail claims.

Reading code and planning tests is `read-only`. Creating tests, fixtures, or
production changes requires authorized `file-editing`. Running bounded local
checks uses `code-execution`. Request separate permission for any installation,
privileged/hardware operation, commit, or external write. Neither TDD nor a
review request authorizes Git staging, committing, or pushing.

## Workflow

### 1. Define behavior and establish a baseline

Confirm one user-visible outcome, its inputs, expected output or state, and
error behavior. Identify the smallest level that can prove it: a pure unit test,
fixture-driven adapter test, or CLI/API integration test. Keep test names about
behavior, not private function names.

Read `Cargo.toml`, `Cargo.lock`, repository instructions, existing tests, CI,
and the relevant code. Check the available `cargo` and `rustc` versions against
the package edition and declared toolchain. Run the smallest relevant existing
test command before editing when possible. Use project-supported commands,
for example `cargo test --locked` for a locked workspace; use `--offline` only
when dependencies are already available. Bound execution time. Do not silently
downgrade the Rust edition, update dependencies, or fetch a new toolchain merely
to obtain a green baseline.

Record any pre-existing failures. If the toolchain cannot parse the manifest,
a dependency is unavailable, or a baseline test fails for unrelated reasons,
report the exact blocker. An environmental error is not a successful TDD red
phase. Agree on an environment fix or clearly limit work to an unexecuted test
draft; do not claim a red/green cycle was completed.

### 2. Write a focused failing test (red)

Write the smallest test that captures the missing behavior **before** changing
its production implementation. Include useful negative and boundary cases:
invalid input, absence, ambiguity, permission denial, overflow/range errors,
or failure propagation when relevant. Avoid mocks that assert only a private
call count while ignoring observable behavior.

Prefer a deterministic fake, a temporary directory, fixture files, or a mock
service. Injectable backends, paths, clocks, and environment are better than
hard-coded host state. Never write to the developer's `/sys` or require live
hardware for the default test command. Do not add a new testing dependency
unless the project cannot express the behavior safely with existing tools.

Run the focused test and capture the failure. Verify it fails for the intended
missing behavior, not a syntax error, an unrelated failing test, an absent API,
missing permissions, or a broken harness. If the test cannot compile because a
new interface is needed, establish a minimal reviewed contract first and
explicitly distinguish that compilation failure from a behavioral red result.
Do not weaken or delete a meaningful test to reach green.

### 3. Implement the smallest change (green)

Change only the production code necessary to satisfy that test. Prefer clear
Rust types and `Result` error paths; avoid `unwrap()` and `expect()` in
production behavior. Keep public API documentation and directly related
usage notes in scope. Run the focused test again and require it to pass before
broadening the change.

If implementation is assigned to another agent, hand off the failing test,
expected behavior, owned files, and exact command. Do not let two agents edit
the same file or index concurrently. A test author may validate a patch, but
passing their own tests does not replace an independent review.

### 4. Refactor with protection

Improve names, duplication, and boundaries only where the now-green behavior
requires it. Re-run the focused test after refactoring. Keep intermediate
commits coherent if commits were separately authorized: a failing red-phase
commit is not required or desirable just to document TDD.

### 5. Broaden validation and report evidence

Run related unit and integration tests, then broader checks when available:
project-standard `cargo fmt --check`, `cargo clippy` on relevant targets, and
the workspace's `cargo test --locked`. Adapt commands to the workspace and
installed features; do not assert they ran if a tool or system library is
missing. Respect bounded runtimes and avoid watchers or long-lived servers.

Use a simple evidence record:

```text
Behavior / acceptance criterion:
Focused test and expected failure:
Red command and observed failure:
Green command and observed result:
Refactor / broader checks and results:
Environment limits and remaining manual checks:
```

Report what was actually executed and any untested layers. Tests with fakes
show contract behavior, not physical device operation, GUI usability, Debian
package correctness, or a release-ready system. If a check is blocked or
fails, distinguish changes you caused from baseline problems and do not claim
validation succeeded. For a security-sensitive change, request an independent
review and relevant integration/manual acceptance before release.
