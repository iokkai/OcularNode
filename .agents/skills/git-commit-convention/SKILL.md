---
name: git-commit-convention
description: Enforce standard Conventional Commits specification for formatting commit messages and release notes. Use whenever generating, drafting, or proposing Git commit messages.
---

# Git Commit Convention Guide

Whenever committing changes or drafting commit messages for this repository, strictly adhere to the following Conventional Commits format and types:

## Format
```
<type>(<scope>): <short description>

[optional body: detailed explanation of what changed and why]
```

## Commit Types & Meanings

| Type | Description | Scope / Use Cases |
| :--- | :--- | :--- |
| **`feat`** | A new feature | `feat(camera): ...`, `feat(thermal): ...`, `feat(ui): ...` |
| **`fix`** | A bug fix | `fix(camera): ...`, `fix(ota): ...`, `fix(webrtc): ...` |
| **`docs`** | Documentation changes only | `docs: ...`, `docs(readme): ...` |
| **`style`** | Code style / formatting changes (whitespace, formatting, missing semi-colons, **no code logic change**) | `style: ...`, `style(format): ...` |
| **`refactor`** | A code change that neither fixes a bug nor adds a feature (**no behavior change**) | `refactor(service): ...`, `refactor(data): ...` |
| **`perf`** | A code change that improves performance (execution speed, memory/CPU reduction) | `perf(camera): ...`, `perf(buffer): ...` |
| **`test`** | Adding missing tests or correcting existing tests | `test(recorder): ...`, `test(core): ...` |
| **`chore`** | Changes to build process, auxiliary tools, libraries, or CI/CD configuration | `chore(gradle): ...`, `chore(ci): ...` |
| **`revert`** | Reverts a previous commit | `revert: ...` |

## Rules & Best Practices
1. **Lowercase Type**: Commit type must always be lowercased (`feat`, `fix`, `refactor`, etc.).
2. **Clear Scope (Optional but Recommended)**: Specify the module/subsystem when applicable (e.g. `camera`, `audio`, `viewer`, `ota`, `thermal`).
3. **Imperative & Present Tense**: Write in a concise, active tone (e.g., `fix(camera): resolve slow-motion video recording`).
4. **No Full Stop**: Do not end the subject line with a period `.`.
