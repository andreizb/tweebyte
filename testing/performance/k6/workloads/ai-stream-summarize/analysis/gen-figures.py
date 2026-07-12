#!/usr/bin/env python3
"""Figure generator for AI-streaming result grids.

Reads a `report`-produced cells.csv (the 2026-06-14+ schema with p50/p95/p999
means, delivered_rps, error_rate, dropped_rate, and per-cell cpu/heap aggregates)
and emits the readable target-RPS curves the operator requires beyond the Java
`plot` command's p99-scaling + residency-collapse figures.

One figure family per (workload, transport, calibration_tag, campaign) group,
async and reactive overlaid:

  latency_percentiles_<group>.png  — p50/p95/p99 vs target RPS (log-y), both stacks
  p99_ratio_<group>.png            — async_p99 / reactive_p99 vs target RPS, marker at y=1
  delivered_rps_<group>.png        — delivered vs target RPS, with the ideal y=x line
  errors_drops_<group>.png         — SUT error_rate + k6 dropped_rate vs target RPS
  cpu_heap_<group>.png             — CPU avg/p95 (left axis) + heap avg/max MB (right) vs RPS

Dependency-light by design: stdlib csv + matplotlib only (no pandas — it is not
installed on the rig; numpy/matplotlib are). Mirrors gen-attribution-figures.py.

Usage:
  python3 gen-figures.py --cells-csv <cells.csv> --out-dir <dir> [--campaign LABEL[,LABEL...]]
"""
import argparse
import csv
from pathlib import Path

try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
except Exception as exc:  # pragma: no cover - environment guard
    raise SystemExit(f"matplotlib is required for gen-figures.py: {exc}")


def fnum(row, key):
    """Float of a CSV cell, or NaN for empty/missing/unparseable."""
    v = row.get(key, "")
    if v is None or v == "":
        return float("nan")
    try:
        return float(v)
    except ValueError:
        return float("nan")


def safe(s):
    return "".join(c if c.isalnum() or c in "._-" else "_" for c in (s or ""))


def load_cells(path, campaign_filter):
    rows = list(csv.DictReader(open(path)))
    if campaign_filter:
        wanted = set(campaign_filter.split(","))
        rows = [r for r in rows if r.get("campaign", "") in wanted]
    return rows


def group_key(r):
    cal = r.get("calibration_tag") or "untagged"
    camp = r.get("campaign") or "uncampaigned"
    tpr = r.get("tokens_per_response") or "tprNA"
    pv = r.get("prompt_variant") or "pvNA"
    ab = r.get("ai_backend") or "mock"
    return f"{r['workload']}_{r['transport']}_{cal}_{camp}_t{tpr}_{pv}_{ab}"


def by_stack(rows, stack):
    pts = [r for r in rows if r.get("stack") == stack]
    pts.sort(key=lambda r: int(r["target_rps"]))
    return pts


def xs_ys(rows, ycol):
    xs, ys = [], []
    for r in rows:
        y = fnum(r, ycol)
        if y != y:  # NaN
            continue
        xs.append(int(r["target_rps"]))
        ys.append(y)
    return xs, ys


def plot_latency(group, rows, out_dir):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    styles = {"async": ("-", "#c0392b"), "reactive": ("-", "#2471a3")}
    pct = {"e2e_p50_mean": ("p50", ":"), "e2e_p95_mean": ("p95", "--"), "e2e_p99_mean": ("p99", "-")}
    any_series = False
    for stack, (_, color) in styles.items():
        srows = by_stack(rows, stack)
        for col, (label, ls) in pct.items():
            xs, ys = xs_ys(srows, col)
            if not xs:
                continue
            any_series = True
            ax.plot(xs, ys, ls, color=color, marker="o", ms=3,
                    label=f"{stack} {label}")
    if not any_series:
        plt.close(fig)
        return
    ax.set_yscale("log")
    ax.set_xlabel("Target RPS")
    ax.set_ylabel("E2E latency (ms, log)")
    ax.set_title(f"Latency percentiles — {group}")
    ax.set_xlim(left=0)  # anchor curves at the origin (linear x)
    ax.grid(True, which="both", alpha=0.3)
    ax.legend(fontsize=8, ncol=2)
    fig.tight_layout()
    fig.savefig(out_dir / f"latency_percentiles_{safe(group)}.png", dpi=150)
    plt.close(fig)


def plot_p99_ratio(group, rows, out_dir):
    a = {int(r["target_rps"]): fnum(r, "e2e_p99_mean") for r in by_stack(rows, "async")}
    r_ = {int(r["target_rps"]): fnum(r, "e2e_p99_mean") for r in by_stack(rows, "reactive")}
    xs, ys = [], []
    for rps in sorted(set(a) & set(r_)):
        av, rv = a[rps], r_[rps]
        if av != av or rv != rv or rv == 0:
            continue
        xs.append(rps)
        ys.append(av / rv)
    if not xs:
        return
    fig, ax = plt.subplots(figsize=(9, 5.5))
    ax.plot(xs, ys, "-o", color="#7d3c98", ms=4, label="async p99 / reactive p99")
    ax.axhline(1.0, color="gray", ls="--", lw=1, label="parity (1×)")
    ax.axhline(6.0, color="#c0392b", ls=":", lw=1, label="H1 threshold (>5×, i.e. >500%)")
    ax.set_yscale("log")
    ax.set_xlabel("Target RPS")
    ax.set_ylabel("p99 ratio (async / reactive)")
    ax.set_title(f"p99 cliff ratio — {group}")
    ax.set_xlim(left=0)
    ax.grid(True, which="both", alpha=0.3)
    ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(out_dir / f"p99_ratio_{safe(group)}.png", dpi=150)
    plt.close(fig)


def plot_delivered(group, rows, out_dir):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    drew = False
    for stack, color in (("async", "#c0392b"), ("reactive", "#2471a3")):
        xs, ys = xs_ys(by_stack(rows, stack), "delivered_rps")
        if xs:
            drew = True
            ax.plot(xs, ys, "-o", color=color, ms=4, label=f"{stack} delivered")
    if not drew:
        plt.close(fig)
        return
    allx = [int(r["target_rps"]) for r in rows]
    lim = max(allx) if allx else 1
    ax.plot([0, lim], [0, lim], "--", color="gray", lw=1, label="ideal (delivered = target)")
    ax.set_xlabel("Target RPS")
    ax.set_ylabel("Delivered RPS (SUT-accepted)")
    ax.set_title(f"Delivered vs target RPS — {group}")
    ax.set_xlim(left=0)
    ax.set_ylim(bottom=0)
    ax.grid(True, alpha=0.3)
    ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(out_dir / f"delivered_rps_{safe(group)}.png", dpi=150)
    plt.close(fig)


def plot_errors_drops(group, rows, out_dir):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    drew = False
    for stack, color in (("async", "#c0392b"), ("reactive", "#2471a3")):
        srows = by_stack(rows, stack)
        xs, ys = xs_ys(srows, "error_rate")
        if xs:
            drew = True
            ax.plot(xs, ys, "-o", color=color, ms=4, label=f"{stack} SUT error_rate")
        xd, yd = xs_ys(srows, "dropped_rate")
        if xd:
            drew = True
            ax.plot(xd, yd, ":s", color=color, ms=4, label=f"{stack} k6 dropped_rate")
    if not drew:
        plt.close(fig)
        return
    ax.axhline(0.01, color="gray", ls="--", lw=1, label="1% dropped_rate gate")
    ax.set_xlabel("Target RPS")
    ax.set_ylabel("Fraction")
    ax.set_title(f"Errors (SUT) & dropped arrivals (k6) — {group}")
    ax.set_xlim(left=0)
    ax.set_ylim(bottom=0)
    ax.grid(True, alpha=0.3)
    ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(out_dir / f"errors_drops_{safe(group)}.png", dpi=150)
    plt.close(fig)


def plot_cpu_heap(group, rows, out_dir):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    ax2 = ax.twinx()
    drew = False
    for stack, color in (("async", "#c0392b"), ("reactive", "#2471a3")):
        srows = by_stack(rows, stack)
        xc, yc = xs_ys(srows, "cpu_avg")
        if xc:
            drew = True
            ax.plot(xc, yc, "-o", color=color, ms=3, label=f"{stack} CPU avg %")
        xp, yp = xs_ys(srows, "cpu_p95")
        if xp:
            ax.plot(xp, yp, ":^", color=color, ms=3, label=f"{stack} CPU p95 %")
        xh, yh = xs_ys(srows, "heap_used_max_mb")
        if xh:
            ax2.plot(xh, yh, "--s", color=color, ms=3, alpha=0.6, label=f"{stack} heap max MB")
    if not drew:
        plt.close(fig)
        return
    ax.set_xlabel("Target RPS")
    ax.set_ylabel("CPU (process %)")
    ax2.set_ylabel("Heap used (MB)")
    ax.set_title(f"CPU & heap — {group}")
    ax.set_xlim(left=0)
    ax.grid(True, alpha=0.3)
    h1, l1 = ax.get_legend_handles_labels()
    h2, l2 = ax2.get_legend_handles_labels()
    ax.legend(h1 + h2, l1 + l2, fontsize=7, ncol=2)
    fig.tight_layout()
    fig.savefig(out_dir / f"cpu_heap_{safe(group)}.png", dpi=150)
    plt.close(fig)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--cells-csv", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    ap.add_argument("--campaign", default="", help="optional comma-separated campaign filter")
    args = ap.parse_args()

    rows = load_cells(args.cells_csv, args.campaign)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    groups = {}
    for r in rows:
        groups.setdefault(group_key(r), []).append(r)

    if not groups:
        print(f"No cells matched (campaign filter={args.campaign or 'none'}).")
        return

    for group, grows in sorted(groups.items()):
        plot_latency(group, grows, args.out_dir)
        plot_p99_ratio(group, grows, args.out_dir)
        plot_delivered(group, grows, args.out_dir)
        plot_errors_drops(group, grows, args.out_dir)
        plot_cpu_heap(group, grows, args.out_dir)
        print(f"  figures for group {group} ({len(grows)} cells)")
    print(f"Draft figures written to {args.out_dir}")


if __name__ == "__main__":
    main()
