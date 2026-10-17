# End-to-end tests

Tests that start a real IntelliJ IDEA with the plugin zip and drive it, with IDE Starter and the UI Driver, in the
monolith and in split mode (a backend and a JetBrains Client as separate processes). The setup follows the one of
`highlighting-helper` (its `docs/e2e-tests.md`), trimmed to what a Git plugin without content modules needs.

## Tests

- `GitDeletePreviousBranchTest` (monolith + split): with `master` checked out after `feature` from the terminal before
  the IDE starts, Delete Previous Branch deletes `feature`; run again, it refuses with a notification, since the
  previous branch no longer exists; after `git checkout -b topic` it refuses to delete the protected `master`; after
  `git checkout -b next` it deletes `topic`.

## Running

```
./gradlew e2eTest
./gradlew e2eTest --tests '*GitDeletePreviousBranchTest*'
```

`./gradlew test` doesn't run them. Each test starts an IDE (about 30–60 s, twice: once per mode).

- The IDE is the build we compile against (`starterVersion` in `build.gradle.kts`, passed as `e2e.ide.build`). The first
  run downloads its installer (about 1.5 GB) into `out/ide-tests/installers`; later runs reuse it.
- The IDE window opens, but the tests don't use OS input, so the machine can be used meanwhile.
- A test's IDE logs and screenshots are in `out/ide-tests/tests/IU-<build>/<test>/.../log/`; its project is in
  `out/ide-tests/projects/<test>`.

## Wiring

- A source set `src/e2eTest`, package `com.max.medvedev.githelper.e2e`, and a plain `Test` task `e2eTest` that depends
  on `buildPlugin` and passes the zip as `path.to.build.plugin`, plus `e2e.ide.build`.
- Not `testFramework(TestFrameworkType.Starter)`: it excludes everything the IDE bundles (the stdlib, coroutines) from
  Starter's artifacts, as if the tests ran inside the IDE. The build declares the Starter and Driver artifacts directly
  at the platform build number and excludes the original `kotlinx-coroutines-core` (Starter uses the IntelliJ fork).
- On 253 the products (`IdeProductProvider.IU`) are in `ide-starter-squashed`; `ide-starter-product-idea-ultimate`
  isn't published for it.
- Starter's JUnit 5 extensions are registered by `ServiceLoader`, so `src/e2eTest/resources/junit-platform.properties`
  turns on extension autodetection.

## Test project

`runIdeWithPlugin` (`E2eIde.kt`) creates a fresh project directory for each run with only `.idea/vcs.xml`, which maps
the project root to Git, and lets the test set it up before the IDE starts (`setUpProject`), e.g. with `GitRepo` from
`Git.kt`, which runs the `git` command line. It installs the plugin zip (in split mode on both sides), starts the IDE,
runs the test with the Driver and closes the IDE. Then it fails the test if an `idea.log` (in split mode the backend's
or the frontend's) has an error record from our code or blaming our plugin, or a `NoClassDefFoundError`. The IDE runs
without the bundled Full Line Code Completion, which unpacks its model under a visible indicator after indexing.

`waitForProjectReady` waits until the project is open, the IDE has found its Git repository and the status bar has had
no indicators for 2 s, rather than the SDK's `waitForIndicators`, which waits 10 s.

## Reading and driving the IDE

- The tests check the repository with the `git` command line (`GitRepo`), not through the IDE.
- A change made from the command line during a test is followed by `refreshGitRepositories`, which calls
  `GitRepository.update()` on the backend: the IDE would otherwise notice it only from the file watcher or when its
  window gets activated, which the test IDE's window never does.
- The plugin depends on the Git plugin, so in split mode its actions run on the backend; `runGitHelperAction` invokes
  them there (`invokeGlobalBackendAction`) in both modes, with the project frame as the context.
- Notifications are read from the backend's `ActionCenter` (`notificationTexts`, `waitForNotification`).
- Split mode: the 253 Starter has no `IdeRunMode`. `E2eIde.kt` defines one that sets the global
  `ConfigurationStorage.splitMode` (Starter's test container reads it, and creates the frontend's context) and binds
  `RemDevDriverRunner` in Starter's DI container (the Driver runner defaults to `LocalDriverRunner`, which starts only
  the backend, and the Driver then talks to it). `runIdeWithPlugin` resets both to the monolith after each run.

## Known problems

- When a test starts, Starter kills every process whose command line contains `ide-tests`, on the whole machine: also
  the IDEs of another project's e2e tests running at the same time (e.g. `highlighting-helper`'s), and a shell that runs
  Gradle if its command line mentions `out/ide-tests/...`. Don't run e2e tests of two projects at once, and read the
  logs in a separate command.
- Gradle's `--tests` filter doesn't match a parameterized case by its display name (`[2] SPLIT`); filter by the
  class, e.g. `--tests '*GitDeletePreviousBranchTest*'`.
