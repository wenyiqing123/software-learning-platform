"""Speed-first LoRA defaults and preflight checks for a 16 GB NVIDIA run."""

DEFAULT_BATCH_SIZE = 0
DEFAULT_BATCH_CANDIDATES = (8, 4, 2, 1)
DEFAULT_EFFECTIVE_BATCH_SIZE = 8
DEFAULT_GRADIENT_ACCUMULATION = None
DEFAULT_MAX_SEQ_LEN = 1024
MIN_TOTAL_VRAM_MIB = 14 * 1024
MIN_FREE_VRAM_MIB = 6 * 1024
MIN_PROBE_RESERVE_MIB = 1024


def accumulation_for_batch(batch_size, effective_batch_size=DEFAULT_EFFECTIVE_BATCH_SIZE):
    if batch_size < 1 or effective_batch_size < 1:
        raise ValueError("Batch sizes must be positive")
    return max(1, (effective_batch_size + batch_size - 1) // batch_size)


def supports_native_bf16(compute_capability, torch_reports_support):
    major, minor = compute_capability
    return (major, minor) >= (8, 0) and bool(torch_reports_support)


def batch_candidates(requested_batch_size, row_count):
    if row_count < 1:
        raise ValueError("Training split must contain at least one row")
    if requested_batch_size < 0:
        raise ValueError("Requested micro-batch cannot be negative")
    if requested_batch_size:
        if requested_batch_size > row_count:
            raise ValueError("Requested micro-batch exceeds the training split size")
        return [requested_batch_size]
    return [size for size in DEFAULT_BATCH_CANDIDATES if size <= row_count]


def probe_memory_reserve(free_before_mib, baseline_allocated_mib, peak_allocated_mib,
                         reserve_mib=MIN_PROBE_RESERVE_MIB):
    extra_peak = max(0, peak_allocated_mib - baseline_allocated_mib)
    projected_free = free_before_mib - extra_peak
    return projected_free >= reserve_mib, int(projected_free)


def validate_vram(total_mib, free_mib):
    if total_mib < MIN_TOTAL_VRAM_MIB:
        raise RuntimeError(
            f"16 GB profile requires at least {MIN_TOTAL_VRAM_MIB} MiB total VRAM; detected {total_mib} MiB."
        )
    if free_mib < MIN_FREE_VRAM_MIB:
        raise RuntimeError(
            f"16 GB profile requires at least {MIN_FREE_VRAM_MIB} MiB free VRAM before model load; "
            f"detected {free_mib} MiB. Close GPU workloads and retry."
        )
    return {"totalMiB": int(total_mib), "freeMiB": int(free_mib)}


def require_complete_split(expected_rows, encoded_rows, over_limit_case_ids):
    if over_limit_case_ids or encoded_rows != expected_rows:
        examples = ", ".join(over_limit_case_ids[:5])
        raise ValueError(
            f"Full-split training requires all {expected_rows} rows to fit max_seq_len; "
            f"encoded {encoded_rows}, over-limit {len(over_limit_case_ids)}. "
            f"Increase max_seq_len and reassess VRAM. Case IDs: {examples}"
        )
    return encoded_rows
