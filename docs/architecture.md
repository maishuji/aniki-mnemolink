# Architecture

MnemoLink is an independent Android companion. Start with a single application module organized by feature and adapter; extract modules when a real boundary needs reuse or build isolation.

## Boundaries

- Compose screens render state and emit events.
- ViewModels coordinate generation and approval; they do not modify Anki data directly.
- Domain models and validation remain independent of Android UI/networking.
- `MnemonicService` only returns a suggestion. It has no collection-writing capability.
- Future Anki and provider adapters live behind small interfaces.
- `AppContainer` supplies the deterministic service; `MainActivity` provides a ViewModel factory and collects state with lifecycle awareness.
- `GenerationViewModel` invalidates a request before canceling its job, preventing a late noncooperative response from replacing newer state. Source edits revoke the draft/approval; draft edits revoke approval.
- Input/output lengths are bounded. Initial plain-text validation rejects field separators, Anki media/template markup, and HTML-like angle brackets. This is a restricted demo, not general HTML/cloze/formula support.

There will be two explicit entry modes: standalone note selection/API save and reviewer-launched result-only generation. One request has one save owner. The return-only companion must never fall back to an API write.

## Initial limits

The first app increment uses deterministic, clearly labeled demo suggestions. It has no network or database permission. Approval is local workflow state, not a claim that a note was saved. The UI explicitly says so, offers editable input/drafts and discard, and supports system dark mode and scrollable large-font layouts.

Drafts remain in the ViewModel only and are lost after process death. Rotation retention has a device test, but requires execution on an emulator/phone before claiming device validation. The `EntryMode` domain type anticipates standalone and return-only modes; no host result endpoint is exposed yet.

The working application ID is `dev.mnemolink.app`; confirm ownership and final distribution naming before publishing. No production license or provider choice is assumed.
