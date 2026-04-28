#!/usr/bin/env python3
"""Generate the attribution figure(s) the §5.12 manuscript section needs.

Reads each cell's <c>_<i>_prom.csv (in-run Prometheus time-series) under
testing-results/performance/k6/results_*/ and produces:

1. testing-results/attribution-summary.csv — per-(label, run) row of
   pool_active_max, queue_depth_max, rejections_delta, tasks_completed_delta.
   Joined with cells.csv via dir+filename later if needed.

2. testing-results/figures/attribution_<cell-label>.png — overlay time-series
   of pool_active, queue_depth, cumulative rejections delta, and rolling p99
   if k6 stdout times-series are available (otherwise just the three Prom
   gauges). One figure per cliff cell (rps=500 pool=400 W0/W1/W2).
"""
import csv
import os
import re
import sys
from pathlib import Path

ROOT = Path("/Users/andrei/Developer/tweebyte/testing-results/performance/k6")
OUT_SUMMARY = Path("/Users/andrei/Developer/tweebyte/testing-results/attribution-summary.csv")
OUT_FIG_DIR = Path("/Users/andrei/Developer/tweebyte/testing-results/figures")

# Try import matplotlib; if missing, skip plotting and just write the summary.
try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    HAVE_MPL = True
except Exception:
    HAVE_MPL = False
    print("matplotlib not available — will only write the summary CSV")

DIR_RE = re.compile(r"results_ai_streaming_(\d{8})_(\d{6})_")
PROM_FILE_RE = re.compile(r"^(\d+)_(\d+)_prom\.csv$")

def parse_prom_csv(path):
    rows = []
    with open(path) as f:
        reader = csv.DictReader(f)
        for r in reader:
            try:
                rows.append({
                    "t": int(r["t_secs"]),
                    "pool_active": float(r.get("pool_active") or 0),
                    "queue_depth": float(r.get("queue_depth") or 0),
                    "pool_size": float(r.get("pool_size") or 0),
                    "tasks_completed": float(r.get("tasks_completed") or 0),
                    "rejections_total": float(r.get("rejections_total") or 0),
                    "reactive_in_flight": float(r.get("reactive_in_flight") or 0),
                    "reactive_completed": float(r.get("reactive_completed") or 0),
                })
            except Exception:
                continue
    return rows

def cell_label_from_dir_and_runtxt(dir_path, run_txt):
    """Reuse the JSON summary embedded in run_txt to derive a (stack, workload, pool, rps, calibration_tag, campaign) label."""
    try:
        text = Path(run_txt).read_text()
        idx = text.find("{")
        if idx < 0: return None
        json_start = text[idx:].split("\n}", 1)[0] + "\n}"
        # Cheap regex extraction (avoid full json parsing for speed):
        def g(field, dflt=""):
            m = re.search(rf'"{field}":\s*"?([^"\n,}}]+)', json_start)
            return m.group(1).strip().strip('"') if m else dflt
        stack = g("stack")
        workload = g("workload")
        pool = g("pool_size") or "-"
        rps = g("target_rps") or "-"
        cal = g("calibration_tag") or "-"
        campaign = g("campaign") or "-"
        return f"{stack}_{workload}_p{pool}_rps{rps}_{cal}_{campaign}"
    except Exception:
        return None

def main():
    summary_rows = []
    cell_runs = {}  # (label) -> list of (run_idx, rows)
    for dir_path in sorted(ROOT.glob("results_ai_streaming_*")):
        for prom_csv in sorted(dir_path.glob("*_prom.csv")):
            m = PROM_FILE_RE.match(prom_csv.name)
            if not m: continue
            c = m.group(1); i = m.group(2)
            run_txt = dir_path / f"{c}_{i}.txt"
            if not run_txt.exists(): continue
            label = cell_label_from_dir_and_runtxt(dir_path, run_txt)
            if not label: continue
            rows = parse_prom_csv(prom_csv)
            if not rows: continue
            t_max = max(r["t"] for r in rows)
            pool_active_max = max(r["pool_active"] for r in rows)
            queue_depth_max = max(r["queue_depth"] for r in rows)
            tasks_delta = rows[-1]["tasks_completed"] - rows[0]["tasks_completed"]
            rej_delta = rows[-1]["rejections_total"] - rows[0]["rejections_total"]
            reactive_in_flight_max = max(r["reactive_in_flight"] for r in rows)
            summary_rows.append({
                "label": label,
                "run_idx": int(i),
                "t_secs_observed": t_max,
                "pool_active_max": pool_active_max,
                "queue_depth_max": queue_depth_max,
                "tasks_completed_delta": tasks_delta,
                "rejections_total_delta": rej_delta,
                "reactive_in_flight_max": reactive_in_flight_max,
                "prom_file": str(prom_csv),
            })
            cell_runs.setdefault(label, []).append((int(i), rows))

    if not summary_rows:
        print("no per-cell prom.csv files found; nothing to generate")
        return 0

    OUT_SUMMARY.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT_SUMMARY, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(summary_rows[0].keys()))
        writer.writeheader()
        writer.writerows(summary_rows)
    print(f"wrote {OUT_SUMMARY} with {len(summary_rows)} rows across {len(cell_runs)} cells")

    if not HAVE_MPL:
        return 0

    OUT_FIG_DIR.mkdir(parents=True, exist_ok=True)
    # Plot the cliff cells (rps=500 with pool=400 or pool=1600).
    for label, runs_list in cell_runs.items():
        # Only plot for cliff cells: rps=500, pool=400 or pool=1600 async, or reactive.
        if "rps500" not in label: continue
        # Aggregate per second across runs (mean of per-run values).
        # Simpler: just plot run 1 (or the first run that has data).
        run_idx, rows = sorted(runs_list)[0]
        ts = [r["t"] for r in rows]
        if "async" in label:
            pa = [r["pool_active"] for r in rows]
            qd = [r["queue_depth"] for r in rows]
            rej = [r["rejections_total"] - rows[0]["rejections_total"] for r in rows]
            fig, ax1 = plt.subplots(figsize=(10, 5))
            ax1.set_xlabel("Time (s)")
            ax1.set_ylabel("pool_active / queue_depth")
            l1 = ax1.plot(ts, pa, label="pool_active", color="tab:blue")
            l2 = ax1.plot(ts, qd, label="queue_depth", color="tab:orange")
            ax1.tick_params(axis="y")
            ax2 = ax1.twinx()
            ax2.set_ylabel("cumulative rejections (Δ)", color="tab:red")
            l3 = ax2.plot(ts, rej, label="rejections_total Δ", color="tab:red", linestyle="--")
            ax2.tick_params(axis="y", labelcolor="tab:red")
            lines = l1 + l2 + l3
            labels = [l.get_label() for l in lines]
            ax1.legend(lines, labels, loc="upper left")
        else:
            rin = [r["reactive_in_flight"] for r in rows]
            rcomp = [r["reactive_completed"] - rows[0]["reactive_completed"] for r in rows]
            fig, ax1 = plt.subplots(figsize=(10, 5))
            ax1.set_xlabel("Time (s)")
            ax1.set_ylabel("reactive_in_flight")
            l1 = ax1.plot(ts, rin, label="reactive_in_flight", color="tab:blue")
            ax2 = ax1.twinx()
            ax2.set_ylabel("cumulative completed (Δ)", color="tab:green")
            l2 = ax2.plot(ts, rcomp, label="completed Δ", color="tab:green", linestyle="--")
            ax2.tick_params(axis="y", labelcolor="tab:green")
            lines = l1 + l2
            labels = [l.get_label() for l in lines]
            ax1.legend(lines, labels, loc="upper left")
        plt.title(f"Attribution time-series — {label} (run {run_idx})")
        plt.tight_layout()
        outpng = OUT_FIG_DIR / f"attribution_{label}_run{run_idx}.png"
        plt.savefig(outpng, dpi=150)
        plt.close()
        print(f"wrote {outpng}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
