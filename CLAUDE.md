# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**SMS Sentry** is a single-module Android app (Kotlin, Jetpack Compose) that ingests SMS
messages, classifies them **offline** into Personal / Finance / Reminder / Spam, and surfaces
finance ledgers, reminders, and a peer-to-peer sync feature. It originated from a Google AI
Studio export. There is **no Gemini/LLM runtime path** despite what `metadata.json` / `.env`
suggest — see [docs/architecture.md](docs/architecture.md).

## Workflow rules (mandatory)

1. **Plan before changing.** For any change to the project, first write a full plan to the
   `plans/` folder. Name the file `yyyymmdd_hhMMss_<short-slug>.md` (date+time prefix, local
   time). The plan must include:
   - the list of files to be changed,
   - what the issue is,
   - the plan for the fix.

   **MANDATORY APPROVAL GATE — you MUST get explicit consent before implementing.**
   - After writing the plan, STOP. Do not edit, create, or delete any project file
     (other than the plan file itself) until the user approves.
   - Present the plan and explicitly ask the user to approve it (e.g. "Do you approve
     this plan?"). Then WAIT for the user's reply.
   - Proceed ONLY on an explicit, affirmative approval (e.g. "yes", "approved", "go ahead").
     Silence, a question, a clarification, or an ambiguous reply is NOT approval — ask again.
   - If you change the plan after feedback, re-present it and get approval again.
   - The only exception is if the user explicitly tells you to skip the plan/approval for a
     specific change. A general earlier "go ahead" does not carry over to later changes.

2. **Log after changing.** After implementing a plan, write a change log to the `change_log/`
   folder. Name the file `yyyymmdd_hhMMss_<short-slug>.md` (date+time prefix, local time),
   describing what was changed and referencing the plan it implements.

## Detailed docs

Read these on demand rather than loading everything up front:

- [docs/build-and-test.md](docs/build-and-test.md) — build/install/test commands (no Gradle
  wrapper), single-test syntax, Roborazzi screenshots, and signing gotchas.
- [docs/architecture.md](docs/architecture.md) — module layout, the single ingestion funnel
  (`SmsRepository.processAndInsertMessage()`), data/engine/ui/receiver/util packages, and the
  non-obvious behaviors (offline-only classification, not a default SMS app, demo seeding,
  insecure-by-design P2P sync).
