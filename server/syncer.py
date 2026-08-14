from __future__ import annotations

import ctypes
import json
import os
from pathlib import Path
from typing import Any

from .build_core import build


class MergeOptions(ctypes.Structure):
    _fields_ = [
        ("override_cb", ctypes.c_void_p),
        ("array_strategy", ctypes.c_int),
        ("max_depth", ctypes.c_uint32),
        ("detect_circular_refs", ctypes.c_bool),
        ("resolve_by_timestamp", ctypes.c_bool),
        ("lww_keys", ctypes.c_char_p),
        ("fww_keys", ctypes.c_char_p),
        ("array_match_keys", ctypes.c_char_p),
    ]


def _library_path() -> Path:
    configured = os.environ.get("OPTO_SYNC_LIB")
    if configured:
        return Path(configured)
    return build()


_LIB = ctypes.CDLL(str(_library_path()))
_LIB.syncer_merge_json_ex.argtypes = [
    ctypes.c_char_p,
    ctypes.c_char_p,
    ctypes.POINTER(MergeOptions),
]
_LIB.syncer_merge_json_ex.restype = ctypes.c_void_p
_LIB.syncer_free.argtypes = [ctypes.c_void_p]
_LIB.syncer_free.restype = None
_LIB.syncer_version.argtypes = []
_LIB.syncer_version.restype = ctypes.c_char_p


def version() -> str:
    return _LIB.syncer_version().decode("utf-8")


def merge(base: dict[str, Any], incoming: dict[str, Any]) -> dict[str, Any]:
    options = MergeOptions(
        override_cb=None,
        array_strategy=4,
        max_depth=0,
        detect_circular_refs=False,
        resolve_by_timestamp=True,
        lww_keys=b"updatedAt,syncedAt",
        fww_keys=None,
        array_match_keys=b"id",
    )
    base_json = json.dumps(base, separators=(",", ":")).encode("utf-8")
    incoming_json = json.dumps(incoming, separators=(",", ":")).encode("utf-8")
    result_pointer = _LIB.syncer_merge_json_ex(
        base_json,
        incoming_json,
        ctypes.byref(options),
    )
    if not result_pointer:
        raise ValueError("syncer.c rejected the merge input")
    try:
        merged_json = ctypes.string_at(result_pointer).decode("utf-8")
    finally:
        _LIB.syncer_free(result_pointer)
    return json.loads(merged_json)
