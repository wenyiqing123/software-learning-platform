"""Export the existing graph through an allow-listed, read-only query path."""
import argparse
import hashlib
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path


def check_read_only_query(query):
    if ";" in query or not re.match(r"^(SHOW|DESCRIBE|USE|MATCH)\b", query):
        raise ValueError("Only a single read-only graph query is allowed")
    if re.search(r"\b(INSERT|UPSERT|UPDATE|DELETE|DROP|CREATE|ALTER|REBUILD|SUBMIT|BALANCE)\b", query, re.I):
        raise ValueError("Graph mutation is not allowed")


def query_rows(session, query):
    check_read_only_query(query)
    result = session.execute(query)
    if not result.is_succeeded():
        raise RuntimeError(f"Read-only query failed: {result.error_msg()}")
    rows = []
    for index in range(result.row_size()):
        values = []
        for value in result.row_values(index):
            if value.is_string():
                values.append(value.as_string())
            elif value.is_int():
                values.append(value.as_int())
            elif value.is_null() or value.is_empty():
                values.append(None)
            elif value.is_bool():
                values.append(value.as_bool())
            else:
                raise ValueError("Unexpected value type in graph export")
        rows.append(values)
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--page-size", type=int, default=200)
    args = parser.parse_args()
    if not 1 <= args.page_size <= 1000:
        parser.error("page size must be between 1 and 1000")
    if args.out.exists():
        parser.error("output directory already exists; use a new snapshot directory")
    from nebula3.Config import Config
    from nebula3.gclient.net import ConnectionPool

    config = Config()
    config.max_connection_pool_size = 1
    config.timeout = 15000
    pool = ConnectionPool()
    host, port = os.getenv("NEBULA_HOST", "127.0.0.1"), int(os.getenv("NEBULA_PORT", "9669"))
    session = None
    try:
        if not pool.init([(host, port)], config):
            raise RuntimeError("Nebula is not available")
        session = pool.get_session(os.getenv("NEBULA_ACCOUNT", "root"), os.getenv("NEBULA_PASSWORD", "nebula"))
        description = query_rows(session, "DESCRIBE SPACE air_ticket")
        if len(description) != 1 or description[0][1] != "air_ticket":
            raise ValueError("The configured graph space did not resolve to air_ticket")
        query_rows(session, "USE air_ticket")
        schema = {"entity": query_rows(session, "DESCRIBE TAG entity"),
                  "relation": query_rows(session, "DESCRIBE EDGE relation")}
        base = "MATCH (s:entity)-[e:relation]->(o:entity) "
        count_query = base + "RETURN count(e) AS edge_count"
        before = query_rows(session, count_query)[0][0]
        if before < 1 or before > 100000:
            raise ValueError("Graph size is empty or exceeds the explicit 100000 edge export limit")
        records, statements, physical_keys = [], [], set()
        for offset in range(0, before, args.page_size):
            query = (base + "RETURN s.entity.name AS subject,e.name AS predicate,o.entity.name AS object,"
                     "id(s) AS src,id(o) AS dst,rank(e) AS edge_rank "
                     f"ORDER BY src ASC,dst ASC,edge_rank ASC SKIP {offset} LIMIT {args.page_size}")
            statements.append(query)
            for row in query_rows(session, query):
                record = dict(zip(("subject", "predicate", "object", "src", "dst", "edge_rank"), row))
                key = (record["src"], record["dst"], record["edge_rank"])
                if key in physical_keys:
                    raise ValueError("Pagination repeated an edge; export is not a stable snapshot")
                physical_keys.add(key)
                records.append(record)
        after = query_rows(session, count_query)[0][0]
        if before != after or len(records) != before:
            raise ValueError("Graph count changed or pagination was incomplete; no snapshot was written")
        args.out.mkdir(parents=True)
        raw = args.out / "raw-triples.jsonl"
        raw.write_text("".join(json.dumps(r, ensure_ascii=False, sort_keys=True) + "\n" for r in records), encoding="utf-8")
        metadata = {"exportedAt": datetime.now(timezone.utc).isoformat(), "source": "live-nebula-read-only",
                    "host": host, "port": port, "logicalSpaceId": 1, "spaceName": "air_ticket",
                    "physicalSpaceId": description[0][0], "schema": schema, "physicalEdgeCount": len(records),
                    "countBefore": before, "countAfter": after, "rawSha256": hashlib.sha256(raw.read_bytes()).hexdigest(),
                    "queries": statements, "consistency": "ordered pagination plus before/after count; not a transactional snapshot"}
        (args.out / "snapshot-metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"output": str(args.out), "physicalEdges": len(records), "physicalSpaceId": description[0][0]}, ensure_ascii=False))
    finally:
        if session:
            session.release()
        pool.close()


if __name__ == "__main__":
    main()
