// Two decoding lanes shared by photo cards; queued work checks cancellation before fetching.
const lanes: Promise<void>[] = [Promise.resolve(), Promise.resolve()]
let nextLane = 0
export function queueStaticFrame(work: () => Promise<void>) {
  const lane = nextLane++ % lanes.length
  const result = lanes[lane].then(work, work)
  lanes[lane] = result.catch(() => {})
  return result
}
