---
name: git-workflow
description: Plan and create small, focused Git commits using Conventional Commits, meaningful message bodies, repository-local identity, and no co-author or assistant attribution. Never commit directly on protected main or dev branches. Use when implementing work in incremental steps, organizing a diff, or preparing commits.
---

# Git workflow

## Intended use

Use this skill for everyday development when the user wants a step-by-step
implementation plan, a reviewable commit sequence, or help preparing and
creating commits.

Trigger examples:

- "Implement this feature in small, targeted commits."
- "Split these changes into logical commits."
- "Review my diff and prepare Conventional Commit messages."

## Non-goals

- Automatically committing whenever files change.
- Combining unrelated cleanup with the requested task.
- Managing releases, rewriting history, or publishing changes by default.
- Committing directly to the protected `main` or `dev` branches, even when asked.
- Changing the user's identity, Git configuration, hooks, or signing policy.

## Inputs and outputs

Inputs: the requested change, target repository, repository instructions,
existing working-tree and index state, local Git identity, and available checks.

Outputs: an ordered plan, one coherent change per step, validation evidence,
Conventional Commit messages, and a final summary. Include commit hashes only
when commits were actually created; otherwise provide proposed messages.

## Side effects and permissions

- Inspection is `read-only`; implementation and index/commit changes require
  authorized `file-editing`. Checks use bounded `code-execution`.
- A request to implement, plan, review, or draft messages is not permission to
  commit. Create commits only when explicitly requested. Permission for a
  defined local commit sequence covers its steps without repeated approval,
  unless scope or risk changes.
- **Never create a commit on `main` or `dev`. These branches are protected by
  this skill, regardless of user authorization or repository settings.** If
  asked to commit while on one of them, stop before staging. Propose a
  descriptive, unused feature-branch name and show its base, scope, and the
  `git switch -c <branch-name>` action. Ask for explicit approval to create and
  switch to that branch. Only after approval, recheck the worktree and branch,
  create the branch, verify it is checked out, and continue this workflow. Never
  stage or commit on the protected branch. Approval to create/switch branches
  authorizes only that operation; it does not authorize a commit unless the
  user's original request explicitly authorized the commit sequence. If the
  proposed branch already exists or changes cannot safely be carried over, stop
  and ask; do not reset, stash, overwrite, or switch to another branch silently.
- Do not create or switch branches, stash changes, amend, reset, rebase, or delete
  work without explicit authorization. Never discard work to make a diff clean.
- Follow repository instructions and applicable shared policy. Remote actions
  require `external-write` permission and explicit confirmation immediately
  before execution. Permission to commit is not permission to push.

## Step-by-step method

### 1. Inspect the correct repository

Set the working directory explicitly for every command. In a multi-folder
workspace, treat each Git repository independently; never stage or commit in a
neighboring repository by accident. Check the current branch before staging or
committing and refuse commits on the exact branch names `main` and `dev`, even
if the user asks for a direct commit.

Read repository instructions and inspect:

```sh
git --no-pager rev-parse --show-toplevel
git --no-pager branch --show-current
git symbolic-ref --quiet --short HEAD
git --no-pager --no-optional-locks status --short
git --no-pager diff
git --no-pager diff --cached
git --no-pager log -n 5 --oneline
```

Inspect relevant untracked files separately; `git diff` does not include them.
Record pre-existing changes and distinguish them from this task's changes.
If the branch is detached, or ownership of existing changes is unclear, resolve
that ambiguity before committing. If the branch name is exactly `main` or `dev`,
stop before staging. Propose a descriptive, unused feature branch based on the
current branch and show its name, scope, and `git switch -c <branch-name>`
command. Ask the user for explicit approval before creating or switching to it.
Once approved, verify the current branch and worktree again, run the approved
branch command, confirm the new branch is checked out, then continue with identity
checks and the ordinary workflow. Preserve all existing changes; if branch
creation/switching is not safe or the name already exists, stop and ask. Approval
to create a branch alone is not commit permission: proceed to commit only if the
user's original request authorized it. Recheck the branch before every commit.
Do not silently include someone else's work.

### 2. Verify repository-local identity before committing

Require both values to be present and non-empty in the target repository:

```sh
git config --local --get user.name
git config --local --get user.email
```

Use that local name and email for both author and committer. Never fall back to
an agent identity or global configuration when a local value is missing. Ask the
user for the exact missing value; set it with `git config --local` only when
explicitly authorized. Never invent a name or email or modify global settings.

Check the effective identities as well:

```sh
git var GIT_AUTHOR_IDENT
git var GIT_COMMITTER_IDENT
```

Compare their names and emails with the local values, ignoring timestamps.
Environment variables, worktree configuration, or other overrides can change
Git's effective identity. If either differs, stop and explain the conflict;
resolve it with the user before committing. Do not supply `--author`,
identity-changing `git -c` options, or `GIT_AUTHOR_*` / `GIT_COMMITTER_*`
overrides to substitute another identity.

### 3. Plan small, coherent steps

Explain the intended sequence before editing. For each step, identify:

1. One purpose and why it is needed.
2. The code, tests, and documentation belonging to that purpose.
3. The checks that establish it works.
4. A proposed Conventional Commit subject.

Keep each commit independently understandable, reviewable, and buildable when
applicable. Keep a behavior change with its regression tests and directly
related documentation. Separate an enabling refactor from a feature only if
both steps stand on their own.

"Small" means one logical concern, not one file or an arbitrary line limit.
Do not split a fix from the test that proves it, manufacture empty commits, or
leave intermediate commits broken just to increase the commit count. A simple
change may need only one commit.

### 4. Implement and validate one step

Make only the changes needed for the current step. Use existing project tools
and dependencies. Run the most targeted checks first, then broader checks when
warranted. Use bounded command runtimes and avoid watchers or interactive tools.

Inspect the resulting diff for unintended changes, secrets, generated noise,
and missing tests. Fix failures caused by this step before committing. For a
pre-existing failure or an unavailable check, record the exact limitation and
obtain agreement before creating a commit with incomplete validation. Do not
claim a check passed unless it actually ran and passed.

### 5. Stage only the intended change

Stage explicit paths; use hunk-level staging when one file contains multiple
concerns. Do not use `git add .`, `git add -A`, or `git commit -a` by default.
Never stage or commit on `main` or `dev`. If currently on either branch, return
to the protected-branch handoff in step 1 and obtain approval to create and
switch to a feature branch before staging. Check the current branch before
staging and again immediately before each commit.
Use non-interactive patch tools when interactive staging is unavailable.

If unrelated changes were already staged, do not commit or unstage them behind
the user's back. Ask how to preserve that index state before proceeding. If
hunks cannot be separated safely, explain the dependency instead of forcing a
split. Leave unrelated working-tree and untracked files untouched.

Review the complete index before each commit:

```sh
git --no-pager diff --cached --stat
git --no-pager diff --cached
git --no-pager diff --cached --check
```

Proceed only if the index contains exactly the intended step. Do not create an
empty commit.

### 6. Write a Conventional Commit message

Use this format:

```text
type(scope): imperative summary

Explain why the change is needed and the important behavior or design choices.
Describe compatibility impact when relevant.

Validation: list the checks actually run and their results or limitations.
```

- Use a lowercase type: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`,
  `ci`, `build`, `perf`, or `revert`. Use `style` for formatting-only changes.
- Include a short scope when it clarifies the affected component.
- Keep the subject under 72 characters, imperative, specific, and without a
  trailing period. Describe the change, not the agent's activity.
- For non-trivial changes, include a body explaining motivation, important
  changes or tradeoffs, and validation. Omit an unnecessary body for a trivial,
  self-explanatory edit. Do not pad messages with a file-by-file diff narration.
- Mark breaking changes with `!` before the colon and a `BREAKING CHANGE:`
  footer explaining migration. Add issue references only when known.
- Never add `Co-authored-by`, `Assisted-by`, or equivalent AI/tool attribution
  trailers or credits anywhere in the message. Do not add sign-off trailers
  automatically. Issue references and breaking-change footers are not assistant
  attribution and remain allowed.

Illustrative non-trivial message; replace validation with actual results:

```text
fix(cli): reject empty output paths

An empty output path previously reached file creation and produced an
unhelpful error. Reject it during argument validation so users receive an
actionable message before any file is written.

Add regression coverage for empty paths and preserve valid-path behavior.

Validation: targeted CLI regression tests passed.
```

A trivial documentation edit can use just:

```text
docs(readme): correct the installation path
```

#### Review the complete message before committing

Before invoking `git commit`, render and review the **exact assembled message**
that Git will store: header, blank line, body paragraphs, and any footers. Check
its Conventional Commit header, subject length/imperative wording, clear purpose,
factual validation results, and absence of forbidden attribution. Do not rely on
the command's source string as a preview; inspect the assembled text itself.

When using multiple `-m` arguments, each argument becomes a paragraph separated
by a blank line. Do not include literal backslash-n sequences such as `\n` or
`\n\n` inside an argument expecting the shell or Git to expand them. For example,
use separate `-m` options:

```sh
GIT_EDITOR=true git commit -m "fix(cli): reject empty output paths" -m "Reject the empty path before file creation." -m "Validation: targeted CLI regression tests passed."
```

**Prevent shell transformations between preview and Git.** Never put message text
containing backticks, `$`, backslashes, quotes, or other shell-significant content
inside a double-quoted shell command and assume it will be passed literally.
Shell parsing can execute command substitutions or alter the text, so a preview of
the source string is not proof of the arguments Git receives. Prefer invoking Git
with an argument vector and `shell=False` (for example, Python's
`subprocess.run(["git", "commit", "-F", message_path], shell=False, check=True)`).
Alternatively, write the already-reviewed exact message to a temporary message
file using a non-shell file API and invoke `git commit -F <path>`; ensure the file
contents are byte-for-byte the preview. Do not use shell interpolation or
`eval` to construct the command.

Immediately before committing, compare the exact message bytes/file or argument
values that will be passed to Git with the displayed preview. If the tool only
allows shell commands, avoid shell-significant characters in message arguments
or pass a safely created message file; do not proceed if exact equivalence cannot
be established. Afterward, compare the stored message with that same preview;
post-commit verification supplements, but never replaces, pre-commit review. If
the stored text differs, stop and report the problem. Do not amend or rewrite
history without separate explicit authorization.

### 7. Commit, verify, and repeat

Recheck both the current branch and effective identity immediately before each
commit. If the current branch is `main` or `dev`, stop without committing and use
the step 1 protected-branch handoff. After explicit branch-switch approval,
verify the new feature branch before continuing. A prior explicit request to
commit the defined sequence remains valid after the approved branch switch; an
approval to switch branches alone does not authorize a commit. On an allowed
branch, commit only after reviewing the exact assembled message as required in
step 6. Use a non-interactive command with the reviewed message supplied
explicitly. For example, for a trivial edit:

```sh
GIT_EDITOR=true git commit -m "docs(readme): correct the installation path"
```

For a detailed message, supply additional `-m` paragraphs or a reviewed message
file. When the body contains shell-significant characters, use the message-file
approach or a shell-free argument-vector API; do not type a double-quoted shell
command and assume it preserves the preview. Verify the exact passed message
before execution and the stored message afterward. Respect hooks and signing
requirements; do not use `--no-verify` or disable signing to get a commit through.
If a hook or repository rule requires forbidden attribution, stop and ask about
the conflict rather than bypassing it.

Verify the actual commit, not just the command's exit status:

```sh
git --no-pager show --no-patch --format=fuller HEAD
git --no-pager show --stat --oneline HEAD
git --no-pager --no-optional-locks status --short
```

Confirm author and committer match local configuration, the final message has no
forbidden attribution, and the changed paths match the intended scope. Hooks
may alter the message or files. If verification fails, stop and report it; do
not automatically amend or rewrite history.

Repeat steps 4–7 for each authorized commit. If commits were not authorized,
leave the edits uncommitted and report the proposed sequence; do not modify the
index just to simulate it.

### 8. Report the outcome

List each created commit's short hash, subject, purpose, and checks. If work
remains uncommitted, say so and provide proposed messages instead of hashes.
Mention outstanding limitations and preserved unrelated changes. Never claim
the repository is clean without checking, or imply commits were pushed when
only local work was performed.
