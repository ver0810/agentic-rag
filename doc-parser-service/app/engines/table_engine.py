import time
from pathlib import Path
from typing import Optional

import cv2
import numpy as np
import onnxruntime as ort
from loguru import logger


class TableStructureRecognizer:
    LABELS = [
        "table", "table column", "table row",
        "table column header", "table projected row header",
        "table spanning cell",
    ]

    def __init__(self, model_dir: str | Path = "models/deepdoc"):
        self.model_dir = Path(model_dir)
        self.session: Optional[ort.InferenceSession] = None
        self.input_shape: tuple[int, int] = (640, 640)

    def load(self):
        model_path = self.model_dir / "tsr.onnx"
        if not model_path.exists():
            logger.warning(f"Table structure model not found: {model_path}")
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
        logger.info("Table structure model loaded")

    @property
    def is_loaded(self) -> bool:
        return self.session is not None

    def preprocess(self, image: np.ndarray) -> tuple[np.ndarray, dict]:
        ori_h, ori_w = image.shape[:2]
        target_h, target_w = self.input_shape

        ratio = min(target_h / ori_h, target_w / ori_w)
        new_h, new_w = int(ori_h * ratio), int(ori_w * ratio)

        img = cv2.resize(image, (new_w, new_h))

        pad_h = target_h - new_h
        pad_w = target_w - new_w
        img = cv2.copyMakeBorder(
            img, 0, pad_h, 0, pad_w,
            cv2.BORDER_CONSTANT, value=(114, 114, 114),
        )

        img = img.astype(np.float32) / 255.0
        img = img.transpose(2, 0, 1)[np.newaxis, :, :, :].astype(np.float32)

        scale_info = {
            "ratio": ratio,
            "ori_h": ori_h,
            "ori_w": ori_w,
        }
        return img, scale_info

    def postprocess(self, output: np.ndarray, scale_info: dict, threshold: float = 0.3) -> list[dict]:
        predictions = np.squeeze(output)

        if predictions.ndim == 1:
            predictions = predictions[np.newaxis, :]

        if predictions.shape[0] > predictions.shape[1]:
            predictions = predictions.T

        boxes = []
        if predictions.shape[1] < 6:
            return boxes

        scores = np.max(predictions[:, 5:], axis=1)
        class_ids = np.argmax(predictions[:, 5:], axis=1)

        mask = scores > threshold
        pred_boxes = predictions[mask, :4]
        scores = scores[mask]
        class_ids = class_ids[mask]

        ratio = scale_info["ratio"]

        for i in range(len(pred_boxes)):
            x1, y1, x2, y2 = pred_boxes[i]
            x1 = float(x1 / ratio)
            y1 = float(y1 / ratio)
            x2 = float(x2 / ratio)
            y2 = float(y2 / ratio)

            x1 = max(0, min(x1, scale_info["ori_w"]))
            y1 = max(0, min(y1, scale_info["ori_h"]))
            x2 = max(0, min(x2, scale_info["ori_w"]))
            y2 = max(0, min(y2, scale_info["ori_h"]))

            if x2 - x1 < 3 or y2 - y1 < 3:
                continue

            cls_id = class_ids[i]
            label = self.LABELS[cls_id] if cls_id < len(self.LABELS) else "unknown"

            boxes.append({
                "type": label.lower(),
                "bbox": [x1, y1, x2, y2],
                "score": float(scores[i]),
            })

        return boxes

    def construct_html(self, boxes: list[dict], ocr_results: list[dict], image_shape: tuple) -> str:
        if not boxes:
            return ""

        rows = [b for b in boxes if "row" in b["type"]]
        cols = [b for b in boxes if "column" in b["type"]]

        rows.sort(key=lambda x: x["bbox"][1])
        cols.sort(key=lambda x: x["bbox"][0])

        if not rows or not cols:
            return self._simple_table(ocr_results)

        # Build grid
        grid: list[list[list[dict]]] = []
        for _ in range(len(rows)):
            grid.append([[] for _ in range(len(cols))])

        # Assign OCR results to cells
        for ocr_item in ocr_results:
            cx = (ocr_item["box"][0][0] + ocr_item["box"][2][0]) / 2
            cy = (ocr_item["box"][0][1] + ocr_item["box"][2][1]) / 2

            row_idx = self._find_row(cy, rows)
            col_idx = self._find_col(cx, cols)

            if row_idx >= 0 and col_idx >= 0:
                grid[row_idx][col_idx].append(ocr_item["text"])

        # Build HTML
        html = "<table>"
        for row in grid:
            html += "<tr>"
            for cell in row:
                text = " ".join(cell)
                html += f"<td>{text}</td>"
            html += "</tr>"
        html += "</table>"

        return html

    def _find_row(self, y: float, rows: list[dict]) -> int:
        for i, row in enumerate(rows):
            if row["bbox"][1] <= y <= row["bbox"][3]:
                return i
        if rows:
            dists = [abs((r["bbox"][1] + r["bbox"][3]) / 2 - y) for r in rows]
            return int(np.argmin(dists))
        return -1

    def _find_col(self, x: float, cols: list[dict]) -> int:
        for i, col in enumerate(cols):
            if col["bbox"][0] <= x <= col["bbox"][2]:
                return i
        if cols:
            dists = [abs((c["bbox"][0] + c["bbox"][2]) / 2 - x) for c in cols]
            return int(np.argmin(dists))
        return -1

    def _simple_table(self, ocr_results: list[dict]) -> str:
        if not ocr_results:
            return ""

        lines: list[list[dict]] = []
        sorted_results = sorted(ocr_results, key=lambda x: x["box"][0][1])

        current_line: list[dict] = [sorted_results[0]]
        line_height = sorted_results[0]["box"][3][1] - sorted_results[0]["box"][0][1]

        for item in sorted_results[1:]:
            y_center = (item["box"][0][1] + item["box"][3][1]) / 2
            last_y = (current_line[-1]["box"][0][1] + current_line[-1]["box"][3][1]) / 2

            if abs(y_center - last_y) > line_height * 0.5:
                lines.append(current_line)
                current_line = [item]
            else:
                current_line.append(item)

        lines.append(current_line)

        html = "<table>"
        for line in lines:
            line.sort(key=lambda x: x["box"][0][0])
            html += "<tr>"
            for item in line:
                html += f"<td>{item['text']}</td>"
            html += "</tr>"
        html += "</table>"

        return html

    def __call__(self, image: np.ndarray, threshold: float = 0.3) -> list[dict]:
        if not self.is_loaded:
            logger.warning("Table structure model not loaded, returning empty result")
            return []

        start = time.time()

        img, scale_info = self.preprocess(image)
        input_name = self.session.get_inputs()[0].name
        output = self.session.run(None, {input_name: img})

        boxes = self.postprocess(output[0], scale_info, threshold)

        elapsed = time.time() - start
        logger.debug(f"Table structure recognition completed: {len(boxes)} elements in {elapsed:.2f}s")
        return boxes
