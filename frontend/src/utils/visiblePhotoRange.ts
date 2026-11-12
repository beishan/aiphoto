export function visiblePhotoRange(boxes: { top: number; height: number }[], lower: number, upper: number) {
  let left = 0, right = boxes.length
  while (left < right) {
    const middle = (left + right) >>> 1
    if (boxes[middle].top + boxes[middle].height < lower) left = middle + 1
    else right = middle
  }
  let end = left
  while (end < boxes.length && boxes[end].top <= upper) end++
  return { start: left, end }
}
