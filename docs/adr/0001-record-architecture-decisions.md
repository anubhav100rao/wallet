# 1. Record Architecture Decisions

Date: 2026-05-10

## Status

Accepted

## Context

We need to record the architectural decisions made on this project so that future contributors (and our future selves) understand **why** choices were made, not just **what** was built.

Without a structured decision record, context is lost in Slack threads, PR comments, and memory. When revisiting a design six months later, the reasoning has evaporated.

## Decision

We will use Architecture Decision Records (ADRs), as described by [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

Every cross-cutting technical decision that affects multiple components or is hard to reverse should produce an ADR in `docs/adr/`.

ADRs are numbered sequentially (`NNNN-title.md`) and are **immutable once accepted** — if a decision is reversed, a new ADR supersedes the old one (the old one's status changes to `Superseded by ADR-NNNN`).

### ADR Format

```markdown
# N. Title

Date: YYYY-MM-DD

## Status

Proposed | Accepted | Deprecated | Superseded by [ADR-NNNN](NNNN-title.md)

## Context

What is the issue that we're seeing that is motivating this decision or change?

## Decision

What is the change that we're proposing and/or doing?

## Consequences

What becomes easier or more difficult to do because of this change?
```

## Consequences

- Every significant decision has a discoverable, permanent record.
- New team members can read the ADR log to understand the system's evolution.
- We accept the overhead of writing ~1 page per decision. This is a feature, not a bug — if a decision isn't worth one page, it probably isn't worth an ADR.
- Superseded ADRs remain in the repo as historical context.
