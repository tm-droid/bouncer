---
name: agentic-eval
description: |
  Patterns and techniques for evaluating and improving AI agent outputs. Use this skill when:
  - Implementing self-critique and reflection loops
  - Building evaluator-optimizer pipelines for quality-critical generation
  - Creating test-driven code refinement workflows
  - Designing rubric-based or LLM-as-judge evaluation systems
  - Adding iterative improvement to agent outputs (code, reports, analysis)
  - Measuring and improving agent response quality
---

# Agentic Evaluation Patterns

Patterns for self-improvement through iterative evaluation and refinement.

## Overview

Evaluation patterns enable agents to assess and improve their own outputs, moving beyond single-shot generation to iterative refinement loops.

```
Generate → Evaluate → Critique → Refine → Output
    ↑                              │
    └──────────────────────────────┘
```

## When to Use

- **Quality-critical generation**: Code, reports, analysis requiring high accuracy
- **Tasks with clear evaluation criteria**: Defined success metrics exist
- **Content requiring specific standards**: Style guides, compliance, formatting

---

## Pattern 1: Basic Reflection

Agent evaluates and improves its own output through self-critique.

```python
def reflect_and_refine(task: str, criteria: list[str], max_iterations: int = 3) -> str:
    """Generate with reflection loop."""
    output = llm(f"Complete this task:\n{task}")
    
    for i in range(max_iterations):
        # Self-critique
        critique = llm(f"""
        Evaluate this output against criteria: {criteria}
        Output: {output}
        Rate each: PASS/FAIL with feedback as JSON.
        """)
        
        critique_data = json.loads(critique)
        all_pass = all(c["status"] == "PASS" for c in critique_data.values())
        if all_pass:
            return output
        
        # Refine based on critique
        failed = {k: v["feedback"] for k, v in critique_data.items() if v["status"] == "FAIL"}
        output = llm(f"Improve to address: {failed}\nOriginal: {output}")
    
    return output
```

**Key insight**: Use structured JSON output for reliable parsing of critique results.

---

## Pattern 2: Evaluator-Optimizer

Separate generation and evaluation into distinct components for clearer responsibilities.

```python
class EvaluatorOptimizer:
    def __init__(self, score_threshold: float = 0.8):
        self.score_threshold = score_threshold
    
    def generate(self, task: str) -> str:
        return llm(f"Complete: {task}")
    
    def evaluate(self, output: str, task: str) -> dict:
        return json.loads(llm(f"""
        Evaluate output for task: {task}
        Output: {output}
        Return JSON: {{"overall_score": 0-1, "dimensions": {{"accuracy": ..., "clarity": ...}}}}
        """))
    
    def optimize(self, output: str, feedback: dict) -> str:
        return llm(f"Improve based on feedback: {feedback}\nOutput: {output}")
    
    def run(self, task: str, max_iterations: int = 3) -> str:
        output = self.generate(task)
        for _ in range(max_iterations):
            evaluation = self.evaluate(output, task)
            if evaluation["overall_score"] >= self.score_threshold:
                break
            output = self.optimize(output, evaluation)
        return output
```

---

## Pattern 3: Code-Specific Reflection

Test-driven refinement loop for code generation.

```python
class CodeReflector:
    def reflect_and_fix(self, spec: str, max_iterations: int = 3) -> str:
        code = llm(f"Write Python code for: {spec}")
        tests = llm(f"Generate pytest tests for: {spec}\nCode: {code}")
        
        for _ in range(max_iterations):
            result = run_tests(code, tests)
            if result["success"]:
                return code
            code = llm(f"Fix error: {result['error']}\nCode: {code}")
        return code
```

---

## Evaluation Strategies

### Outcome-Based
Evaluate whether output achieves the expected result.

```python
def evaluate_outcome(task: str, output: str, expected: str) -> str:
    return llm(f"Does output achieve expected outcome? Task: {task}, Expected: {expected}, Output: {output}")
```

### LLM-as-Judge
Use LLM to compare and rank outputs.

```python
def llm_judge(output_a: str, output_b: str, criteria: str) -> str:
    return llm(f"Compare outputs A and B for {criteria}. Which is better and why?")
```

### Rubric-Based
Score outputs against weighted dimensions.

```python
RUBRIC = {
    "accuracy": {"weight": 0.4},
    "clarity": {"weight": 0.3},
    "completeness": {"weight": 0.3}
}

def evaluate_with_rubric(output: str, rubric: dict) -> float:
    scores = json.loads(llm(f"Rate 1-5 for each dimension: {list(rubric.keys())}\nOutput: {output}"))
    return sum(scores[d] * rubric[d]["weight"] for d in rubric) / 5
```

---

## Pattern 4: Kotlin/Android Code Evaluation

Evaluation patterns specific to Kotlin and Android development.

```kotlin
class AndroidCodeEvaluator {
    val androidCriteria = listOf(
        "Null safety: Proper use of nullable types and null checks",
        "Coroutines: Structured concurrency with appropriate dispatchers",
        "Lifecycle awareness: No memory leaks, proper scope handling",
        "Resource management: Context usage, avoiding memory leaks"
    )
    
    fun evaluateKotlinCode(code: String): Map<String, String> {
        return mapOf(
            "null_safety" to llm("Evaluate null safety patterns in: $code"),
            "coroutines" to llm("Check coroutine usage and dispatchers in: $code"),
            "lifecycle" to llm("Identify potential lifecycle issues in: $code")
        )
    }
}
```

---

## Pattern 5: Compose UI Evaluation Rubric

Evaluate Jetpack Compose code for performance and accessibility.

```kotlin
val COMPOSE_RUBRIC = mapOf(
    "recomposition" to mapOf(
        "weight" to 0.3f,
        "criteria" to listOf(
            "State hoisting properly implemented",
            "remember{} used for expensive computations",
            "derivedStateOf{} for derived state",
            "Stable keys in LazyColumn items"
        )
    ),
    "accessibility" to mapOf(
        "weight" to 0.25f,
        "criteria" to listOf(
            "contentDescription on images/icons",
            "Semantic properties for screen readers",
            "Touch target sizes >= 48dp",
            "Color contrast ratios meet WCAG"
        )
    ),
    "performance" to mapOf(
        "weight" to 0.25f,
        "criteria" to listOf(
            "No heavy computation in composables",
            "Animations use Modifier.graphicsLayer",
            "Lists use LazyColumn/LazyRow",
            "Images loaded with Coil/async"
        )
    ),
    "architecture" to mapOf(
        "weight" to 0.2f,
        "criteria" to listOf(
            "Unidirectional data flow",
            "ViewModel state management",
            "Side effects in LaunchedEffect",
            "Preview functions for components"
        )
    )
)

fun evaluateComposeUI(composable: String, rubric: Map<String, Any>): Float {
    val scores = rubric.map { (dimension, config) ->
        val criteria = (config as Map<*, *>)["criteria"] as List<*>
        val weight = (config["weight"] as Number).toFloat()
        val score = llm("Rate 1-5 for each: $criteria\nCode: $composable")
        parseScore(score) * weight
    }
    return scores.sum() / 5f
}
```

---

## Pattern 6: Test Coverage Evaluation

Evaluate test quality and coverage for Android projects.

```kotlin
class TestCoverageEvaluator {
    data class TestMetrics(
        val unitTestCount: Int,
        val instrumentedTestCount: Int,
        val viewModelCoverage: Float,
        val repositoryCoverage: Float,
        val uiTestCoverage: Float
    )
    
    val testCriteria = mapOf(
        "viewmodel_tests" to "ViewModels have unit tests for all public methods",
        "repository_tests" to "Repositories tested with fake data sources",
        "ui_tests" to "Critical user flows have Compose UI tests",
        "edge_cases" to "Tests cover error states and edge cases",
        "mocking" to "Dependencies properly mocked/faked"
    )
    
    fun evaluateTestSuite(testFiles: List<String>): Map<String, String> {
        return testCriteria.mapValues { (key, criterion) ->
            llm("Evaluate if tests meet criterion: $criterion\nTest files: $testFiles")
        }
    }
}
```

---

## Pattern 7: Sentry Error Monitoring Integration

Patterns for evaluating error handling and monitoring integration.

```kotlin
val SENTRY_EVALUATION_CRITERIA = listOf(
    "Breadcrumbs: Key user actions logged as breadcrumbs",
    "Context: User/device context attached to events",
    "Transactions: Performance transactions for critical paths",
    "Scopes: Proper scope management for user sessions",
    "Filtering: Sensitive data filtered from events"
)

fun evaluateSentryIntegration(code: String): Map<String, Boolean> {
    return mapOf(
        "has_breadcrumbs" to code.contains("Sentry.addBreadcrumb"),
        "has_transactions" to code.contains("Sentry.startTransaction"),
        "has_context" to code.contains("Sentry.configureScope"),
        "captures_exceptions" to code.contains("Sentry.captureException"),
        "has_filtering" to code.contains("beforeSend")
    )
}

// Example integration check
fun auditSentryUsage(activityCode: String): List<String> {
    val issues = mutableListOf<String>()
    if (!activityCode.contains("addBreadcrumb")) {
        issues.add("Missing lifecycle breadcrumbs in Activity")
    }
    if (!activityCode.contains("captureException") && activityCode.contains("catch")) {
        issues.add("Exceptions caught but not reported to Sentry")
    }
    return issues
}
```

---

## Pattern 8: Mobile-Specific Evaluation

Evaluate for mobile platform constraints and best practices.

```kotlin
data class MobileEvaluation(
    val batteryEfficiency: Float,
    val memoryManagement: Float,
    val networkEfficiency: Float,
    val offlineSupport: Float
)

val MOBILE_CRITERIA = mapOf(
    "battery" to mapOf(
        "weight" to 0.25f,
        "checks" to listOf(
            "Background work uses WorkManager",
            "Location updates use appropriate interval",
            "Wake locks released properly",
            "Sensors unregistered when not needed"
        )
    ),
    "memory" to mapOf(
        "weight" to 0.25f,
        "checks" to listOf(
            "Bitmaps recycled or use Coil",
            "No Context leaks in singletons",
            "ViewModels don't hold View references",
            "Large objects cleared in onDestroy"
        )
    ),
    "network" to mapOf(
        "weight" to 0.25f,
        "checks" to listOf(
            "API calls batched when possible",
            "Images use appropriate resolution",
            "Caching implemented for frequent requests",
            "Connectivity checked before requests"
        )
    ),
    "offline" to mapOf(
        "weight" to 0.25f,
        "checks" to listOf(
            "Local database for offline data",
            "Queue for failed operations",
            "Graceful degradation without network",
            "Sync strategy for reconnection"
        )
    )
)

fun evaluateMobileOptimization(codebase: Map<String, String>): MobileEvaluation {
    // Evaluate each dimension
    return MobileEvaluation(
        batteryEfficiency = evaluateDimension("battery", codebase),
        memoryManagement = evaluateDimension("memory", codebase),
        networkEfficiency = evaluateDimension("network", codebase),
        offlineSupport = evaluateDimension("offline", codebase)
    )
}
```

---

## Best Practices

| Practice | Rationale |
|----------|-----------|
| **Clear criteria** | Define specific, measurable evaluation criteria upfront |
| **Iteration limits** | Set max iterations (3-5) to prevent infinite loops |
| **Convergence check** | Stop if output score isn't improving between iterations |
| **Log history** | Keep full trajectory for debugging and analysis |
| **Structured output** | Use JSON for reliable parsing of evaluation results |

---

## Android-Specific Best Practices

| Practice | Rationale |
|----------|-----------|
| **Use ViewModelScope** | Automatically cancels coroutines on ViewModel clear |
| **Prefer StateFlow over LiveData** | Better null safety and Compose integration |
| **Hilt for DI** | Compile-time verification, lifecycle awareness |
| **Room for persistence** | Type-safe SQL, Flow integration |
| **Coil for images** | Kotlin-first, Compose support, caching |

---

## Quick Start Checklist

```markdown
## Evaluation Implementation Checklist

### Setup
- [ ] Define evaluation criteria/rubric
- [ ] Set score threshold for "good enough"
- [ ] Configure max iterations (default: 3)

### Implementation
- [ ] Implement generate() function
- [ ] Implement evaluate() function with structured output
- [ ] Implement optimize() function
- [ ] Wire up the refinement loop

### Safety
- [ ] Add convergence detection
- [ ] Log all iterations for debugging
- [ ] Handle evaluation parse failures gracefully
```

### Android/Compose Quick Start

```markdown
## Android Evaluation Checklist

### Compose UI
- [ ] No unnecessary recompositions (use Layout Inspector)
- [ ] Accessibility: contentDescription, semantics
- [ ] Performance: LazyColumn for lists, graphicsLayer for animations
- [ ] Previews for all reusable components

### Architecture
- [ ] Unidirectional data flow (ViewModel → UI)
- [ ] Side effects in LaunchedEffect/SideEffect
- [ ] Navigation state in NavController only

### Testing
- [ ] ViewModel unit tests
- [ ] Compose UI tests for critical flows
- [ ] Fake repositories for isolation

### Monitoring
- [ ] Sentry breadcrumbs for user actions
- [ ] Performance transactions for critical paths
- [ ] Error boundary in Compose (SentryErrorBoundary)
```
