"""Build graph-derived chat SFT and paired evaluation data without graph writes or LLM calls."""
import argparse
import hashlib
import json
import math
import os
import random
import re
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path


def norm(text):
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", text)).casefold()


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True).encode()).hexdigest()


def prepare_facts(rows):
    unique, rejected, duplicates = {}, [], 0
    for index, row in enumerate(rows):
        parts = [row.get(k) for k in ("subject", "predicate", "object")]
        if any(not isinstance(x, str) or not x.strip() or x.strip() in {"__EMPTY__", "NULL"} for x in parts):
            rejected.append({"row": index + 1, "reason": "empty-or-invalid-field"})
            continue
        subject, predicate, obj = [x.strip() for x in parts]
        key = (norm(subject), norm(predicate), norm(obj))
        source = {k: row.get(k) for k in ("src", "dst", "edge_rank")}
        if key in unique:
            unique[key]["sources"].append(source)
            duplicates += 1
        else:
            unique[key] = {"id": digest(key)[:24], "subject": subject, "predicate": predicate,
                           "object": obj, "sources": [source]}
    facts = sorted(unique.values(), key=lambda f: f["id"])
    return facts, {"invalid_rows": len(rejected), "duplicate_edges": duplicates, "rejected": rejected}


def split_facts(facts, seed=20261116):
    facts = sorted(facts, key=lambda f: f["id"])
    parents = list(range(len(facts)))

    def find(i):
        while parents[i] != i:
            parents[i] = parents[parents[i]]
            i = parents[i]
        return i

    def union(i, j):
        parents[find(j)] = find(i)

    subjects, answers = {}, {}
    for i, f in enumerate(facts):
        for owners, value in ((subjects, f["subject"]), (answers, f["object"])):
            key = norm(value)
            if key in owners:
                union(i, owners[key])
            owners[key] = i
    # Protect complete long answer strings even when another fact embeds them in its subject/object.
    for answer, owner in answers.items():
        if len(answer) >= 8:
            for j, f in enumerate(facts):
                if any(answer in norm(f[field]) for field in ("subject", "predicate", "object")):
                    union(owner, j)
    groups = defaultdict(list)
    for i, f in enumerate(facts):
        groups[find(i)].append(f)
    if len(groups) < 3:
        raise ValueError("At least three independent subject/answer groups are required for a leakage-controlled split")
    groups = sorted(groups.values(), key=lambda g: (-len(g), digest([seed, sorted(f["id"] for f in g)])))
    splits = {"train": [], "validation": [], "test": []}
    targets = {"train": len(facts) * .8, "validation": len(facts) * .1, "test": len(facts) * .1}
    for group in groups:
        selected = min(splits, key=lambda s: len(splits[s]) / targets[s])
        splits[selected].extend(group)
    return {s: sorted(fs, key=lambda f: f["id"]) for s, fs in splits.items()}


def reference(subject, predicate, objects):
    body = "；".join(objects)
    if predicate in {"包含", "包括", "属于", "使用", "采用", "具有", "依赖", "对应", "用于", "需要", "遵循",
                     "分为", "划分为", "前置于", "建立", "关联", "发送消息", "接收消息", "涵盖", "描述", "导出", "是", "实现", "扩展", "绘制", "编写"}:
        return f"{subject}{predicate}{body}。"
    if "规则" in predicate and re.match(r"^(应|必须|需|采用|使用|禁止|不能)", body):
        return f"{subject}{predicate}{body}。"
    return f"{subject}的{predicate}是{body}。"


def question_variants(subject, predicate):
    if predicate in {"包含", "包括", "涵盖"}:
        return (f"{subject}{predicate}哪些内容？", f"根据课程知识，请列出{subject}{predicate}的内容。")
    if predicate in {"分为", "划分为"}:
        return (f"{subject}{predicate}哪些类别？", f"根据课程知识，请说明{subject}的分类。")
    if predicate == "使用工具":
        return (f"{subject}使用哪些工具？", f"根据课程知识，请列出{subject}使用的工具。")
    if predicate in {"前置于", "依赖", "关联"}:
        return (f"{subject}{predicate}哪些知识点或对象？", f"课程图谱中，{subject}的“{predicate}”关系指向哪些对象？")
    return (f"{subject}的{predicate}是什么？", f"根据课程知识，请说明{subject}的{predicate}。")


def make_cases(facts, seed=20261116, max_candidates=6):
    grouped = defaultdict(list)
    for fact in sorted(facts, key=lambda f: f["id"]):
        grouped[(fact["subject"], fact["predicate"])].append(fact)
    cases = []
    for (subject, predicate), gold in sorted(grouped.items()):
        gold_ids = {f["id"] for f in gold}
        gold = sorted(gold, key=lambda f: f["object"])
        # Do not synthesize a partial answer for a genuinely multi-valued relation.
        if len(gold) > max_candidates or sum(len(f["object"]) for f in gold) > 1800:
            continue
        others = [f for f in facts if f["id"] not in gold_ids]
        others.sort(key=lambda f: (-len(set(subject + predicate) & set(f["subject"] + f["predicate"])), digest([seed, f["id"], subject, predicate])))
        candidates = list(gold)
        chars = sum(len(f["subject"] + f["predicate"] + f["object"]) for f in candidates)
        for f in others:
            size = len(f["subject"] + f["predicate"] + f["object"])
            if len(candidates) < max_candidates and chars + size <= 1800:
                candidates.append(f)
                chars += size
        for variant, question in enumerate(question_variants(subject, predicate)):
            ordered = sorted(candidates, key=lambda f: digest([seed, subject, predicate, variant, f["id"]]))
            evidence = [{"id": f"T{i+1}", "factId": f["id"], "subject": f["subject"], "predicate": f["predicate"], "object": f["object"]} for i, f in enumerate(ordered)]
            cases.append({"id": digest([seed, subject, predicate, variant])[:24], "subject": subject, "predicate": predicate,
                          "variant": variant, "question": question, "referenceAnswer": reference(subject, predicate, [f["object"] for f in gold]),
                          "expectedEntities": [f["object"] for f in gold], "candidates": evidence,
                          "goldEvidenceIds": [e["id"] for e in evidence if e["factId"] in gold_ids],
                          "taskType": "SINGLE_FACT" if len(gold) == 1 else "MULTI_FACT", "labelSource": "graph-template-needs-human-review"})
    return cases


def original_prompt(case):
    ordered = sorted(case["candidates"], key=lambda e: e["score"])
    parts = [str([e["subject"], e["predicate"], e["object"], f"与问题相关性{e['score']:.4f}"]) for e in ordered]
    return ("根据提供的三元组，参考它们与问题的相关性回答问题。 三元组：[" + ", ".join(parts)
            + f"] 。根据这些三元组，参考其与问题的相关性，回答问题： 问题：{case['question']} 答案：")


def make_messages(case, selection_system, answer_system):
    if any(not isinstance(e.get("score"), (int, float)) or not math.isfinite(e["score"]) for e in case["candidates"]):
        raise ValueError("Every candidate requires an actual finite semantic score")
    # factId is audit metadata, not a feature available to the learner.
    evidence = [{k: e[k] for k in ("id", "subject", "predicate", "object", "score")} for e in case["candidates"]]
    selected = [e for e in evidence if e["id"] in case["goldEvidenceIds"]]
    dumps = lambda value: json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    triples_prompt = "根据以下课程知识三元组回答问题。若证据不足，请明确说明。\n三元组：" + dumps([[e[k] for k in ("subject", "predicate", "object")] for e in evidence]) + "\n问题：" + case["question"]
    tasks = [
        ("DIRECT", None, case["question"], case["referenceAnswer"]),
        ("TRIPLES", None, triples_prompt, case["referenceAnswer"]),
        ("EVIDENCE_SELECTION", selection_system, dumps({"question": case["question"], "candidates": evidence}),
         dumps({"status": "SUFFICIENT", "taskType": case["taskType"], "selectedIds": case["goldEvidenceIds"]})),
        ("GROUNDED_ANSWER", answer_system + "\n" + dumps({"question": case["question"], "taskType": case["taskType"], "selectedEvidence": selected}),
         original_prompt(case), dumps({"answer": case["referenceAnswer"], "usedIds": case["goldEvidenceIds"]})),
    ]
    records = []
    for task, system, user, answer in tasks:
        messages = ([{"role": "system", "content": system}] if system else [])
        messages += [{"role": "user", "content": user}, {"role": "assistant", "content": answer}]
        records.append({"messages": messages, "task": task, "caseId": case["id"], "labelSource": case["labelSource"]})
    return records


def validate_dataset(splits, cases):
    owners = {"fact": {}, "subject": {}, "answer": {}}
    for split, facts in splits.items():
        ids = {f["id"] for f in facts}
        if not ids:
            raise ValueError(f"Empty split: {split}")
        for f in facts:
            for kind, value in (("fact", f["id"]), ("subject", norm(f["subject"])), ("answer", norm(f["object"]))):
                previous = owners[kind].setdefault(value, split)
                if previous != split:
                    raise ValueError(f"Cross-split {kind} leakage")
        for case in cases[split]:
            if any(e["factId"] not in ids for e in case["candidates"]):
                raise ValueError("Candidate fact from another split")
            if not case["goldEvidenceIds"] or not set(case["goldEvidenceIds"]) <= {e["id"] for e in case["candidates"]}:
                raise ValueError("Invalid gold evidence ID")
            if any(obj not in case["referenceAnswer"] for obj in case["expectedEntities"]):
                raise ValueError("An expected object is absent from the generated reference")
    return {"cross_split_fact_overlap": 0, "cross_split_subject_overlap": 0, "cross_split_identical_answer_overlap": 0,
            "cross_split_candidate_overlap": 0, "fact_counts": {s: len(fs) for s, fs in splits.items()},
            "case_counts": {s: len(cs) for s, cs in cases.items()}}


def score_cases(cases, model_path):
    os.environ.setdefault("HF_HUB_OFFLINE", "1")
    os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
    import torch
    from sentence_transformers import SentenceTransformer
    torch.set_num_threads(2)
    model = SentenceTransformer(str(model_path), device="cpu")
    texts = sorted({c["question"] for group in cases.values() for c in group} |
                   {" ".join(e[k] for k in ("subject", "predicate", "object")) for group in cases.values() for c in group for e in c["candidates"]})
    vectors = model.encode(texts, convert_to_tensor=True, normalize_embeddings=True, batch_size=24, show_progress_bar=True)
    lookup = {text: vectors[i] for i, text in enumerate(texts)}
    for group in cases.values():
        for case in group:
            for e in case["candidates"]:
                text = " ".join(e[k] for k in ("subject", "predicate", "object"))
                e["score"] = float(torch.dot(lookup[case["question"]], lookup[text]).item())
    return {"source": "existing-mecha-qa-sentence-transformer", "modelPath": str(model_path.resolve()),
            "device": "cpu", "score": "cosine similarity of question and subject predicate object; no gold label used"}


def load_systems(java_source):
    source = java_source.read_text(encoding="utf-8")
    systems = []
    for field in ("SELECT_SYSTEM", "ANSWER_SYSTEM"):
        match = re.search(r"private static final String " + field + r' = """\n(.*?)\n\s*"""\.strip\(\);', source, re.S)
        if not match:
            raise ValueError(f"Cannot load the current Java {field} prompt")
        import textwrap
        systems.append(textwrap.dedent(match.group(1)).strip())
    version = re.search(r'VERSION = "([^"]+)"', source).group(1)
    return systems[0], systems[1], version


def write_jsonl(path, rows):
    path.write_text("".join(json.dumps(r, ensure_ascii=False, sort_keys=True) + "\n" for r in rows), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=20261116)
    parser.add_argument("--scoring-model", type=Path, required=True)
    parser.add_argument("--max-candidates", type=int, default=6)
    args = parser.parse_args()
    if args.out.exists():
        parser.error("output already exists; choose a new directory")
    if not 2 <= args.max_candidates <= 20:
        parser.error("max-candidates must be 2..20")
    rows = [json.loads(line) for line in args.input.read_text(encoding="utf-8").splitlines() if line.strip()]
    facts, excluded = prepare_facts(rows)
    splits = split_facts(facts, args.seed)
    cases = {s: make_cases(fs, args.seed, args.max_candidates) for s, fs in splits.items()}
    report = validate_dataset(splits, cases)
    print(json.dumps(report, ensure_ascii=False), flush=True)
    scoring = score_cases(cases, args.scoring_model)
    root = Path(__file__).resolve().parents[1]
    java_source = root / "backend/ai-service/src/main/java/edu/wfust/learning/ai/GroundedRankedAnswer.java"
    selector, answer, version = load_systems(java_source)
    args.out.mkdir(parents=True)
    write_jsonl(args.out / "facts.jsonl", facts)
    for split in splits:
        write_jsonl(args.out / f"{split}-facts.jsonl", splits[split])
        write_jsonl(args.out / f"{split}-cases.jsonl", cases[split])
        records = [r for c in cases[split] for r in make_messages(c, selector, answer)]
        write_jsonl(args.out / f"{split}.jsonl", records)
        report.setdefault("sft_counts", {})[split] = len(records)
        report.setdefault("max_message_chars", {})[split] = max(sum(len(m["content"]) for m in r["messages"]) for r in records)
        used = {e["factId"] for c in cases[split] for e in c["candidates"] if e["id"] in c["goldEvidenceIds"]}
        report.setdefault("gold_fact_counts", {})[split] = len(used)
        report.setdefault("independent_question_groups", {})[split] = len(cases[split]) // 2
        report.setdefault("excluded_from_qa", {})[split] = [
            {"factId": f["id"], "subject": f["subject"], "predicate": f["predicate"],
             "reason": "relation has more objects or answer characters than the candidate/length budget"}
            for f in splits[split] if f["id"] not in used]
    report.update({"seed": args.seed, "split": "subject/identical-object groups, approx 80/10/10; long answer substring groups >=8 chars",
                   "inputSha256": hashlib.sha256(args.input.read_bytes()).hexdigest(), "physical_edges": len(rows),
                   "unique_facts": len(facts), "excluded": excluded, "semanticScoring": scoring, "javaStrategyVersion": version,
                   "labelSource": "graph-template-needs-human-review", "llmCalls": 0,
                   "limitations": ["Template questions/references require teacher review; this is not a formal human evaluation dataset.",
                                   "Objects shared as short substrings of other facts may still occur; subject/fact/identical-answer overlaps are prohibited.",
                                   "Candidates are constructed within split and include gold evidence; this tests evidence use, not end-to-end retrieval recall.",
                                   "No data is evidence for improved cognition or guaranteed superiority of RANKED.",
                                   "Examples from one subject/relation always share a split; multiple objects are combined, never trained as contradictory single answers.",
                                   "Sequence lengths are recorded as characters; tokenizer-length filtering is required before actual training."]})
    report["sha256"] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(args.out.glob("*.jsonl"))}
    (args.out / "manifest.json").write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.out), **{k: report[k] for k in ("unique_facts", "fact_counts", "case_counts", "sft_counts")}}, ensure_ascii=False))


if __name__ == "__main__":
    main()
