# Architecture

MnemoLink is an independent Android companion. Start with a single application module organized by feature and adapter; extract modules when a real boundary needs reuse or build isolation.

## Boundaries

- Compose screens render state and emit events.
- ViewModels coordinate generation and approval; they do not modify Anki data directly.
- Domain models and validation remain independent of Android UI/networking.
- `MnemonicService` only returns a suggestion. It has no collection-writing capability.
- Future Anki and provider adapters live behind small interfaces.

There will be two explicit entry modes: standalone note selection/API save and reviewer-launched result-only generation. One request has one save owner. The return-only companion must never fall back to an API write.

## Initial limits

The first app increment uses deterministic, clearly labeled demo suggestions. It has no network or database permission. Approval is local workflow state, not a claim that a note was saved.

The working application ID is `dev.mnemolink.app`; confirm ownership and final distribution naming before publishing. No production license or provider choice is assumed.
