export function gradeAnswer(selected: string[], expected: string[]) {
  if (selected.length === 0 || expected.length === 0) return false
  if (new Set(selected).size !== selected.length) return false
  if (selected.length !== expected.length) return false
  const selectedSet = new Set(selected)
  return expected.every((answer) => selectedSet.has(answer))
}
