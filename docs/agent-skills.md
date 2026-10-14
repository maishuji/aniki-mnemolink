# Project-local agent skills

## Use in Zed

Start a new Zed Agent conversation after installation so the skill catalog can
refresh. Skills live in `.agents/skills/<name>/SKILL.md`; they are development
instructions, not runtime application features or separate registered agents.

Invoke `/mnemonic-evaluator` to review a supplied word, reading, meaning,
candidate mnemonic, and learner objective. For example, ask it to assess a
synthetic Japanese sound-and-meaning cue for a French-speaking learner. It
returns an evidence-backed heuristic review, not proof of memory improvement
or permission to approve/save an Anki note.

Use `/git-workflow` for planning or preparing focused Conventional Commits, and
`/documentation` for project documentation. Implementation requests do not
authorize commits or pushes. The feature-branch and identity rules in
[`AGENTS.md`](../AGENTS.md) still apply.

## Installed collection

All nine canonical skills from `portable-agent-kit` are installed:

- `mnemonic-evaluator`
- `git-workflow`
- `documentation`
- `rust-tdd`
- `asus-keyboard-backlight-discovery`
- `dreamcast-hardware`
- `kos-build-environment`
- `kos-deploy-debug`
- `kos-ports-integration`

Select skills by task, not just availability. The Rust, ASUS, and Dreamcast/KOS
skills do not apply to ordinary Kotlin/Compose changes. Installing them grants
no tool permissions and performs no hardware operations. Agent profiles from
the kit's `agents/` directory are not included by the skill installer.

## Source of truth and updates

Author and review skills in `portable-agent-kit/skills/`; do not independently
edit the installed copies. Copies do not synchronize automatically. Synthetic
behavioral evaluation scenarios live in the kit's `evals/skills/`; structural
validation does not execute those scenarios.

With this project checked out beside `portable-agent-kit`, run these commands
**from the `portable-agent-kit` root** to preview and install the collection:

```sh
python3 scripts/install_skills.py --all --destination ../anki_mnemotic/.agents/skills --dry-run
python3 scripts/install_skills.py --all --destination ../anki_mnemotic/.agents/skills
```

Identical copies are left unchanged. Differing copies are refused by default.
To update a specific skill, first inspect any local changes and preview the
backup/update using `--replace --dry-run`; after review, rerun without
`--dry-run`. For example, from the same kit root:

```sh
python3 scripts/install_skills.py --skill mnemonic-evaluator --destination ../anki_mnemotic/.agents/skills --replace --dry-run
python3 scripts/install_skills.py --skill mnemonic-evaluator --destination ../anki_mnemotic/.agents/skills --replace
```

Replacement saves a timestamped backup alongside the skills directory under
`.agents/.skills-skill-backups/`. Inspect backups before deleting or committing
anything; do not put private references or learner notes into skill copies.

## Application boundary

The evaluator supports review and quality-test design. It does not add language
profiles, evaluate generations inside the app, call Ollama/AnkiConnect, or write
notes. Application input contracts, synthetic benchmarks, and release criteria
remain in this repository; see the
[Japanese mnemonic delivery plan](japanese-mnemonics-plan.md).
