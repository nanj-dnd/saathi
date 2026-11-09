import XCTest
import UIKit
@testable import Bob

final class ScreenSafetyTests: XCTestCase {
    private let id = UUID()
    private let now = Date(timeIntervalSince1970: 1_000)
    private func frame(_ text: String = "Display & Brightness", at: Date, blocked: Bool = false) -> ScreenSnapshot {
        ScreenSnapshot(sessionID: id, capturedAt: at, width: 390, height: 844, lines: [ScreenLine(text: text, x: 0, y: 0.3, width: 1, height: 0.1)], blocked: blocked)
    }
    func testSensitiveScreensStayLocal() {
        for text in ["Enter your PIN", "One-time verification code", "Enter password", "Pay now", "ओटीपी", "கடவுச்சொல்", "పాస్‌వర్డ్", "ಪಾಸ್‌ವರ್ಡ್", "പാസ്‌വേഡ്"] {
            XCTAssertTrue(ScreenPrivacy.isSensitive(text), text)
        }
        XCTAssertFalse(ScreenPrivacy.isSensitive("Settings\nDisplay & Brightness\nAccessibility"))
        XCTAssertFalse(ScreenPrivacy.isSensitive("Settings\nFace ID & Passcode\nDisplay & Brightness"))
        XCTAssertTrue(ScreenPrivacy.isSensitive("Face ID & Passcode\nEnter your passcode"))
    }
    func testEmailAndUnicodeNumbersAreRedacted() {
        let result = ScreenPrivacy.sanitize("Call +91 98765 43210 or test@example.com. कोड १२३४")
        XCTAssertFalse(result.contains("98765"))
        XCTAssertFalse(result.contains("test@example.com"))
        XCTAssertFalse(result.contains("१२३४"))
    }
    func testDistinctStableFramesRequiredAndUnchangedScreenDoesNotRepeat() {
        var gate = ScreenGate()
        XCTAssertFalse(gate.accept(frame(at: now), sessionID: id, now: now))
        XCTAssertFalse(gate.accept(frame(at: now), sessionID: id, now: now.addingTimeInterval(0.5)))
        XCTAssertTrue(gate.accept(frame(at: now.addingTimeInterval(1)), sessionID: id, now: now.addingTimeInterval(1)))
        XCTAssertFalse(gate.accept(frame(at: now.addingTimeInterval(20)), sessionID: id, now: now.addingTimeInterval(20)))
    }
    func testStaleProtectedAndWrongSessionFramesCannotAdvance() {
        var gate = ScreenGate()
        XCTAssertFalse(gate.accept(frame(at: now.addingTimeInterval(-10)), sessionID: id, now: now))
        XCTAssertFalse(gate.accept(frame(at: now, blocked: true), sessionID: id, now: now))
        XCTAssertFalse(gate.accept(frame(at: now), sessionID: UUID(), now: now))
    }
    func testRapidUIChangesCannotSendAnotherTurnBeforeMinimumInterval() {
        var gate = ScreenGate()
        _ = gate.accept(frame(at: now), sessionID: id, now: now)
        XCTAssertTrue(gate.accept(frame(at: now.addingTimeInterval(1)), sessionID: id, now: now.addingTimeInterval(1)))
        XCTAssertFalse(gate.accept(frame("Text Size", at: now.addingTimeInterval(2)), sessionID: id, now: now.addingTimeInterval(2)))
        XCTAssertFalse(gate.accept(frame("Text Size", at: now.addingTimeInterval(3)), sessionID: id, now: now.addingTimeInterval(3)))
        XCTAssertTrue(gate.accept(frame("Text Size", at: now.addingTimeInterval(10)), sessionID: id, now: now.addingTimeInterval(10)))
    }
    func testCaptureRequiresBackgroundConsentAndLiveHostHeartbeat() {
        var config = LiveConfiguration(sessionID: id, expiresAt: now.addingTimeInterval(60), hostHeartbeat: now, foreground: false, language: "en-IN", stopRequested: false)
        XCTAssertTrue(config.permitsCapture(at: now))
        config.foreground = true; XCTAssertFalse(config.permitsCapture(at: now))
        config.foreground = false; config.stopRequested = true; XCTAssertFalse(config.permitsCapture(at: now))
        config.stopRequested = false; XCTAssertFalse(config.permitsCapture(at: now.addingTimeInterval(20)))
        config.hostHeartbeat = now.addingTimeInterval(70); XCTAssertFalse(config.permitsCapture(at: now.addingTimeInterval(70)))
    }
    func testOnlyOCRTextIsSerializedForCloudRequests() throws {
        let snapshot = frame("Line with \"quotes\"", at: now)
        let payload = TurnPayload(language: "en-IN", utterance: "Help", goal: "Settings", screen_w: 390, screen_h: 844, tree: snapshot.tree, secure_screen: false, history: [])
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(payload)) as? [String: Any])
        XCTAssertNil(object["screenshot_b64"])
        XCTAssertEqual(object["platform"] as? String, "ios")
        XCTAssertTrue((object["tree"] as? String)?.contains("Text") == true)
    }
    func testAtomicSharedStateAndClear() throws {
        let path = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: path) }
        let store = try SharedStore(directory: path)
        try store.write(frame(at: now), named: "frame.json")
        XCTAssertNotNil(store.read(ScreenSnapshot.self, named: "frame.json"))
        store.clear()
        XCTAssertNil(store.read(ScreenSnapshot.self, named: "frame.json"))
    }

    @MainActor private func renderedFrame(_ lines: [String]) -> CapturedFrame {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 390, height: 844)).image { context in
            UIColor.white.setFill(); context.fill(CGRect(x: 0, y: 0, width: 390, height: 844))
            for (i, line) in lines.enumerated() {
                (line as NSString).draw(at: CGPoint(x: 25, y: 120 + i * 70), withAttributes: [.font: UIFont.systemFont(ofSize: 25), .foregroundColor: UIColor.black])
            }
        }
        return CapturedFrame(sessionID: id, capturedAt: now, width: 390, height: 844, jpeg: image.jpegData(compressionQuality: 0.9)!)
    }
    @MainActor func testNativeOCRReadsARealRenderedScreenAndScrubsContactData() async throws {
        let snapshot = try await OnDeviceReader().read(renderedFrame(["Settings", "Display & Brightness", "test@example.com", "123456"]), language: "en-IN")
        XCTAssertFalse(snapshot.blocked)
        XCTAssertTrue(snapshot.lines.contains { $0.text.contains("Display") })
        XCTAssertFalse(snapshot.tree.contains("test@example.com"))
        XCTAssertFalse(snapshot.tree.contains("123456"))
    }
    @MainActor func testNativeOCRWithholdsPINScreen() async throws {
        let snapshot = try await OnDeviceReader().read(renderedFrame(["Enter your PIN", "123456"]), language: "en-IN")
        XCTAssertTrue(snapshot.blocked)
        XCTAssertTrue(snapshot.lines.isEmpty)
    }
}
