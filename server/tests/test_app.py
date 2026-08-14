from fastapi.testclient import TestClient

from server.app import app


def test_fastapi_merges_with_the_pinned_core() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/merge",
            json={
                "base": {
                    "id": "doc-1",
                    "profile": {"server": "kept"},
                    "items": [{"id": "a", "server": True}],
                },
                "incoming": {
                    "profile": {"client": "kept"},
                    "items": [{"id": "a", "client": True}],
                },
            },
        )

    assert response.status_code == 200
    body = response.json()
    assert body["core_version"]
    assert body["merged"]["profile"] == {
        "server": "kept",
        "client": "kept",
    }
    assert body["merged"]["items"] == [
        {"id": "a", "server": True, "client": True}
    ]
