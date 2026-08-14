# FastAPI + Kotlin Opto-Sync E2E

This repository proves a Python/FastAPI server and an official Kotlin client can share an Opto-Sync reconciliation boundary over real HTTP.

## What the test covers

- Python compiles the exact vendored `syncer.c` and `yyjson.c` sources into a platform-native library;
- a typed FastAPI endpoint calls the C API through `ctypes` with the shared CRDT merge policy;
- the Kotlin test compiles the official `OptoSyncClient` source from the pinned clients submodule;
- Java's HTTP client sends an actual request to Uvicorn/FastAPI;
- nested objects and array elements matched by `id` retain independent server and client fields;
- the response exposes the native core version.

The Kotlin source set now includes a scheduler-neutral background worker core
that can be called from Android WorkManager, a desktop scheduler, or a JVM
service. One wake sends mobile and desktop lanes concurrently with Java's
asynchronous HTTP transport and retries the immutable batch when any response
is lost; the integration test verifies both lanes reach the FastAPI/OptoSync
boundary.

`vendor/opto-sync-clients` and its nested `syncer.c` repository are Git submodules. Their exact revisions are recorded in `opto-sync-pin.json`.

## Run locally

Prerequisites: Python 3.12+, a C compiler, and Java 17+.

```sh
git submodule update --init --recursive
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m pytest -q server/tests
.venv/bin/python -m uvicorn server.app:app --host 127.0.0.1 --port 8061
```

With the server running, execute the Kotlin network test in another terminal:

```sh
cd kotlin
./gradlew test --no-daemon
```
