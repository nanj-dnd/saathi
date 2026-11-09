import ReplayKit
import CoreImage
import ImageIO

final class SampleHandler: RPBroadcastSampleHandler {
    private let queue = DispatchQueue(label: "bob.screen-frame")
    private let context = CIContext(options: [.cacheIntermediates: false, .useSoftwareRenderer: true])
    private var store: SharedStore?
    private var sessionID: UUID?
    private var lastSample = Date.distantPast
    private var processing = false
    private let lock = NSLock()
    private var ended = false

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        do {
            let shared = try SharedStore()
            guard let config = shared.read(LiveConfiguration.self, named: "session.json"), !config.stopRequested,
                  config.expiresAt > Date(), Date().timeIntervalSince(config.hostHeartbeat) < 12 else {
                throw NSError(domain: "Bob", code: 2, userInfo: [NSLocalizedDescriptionKey: "Open Bob and prepare a new session first."])
            }
            store = shared; sessionID = config.sessionID; status("sharing")
        } catch { finishBroadcastWithError(error) }
    }
    override func broadcastPaused() { store?.clearFrame(); status("paused") }
    override func broadcastResumed() { status("sharing") }
    override func broadcastFinished() {
        lock.lock(); ended = true; lock.unlock()
        store?.clearFrame(); status("finished")
    }
    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer, with sampleBufferType: RPSampleBufferType) {
        // Broadcast microphone and app audio are ignored. The host handles listening and speech.
        guard sampleBufferType == .video, let store, let sessionID else { return }
        guard let config = store.read(LiveConfiguration.self, named: "session.json"), config.sessionID == sessionID,
              !config.stopRequested, config.expiresAt > Date(), Date().timeIntervalSince(config.hostHeartbeat) < 12 else {
            finishBroadcastWithError(NSError(domain: "Bob", code: 3, userInfo: [NSLocalizedDescriptionKey: "Bob's help session ended. You can start again in Bob."]))
            return
        }
        guard config.permitsCapture(at: Date()), let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lock.lock()
        guard !ended, !processing, Date().timeIntervalSince(lastSample) >= 1 else { lock.unlock(); return }
        processing = true; lastSample = Date(); lock.unlock()
        let raw = (CMGetAttachment(sampleBuffer, key: RPVideoSampleOrientationKey as CFString, attachmentModeOut: nil) as? NSNumber)?.uint32Value ?? 1
        let orientation = CGImagePropertyOrientation(rawValue: raw) ?? .up
        queue.async { [weak self] in
            guard let self else { return }
            defer { self.lock.lock(); self.processing = false; self.lock.unlock() }
            autoreleasepool {
                let image = CIImage(cvPixelBuffer: buffer).oriented(orientation)
                let scale = min(1, 1280 / max(image.extent.width, image.extent.height))
                let resized = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
                guard let jpeg = self.context.jpegRepresentation(of: resized, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!, options: [kCGImageDestinationLossyCompressionQuality as CIImageRepresentationOption: 0.65]) else {
                    self.status("unreadable"); return
                }
                self.lock.lock(); let ended = self.ended; self.lock.unlock()
                guard !ended, let current = store.read(LiveConfiguration.self, named: "session.json"),
                      current.sessionID == sessionID, current.permitsCapture(at: Date()) else { return }
                // One bounded, file-protected frame, shared only on-device. No networking in this extension.
                let frame = CapturedFrame(sessionID: sessionID, capturedAt: Date(), width: Int(resized.extent.width), height: Int(resized.extent.height), jpeg: jpeg)
                do { try store.write(frame, named: "capture.json"); self.status("sharing") }
                catch { store.clearFrame(); self.status("unreadable") }
            }
        }
    }
    private func status(_ phase: String) {
        guard let sessionID else { return }
        try? store?.write(BroadcastStatus(sessionID: sessionID, phase: phase, updatedAt: Date()), named: "status.json")
    }
}
