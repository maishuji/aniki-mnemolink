# Local Ollama generation (Linux desktop)

The suggestion-only Ollama adapter is committed in `9935282`. Desktop UI integration through `DesktopGenerationController` is implemented; the controller defaults to **Local Ollama — Qwen3 14B** (`qwen3:14b`) and also offers **Local Ollama — Qwen2.5 14B** (`qwen2.5:14b`) and **Offline mock demo**. The selector and confirmation flow below describe implemented behavior, not a recorded UI-click result. Android remains mock-only.

## Local service and model requirements

Use a **trusted, locally administered Ollama installation with local model weights and local inference**. A loopback URL alone does not prove inference is local: a local proxy or cloud-backed model alias can forward content elsewhere. Do not use cloud-backed aliases or substitute remote-backed models under the supported names. Review the local service's configuration, model provenance, and logging; the companion cannot establish these properties from an HTTP response.

The desktop application uses the fixed endpoint `http://127.0.0.1:11434/api/chat`. There is no remote/custom endpoint UI, cloud credential setup, automatic model download, or automatic retry. Starting the companion, selecting a backend, loading Anki input, or approving a draft does not send a generation request. AnkiConnect is not needed for manual-input generation.

With your locally managed Ollama service available, inspect models and running inference:

```sh
ollama list
ollama ps
```

`ollama list` shows installed models; `ollama ps` shows currently loaded models and processor placement, not a guarantee that every future request will use the same placement. Both `qwen3:14b` and `qwen2.5:14b` are already present on the development machine. A previous hardware observation on an RTX 3080 Ti Laptop GPU with 16 GB VRAM showed 100% GPU placement. This is not a live test of this adapter, a UI test, or a performance/VRAM guarantee for another machine.

If a supported model is absent, the user may explicitly install it manually:

```sh
ollama pull qwen3:14b
# Optional alternative:
ollama pull qwen2.5:14b
```

Each pull uses the network and roughly 9 GB of model storage (depending on tag/version); allow additional runtime memory and free disk space. Pull only the models you intend to use. MnemoLink never runs these commands or installs models automatically. Manage Ollama's service using your installation's normal service controls; documentation/test checks do not start an unbounded `ollama serve` process.

## Explicit generation and local approval

The desktop UI implements this flow:

1. Enter concept/context manually or explicitly load a reviewed plaintext Anki preview. Loading is not consent to generate.
2. Select Qwen3 (default), Qwen2.5, or offline Demo. A change to a different backend requires confirmation only if a draft, local approval, or in-flight generation exists; confirming clears draft/approval and cancels the previous request. With empty idle state, switching is immediate and sends no request. Canceling the dialog leaves the current backend and workflow unchanged; reselecting the same backend is not a reset.
3. Every Ollama Generate or Regenerate action shows a confirmation dialog containing the **current concept, context, and exact model**. Cancel/dismiss sends nothing. Confirmation authorizes only that request; it is not persistent content-sharing consent. Confirmation checks that the input is still current and generation is allowed before dispatch.
4. The request sends only concept/context as source data, alongside the selected model and fixed prompt/options. No note IDs, field names, raw source HTML, destination field/value, collection metadata, API keys, or previous drafts are included.
5. Review and edit the returned suggestion. Approval remains **local — not saved to Anki**. Source changes invalidate the old draft/approval; draft edits revoke approval. No write operation exists.

Offline Demo remains deterministic and makes no server requests. Smoke mode always selects Demo regardless of the normal Qwen3 default; it contacts neither Ollama nor AnkiConnect.

## Prompt and request limits

The system prompt asks for a brief French mnemonic: one to three plain-text sentences, without preamble, markup, or reasoning. Invented associations must be presented as mnemonic images/fiction, **never as real etymology** or invented factual explanations. Concept/context are serialized into a JSON user message and explicitly treated as **untrusted data, not instructions**. No tools are supplied; tool-call responses are rejected. Prompt policy is not a factuality or prompt-injection guarantee: review every suggestion.

| Setting | Desktop adapter value |
| --- | --- |
| Endpoint | `http://127.0.0.1:11434/api/chat` |
| Call/read timeout | 60 seconds |
| Maximum response body | 256 KiB, including unknown-length/chunked bodies |
| Streaming | `false` |
| Context window (`num_ctx`) | 2048 tokens |
| Output budget (`num_predict`) | 384 tokens |
| Thinking (`think`) | `false` |
| Model retention (`keep_alive`) | `2m` |

Redirects, proxy routing, and automatic connection retries are disabled. Input and draft validation remain bounded by the shared workflow. The parser requires the expected model, completed assistant response, and `done_reason: stop`. Truncation (`done_reason: length`), malformed/incomplete envelopes, tool calls, oversized bodies, and invalid/unsafe draft content are rejected rather than partially accepted or silently truncated. Errors are fixed safe categories/messages, not server bodies, prompts, or note content.

Cancellation cancels the HTTP call; responses are closed after reading. Backend replacement closes the previous service. Window/controller disposal closes the workflow and Ollama client, cancels owned requests, evicts pooled connections, and shuts down its executor without canceling the caller's scope. Client cleanup does not stop the independently managed Ollama server; `keep_alive: 2m` requests bounded model retention there.

The adapter constructor permits validated loopback endpoints and shorter timeouts for fixture tests; this is not a user-configurable remote/custom endpoint feature.

## Verification status

Fixture coverage comprises 14 Ollama adapter tests, 10 desktop controller tests, and 2 core safe-error tests. Adapter fixtures use MockWebServer with synthetic data on ephemeral loopback ports; controller tests use fake services. These fixtures do not require an installed model or live Ollama server.

The separate opt-in `OllamaLocalIntegrationTest` **passed** with installed `qwen3:14b`, using synthetic concept `ubiquitous` and context `Mot anglais : présent partout.` through the adapter's 60-second bounded request. It checks shared draft validity, not factual quality or UI behavior. By default it is skipped via a JUnit assumption unless `MNEMOLINK_OLLAMA_TEST=true`; the skipped test makes no Ollama request.

To deliberately rerun it against your trusted local service and already installed model:

```sh
MNEMOLINK_OLLAMA_TEST=true ./gradlew -PdesktopOnly=true :desktop:test --tests '*OllamaLocalIntegrationTest' --rerun-tasks
```

This opt-in command sends only synthetic concept/context to the fixed local endpoint, performs local inference, and consumes local compute; it never downloads a model or starts a server. No live Anki note read/write, rendered UI-click validation, or final packaging result is claimed. See [test coverage and pending manual checklist](testing.md#local-ollama-coverage-and-manual-verification) and [Linux setup](desktop-linux.md).
