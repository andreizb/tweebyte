# Quality gates

Use this page to decide what evidence a Tweebyte change needs. [Testing](testing/README.md#choose-verification-for-the-change) has the commands; [Architecture](ARCHITECTURE.md) defines the shared API and runtime boundaries.

| Change | Evidence before integration |
|---|---|
| Documentation | Check edited links and anchors, and verify claims and example commands against their source. |
| Benchmark harness or cleanup | Check shell syntax and run focused tests for completion, failure, cancellation, and open writers. Use disposable fixtures; run a bounded workload only when measurement behavior changes. |
| Service behavior | Test the affected module and its counterpart when their shared behavior changes. Require at least 90% line and 90% branch coverage per affected service; keep async/reactive test counts within 5% per service pair. |
| Shared runtime, gateway, or API contract | Run applicable module gates and the shared functional-equivalence suite against both stacks. Keep fixtures and expectations shared. |
| CI workflow or security scan | Check workflow syntax, then exercise the changed path in a PR that cannot deploy. Inspect each required scanner result, not just the workflow badge. Missing or incomplete reports are not clean scans. |

## Integrating work from parallel tasks

- Use separate checkouts for independent changes. Keep a single writer for a shared checkout, result directory, or deployment. Agree on the required output and who integrates it before work starts.
- Return the exact commit or patch, the checks and their results, and anything still unverified. Review each change before combining it; rerun the affected checks on the combined revision.
- Keep scope and evidence together. A passing focused test is not a new full-system sign-off; a skipped optional integration is not a pass for that integration.

## Preserve evidence

- Delete raw benchmark samples only for the exact owned run, after complete aggregation and writer exit. Keep failed or interrupted results and honor `KEEP_RAW=1`. See [cleanup lifecycle](testing/performance/README.md#cleanup-lifecycle).
- Keep historical figures in [testing/RESULTS.md](testing/RESULTS.md) unchanged unless the corresponding measurements are verified. Keep separate runtime topologies separate; label assumptions and limits when reporting results.
- If a required check cannot run, report why. Do not disable a gate or widen a suppression to turn CI green.
