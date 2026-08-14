from __future__ import annotations

from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, ConfigDict

from .syncer import merge, version


class MergeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    base: dict[str, Any]
    incoming: dict[str, Any]


class MergeResponse(BaseModel):
    merged: dict[str, Any]
    core_version: str


app = FastAPI(title="FastAPI Kotlin Opto-Sync E2E", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "core_version": version()}


@app.post("/merge", response_model=MergeResponse)
def reconcile(request: MergeRequest) -> MergeResponse:
    return MergeResponse(
        merged=merge(request.base, request.incoming),
        core_version=version(),
    )
