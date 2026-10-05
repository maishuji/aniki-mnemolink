# Repository instructions for agents

## Git identity and commit attribution

- Every commit must use the repository-local Git identity configured by the owner (`git config --local user.name` and `git config --local user.email`) for both author and committer.
- Verify that both local values are configured before committing. If either is missing, ask the owner to configure it; do not invent an identity or fall back to global configuration.
- Do not change the owner's Git identity or override it with agent-specific author/committer settings.
- Never add `Assisted-by`, `Co-authored-by`, or any other assistant/agent attribution to commit messages or trailers.

## Feature branches and pushes

- Every feature or repository change, including documentation and configuration, must be implemented on a dedicated `feat/<descriptive-name>` branch.
- Check the current branch and working tree before editing. Create or switch to the appropriate feature branch first, preserving existing user changes.
- Never create commits directly on `main`, and never push commits directly to `main`. Integrate feature branches through the owner's review/merge workflow.
- Do not push any branch unless the owner explicitly requests it.

## Before committing

- Review the diff and stage only files relevant to the requested change.
- Run checks appropriate to the change and report their results accurately.
- Confirm that the current branch is a feature branch and that author/committer identity uses the local configuration.
- Use a clear commit message without assistant attribution or co-author trailers.
