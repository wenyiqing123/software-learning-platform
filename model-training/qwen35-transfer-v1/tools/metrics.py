"""Parity implementation of backend ExperimentMetrics for Unicode character scoring."""
import math
import re
import unicodedata


def tokens(text):
    if text is None:
        return []
    output = []
    for char in text.lower():
        category = unicodedata.category(char)
        if category.startswith("L") or category == "Nd":
            output.append(char)
            if len(output) == 4000:
                break
    return output


def normalize_for_java(text):
    return "".join(char for char in (text or "").lower()
                   if unicodedata.category(char).startswith("L") or unicodedata.category(char) == "Nd")


def _round(value):
    return math.floor(value * 1_000_000 + 0.5) / 1_000_000


def _ngrams(values, order):
    result = {}
    for index in range(len(values) - order + 1):
        gram = "\x01".join(values[index:index + order])
        result[gram] = result.get(gram, 0) + 1
    return result


def _bleu(actual, expected):
    if not actual:
        return 0.0
    orders = min(4, len(actual), len(expected))
    if not orders:
        return 0.0
    logarithms = 0.0
    for order in range(1, orders + 1):
        found, reference = _ngrams(actual, order), _ngrams(expected, order)
        matches = sum(min(count, reference.get(gram, 0)) for gram, count in found.items())
        if not matches:
            return 0.0
        logarithms += math.log(matches / (len(actual) - order + 1))
    brevity = min(0.0, 1.0 - len(expected) / len(actual))
    return math.exp(brevity + logarithms / orders)


def _lcs_length(actual, expected):
    previous = [0] * (len(expected) + 1)
    for token in actual:
        current = [0] * (len(expected) + 1)
        for index, reference_token in enumerate(expected, 1):
            current[index] = previous[index - 1] + 1 if token == reference_token else max(previous[index], current[index - 1])
        previous = current
    return previous[-1]


def evaluate_answer(answer, reference, entities=None):
    actual, expected = tokens(answer), tokens(reference)
    rouge = bleu = None
    if expected:
        lcs = _lcs_length(actual, expected)
        rouge = _round(2.0 * lcs / (len(actual) + len(expected))) if actual else 0.0
        bleu = _round(_bleu(actual, expected))
    entity_recall = None
    clean = list(dict.fromkeys(entity.strip() for entity in (entities or []) if entity and entity.strip()))
    if clean:
        entity_recall = _round(sum(entity in (answer or "") for entity in clean) / len(clean))
    return {"rougeL": rouge, "bleu4": bleu, "entityRecall": entity_recall, "tokenizer": "unicode-character-v1"}


def mean_metrics(rows):
    keys = ("rougeL", "bleu4", "entityRecall")
    return {key: _round(sum(row[key] for row in rows if row.get(key) is not None) /
                        sum(row.get(key) is not None for row in rows)) if any(row.get(key) is not None for row in rows) else None
            for key in keys}


def requires_multiple_evidence(question, task_type):
    question_marks = sum(question.count(mark) for mark in ("?", "？"))
    return task_type != "SINGLE_FACT" or bool(re.search("分别|比较|对比|区别|差异|各自|异同|相同点|不同点", question)) or question_marks > 1
