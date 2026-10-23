# Local UAT

Given/When/Then acceptance scenarios are in `scenarios.md` and mapped in `traceability.md`. `make test-uat` performs API smoke steps against the loopback-only Compose stack; visual/accessibility judgment remains an explicit human step. Prerequisites: a healthy `make compose-up compose-ready` stack and curl. Fixtures use reserved `.invalid` identities and deterministic references. Output is terminal-only and contains no credentials.

Do not point this suite at production or any host other than loopback. Traceability: #1, #10, #17 and #13–#16.
