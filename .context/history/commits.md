# Commit History

## feat(settings): add project path prefix setting

- Context-Id: `06ce3b8f-cda2-4541-8328-b8c6a62a379f`
- Branch: `master`
- Files:
  - `build.gradle.kts`
  - `src/main/kotlin/com/github/aifolderpath/PathResolver.kt`
  - `src/main/kotlin/com/github/aifolderpath/settings/ProjectPathSettings.kt`
  - `src/main/kotlin/com/github/aifolderpath/settings/ProjectPathSettingsConfigurable.kt`
  - `src/main/resources/META-INF/plugin.xml`
- Decisions:
  - Use project-level PersistentStateComponent so prefixDirectory is scoped to the current project.
  - Register a projectConfigurable for the prefix directory setting instead of extending global shortcut settings.
  - Apply the prefix in PathResolver finalization so all AI path outputs share the same behavior.
  - Bump plugin version to 1.2.0 for the feature release.
- Tests:
  - git diff --check passed; Kotlin compile task was blocked by local hook.
