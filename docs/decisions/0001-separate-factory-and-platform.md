# 0001: Separate app creation from the shared platform

Status: Accepted

## Context

The Android App Factory creates a new Android repository and a public legal-site repository. A
generated repository then evolves independently and needs ongoing build, dependency, and quality
updates.

## Decision

Keep the Factory in the Magic App Dev Codex plugin. Maintain build conventions, quality gates, and
future runtime libraries in this independent platform repository.

The Factory will consume a released platform version after the platform has passed an isolated
sample build plus one non-Pulse app migration and one Pulse app migration. Generated apps do not
depend on a machine-specific sibling path.

## Consequences

- Factory templates stay focused on truthful project creation and legal-site ownership.
- Existing apps can adopt platform plugins without being regenerated.
- Platform source and Maven releases have one version line independent of the Codex plugin.
- Composite builds are a development and validation mechanism, not a committed requirement for
  generated apps.
