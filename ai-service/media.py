"""Decode photos or a deterministic representative video frame for inference."""

import io
from pathlib import Path
import tempfile

import cv2
from PIL import Image, ImageOps

VIDEO_EXTENSIONS = {".mp4", ".avi", ".mov", ".mkv", ".webm"}


def decode_media(data: bytes, filename: str = "") -> Image.Image:
    if not data:
        raise ValueError("媒体文件为空")
    extension = Path(filename or "").suffix.lower()
    if extension not in VIDEO_EXTENSIONS:
        with Image.open(io.BytesIO(data)) as image:
            return ImageOps.exif_transpose(image).convert("RGB")

    # All model stages select the same midpoint so face coordinates refer to one frame.
    with tempfile.TemporaryDirectory(prefix="aiphoto-video-") as directory:
        path = Path(directory) / ("video" + extension)
        path.write_bytes(data)
        capture = cv2.VideoCapture(str(path))
        try:
            if not capture.isOpened():
                raise ValueError("无法打开视频，请检查编码格式")
            frames = capture.get(cv2.CAP_PROP_FRAME_COUNT)
            if frames > 1:
                capture.set(cv2.CAP_PROP_POS_FRAMES, int(frames // 2))
            ok, frame = capture.read()
            if not ok:
                capture.set(cv2.CAP_PROP_POS_FRAMES, 0)
                ok, frame = capture.read()
            if not ok or frame is None:
                raise ValueError("无法提取视频代表帧")
            return Image.fromarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
        finally:
            capture.release()
