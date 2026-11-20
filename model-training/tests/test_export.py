import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from export_nebula import check_read_only_query


class ExportSafetyTests(unittest.TestCase):
    def test_only_explicit_read_queries_are_permitted(self):
        for q in ("SHOW TAGS", "USE air_ticket", "DESCRIBE SPACE air_ticket",
                  "MATCH (s:entity)-[e:relation]->(o:entity) RETURN count(e)"):
            check_read_only_query(q)
        for q in ("UPSERT VERTEX ON entity 'x' SET name='y'", "DROP SPACE air_ticket",
                  "USE air_ticket; DELETE VERTEX 'x'", "CREATE SPACE x", "MATCH (v) DELETE v"):
            with self.assertRaises(ValueError):
                check_read_only_query(q)


if __name__ == "__main__":
    unittest.main()
