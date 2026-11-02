import Foundation

class CommentaryFusionService {
    // OpenRouter.ai settings
    var apiUrl: String = "https://openrouter.ai/api/v1/chat/completions"
    // Using verified OpenRouter model: GPT-4o-mini
    var model: String = "anthropic/claude-3.5-sonnet"
    // Insert your OpenRouter API key below
    let openRouterApiKey = "REDACTED_ROTATE_ME" // <-- Replace with your actual key

    /// Advanced prompt template for AI fusion
    static let fusionPromptTemplate = """
    You are a professional cricket commentator.
    Given the following ball-by-ball input and recent match context, write a single, natural-sounding commentary line for the current ball.
    
    Ball-by-ball input from two sources:
    Commentary from source 1: {snippet1}
    Commentary from source 2: {snippet2}
    
    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    
    Recent match context (last 3 overs summary):
    {overs_summary}
    
    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the ball input into a concise, engaging, and human-like sentence, as a real commentator would say on TV or radio.
    - If the match context (from the summary) suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., \"It's not looking good for the Indian team here\" or \"The bowlers are on top at the moment\").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - Do not simply merge the two sources; pick the best or rephrase for freshness.
    """

    // Rolling buffer of previous balls for each match
    private var previousBallsBuffer: [String: [String]] = [:] // matchId: [ball1, ball2, ...]
    private let maxBufferSize = 6

    /// Call this to add a new ball to the buffer for a match
    func addBallToBuffer(matchId: String, ball: String) {
        var buffer = previousBallsBuffer[matchId] ?? []
        buffer.append(ball)
        if buffer.count > maxBufferSize {
            buffer.removeFirst(buffer.count - maxBufferSize)
        }
        previousBallsBuffer[matchId] = buffer
    }

    /// Get the buffer for a match
    func getPreviousBalls(matchId: String) -> [String] {
        return previousBallsBuffer[matchId] ?? []
    }

    /// Helper to call OpenRouter with a specific model and prompt
    private func callOpenRouter(model: String, prompt: String) async -> String? {
        print("CommentaryFusionService (OpenRouter): Using model: \(model)")
        let requestBody: [String: Any] = [
            "model": model,
            "messages": [
                ["role": "user", "content": prompt]
            ],
            "max_tokens": 512
        ]
        guard let url = URL(string: apiUrl),
              let httpBody = try? JSONSerialization.data(withJSONObject: requestBody) else {
            print("CommentaryFusionService (OpenRouter): Failed to serialize request body, using fallback.")
            return nil
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(openRouterApiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("bouncer-app", forHTTPHeaderField: "HTTP-Referer")
        request.httpBody = httpBody
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                let errorBody = String(data: data, encoding: .utf8)
                if let errorBody = errorBody {
                    print("CommentaryFusionService (OpenRouter): API call failed with status code \((response as? HTTPURLResponse)?.statusCode ?? -1). Response body: \(errorBody)")
                } else {
                    print("CommentaryFusionService (OpenRouter): API call failed with status code \((response as? HTTPURLResponse)?.statusCode ?? -1), using fallback.")
                }
                return nil
            }
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                // Try OpenAI/OpenRouter format: { choices: [ { message: { content: ... } } ] }
                if let choices = json["choices"] as? [[String: Any]],
                   let first = choices.first,
                   let message = first["message"] as? [String: Any],
                   let content = message["content"] as? String {
                    print("CommentaryFusionService (OpenRouter): AI response received (choices format).")
                    return content.trimmingCharacters(in: .whitespacesAndNewlines)
                }
                // Try legacy format: { message: { content: ... } }
                if let message = json["message"] as? [String: Any],
                   let content = message["content"] as? String {
                    print("CommentaryFusionService (OpenRouter): AI response received (message format).")
                    return content.trimmingCharacters(in: .whitespacesAndNewlines)
                }
                // If neither, log the full response for debugging
                print("CommentaryFusionService (OpenRouter): Failed to parse AI response, dumping body: \(String(data: data, encoding: .utf8) ?? "<non-utf8>")")
                return nil
            } else {
                print("CommentaryFusionService (OpenRouter): Failed to parse AI response, using fallback.")
                return nil
            }
        } catch {
            print("CommentaryFusionService (OpenRouter): Error during AI API call: \(error). Using fallback.")
            return nil
        }
    }

    /// Fuses two commentary snippets and recent history into a single, coherent commentary line using Claude 3.5 Sonnet
    func fuse(snippet1: String, snippet2: String, oversSummary: String, matchId: String, over: Int, ballNumber: String, bowler: String, batsman: String, scoreContext: String) async -> String {
        let contextHash = String((snippet1 + snippet2 + oversSummary).hashValue)
        let cacheKey = "ai_commentary:\(matchId):\(over):\(contextHash)"
        if let cached = await AICacheService.shared.getCommentary(for: cacheKey) {
            return cached
        }
        let previousBalls = getPreviousBalls(matchId: matchId)
        let previousContext = previousBalls.isEmpty ? "" : "\nPrevious balls: " + previousBalls.joined(separator: ", ")
        let prompt: String
        if snippet2.isEmpty || snippet1.isEmpty {
            prompt = """
            You are a professional cricket commentator. Given the following ball-by-ball input and recent match context, write a vivid, engaging, and concise commentary line for this specific ball, as if you are commentating live.
            
            Ball commentary: \(snippet1.isEmpty ? snippet2 : snippet1)
            Ball number: \(ballNumber)
            Bowler: \(bowler)
            Batsman: \(batsman)
            Current score/context: \(scoreContext)
            \(previousContext)
            
            Instructions:
            - Start your commentary line with the ball number (e.g., 18.5: ...).
            - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
            - Do not invent details about the play, score, or situation if not present in the input/context.
            - Paraphrase the input into a natural, commentator-style sentence.
            - If the recent context suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., \"It's not looking good for the Indian team here\").
            - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
            - Otherwise, focus on clear, vivid, and accurate description of the current ball.
            - Avoid repetition and keep it concise.
            """
        } else {
            prompt = CommentaryFusionService.fusionPromptTemplate
                .replacingOccurrences(of: "{overs_summary}", with: oversSummary)
                .replacingOccurrences(of: "{snippet1}", with: snippet1)
                .replacingOccurrences(of: "{snippet2}", with: snippet2)
                .replacingOccurrences(of: "{ball_number}", with: ballNumber)
                .replacingOccurrences(of: "{bowler}", with: bowler)
                .replacingOccurrences(of: "{batsman}", with: batsman)
                .replacingOccurrences(of: "{score_context}", with: scoreContext)
        }
        guard let output = await callOpenRouter(model: "openai/gpt-4o-mini", prompt: prompt) else {
            print("CommentaryFusionService: GPT-4o Mini failed, using fallback.")
            return "[AI Fused] " + snippet1 + " / " + snippet2
        }
        await AICacheService.shared.setCommentary(output, for: cacheKey)
        return output
    }

    /// Summarizes the last 3 overs (18 balls) into a paragraph using OpenRouter.ai
    func summarizeOversWithAI(commentaryLines: [String], matchId: String, over: Int) async -> String {
        guard !commentaryLines.isEmpty else { return "No recent commentary available." }
        let totalBalls = min(commentaryLines.count, 18)
        let lines: [String]
        if totalBalls > 0 {
            lines = Array(commentaryLines.prefix(totalBalls))
        } else {
            lines = []
        }
        let context = lines.joined(separator: " ")
        let contextHash = String(context.hashValue)
        let cacheKey = "ai_summary:\(matchId):\(over):\(contextHash)"
        if let cached = await AICacheService.shared.getCommentary(for: cacheKey) {
            return cached
        }
        let numbered = lines.enumerated().map { "\($0 + 1). \($1)" }.joined(separator: "\n")
        let prompt = """
        You are a professional cricket commentator. Summarize the following 18 ball-by-ball commentary lines into a single, engaging paragraph, highlighting key events and players.\n\n\(numbered)
        """
        print("CommentaryFusionService (OpenRouter): Using API URL: \(apiUrl)")
        print("CommentaryFusionService (OpenRouter): Using model: \(model)")
        let requestBody: [String: Any] = [
            "model": model,
            "messages": [
                ["role": "user", "content": prompt]
            ],
            "max_tokens": 256
        ]
        guard let url = URL(string: apiUrl),
              let httpBody = try? JSONSerialization.data(withJSONObject: requestBody) else {
            print("CommentaryFusionService (OpenRouter): Failed to serialize request body for overs summary, using fallback.")
            return "[Summary unavailable]"
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(openRouterApiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("bouncer-app", forHTTPHeaderField: "HTTP-Referer")
        request.httpBody = httpBody
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                let errorBody = String(data: data, encoding: .utf8)
                if let errorBody = errorBody {
                    print("CommentaryFusionService (OpenRouter): API call for overs summary failed with status code \((response as? HTTPURLResponse)?.statusCode ?? -1). Response body: \(errorBody)")
                } else {
                    print("CommentaryFusionService (OpenRouter): API call for overs summary failed with status code \((response as? HTTPURLResponse)?.statusCode ?? -1), using fallback.")
                }
                return "[Summary unavailable]"
            }
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let message = json["message"] as? [String: Any],
               let content = message["content"] as? String {
                print("CommentaryFusionService (OpenRouter): AI overs summary received.")
                let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
                await AICacheService.shared.setCommentary(trimmed, for: cacheKey)
                return trimmed
            } else {
                print("CommentaryFusionService (OpenRouter): Failed to parse AI overs summary response, using fallback.")
                return "[Summary unavailable]"
            }
        } catch {
            print("CommentaryFusionService (OpenRouter): Error during AI API call for overs summary: \(error). Using fallback.")
            return "[Summary unavailable]"
        }
    }
} 