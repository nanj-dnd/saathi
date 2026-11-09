import Foundation
import Vision

struct OnDeviceReader {
    func read(_ frame: CapturedFrame, language: String) async throws -> ScreenSnapshot {
        try await Task.detached(priority: .utility) {
            try Task.checkCancellation()
            return try autoreleasepool {
                let request = VNRecognizeTextRequest()
                request.recognitionLevel = .accurate
                request.usesLanguageCorrection = false
                let supported = try request.supportedRecognitionLanguages()
                let languages = [language, "en-US"].filter { supported.contains($0) }
                if !languages.isEmpty { request.recognitionLanguages = languages }
                try VNImageRequestHandler(data: frame.jpeg).perform([request])
                let observations = request.results ?? []
                let text = observations.compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")
                let blocked = ScreenPrivacy.isSensitive(text)
                let lines = blocked ? [] : observations.compactMap { item -> ScreenLine? in
                    guard let candidate = item.topCandidates(1).first else { return nil }
                    let box = item.boundingBox
                    guard box.midY < 0.94, box.midY > 0.04 else { return nil }
                    return ScreenLine(text: ScreenPrivacy.sanitize(candidate.string), x: box.minX, y: 1-box.maxY, width: box.width, height: box.height)
                }
                return ScreenSnapshot(sessionID: frame.sessionID, capturedAt: frame.capturedAt, width: frame.width, height: frame.height, lines: Array(lines.prefix(100)), blocked: blocked)
            }
        }.value
    }
}
