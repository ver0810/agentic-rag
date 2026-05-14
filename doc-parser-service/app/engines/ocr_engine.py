import os
import time
from pathlib import Path
from typing import Optional

import cv2
import numpy as np
import onnxruntime as ort
from loguru import logger


class OCR:
    def __init__(self, model_dir: str | Path = "models/deepdoc", device_id: Optional[int] = None):
        self.model_dir = Path(model_dir)
        self.device_id = device_id
        self.det_session: Optional[ort.InferenceSession] = None
        self.rec_session: Optional[ort.InferenceSession] = None
        self.rec_char_dict: list[str] = []

    def load(self):
        self._load_det_model()
        self._load_rec_model()
        self._load_char_dict()
        logger.info("OCR models loaded")

    def _get_session_options(self) -> ort.SessionOptions:
        options = ort.SessionOptions()
        options.enable_cpu_mem_arena = False
        options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
        options.intra_op_num_threads = 2
        options.inter_op_num_threads = 2
        return options

    def _get_providers(self) -> tuple[list[str], list[dict]]:
        available = ort.get_available_providers()
        if "CUDAExecutionProvider" in available:
            return ["CUDAExecutionProvider", "CPUExecutionProvider"], [{"device_id": self.device_id or 0}, {}]
        return ["CPUExecutionProvider"], [{}]

    def _load_det_model(self):
        model_path = self.model_dir / "det.onnx"
        if not model_path.exists():
            logger.warning(f"Detection model not found: {model_path}")
            return
        providers, provider_options = self._get_providers()
        self.det_session = ort.InferenceSession(
            str(model_path),
            sess_options=self._get_session_options(),
            providers=providers,
        )

    def _load_rec_model(self):
        model_path = self.model_dir / "rec.onnx"
        if not model_path.exists():
            logger.warning(f"Recognition model not found: {model_path}")
            return
        providers, provider_options = self._get_providers()
        self.rec_session = ort.InferenceSession(
            str(model_path),
            sess_options=self._get_session_options(),
            providers=providers,
        )

    def _load_char_dict(self):
        dict_path = self.model_dir / "ocr.res"
        if dict_path.exists():
            with open(dict_path, "r", encoding="utf-8") as f:
                self.rec_char_dict = [line.strip() for line in f.readlines()]
        else:
            logger.warning(f"Char dict not found: {dict_path}, using empty dict")

    @property
    def is_loaded(self) -> bool:
        return self.det_session is not None and self.rec_session is not None

    def detect(self, image: np.ndarray) -> list[dict]:
        if self.det_session is None:
            return []

        ori_h, ori_w = image.shape[:2]

        # Preprocess
        img = self._det_preprocess(image)
        input_name = self.det_session.get_inputs()[0].name

        # Inference
        outputs = self.det_session.run(None, {input_name: img})

        # Postprocess
        boxes = self._det_postprocess(outputs[0], ori_h, ori_w)
        return boxes

    def _det_preprocess(self, image: np.ndarray) -> np.ndarray:
        limit_side_len = 960
        h, w = image.shape[:2]

        # Resize
        if max(h, w) > limit_side_len:
            ratio = limit_side_len / max(h, w)
            new_h, new_w = int(h * ratio), int(w * ratio)
            image = cv2.resize(image, (new_w, new_h))

        # Normalize
        img = image.astype(np.float32) / 255.0
        mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
        std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
        img = (img - mean) / std

        # HWC to CHW and add batch dimension
        img = img.transpose(2, 0, 1)[np.newaxis, :, :, :].astype(np.float32)
        return img

    def _det_postprocess(self, output: np.ndarray, ori_h: int, ori_w: int) -> list[dict]:
        pred = output[0, 0, :, :]
        bitmap = pred > 0.3  # threshold

        boxes = []
        contours, _ = cv2.findContours(bitmap.astype(np.uint8), cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)

        for contour in contours:
            if cv2.contourArea(contour) < 100:
                continue
            rect = cv2.minAreaRect(contour)
            box = cv2.boxPoints(rect)
            box = self._order_points_clockwise(box)

            # Scale back to original size
            h_ratio = ori_h / pred.shape[0]
            w_ratio = ori_w / pred.shape[1]
            box[:, 0] *= w_ratio
            box[:, 1] *= h_ratio

            boxes.append({
                "box": box.astype(np.float32),
                "score": float(pred[bitmap].mean()) if bitmap.any() else 0.0,
            })

        return boxes

    def _order_points_clockwise(self, pts: np.ndarray) -> np.ndarray:
        rect = np.zeros((4, 2), dtype=np.float32)
        s = pts.sum(axis=1)
        rect[0] = pts[np.argmin(s)]
        rect[2] = pts[np.argmax(s)]
        diff = np.diff(pts, axis=1)
        rect[1] = pts[np.argmin(diff)]
        rect[3] = pts[np.argmax(diff)]
        return rect

    def recognize(self, image: np.ndarray, box: np.ndarray) -> tuple[str, float]:
        if self.rec_session is None:
            return "", 0.0

        # Crop text region
        cropped = self._crop_text_region(image, box)
        if cropped is None or cropped.size == 0:
            return "", 0.0

        # Preprocess
        img = self._rec_preprocess(cropped)
        input_name = self.rec_session.get_inputs()[0].name

        # Inference
        outputs = self.rec_session.run(None, {input_name: img})

        # Decode
        text, confidence = self._rec_decode(outputs[0])
        return text, confidence

    def _crop_text_region(self, image: np.ndarray, box: np.ndarray) -> Optional[np.ndarray]:
        try:
            pts = box.astype(np.float32)
            width = int(np.linalg.norm(pts[0] - pts[1]))
            height = int(np.linalg.norm(pts[0] - pts[3]))

            if width < 2 or height < 2:
                return None

            dst_pts = np.array([
                [0, 0],
                [width - 1, 0],
                [width - 1, height - 1],
                [0, height - 1],
            ], dtype=np.float32)

            M = cv2.getPerspectiveTransform(pts, dst_pts)
            cropped = cv2.warpPerspective(image, M, (width, height))

            # Rotate if height > width
            if cropped.shape[0] > cropped.shape[1] * 1.5:
                cropped = cv2.rotate(cropped, cv2.ROTATE_90_COUNTERCLOCKWISE)

            return cropped
        except Exception:
            return None

    def _rec_preprocess(self, image: np.ndarray) -> np.ndarray:
        target_h = 48
        target_w = 320
        h, w = image.shape[:2]

        # Resize keeping aspect ratio
        ratio = target_h / h
        new_w = min(int(w * ratio), target_w)
        image = cv2.resize(image, (new_w, target_h))

        # Pad to target width
        if new_w < target_w:
            image = cv2.copyMakeBorder(image, 0, 0, 0, target_w - new_w, cv2.BORDER_CONSTANT, value=(0, 0, 0))

        # Normalize
        img = image.astype(np.float32) / 255.0
        mean = np.array([0.5, 0.5, 0.5], dtype=np.float32)
        std = np.array([0.5, 0.5, 0.5], dtype=np.float32)
        img = (img - mean) / std

        # HWC to CHW and add batch dimension
        img = img.transpose(2, 0, 1)[np.newaxis, :, :, :].astype(np.float32)
        return img

    def _rec_decode(self, output: np.ndarray) -> tuple[str, float]:
        if len(self.rec_char_dict) == 0:
            return "", 0.0

        preds = output[0]
        text = []
        confidences = []
        prev_idx = -1

        for t in range(preds.shape[0]):
            idx = np.argmax(preds[t])
            confidence = float(preds[t][idx])

            if idx != prev_idx and idx > 0 and idx <= len(self.rec_char_dict):
                char = self.rec_char_dict[idx - 1]
                text.append(char)
                confidences.append(confidence)

            prev_idx = idx

        avg_confidence = np.mean(confidences) if confidences else 0.0
        return "".join(text), avg_confidence

    def __call__(self, image: np.ndarray) -> list[dict]:
        if not self.is_loaded:
            logger.warning("OCR models not loaded, returning empty result")
            return []

        start = time.time()

        # Detect text regions
        boxes = self.detect(image)

        # Recognize text in each region
        results = []
        for box_info in boxes:
            text, confidence = self.recognize(image, box_info["box"])
            if text:
                results.append({
                    "text": text,
                    "box": box_info["box"].tolist(),
                    "confidence": confidence,
                    "det_score": box_info["score"],
                })

        elapsed = time.time() - start
        logger.debug(f"OCR completed: {len(results)} texts in {elapsed:.2f}s")
        return results
