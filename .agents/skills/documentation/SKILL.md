---
name: documentation
description: Write, review, and restructure readable English project documentation with a Getting Started-first README, mandatory standard sections, structured advanced docs, and validated Mermaid sequence and C4-style diagrams. Use when creating or updating READMEs, guides, architecture documentation, or documentation navigation.
---

# Readable project documentation

## Intended use

Use this skill to create, review, or restructure a project's README and
supporting documentation, or to keep documentation aligned with a code change.

Trigger examples:

- "Write a README that helps a new user get started quickly."
- "Move advanced configuration and architecture details into docs/."
- "Document this use case and its components with Mermaid diagrams."

## Non-goals

- Rewriting unrelated documentation or changing application behavior.
- Inventing installation steps, features, hardware support, or license terms.
- Generating a documentation website or adopting a new toolchain by default.
- Adding diagrams or empty pages just to satisfy a template.

## Inputs, outputs, and permissions

Inputs: intended readers and tasks, implemented behavior, verified commands,
repository conventions, existing docs/license, and target Markdown/Mermaid
renderers.

Outputs: a concise README, navigable supporting docs, appropriate diagrams,
and a report of validation performed and remaining limitations.

Inspection is `read-only`. Documentation edits require authorized
`file-editing`; bounded lint, link, and render checks use `code-execution`.
Respect repository instructions and applicable shared policy. Do not install
system packages, exercise privileged or destructive examples, commit, push, or
publish merely to validate documentation. Apply the relevant approval rules.

## Writing rules

- Default to **English** for prose, headings, captions, and diagram labels unless
  the user explicitly requests another language. Preserve public identifiers,
  proper names, and intentionally maintained translations.
- Write for the reader's task: short paragraphs, direct sentences, descriptive
  headings, and numbered procedures when order matters. Explain unfamiliar
  terms on first use. Avoid marketing filler and unexplained jargon.
- Prefer one clear example to a long exhaustive listing. Use tables only when
  they make comparison easier. Keep prerequisites and warnings near the step
  they affect.
- Distinguish implemented behavior, proposed work, and unverified assumptions.
  State platform/version limitations. Never advertise support without evidence.
- Use relative links for repository pages and descriptive link text. Keep
  important guidance in text as well as diagrams; visuals must not be the only
  explanation.

## Workflow

### 1. Verify the source of truth

Inspect existing documentation, manifests, CLI help/API contracts, tests, CI,
and license notices relevant to the task. Confirm actual commands and paths
before documenting them. Preserve user edits and the requested scope.

Identify the primary reader and shortest safe path to a useful result. Decide
which details belong on the landing page and which require a linked guide.
Do not transform a planning document into a claim of implemented functionality.

### 2. Put Getting Started first in the README

A project title and a brief tagline may precede it, but **Getting Started must
be the first substantive section**, before Overview or a long diagram.

Use this section order:

```text
# Project name
## Getting Started
## Overview
## Installation
## Usage
## Development / Testing
## Contributing
## License
```

All seven sections are required. Additional top-level sections, if genuinely
needed, must not disrupt the relative order of the six standard sections from
Overview through License. Prefer links or subsections over a growing README.

- **Getting Started:** the minimal prerequisites, fastest supported setup path,
  one useful action, expected result, and links to fuller installation/usage.
  Keep it short and safe to copy. If the project is not runnable yet, state that
  and explain the current supported activity instead of inventing a quickstart.
- **Overview:** purpose, intended audience, key capabilities, scope, and current
  maturity/support limits.
- **Installation:** supported platforms and prerequisites, a concise canonical
  install path, and links to alternatives and troubleshooting.
- **Usage:** representative everyday examples and a link to full reference.
- **Development / Testing:** supported toolchain, concise setup and build/test
  commands, and links to detailed test or development guidance. Identify
  hardware-dependent checks separately from normal tests.
- **Contributing:** how to report issues or propose changes; link to
  `CONTRIBUTING.md` if present. Do not invent a contribution policy.
- **License:** accurate license identification and a link to the actual license
  file. Preserve attribution. If no license exists, explicitly state that;
  never imply the project is licensed under a guessed license.

Getting Started is intentionally before the required standard sequence; it does
not replace Overview, Installation, or Usage. Keep repeated commands consistent
and leave advanced variants in a single canonical guide.

### 3. Structure advanced material under docs/

Keep detailed configuration, architecture, security/permissions, integration,
troubleshooting, API/CLI reference, and test strategy out of the README's main
narrative. Group them by reader purpose, adapting existing conventions:

```text
docs/
  README.md                 # navigation and suggested reading paths
  guides/                   # installation, configuration, troubleshooting
  reference/                # CLI/API/options and compatibility reference
  architecture/             # components, runtime interactions, security
    decisions/              # architecture decision records, when needed
  development/              # contributor setup and testing details
```

This is a suggested layout, not a requirement to create every directory. Create
only useful, populated pages; a small project may need just a few documents.
Preserve stable paths where possible. When moving a page, update inbound links
and anchors in scope, and account for published links rather than breaking them
silently.

Link each important guide from the relevant README section and from
`docs/README.md`. Avoid orphan pages and duplicate sources of truth. Give each
page a clear purpose, prerequisite/context, useful content, and related links.

### 4. Use the right Mermaid diagram

Add a diagram only when it makes relationships or behavior easier to understand.
Put substantial diagrams beside the explanation in the relevant docs page, not
before the README quickstart. Introduce the diagram and explain its important
conclusion in plain text.

- **Runtime behavior or use cases:** use `sequenceDiagram`. Show actors,
  participants, ordered interactions, and meaningful failure/alternative paths.
- **Logical architecture or components:** use a **C4-style view** in portable
  Mermaid `flowchart TB` syntax. Pick one abstraction level, label people,
  systems, containers, or components explicitly, show boundaries, and label
  relationships. Do not mix container and component levels without explanation.
- Use a context view for system scope, a container view for applications/services,
  and a component view for internals. "Container" here means a C4 deployment/run
  unit, not necessarily a Docker container.
- Native Mermaid `C4Context`, `C4Container`, or `C4Component` syntax is not
  supported by every viewer, including Zed's documented diagram-type set. Use
  the flowchart representation unless every required renderer is confirmed to
  support the native C4 syntax.

Keep diagrams small and consistent with the code/evidence. Prefer vertical
layout, simple stable IDs, quoted descriptive node labels, and a manageable
number of actors/components. Avoid inline HTML, hard-coded colors, custom
`classDef` styling, and `%%{init}%%` directives; let the viewer theme them.
Use fenced `mermaid` blocks. See [reference patterns](references/patterns.md)
for illustrative sequence and C4-style component examples.

### 5. Validate before declaring the documentation complete

1. Verify the README starts with Getting Started and retains every required
   section in order. Review English, readability, and accuracy.
2. Check relative file links and heading anchors using the project's tools or
   the target renderer's slug rules. Check renamed paths and code fences too.
3. Confirm examples against actual help, tests, and supported environments.
   Run safe, bounded examples when practical; report what was not executed.
4. Extract **every added or changed Mermaid block**, including examples in
   supporting files, and parse/render it with the target-compatible Mermaid
   version. Prefer existing CI/tooling; otherwise use an isolated temporary
   Mermaid CLI environment, not an unsolicited project dependency.
5. For an extracted `diagram.mmd`, a basic render check is
   `mmdc -i diagram.mmd -o diagram.svg`. Use actual temporary paths and existing
   browser/tool configuration. Record the renderer version and inspect the
   output when a preview is available; check legibility, arrows, and boundaries.
6. Fix syntax errors and rerun the affected diagrams. Passing Markdown lint,
   code-fence checks, or a regex scan does **not** prove Mermaid syntax is valid.
   Rendering with one version also does not prove another viewer supports it.
7. If rendering is unavailable, perform static review and report **Mermaid
   render validation not completed**, with the reason and next command/tool
   needed. Do not claim diagrams passed or hide unverified syntax behind an
   image. For publishing, resolve the missing validation or explicitly agree
   the outstanding limitation with the user.

Finish by listing changed pages, what moved, navigation updates, checks actually
run, renderer/version if used, and remaining limitations. Do not claim the
repository structural validator executes documentation or behavioral checks.
