# MnemoLink for AnkiDroid

An Android companion app for AnkiDroid that will generate AI-assisted mnemonics, let you review and edit them, and save approved memory aids to your notes.

## Delivery approach

Implementation starts with the companion application. LLM configuration, credentials, prompting, and draft editing stay outside AnkiDroid. A later thin AnkiDroid integration will provide a native reviewer action and own the final save for that entry point.

The standalone companion will use stock AnkiDroid's public API to find and update existing notes. It will require an existing dedicated destination field, preserve unrelated note content, and never save generated output without explicit approval.

## Current status

Repository foundation only. Android scaffolding and the deterministic preview/edit/approval workflow are the first implementation increments. No LLM service, AnkiDroid integration, or note-writing functionality is implemented yet.

Private planning files, SDK paths, keys, build output, and personal Anki collections are excluded from Git. Do not add real note content or provider credentials to tests or documentation.

## Product boundaries

- Mnemonics belong to **notes**; card templates determine whether they are displayed.
- The native reviewer action is part of the integrated MVP, not something stock AnkiDroid can gain by installing the companion alone.
- A companion API save and a host-owned save are separate modes. They must never both execute for one request.
- Provider output is a suggestion, not a factual guarantee.

This is an independent project, not an official Anki or AnkiDroid application. Project licensing will be decided before distribution; absence of a license is not permission to redistribute.
