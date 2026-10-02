#!/usr/bin/env python3
"""对比基准结果 JSON 并生成 Markdown 报告（支持多样本聚合）。

用法:
    python benchmark/compare.py <baseline_labels> <candidate_labels> <report_title> [out_md] [--target substr1,substr2]
    labels 以逗号分隔（如 baseline,baseline2），每个 label 对应
    benchmark/results/<label>/ 目录（每组一个 json）或旧版单文件；
    同侧多样本按「每基准取最小 ns/op」聚合（最小值受 JIT/GC 噪声污染最少）。

    --target 指定本轮的目标基准子串（逗号分隔）。目标基准严格判定，且劣化需
    同时满足相对劣化 >5% 与绝对差 >3ns（亚纳秒级基准的百分比波动无意义）；
    非目标（看守）基准需劣化 >12% 且绝对差 >15ns 才计回归——
    共享开发机上的跨 JVM 抖动在此范围内。

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
        # 新协议: results/<label>/<group>.json（每组独立 JVM）；兼容旧单文件 results/<label>.json
        paths = sorted((ROOT / "benchmark" / "results" / label).glob("*.json"))
        single = ROOT / "benchmark" / "results" / f"{label}.json"
        if not paths and single.exists():
            paths = [single]
        for path in paths:
            with path.open(encoding="utf-8") as fh:
                data = json.load(fh)
            for r in data["results"]:
                name = r["name"]
                if name not in merged or r["nsPerOp"] < merged[name]["nsPerOp"]:
                    merged[name] = r
                merged.setdefault(name + "::samples", {"samples": []})
                merged[name + "::samples"]["samples"].append((f"{label}/{path.stem}", r["nsPerOp"]))
    return merged



def main() -> int:
    args = [a for a in sys.argv[1:]]
    targets = []
    if "--target" in args:
        i = args.index("--target")
        targets = [t for t in args[i + 1].split(",") if t]
        args = args[:i]
    if len(args) < 3:
        print(__doc__)
        return 2
    base_labels = args[0].split(",")
    cand_labels = args[1].split(",")
    title = args[2]
    out_path = Path(args[3]) if len(args) > 3 else (
        ROOT / "note" / "report" / "perf" / f"{base_labels[0]}_vs_{cand_labels[0]}.md"
    )

    base = load_merged(base_labels)
    cand = load_merged(cand_labels)

    def is_target(name: str) -> bool:
        return any(t in name for t in targets)

    lines = []
    lines.append(f"# 性能对比：{title}")
    lines.append("")
    lines.append(f"- 基线样本: {', '.join(base_labels)}　候选样本: {', '.join(cand_labels)}")
    lines.append(f"- 目标基准过滤: {', '.join(targets) if targets else '(全部按目标严格判定)'}")
    lines.append("- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录")
    lines.append("- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）")
    lines.append("- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%")
    lines.append("- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）")
    lines.append("")
    lines.append("| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |")
    lines.append("|------|------|-----------:|----------:|---------:|------|")

    wins = regressions = 0
    appendix = ["", "## 附录：原始样本", "",
                "| 基准 | 样本 | ns/op |", "|------|------|------:|"]
    for name in base:
        if name.endswith("::samples"):
            continue
        b = base[name]
        c = cand.get(name)
        if c is None:
            lines.append(f"| {name} | {'目标' if is_target(name) else '看守'} | {b['nsPerOp']:.1f} | (missing) | - | - |")
            continue
        speedup = b["nsPerOp"] / c["nsPerOp"] if c["nsPerOp"] > 0 else float("inf")
        delta = b["nsPerOp"] - c["nsPerOp"]
        kind = "目标" if is_target(name) else "看守"
        if speedup >= 1.10:
            verdict = f"**+{(speedup - 1) * 100:.1f}%**"
            wins += 1
        elif speedup <= 0.95 and -delta > 3.0 and (is_target(name) or (speedup <= 0.88 and -delta > 15.0)):
            verdict = f"**{(speedup - 1) * 100:.1f}%**"
            regressions += 1
        else:
            verdict = "持平" if abs(speedup - 1) < 0.10 else "持平·噪声带"
        lines.append(
            f"| {name} | {kind} | {b['nsPerOp']:.1f} | {c['nsPerOp']:.1f} "
            f"| {speedup:.3f}x | {verdict} |"
        )

    # 候选侧独有的基准（如候选轮新增 API 的基准；基线 jar 无对应方法，无法测量）
    for name in cand:
        if name.endswith("::samples") or name in base:
            continue
        lines.append(
            f"| {name} | {'目标' if is_target(name) else '看守'} | (新增) | {cand[name]['nsPerOp']:.1f} "
            f"| - | 候选侧新增基准，无基线可比 |"
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
