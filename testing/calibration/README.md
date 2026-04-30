# testing/calibration

Maven module + picocli CLI that drives an OpenAI-compatible LLM endpoint, collects per-stream timing samples, fits distributions, and emits `calibration.json` for the mock chat model to consume.

## Subcommands

```bash
java -jar testing/calibration/target/calibration-0.0.1-SNAPSHOT.jar <subcommand> [opts]
```

| Subcommand | Purpose |
|---|---|
| `collect` | Issue streaming requests against an OpenAI-compatible endpoint, record TTFT / ITL / token counts. |
| `fit` | Fit log-normal, gamma, Weibull, shifted-log-normal, and 2-component log-normal mixture families to the recorded samples; write results into `calibration.json`. |
| `refit` | Re-fit families against the persisted samples without re-collecting (useful when fit parameters or burst-threshold change). |
| `validate` | Two-sample Kolmogorov–Smirnov of mock draws against the recorded samples. Family selectable via `--itl-family={gamma,shifted_lognormal,lognormal_mixture}`. |

## Inputs

- An OpenAI-compatible streaming endpoint (LM Studio, `mlx_lm.server`, etc.) reachable on a `BASE_URL`.
- A model name the endpoint exposes on `/v1/models`.

## Output

`testing/calibration/calibration.json` carries the raw `ttft_samples`, `itl_samples`, `token_counts` arrays plus all fitted families with AIC/BIC. The mock at runtime reads:

- `ttft.lognormal.{mu, sigma}` for time-to-first-token,
- `itl_fits.gamma.{shape, scale}` and `itl_fits.p_burst` for the zero-inflated gamma ITL draw.

Calibration files without `p_burst` are accepted with `p_burst = 0.0`, collapsing the mock to pure-gamma behaviour.

## Mock consumption

The Spring AI `MockStreamingChatModel` (`async/tweet-service` and `reactive/tweet-service`) reads `calibration.json` at startup when `AI_MOCK_CALIBRATION_JSON=/path/to/calibration.json` is set in the service environment. The compose files bind-mount `./testing/calibration → /app/calibration:ro` for containerised runs.
