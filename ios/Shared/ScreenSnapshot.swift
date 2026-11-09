import Foundation
import CryptoKit

/** A transient frame shared on-device only. Never part of a Worker request. */
struct CapturedFrame: Codable {
    var sessionID: UUID
    var capturedAt: Date
    var width: Int
    var height: Int
    var jpeg: Data
}

struct ScreenLine: Codable, Equatable {
    var text: String
    var x: Double
    var y: Double
    var width: Double
    var height: Double
}

struct ScreenSnapshot: Codable {
    var sessionID: UUID
    var capturedAt: Date
    var width: Int
    var height: Int
    var lines: [ScreenLine]
    var blocked: Bool

    var signature: String {
        let text = lines.map(\.text).joined(separator: "\n")
        return SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    // These are OCR text regions, not controls or an accessibility hierarchy.
    var tree: String {
        lines.enumerated().map { i, line in
            let label = line.text.replacingOccurrences(of: "\"", with: "'").replacingOccurrences(of: "\n", with: " ")
            return "n\(i) Text \"\(label)\" [\(Int(line.x * Double(width))),\(Int(line.y * Double(height))),\(Int((line.x + line.width) * Double(width))),\(Int((line.y + line.height) * Double(height)))]"
        }.joined(separator: "\n")
    }
}

struct LiveConfiguration: Codable {
    var sessionID: UUID
    var expiresAt: Date
    var hostHeartbeat: Date
    var foreground: Bool
    var language: String
    var stopRequested: Bool

    func permitsCapture(at now: Date) -> Bool {
        !stopRequested && !foreground && now < expiresAt && now.timeIntervalSince(hostHeartbeat) < 12
    }
}

struct BroadcastStatus: Codable {
    var sessionID: UUID
    var phase: String
    var updatedAt: Date
}

enum ScreenPrivacy {
    // Conservative trial: no screen images leave the phone. Known sensitive screens are
    // withheld entirely, and numeric/contact data is scrubbed from the remaining OCR.
    static func isSensitive(_ text: String) -> Bool {
        let pattern = #"(?i)\b(otp|pin|password|passcode|cvv|payment|pay now|bank|credit card|verification code|one.time|send money|transfer money)\b|ओटीपी|पिन|पासवर्ड|भुगतान|बैंक|பணம்|கடவுச்சொல்|ஓடிபி|పాస్‌వర్డ్|చెల్లింపు|ಪಾಸ್‌ವರ್ಡ್|ಪಾವತಿ|പാസ്‌വേഡ്|പണമട"#
        // A navigation row is not a passcode-entry screen.
        let content = text.components(separatedBy: .newlines).filter {
            !["face id & passcode", "touch id & passcode"].contains($0.lowercased().trimmingCharacters(in: .whitespaces))
        }.joined(separator: "\n")
        return content.range(of: pattern, options: .regularExpression) != nil
    }

    static func sanitize(_ text: String) -> String {
        text.replacingOccurrences(of: #"(?i)[\w.+-]+@[\w.-]+\.[a-z]{2,}"#, with: "[private]", options: .regularExpression)
            .replacingOccurrences(of: #"\p{Nd}[\p{Nd}\s()+.,/-]*"#, with: "[number]", options: .regularExpression)
    }
}

struct ScreenGate {
    private var candidate = ""
    private var stableCount = 0
    private var lastSent = ""
    private var lastSentAt = Date.distantPast
    private var lastObservedAt = Date.distantPast

    mutating func accept(_ snapshot: ScreenSnapshot, sessionID: UUID, now: Date) -> Bool {
        guard snapshot.sessionID == sessionID, !snapshot.blocked, !snapshot.lines.isEmpty,
              now.timeIntervalSince(snapshot.capturedAt) >= -1,
              now.timeIntervalSince(snapshot.capturedAt) < 5,
              snapshot.capturedAt > lastObservedAt else { return false }
        lastObservedAt = snapshot.capturedAt
        let signature = snapshot.signature
        if candidate == signature { stableCount += 1 } else { candidate = signature; stableCount = 1 }
        guard stableCount >= 2, signature != lastSent, now.timeIntervalSince(lastSentAt) >= 8 else { return false }
        lastSent = signature
        lastSentAt = now
        return true
    }

    mutating func retry() { lastSent = "" }
}
