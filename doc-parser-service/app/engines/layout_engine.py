import time
from pathlib import Path
from typing import Optional

import cv2
import numpy as np
import onnxruntime as ort
from loguru import logger


class LayoutRecognizer:
    LABELS = [
        "_background_", "Text", "Title", "Figure",
        "Figure caption", "Table", "Table caption",
        "Header", "Footer", "Reference", "Equation",
    ]

    def __init__(self, model_dir: str | Path = "models/deepdoc"):
        self.model_dir = Path(model_dir)
        self.session: Optional[ort.InferenceSession] = None
        self.input_shape: tuple[int, int] = (1024, 1024)  # height, width

    def load(self):
        model_path = self.model_dir / "layout.onnx"
        if not model_path.exists():
            logger.warning(f"Layout model not found: {model_path}")
            return

        options = ort.SessionOptions()
        options.enable_cpu_mem_arena = False
        options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL

        providers = ["CPUExecutionProvider"]
        available = ort.get_available_providers()
        if "CUDAExecutionProvider" in available:
            providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]

        self.session = ort.InferenceSession(
            str(model_path),
            sess_options=options,
            providers=providers,
        )
        logger.info("Layout model loaded")

    @property
    def is_loaded(self) -> bool:
        return self.session is not None

    def preprocess(self, image: np.ndarray) -> tuple[np.ndarray, dict]:
        ori_h, ori_w = image.shape[:2]
        target_h, target_w = self.input_shape

        # Calculate resize ratio
        ratio = min(target_h / ori_h, target_w / ori_w)
        new_h, new_w = int(ori_h * ratio), int(ori_w * ratio)

        # Resize
        img = cv2.resize(image, (new_w, new_h))

        # Pad
        pad_h = target_h - new_h
        pad_w = target_w - new_w
        img = cv2.copyMakeBorder(
            img,
            0, pad_h, 0, pad_w,
            cv2.BORDER_CONSTANT,
            value=(114, 114, 114),
        )

        # Normalize and convert to CHW
        img = img.astype(np.float32) / 255.0
        img = img.transpose(2, 0, 1)[np.newaxis, :, :, :].astype(np.float32)

        scale_info = {
            "ratio": ratio,
            "ori_h": ori_h,
            "ori_w": ori_w,
            "new_h": new_h,
            "new_w": new_w,
            "pad_w": pad_w,
            "pad_h": pad_h,
        }
        return img, scale_info

    def postprocess(self, output: np.ndarray, scale_info: dict, threshold: float = 0.25) -> list[dict]:
        predictions = np.squeeze(output)

        if predictions.ndim == 1:
            predictions = predictions[np.newaxis, :]

        # Handle different output formats
        if predictions.shape[0] > predictions.shape[1]:
            predictions = predictions.T

        boxes = []
        if predictions.shape[1] < 5:
            return boxes

        # Extract boxes and scores
        scores = np.max(predictions[:, 4:], axis=1)
        class_ids = np.argmax(predictions[:, 4:], axis=1)

        # Filter by threshold
        mask = scores > threshold
        scores = scores[mask]
        class_ids = class_ids[mask]
        pred_boxes = predictions[mask, :4]

        # NMS for each class
        unique_classes = np.unique(class_ids)
        keep_indices = []

        for cls_id in unique_classes:
            cls_mask = class_ids == cls_id
            cls_boxes = pred_boxes[cls_mask]
            cls_scores = scores[cls_mask]
            cls_indices = np.where(cls_mask)[0]

            # Simple NMS
            keep = self._nms(cls_boxes, cls_scores, iou_threshold=0.45)
            keep_indices.extend(cls_indices[keep])

        # Build results
        for idx in keep_indices:
            box = pred_boxes[idx]
            x1, y1, x2, y2 = box

            # Scale back to original size
            ratio = scale_info["ratio"]
            x1 = (x1 - scale_info["pad_w"] / 2) / ratio
            y1 = (y1 - scale_info["pad_h"] / 2) / ratio
            x2 = (x2 - scale_info["pad_w"] / 2) / ratio
            y2 = (y2 - scale_info["pad_h"] / 2) / ratio

            # Clip to image bounds
            x1 = max(0, min(x1, scale_info["ori_w"]))
            y1 = max(0, min(y1, scale_info["ori_h"]))
            x2 = max(0, min(x2, scale_info["ori_w"]))
            y2 = max(0, min(y2, scale_info["ori_h"]))

            if x2 - x1 < 5 or y2 - y1 < 5:
                continue

            cls_id = class_ids[idx]
            label = self.LABELS[cls_id] if cls_id < len(self.LABELS) else "unknown"

            boxes.append({
                "type": label.lower(),
                "bbox": [float(x1), float(y1), float(x2), float(y2)],
                "score": float(scores[idx]),
            })

        return boxes

    def _nms(self, boxes: np.ndarray, scores: np.ndarray, iou_threshold: float = 0.45) -> np.ndarray:
        if len(boxes) == 0:
            return np.array([], dtype=int)

        x1 = boxes[:, 0]
        y1 = boxes[:, 1]
        x2 = boxes[:, 2]
        y2 = boxes[:, 3]
        areas = (x2 - x1) * (y2 - y1)

        order = scores.argsort()[::-1]
        keep = []

        while order.size > 0:
            i = order[0]
            keep.append(i)

            if order.size == 1:
                break

            xx1 = np.maximum(x1[i], x1[order[1:]])
            yy1 = np.maximum(y1[i], y1[order[1:]])
            xx2 = np.minimum(x2[i], x2[order[1:]])
            yy2 = np.minimum(y2[i], y2[order[1:]])

            w = np.maximum(0.0, xx2 - xx1)
            h = np.maximum(0.0, yy2 - yy1)
            inter = w * h

            iou = inter / (areas[i] + areas[order[1:]] - inter)
            inds = np.where(iou <= iou_threshold)[0]
            order = order[inds + 1]

        return np.array(keep, dtype=int)

    def sort_layouts(self, layouts: list[dict], threshold: float = 50.0) -> list[dict]:
        if not layouts:
            return layouts

        layouts.sort(key=lambda x: (x["bbox"][1], x["bbox"][0]))

        groups: list[list[dict]] = []
        current_group: list[dict] = [layouts[0]]

        for layout in layouts[1:]:
            if layout["bbox"][1] - current_group[-1]["bbox"][3] > threshold:
                groups.append(current_group)
                current_group = [layout]
            else:
                current_group.append(layout)

        groups.append(current_group)

        for group in groups:
            group.sort(key=lambda x: x["bbox"][0])

        result = []
        for group in groups:
            result.extend(group)

        return result

    def __call__(self, image: np.ndarray, threshold: float = 0.25) -> list[dict]:
        if not self.is_loaded:
            logger.warning("Layout model not loaded, returning empty result")
            return []

        start = time.time()

        img, scale_info = self.preprocess(image)
        input_name = self.session.get_inputs()[0].name
        output = self.session.run(None, {input_name: img})

        layouts = self.postprocess(output[0], scale_info, threshold)
        layouts = self.sort_layouts(layouts)

        elapsed = time.time() - start
        logger.debug(f"Layout recognition completed: {len(layouts)} layouts in {elapsed:.2f}s")
        return layouts
