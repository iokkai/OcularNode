---
description: Always follow Conventional Commits standard when creating git commit messages
trigger: always_on
---

# Git Commit Convention Rule

When proposing or generating Git commit messages, always follow these Conventional Commit types:
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation changes only
- `style`: Code style / formatting changes (whitespace, formatting, missing semi-colons, no logic change)
- `refactor`: A code change that neither fixes a bug nor adds a feature (no behavior change)
- `perf`: A code change that improves performance
- `test`: Adding missing tests or correcting existing tests
- `chore`: Changes to build process, auxiliary tools, libraries, or CI/CD configuration
- `revert`: Reverts a previous commit

Format: `<type>(<scope>): <concise description>`
