export function clampPhotoOffset(offset: number, imageSize: number, viewportSize: number, scale: number) {
  const limit = Math.max(0, (imageSize * scale - viewportSize) / 2)
  return Math.max(-limit, Math.min(limit, offset))
}

export function photoWheelScale(scale: number, delta: number) {
  return Math.max(1, Math.min(8, scale * Math.exp(-Math.max(-200, Math.min(200, delta)) * 0.002)))
}

export function zoomPhotoOffset(offset: number, pointer: number, oldScale: number, newScale: number) {
  return pointer - (pointer - offset) * newScale / oldScale
}
