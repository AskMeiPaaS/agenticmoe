# Agentic Mixture of Experts (A-MoE)

An Enterprise-ready Java Agentic System leveraging **Voyage AI** for low-latency, low-cost routing using Matryoshka Representation Learning (MRL).

---

## 🚀 Overview

The **Agentic MoE** architecture avoids the high cost of monolithic LLMs by separating **Routing** (intent classification) from **Execution** (generation). 

By indexing domain expert capabilities with voyage-4-large and performing ultra-fast routing using cosine similarity on raw float arrays, the system achieves **<15ms intent classification** and routes traffic predictably to specialized domain experts. 

### Why Use Agentic MoE?
1. **Zero-Cost Routing**: Routing logic occurs in-memory using pre-computed embedding vectors.
2. **Deterministic Fallback**: If a query is malformed or out-of-scope, it safely drops into the Generalist expert.
3. **Semantic Caching**: Common queries and expert selection are cached to MongoDB, eliminating API calls for repeated questions.

---

## 🛠️ Architecture Flow

When a user submits a query:
1. **Tier 1 (Routing)**: The `AgentService` embeds the user query into a 512-dimension vector (`voyage-4-large`).
2. **Classification**: `SemanticRouter.java` computes the Cosine Similarity against all registered experts (e.g. `CODE`, `FINANCE`, `GENERAL`).
3. **Execution**: The query is handed off to the `Expert` interface, which queries Voyage's LLM endpoints using a domain-specific model (e.g., `voyage-code-3`).
4. **Caching**: Vectors, Expert answers, and the top matched expert embedding are cached in MongoDB for instant retrieval on identical semantic queries.

For a deep dive into the technical details and cost-savings projections, see `ARCHITECTURE.md`.

---

## 📦 Prerequisites

* Java 21+ 
* Maven 3.9+
* MongoDB (running locally on `localhost:27017` or configured in `application.properties`)
* Node.js (for the frontend chat UI)
* A valid **Voyage AI API Key** 

## 🏃 Getting Started

### 1. Configure the API Key
Set your Voyage AI token as an environment variable (`VOYAGEAI_API_KEY`) so that Spring Boot can inject it:
```bash
export VOYAGEAI_API_KEY=YOUR_VOYAGE_AI_API_KEY
```

### 2. Start the Spring Boot Backend
```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

### 3. Start the Node.js Chat Interface
```bash
cd node-chat-app
node server.js
```
Navigate to `http://localhost:3000` to interact with the A-MoE agent. 

---

## 🧪 Testing

The codebase includes standard JUnit 5 testing using Mockito to verify the asynchronous orchestration:
```bash
mvn test
```
