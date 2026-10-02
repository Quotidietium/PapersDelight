#!/usr/bin/env python3
"""对比基准结果 JSON 并生成 Markdown 报告（支持多样本聚合）。

用法:
    python benchmark/compare.py <baseline_labels> <candidate_labels> <report_title> [out_md]
    labels 以逗号分隔（如 baseline,baseline2），每个 label 对应
    benchmark/results/<label>.json；同侧多样本按「每基准取最小 ns/op」聚合
    （最小值受 JIT/GC 噪声污染最少），原始样本一并列出。

输出默认写到 note/report/perf/<first_baseline>_vs_<first_candidate>.md
仅使用 Python 标准库。
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load_merged(labels):
    merged = {}
    for label in labels:
        path = ROOT / "benchmark" / "results" / f"{label}.json"
        with path.open(encoding="utf-8") as fh:
            data = json.load(fh)
        for r in data["results"]:
            name = r["name"]
            if name not in merged or r["nsPerOp"] < merged[name]["nsPerOp"]:
                merged[name] = r
            merged.setdefault(name + "::samples", {"samples": []})
            merged[name + "::samples"]["samples"].append((label, r["nsPerOp"]))
    return merged


def fmt_ratio(speedup: float) -> str:
    if speedup >= 1.10:
        return f"**+{(speedup - 1) * 100:.1f}%**"
    if speedup <= 0.90:
        return f"**{(speedup - 1) * 100:.1f}%**"
    return f"{(speedup - 1) * 100:+.1f}%"


def main() -> int:
    if len(sys.argv) < 4:
        print(__doc__)
        return 2
    base_labels = sys.argv[1].split(",")
    cand_labels = sys.argv[2].split(",")
    title = sys.argv[3]
    out_path = Path(sys.argv[4]) if len(sys.argv) > 4 else (
        ROOT / "note" / "report" / "perf" / f"{base_labels[0]}_vs_{cand_labels[0]}.md"
    )

    base = load_merged(base_labels)
    cand = load_merged(cand_labels)

    lines = []
    lines.append(f"# 性能对比：{title}")
    lines.append("")
    lines.append(f"- 基线样本: {', '.join(base_labels)}　候选样本: {', '.join(cand_labels)}")
    lines.append("- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录")
    lines.append("- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆）")
    lines.append("- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%")
    lines.append("")
    lines.append("| 基准 | 基线 ns/op | 候选 ns/op | 速度倍率 | 耗时变化 |")
    lines.append("|------|-----------:|----------:|---------:|---------:|")

    wins = regressions = 0
    appendix = ["", "## 附录：原始样本", "",
                "| 基准 | 样本 | ns/op |", "|------|------|------:|"]
    for name in base:
        if name.endswith("::samples"):
            continue
        b = base[name]
        c = cand.get(name)
        if c is None:
            lines.append(f"| {name} | {b['nsPerOp']:.1f} | (missing) | - | - |")
            continue
        speedup = b["nsPerOp"] / c["nsPerOp"] if c["nsPerOp"] > 0 else float("inf")
        delta_pct = (1 - 1 / speedup) * 100 if speedup > 0 else 0
        verdict = fmt_ratio(speedup)
        if speedup >= 1.10:
            wins += 1
        elif speedup <= 0.95:
            regressions += 1
        lines.append(
            f"| {name} | {b['nsPerOp']:.1f} | {c['nsPerOp']:.1f} "
            f"| {speedup:.3f}x | {verdict} ({delta_pct:+.1f}%) |"
        )

    for name in base:
        if name.endswith("::samples"):
            continue
        for label, v in base[name + "::samples"]["samples"]:
            appendix.append(f"| {name} | 基线 {label} | {v:.1f} |")
    for name in cand:
        if name.endswith("::samples"):
            continue
        for label, v in cand[name + "::samples"]["samples"]:
            appendix.append(f"| {name} | 候选 {label} | {v:.1f} |")

    lines.append("")
    lines.append(f"**汇总**: 明显提速 (≥1.10x) {wins} 项 / 回归 (≤0.95x) {regressions} 项 / 共 {len([n for n in base if not n.endswith('::samples')])} 项。")
    lines.append("")
    lines.append("> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。")
    lines.extend(appendix)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"report -> {out_path}")
    print(f"wins={wins} regressions={regressions}")
    return 0 if regressions == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
