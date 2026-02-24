# Architecture Strategy: Agentic Mixture of Experts (A-MoE)

**Artifact ID:** `com.ayedata.agenticmoe`
**Version:** 2.0 (Voyage AI 2026 Stack)
**Status:** Approved for Pilot
**Date:** February 24, 2026

---

## 1. Executive Summary
This document outlines the architectural strategy for **Ayedata's** next-generation Agentic System. Moving away from "Monolithic" agents (single, expensive LLMs), we adopt a **System-Level Mixture of Experts (MoE)**.

By leveraging **Voyage AI’s** shared embedding spaces, we decouple **Routing** (cost-center) from **Execution** (value-center). This allows us to route 100% of traffic using ultra-low-cost local models (`voyage-4-nano`), reserving expensive frontier models only for complex reasoning tasks.

---

## 2. Core Architectural Principles
The system is built on three pillars of cost-efficiency and performance:

### A. Asymmetric Retrieval (The "Zero-Cost" Router)
* **Concept:** Index data with high-fidelity models; route queries with lightweight models.
* **Implementation:** Documents are indexed using `voyage-4-large`. Queries are embedded using `voyage-4-nano`.
* **Benefit:** Enables routing latency of **<10ms** and near-zero inference cost while maintaining high retrieval accuracy due to the shared vector space.

### B. Matryoshka Representation Learning (MRL)
* **Concept:** "Elastic" vectors that retain semantic meaning even when truncated.
* **Implementation:** The Router uses **256-dimension binary vectors** (instead of standard 1536 float32) for the initial search.
* **Benefit:** Reduces Vector RAM usage by **~90%**, allowing the Routing Index to reside entirely in memory.

### C. The "Step-Up" Escalation Policy
* **Concept:** Always attempt the cheapest path first.
* **Implementation:**
    1.  **L1:** Try Fast Expert (`voyage-4`).
    2.  **L2:** If confidence < 0.15, escalate to Domain Expert (`voyage-code-3`, `voyage-law-2`).
    3.  **L3:** If Reranker Score < 0.10, escalate to Clarification/Fallback.

---

## 3. System Architecture Diagram

```text
graph TD
    User([User Query]) --> Ingress{Ingress Gateway}
    
    subgraph "Tier 1: Semantic Routing (Local)"
        Ingress --> Router[voyage-4-nano]
        Router -- "Binary Quantized Search" --> SkillMap[(Expert Clusters)]
        SkillMap --> Decider{Confidence Check}
    end
    
    subgraph "Tier 2: Domain Experts"
        Decider -- "Tech/Syntax" --> CodeExp[Code Expert<br/>(voyage-code-3)]
        Decider -- "Compliance" --> LegalExp[Legal Expert<br/>(voyage-law-2)]
        Decider -- "General" --> GenExp[Generalist<br/>(voyage-4-lite)]
    end
    
    subgraph "Tier 3: Audit & Validation"
        CodeExp & LegalExp & GenExp --> Validator[rerank-2.5-lite]
        Validator -- "Score > 0.8" --> Execution[LLM Execution]
        Validator -- "Score < 0.5" --> Loop[Feedback Loop / Clarify]
    end

    Execution --> Output([Final Response])
    Loop -.-> Router
```
## 4. Component Definitions

### Tier 1: The Ingress Router (`com.ayedata.agenticmoe.router`)
The "Brain" of the operation. It decides *where* a query goes without incurring network latency or high inference costs.
* **Model:** `voyage-4-nano` (Quantized to 1-bit/Binary).
* **Function:** Maps user intent to a specific expert cluster.
* **Logic:** Uses **Cosine Similarity** against a pre-computed map of "Expert Capabilities" (Skill Map).
* **Performance Constraint:** Must execute routing logic in **< 15ms** (99th percentile).

### Tier 2: The Expert Swarm (`com.ayedata.agenticmoe.experts`)
Each expert is an isolated RAG pipeline optimized for a specific domain. They utilize **Sealed Interfaces** in Java to strictly enforce domain boundaries.

| Expert Type | Embedding Model | Optimization Goal | Knowledge Base |
| :--- | :--- | :--- | :--- |
| **Code Expert** | `voyage-code-3` | Syntax, API Signatures, Deprecation checks | GitHub, JIRA, Confluence |
| **Legal Expert** | `voyage-law-2` | Regulatory nuance, Contract definitions | SharePoint, LexisNexis |
| **Generalist** | `voyage-4-lite` | Broad context, Summarization | Wiki, Public Internet |

### Tier 3: Validation & Memory (`com.ayedata.agenticmoe.core`)
The "Teacher" layer that ensures quality and learns from mistakes.
* **Validator:** `rerank-2.5-lite`. It scores the retrieved documents from the Expert.
    * *Threshold:* If Score < 0.5, the system aborts the LLM call and asks for clarification.
* **Memory:** `voyage-context-3`. Stores "Failure Embeddings." If a query fails or receives negative feedback, its vector is stored as a "Negative Constraint" to prevent the Router from making the same mistake twice.
* **Semantic Caching:** Uses **MongoDB** with Time-To-Live (TTL) indexing (e.g., 30-day expiration) to cache query embeddings (`CachedEmbedding.java`). The `SemanticCache` layer sits before Tier 1, instantly returning expert logic if the exact query embedding `voyage-4-nano` matches the cache.

### Thresholding & Binary Quantization Note
When switching from `float32` vectors to **Binary Quantized Vectors**, cosine similarity scores drop significantly. In a float space, matching context might score `0.80`, but in binary 256-dim space, that same match yields `0.15`. The `voyage.routing.threshold` handles this intentionally to enforce rapid classification.

## 5. Implementation Stack (Java 21)

The system is implemented as a modular Java library, designed for high-throughput enterprise environments.

* **Runtime:** Java 21 LTS.
* **Concurrency:** **Virtual Threads** (`Executors.newVirtualThreadPerTaskExecutor`) are used for all I/O-bound tasks (Voyage API calls), allowing the system to handle thousands of concurrent agents without blocking OS threads.
* **Type Safety:** **Sealed Interfaces** (`permits CodeExpert, LegalExpert...`) ensure that the Router can only dispatch to known, authorized agents.
* **Resilience:**
    * **Circuit Breakers:** Prevent cascading failures if the Voyage API experiences latency.
    * **Fallback Strategy:** Automatically degrades to the Generalist expert (`voyage-4-lite`) if the specialized routing is unreachable or confidence is too low.
* **State Management:** **MongoDB** provides a scalable document store for Semantic Caching, capitalizing on built-in TTL indexes for stale-vector eviction.
* **Endpoint Independence:** Explicit configuration profiles (`voyage.embedding-url`, `voyage.chat-url`) separate embedding and generation workloads, enabling distinct retry capabilities and granular rate limiting.

### Package Structure
```text
project-root
├── pom.xml
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── ayedata
│   │   │           └── agenticmoe
│   │   │               ├── AgentApplication.java
│   │   │               ├── api
│   │   │               │   └── AgentController.java
│   │   │               ├── config
│   │   │               │   └── AppConfig.java
│   │   │               ├── core
│   │   │               │   └── VoyageClient.java
│   │   │               ├── experts
│   │   │               │   ├── Expert.java
│   │   │               │   ├── CodeExpert.java
│   │   │               │   ├── LegalExpert.java
│   │   │               │   └── GeneralistExpert.java
│   │   │               ├── model
│   │   │               │   └── ExpertType.java
│   │   │               ├── orchestrator
│   │   │               │   └── AgentService.java
│   │   │               └── router
│   │   │                   └── SemanticRouter.java
│   │   └── resources
│   │       └── application.properties
│   └── test
│       └── java
│           └── com
│               └── ayedata
│                   └── agenticmoe
│                       └── orchestrator
│                           └── AgentServiceTest.java
└── node-chat-app
    ├── package.json
    ├── server.js
    └── public
        └── index.html
```

## 6. Cost Impact Analysis (Projected)

By shifting from a monolithic "One-Model-Fits-All" approach to the **Ayedata Agentic MoE**, we project significant savings across compute, storage, and inference.

**Scenario:** Enterprise processing **1 Million** complex queries/month.

| Cost Component | Legacy Monolithic Agent (GPT-4) | Ayedata MoE Agent (Voyage + Mixed Models) | Savings |
| :--- | :--- | :--- | :--- |
| **Routing** | $500 (GPT-3.5 Turbo) | **$0** (Local Nano Model) | **100%** |
| **Vector Storage** | $2,000 (Full Float32) | **$200** (Binary Quantization / MRL) | **90%** |
| **Context Processing** | $15,000 (Retrieving Top-10 Chunks) | **$4,500** (Top-3 via Rerank Gate) | **70%** |
| **LLM Inference** | $30,000 (All queries to Frontier Model) | **$8,000** (80% routed to Small/Medium models) | **73%** |
| **Total Monthly Bill** | **$47,500** | **$12,700** | **~73% Savings** |

---

## 7. Security & Compliance

The Ayedata MoE architecture introduces specific security controls at the routing and retrieval layers to ensure enterprise-grade compliance.

### A. Input Sanitization (Pre-Embedding)
All user queries pass through a `SanitizationFilter` in the `VoyageClient` before any embedding or inference occurs.
* **Mechanism:** Regex-based filtering to strip potential **Prompt Injection** patterns and PII (Personal Identifiable Information) markers.
* **Benefit:** Prevents malicious payloads from polluting the vector space or triggering unintended expert behaviors.

### B. Role-Based Access Control (RBAC)
The `SemanticRouter` integrates with the enterprise identity provider (IdP) to check user permissions *before* dispatching to a Domain Expert.
* **Logic:** A query routed to the `LegalExpert` (Tier 2) will be blocked if the user's JWT does not contain the `GROUP_LEGAL` claim.
* **Benefit:** Ensures that sensitive knowledge bases (e.g., HR records, M&A documents) remain inaccessible to unauthorized agents.

### C. Data Residency & Local Routing
The Tier 1 routing logic (using `voyage-4-nano`) executes entirely **locally** (On-Prem or Private Cloud).
* **Privacy:** Sensitive query intents are processed in-memory.
* **Leakage Prevention:** No data is sent to external model providers unless the query is explicitly escalated to a Tier 2/3 cloud expert, and even then, it is anonymized where possible.

---

## 8. Deployment Roadmap

We will follow a phased rollout strategy to mitigate risk and validate cost assumptions.

### Phase 1: The "Lite" Pilot (Weeks 1-4)
* **Goal:** Validate Routing Accuracy without impacting production.
* **Action:** Deploy the `SemanticRouter` locally alongside the existing system. Log routing decisions (e.g., "Router chose Legal Expert") without executing them ("Shadow Mode").
* **Success Metric:** Router achieves **>90% intent classification accuracy** compared to human-labeled ground truth.

### Phase 2: Domain Indexing (Weeks 5-8)
* **Goal:** Build the specialized Expert Knowledge Bases.
* **Action:**
    * Re-index the Engineering Wiki and Codebase using `voyage-code-3`.
    * Re-index Legal Contracts using `voyage-law-2`.
* **Success Metric:** Retrieval precision (Recall@5) improves by **40%** compared to the legacy generalist index.

### Phase 3: Production Rollout (Weeks 9+)
* **Goal:** Full System Live with Cost Controls.
* **Action:**
    * Deploy `AgentOrchestrator` to production.
    * Enable the `rerank-2.5-lite` gatekeeper to filter irrelevant context.
    * Activate the `voyage-context-3` feedback loop for continuous router improvement.
* **Success Metric:** System cost per 1k queries drops below **$15.00** while maintaining or improving user satisfaction scores (CSAT).