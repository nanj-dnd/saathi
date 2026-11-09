import Foundation

struct Guide: Codable {
    var say: String
    var goal: String
    var target_kind: String
    var node_id: String
    var x: Double
    var y: Double
    var label: String
    var status: String
    var danger: String
    var expect: String
}
struct PastStep: Codable {
    var say: String
    var label: String
    var status: String
    var app = "ios.screen-broadcast"
}
struct TurnPayload: Encodable {
    let platform = "ios"
    var language: String
    var utterance: String?
    var goal: String
    let app = "ios.screen-broadcast"
    let app_name = "Shared iPhone screen"
    let playbook = "Text regions come from on-device OCR, not controls. No installed-app inventory is available. Only describe manual next steps visible in this shared screen. Bob cannot scroll, open apps, open store pages or draw over other apps on iOS."
    var screen_w: Int
    var screen_h: Int
    var tree: String
    var secure_screen: Bool
    var history: [PastStep]
}
struct TurnResult: Decodable { var guide: Guide; var source: String }
struct WorkerFailure: LocalizedError {
    var status: Int
    var errorDescription: String? {
        switch status {
        case 429: return "That is enough requests for now. Please try again later."
        case 501: return "The cloud voice is unavailable. Bob will use the iPhone voice."
        default: return "Bob could not connect. Please check your internet and try again."
        }
    }
}

actor WorkerClient {
    private let base: URL
    private var token: String?
    init(base: URL = URL(string: "https://saathi-worker.nanj-dnd.workers.dev")!) { self.base = base }

    func turn(snapshot: ScreenSnapshot, language: String, goal: String, utterance: String?, history: [PastStep]) async throws -> TurnResult {
        let payload = TurnPayload(language: language, utterance: utterance, goal: String(goal.prefix(300)), screen_w: snapshot.width, screen_h: snapshot.height, tree: snapshot.tree, secure_screen: snapshot.blocked, history: Array(history.suffix(8)))
        let data = try await post("turn", body: JSONEncoder().encode(payload))
        return try JSONDecoder().decode(TurnResult.self, from: data)
    }
    func speech(_ text: String, language: String) async throws -> Data {
        struct Payload: Encodable { let text: String; let language: String; let pace = 1.0 }
        return try await post("speak", body: JSONEncoder().encode(Payload(text: text, language: language)))
    }
    private func post(_ path: String, body: Data) async throws -> Data {
        for attempt in 0..<2 {
            if token == nil { try await register() }
            var request = URLRequest(url: base.appendingPathComponent(path), timeoutInterval: 30)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("Bearer \(token ?? "")", forHTTPHeaderField: "Authorization")
            request.httpBody = body
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let response = response as? HTTPURLResponse else { throw WorkerFailure(status: 502) }
            if response.statusCode == 401 && attempt == 0 { token = nil; continue }
            guard response.statusCode == 200 else { throw WorkerFailure(status: response.statusCode) }
            return data
        }
        throw WorkerFailure(status: 401)
    }
    private func register() async throws {
        var request = URLRequest(url: base.appendingPathComponent("register"), timeoutInterval: 15)
        request.httpMethod = "POST"
        request.httpBody = Data("{}".utf8)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw WorkerFailure(status: 503) }
        struct Registration: Decodable { let token: String }
        token = try JSONDecoder().decode(Registration.self, from: data).token
    }
}
