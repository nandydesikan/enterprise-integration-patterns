# Software

This folder contains deployable application code. Cloud provisioning does not belong here.

`service-mediation-layer/` is the returns-processing orchestration service. Its internal packages follow ports and adapters: domain rules at the center, application use cases around them, and Spring/API/persistence concerns at the edges.
