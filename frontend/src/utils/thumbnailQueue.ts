/** Bound thumbnail network/decode work and share requests across overlapping refreshes. */
export function createThumbnailQueue(load: (id: number, signal: AbortSignal) => Promise<void>, concurrency = 4) {
  type Entry = { id: number; controller: AbortController; promise: Promise<void>; resolve: () => void }
  const entries = new Map<number, Entry>()
  const queued: Entry[] = []
  let active = 0
  let disposed = false

  function pump() {
    while (!disposed && active < concurrency && queued.length) {
      const entry = queued.shift()!
      active++
      void Promise.resolve().then(() => {
        if (!entry.controller.signal.aborted) return load(entry.id, entry.controller.signal)
      }).catch(() => { /* A failed thumbnail can be retried on the next refresh. */ }).finally(() => {
        active--
        if (entries.get(entry.id) === entry) entries.delete(entry.id)
        entry.resolve()
        pump()
      })
    }
  }

  function cancel() {
    for (const entry of entries.values()) entry.controller.abort()
    for (const entry of queued.splice(0)) entry.resolve()
    entries.clear()
  }

  return {
    enqueue(id: number): Promise<void> {
      if (disposed) return Promise.resolve()
      const existing = entries.get(id)
      if (existing) return existing.promise
      let resolve!: () => void
      const promise = new Promise<void>(done => { resolve = done })
      const entry = { id, controller: new AbortController(), promise, resolve }
      entries.set(id, entry)
      queued.push(entry)
      pump()
      return promise
    },
    cancel,
    dispose() { disposed = true; cancel() },
  }
}
