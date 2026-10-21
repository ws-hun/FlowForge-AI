import { describe, expect, it } from 'vitest'
import {
  captureFlowCanvasEditSnapshot,
  createFlowCanvasHistory,
  FLOW_CANVAS_HISTORY_LIMIT,
  recordFlowCanvasEdit,
  redoFlowCanvasEdit,
  resolveFlowCanvasHistoryShortcut,
  undoFlowCanvasEdit
} from '@/utils/flowCanvasHistory'

const snapshot = (x: number) => captureFlowCanvasEditSnapshot(
  [{ id: 'node', position: { x, y: 20 } }],
  [{ id: 'edge', source: 'node', target: 'next' }]
)

describe('flow canvas edit history', () => {
  it('undoes and redoes snapshots without mutating the originals', () => {
    const original = snapshot(10)
    const edited = snapshot(40)
    const history = recordFlowCanvasEdit(createFlowCanvasHistory(), original)

    const undone = undoFlowCanvasEdit(history, edited)
    expect(undone.snapshot).toEqual(original)
    expect(redoFlowCanvasEdit(undone.history, original).snapshot).toEqual(edited)
    expect(original.positions.node).toEqual({ x: 10, y: 20 })
  })

  it('clears redo states after a new edit', () => {
    const initial = snapshot(10)
    const edited = snapshot(40)
    const undone = undoFlowCanvasEdit(recordFlowCanvasEdit(createFlowCanvasHistory(), initial), edited)

    expect(recordFlowCanvasEdit(undone.history, initial).future).toEqual([])
  })

  it('keeps history bounded', () => {
    const history = Array.from({ length: FLOW_CANVAS_HISTORY_LIMIT + 5 }, (_, index) => snapshot(index))
      .reduce(recordFlowCanvasEdit, createFlowCanvasHistory())

    expect(history.past).toHaveLength(FLOW_CANVAS_HISTORY_LIMIT)
    expect(history.past[0]).toEqual(snapshot(5))
  })

  it('returns no snapshot when the requested direction is unavailable', () => {
    const empty = createFlowCanvasHistory()
    expect(undoFlowCanvasEdit(empty, snapshot(0))).toEqual({ history: empty, snapshot: null })
    expect(redoFlowCanvasEdit(empty, snapshot(0))).toEqual({ history: empty, snapshot: null })
  })

  it('resolves undo and redo shortcuts only for the focused canvas', () => {
    const base = { metaKey: false, ctrlKey: true, shiftKey: false, altKey: false, insideCanvas: true, editableTarget: false }
    expect(resolveFlowCanvasHistoryShortcut({ ...base, key: 'z' })).toBe('undo')
    expect(resolveFlowCanvasHistoryShortcut({ ...base, key: 'z', metaKey: true, shiftKey: true })).toBe('redo')
    expect(resolveFlowCanvasHistoryShortcut({ ...base, key: 'y' })).toBe('redo')
    expect(resolveFlowCanvasHistoryShortcut({ ...base, key: 'z', insideCanvas: false })).toBeNull()
    expect(resolveFlowCanvasHistoryShortcut({ ...base, key: 'z', editableTarget: true })).toBeNull()
    expect(resolveFlowCanvasHistoryShortcut({ ...base, key: 'z', altKey: true })).toBeNull()
  })
})
