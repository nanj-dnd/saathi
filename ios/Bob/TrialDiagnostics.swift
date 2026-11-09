import Foundation

/// Local debug-only events. Never records screen text, task text, transcripts, tokens or images.
@MainActor
enum TrialDiagnostics {
    #if DEBUG
    private static var events: [[String: String]] = []
    #endif
    static func record(_ event: String, _ fields: [String: String] = [:]) {
        #if DEBUG
        var row = fields
        row["event"] = event
        row["time"] = ISO8601DateFormatter().string(from: Date())
        events.append(row)
        events = Array(events.suffix(160))
        guard let path = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first,
              let data = try? JSONSerialization.data(withJSONObject: events, options: [.sortedKeys]) else { return }
        try? data.write(to: path.appendingPathComponent("bob-trial-diagnostics.json"), options: [.atomic, .completeFileProtection])
        #endif
    }
}
