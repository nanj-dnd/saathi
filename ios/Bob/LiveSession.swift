import SwiftUI
import AVFoundation

@MainActor
final class LiveSession: ObservableObject {
    @Published var goal = ""
    @Published var language = "en-IN"
    @Published var active = false
    @Published var preparing = false
    @Published var caption = ""
    @Published var status = "Ready when you are"
    @Published var error: String?
    @Published var preview = false
    private let client = WorkerClient()
    private let voice = LiveVoice()
    private var store: SharedStore?
    private var config: LiveConfiguration?
    private var poll: Task<Void, Never>?
    private var guideTask: Task<Void, Never>?
    private var startup: Task<Void, Never>?
    private var gate = ScreenGate()
    private var history: [PastStep] = []
    private var question: String?
    private var foreground = true
    private var backgroundAt = Date.distantPast
    private var lastReadAt = Date.distantPast
    private var turns = 0
    private var preparedAt = Date.distantPast
    private var lastDiagnosticAt = Date.distantPast
    private var interruptionObserver: NSObjectProtocol?

    init() {
        voice.onFailure = { [weak self] message in self?.stop(message: message) }
        interruptionObserver = NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.stop(message: "Audio was interrupted. Start a new session when you are ready.") }
        }
    }
    deinit { if let interruptionObserver { NotificationCenter.default.removeObserver(interruptionObserver) } }

    func begin() {
        guard !active, !preparing else { return }
        error = nil
        caption = ""
        status = "Allow listening when prompted"
        preparing = true
        TrialDiagnostics.record("prepare")
        startup = Task { [weak self] in
            guard let self else { return }
            do {
                let shared = try SharedStore()
                shared.clear()
                try await voice.start(language: language) { [weak self] text in self?.heard(text) }
                try Task.checkCancellation()
                store = shared
                preparedAt = Date()
                config = LiveConfiguration(sessionID: UUID(), expiresAt: Date().addingTimeInterval(600), hostHeartbeat: Date(), foreground: foreground, language: language, stopRequested: false)
                try shared.write(config!, named: "session.json")
                history = []; turns = 0; gate = ScreenGate(); lastReadAt = .distantPast
                question = ScreenPrivacy.sanitize(goal)
                active = true; preparing = false
                TrialDiagnostics.record("prepared")
                status = goal.isEmpty ? "Tell Bob what you need help with" : "Now start screen sharing below"
                poll = Task { await self.runLoop() }
                await voice.say(startMessage, language: language, client: client)
            } catch {
                guard !Task.isCancelled else { return }
                voice.stop()
                preparing = false
                self.error = error.localizedDescription
                TrialDiagnostics.record("prepare-failed", ["domain": (error as NSError).domain, "code": String((error as NSError).code)])
            }
        }
    }
    func sceneChanged(_ phase: ScenePhase) {
        foreground = phase != .background
        TrialDiagnostics.record("scene", ["foreground": String(foreground)])
        if !foreground { backgroundAt = Date() }
        config?.foreground = foreground
        if let config { try? store?.write(config, named: "session.json") }
    }
    func stop(message: String = "Session stopped") {
        active = false; preparing = false
        startup?.cancel(); poll?.cancel(); guideTask?.cancel()
        startup = nil; poll = nil; guideTask = nil
        voice.stop()
        config?.stopRequested = true
        if let config { try? store?.write(config, named: "session.json") }
        store?.clearFrame()
        question = nil; history = []
        status = message
        TrialDiagnostics.record("stopped", ["reason": message])
    }
    private func heard(_ text: String) {
        if ["stop", "stop bob", "रुको", "बंद करो", "நிறுத்து", "ఆపు", "ನಿಲ್ಲಿಸು", "നിർത്തൂ"].contains(text.lowercased().trimmingCharacters(in: .punctuationCharacters)) { stop(); return }
        let clean = ScreenPrivacy.sanitize(String(text.prefix(1000)))
        if goal.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { goal = String(clean.prefix(300)) }
        question = clean
        TrialDiagnostics.record("question-received", ["hasGoal": String(!goal.isEmpty)])
        gate.retry()
    }
    private var startMessage: String {
        let needsGoal = goal.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let ask = ["en-IN": "What would you like help with? Tell me, then start sharing your screen.",
                   "hi-IN": "आपको किस काम में मदद चाहिए? मुझे बताइए, फिर स्क्रीन शेयर शुरू कीजिए।",
                   "ta-IN": "எந்த வேலைக்கு உதவி வேண்டும்? சொல்லுங்கள், பிறகு திரையைப் பகிருங்கள்.",
                   "te-IN": "ఏ పనిలో సహాయం కావాలి? చెప్పండి, తర్వాత స్క్రీన్ షేర్ ప్రారంభించండి.",
                   "kn-IN": "ಯಾವ ಕೆಲಸಕ್ಕೆ ಸಹಾಯ ಬೇಕು? ಹೇಳಿ, ನಂತರ ಪರದೆ ಹಂಚಿಕೆಯನ್ನು ಪ್ರಾರಂಭಿಸಿ.",
                   "ml-IN": "ഏത് കാര്യത്തിലാണ് സഹായം വേണ്ടത്? പറയൂ, പിന്നെ സ്ക്രീൻ പങ്കിടൽ തുടങ്ങൂ."]
        let ready = ["en-IN": "I am ready. Use the recording button below to share your screen.",
                     "hi-IN": "मैं तैयार हूँ। नीचे रिकॉर्डिंग बटन से स्क्रीन शेयर शुरू कीजिए।",
                     "ta-IN": "நான் தயார். கீழே உள்ள பதிவு பொத்தானால் திரையைப் பகிருங்கள்.",
                     "te-IN": "నేను సిద్ధంగా ఉన్నాను. కింద రికార్డింగ్ బటన్‌తో స్క్రీన్ షేర్ చేయండి.",
                     "kn-IN": "ನಾನು ಸಿದ್ಧನಿದ್ದೇನೆ. ಕೆಳಗಿನ ರೆಕಾರ್ಡಿಂಗ್ ಬಟನ್ ಮೂಲಕ ಪರದೆಯನ್ನು ಹಂಚಿಕೊಳ್ಳಿ.",
                     "ml-IN": "ഞാൻ തയ്യാറാണ്. താഴെയുള്ള റെക്കോർഡിംഗ് ബട്ടൺ ഉപയോഗിച്ച് സ്ക്രീൻ പങ്കിടൂ."]
        return (needsGoal ? ask : ready)[language] ?? ask["en-IN"]!
    }
    private func runLoop() async {
        while active && !Task.isCancelled {
            guard var config, let store else { stop(); return }
            let now = Date()
            if now >= config.expiresAt { stop(message: "Your ten-minute trial ended. Start again for more help."); return }
            config.hostHeartbeat = now
            config.foreground = foreground
            self.config = config
            if now.timeIntervalSince(lastDiagnosticAt) >= 5 {
                lastDiagnosticAt = now
                let broadcast = store.read(BroadcastStatus.self, named: "status.json")
                let frame = store.read(CapturedFrame.self, named: "capture.json")
                TrialDiagnostics.record("tick", ["foreground": String(foreground), "broadcast": broadcast?.phase ?? "none", "frame": String(frame != nil), "frameAge": frame.map { String(format: "%.1f", now.timeIntervalSince($0.capturedAt)) } ?? "none", "busy": String(guideTask != nil)])
            }
            do { try store.write(config, named: "session.json") }
            catch { stop(message: "Screen sharing paused. Unlock your iPhone and return to Bob."); return }
            if let broadcast = store.read(BroadcastStatus.self, named: "status.json"), broadcast.sessionID == config.sessionID {
                if broadcast.phase == "finished" { stop(); return }
                if broadcast.phase == "paused" || broadcast.phase == "unreadable" {
                    status = "Screen sharing is paused. Return to Bob if you need help."
                    if now.timeIntervalSince(broadcast.updatedAt) > 15 { stop(message: status); return }
                } else if now.timeIntervalSince(max(broadcast.updatedAt, backgroundAt)) > 15 && !foreground {
                    stop(message: "Screen sharing stopped responding. Return to Bob to start again."); return
                } else { status = goal.isEmpty ? "Tell Bob what you need help with" : (foreground ? "Open the app you need help with" : "Bob is listening and looking") }
            } else if now.timeIntervalSince(preparedAt) > 90 {
                stop(message: "Screen sharing did not start. You can try again."); return
            }
            if !foreground && !goal.isEmpty && guideTask == nil, let frame = store.read(CapturedFrame.self, named: "capture.json"),
               frame.sessionID == config.sessionID, frame.capturedAt > lastReadAt, now.timeIntervalSince(frame.capturedAt) < 5 {
                lastReadAt = frame.capturedAt
                guideTask = Task { await readAndGuide(frame) }
            }
            try? await Task.sleep(for: .seconds(1))
        }
    }
    private func readAndGuide(_ frame: CapturedFrame) async {
        defer { if config?.sessionID == frame.sessionID { guideTask = nil } }
        do {
            let snapshot = try await OnDeviceReader().read(frame, language: language)
            TrialDiagnostics.record("ocr", ["lines": String(snapshot.lines.count), "blocked": String(snapshot.blocked)])
            try Task.checkCancellation()
            guard active, !foreground, config?.sessionID == snapshot.sessionID else { return }
            if snapshot.blocked {
                caption = privacyMessage
                await voice.say(caption, language: language, client: client)
                stop(message: "Paused for your privacy. Start a new session after this step.")
            } else if gate.accept(snapshot, sessionID: frame.sessionID, now: Date()) {
                guard turns < 20 else { stop(message: "Your trial reached twenty steps. Start again for more help."); return }
                let utterance = question; question = nil
                await guide(snapshot, utterance: utterance)
            }
        } catch {
            if !Task.isCancelled { stop(message: "Bob could not read this screen. Return to Bob to try again.") }
            TrialDiagnostics.record("ocr-failed", ["domain": (error as NSError).domain, "code": String((error as NSError).code)])
        }
    }
    private var privacyMessage: String {
        ["en-IN": "Do this private step yourself. Never share a PIN, password or code.",
         "hi-IN": "यह निजी कदम खुद कीजिए। PIN, पासवर्ड या कोड कभी मत बताइए।",
         "ta-IN": "இந்தத் தனிப்பட்ட படியை நீங்களே செய்யுங்கள். PIN, கடவுச்சொல் அல்லது குறியீட்டை யாரிடமும் சொல்லாதீர்கள்.",
         "te-IN": "ఈ వ్యక్తిగత దశను మీరే చేయండి. PIN, పాస్‌వర్డ్ లేదా కోడ్ ఎవరికీ చెప్పకండి.",
         "kn-IN": "ಈ ಖಾಸಗಿ ಹೆಜ್ಜೆಯನ್ನು ನೀವೇ ಮಾಡಿ. PIN, ಪಾಸ್‌ವರ್ಡ್ ಅಥವಾ ಕೋಡ್ ಯಾರಿಗೂ ಹೇಳಬೇಡಿ.",
         "ml-IN": "ഈ സ്വകാര്യ ഘട്ടം സ്വയം ചെയ്യുക. PIN, പാസ്‌വേഡ് അല്ലെങ്കിൽ കോഡ് ആർക്കും പറയരുത്."][language] ?? "Do this private step yourself."
    }
    private func guide(_ snapshot: ScreenSnapshot, utterance: String?) async {
        do {
            turns += 1
            TrialDiagnostics.record("guide-request")
            let response = try await client.turn(snapshot: snapshot, language: language, goal: ScreenPrivacy.sanitize(goal), utterance: utterance, history: history)
            try Task.checkCancellation()
            guard active, snapshot.sessionID == config?.sessionID, !foreground else { gate.retry(); return }
            caption = response.guide.say
            TrialDiagnostics.record("guide-response", ["status": response.guide.status, "source": response.source])
            if !response.guide.goal.isEmpty { goal = response.guide.goal }
            history.append(PastStep(say: caption, label: response.guide.label, status: response.guide.status))
            history = Array(history.suffix(8))
            await voice.say(caption, language: language, client: client)
            if response.guide.danger != "none" || response.guide.status == "user_must_do" || response.guide.status == "done" {
                stop(message: response.guide.status == "done" ? "That task is done" : "Paused for your privacy")
            }
        } catch {
            guard !Task.isCancelled else { return }
            // A failed request does not loop and keep charging against an unchanged screen.
            stop(message: "Connection interrupted. Return to Bob to try again.")
            self.error = error.localizedDescription
            TrialDiagnostics.record("guide-failed", ["domain": (error as NSError).domain, "code": String((error as NSError).code)])
        }
    }
    #if DEBUG
    func showPreview() {
        preview = true
        goal = "Make the text bigger"
        caption = "Tap ‘Display & Brightness’."
        status = "Example guidance · no screen sharing"
    }
    func smokeTest() async {
        guard !preparing, !active else { return }
        preparing = true
        defer { preparing = false }
        showPreview(); status = "Testing the live Worker with a synthetic Settings screen"
        let snapshot = ScreenSnapshot(sessionID: UUID(), capturedAt: Date(), width: 390, height: 844, lines: [ScreenLine(text: "Settings", x: 0.1, y: 0.1, width: 0.8, height: 0.08), ScreenLine(text: "Display & Brightness", x: 0.1, y: 0.4, width: 0.8, height: 0.06), ScreenLine(text: "Accessibility", x: 0.1, y: 0.5, width: 0.8, height: 0.06)], blocked: false)
        do {
            let result = try await client.turn(snapshot: snapshot, language: "en-IN", goal: goal, utterance: goal, history: [])
            caption = result.guide.say
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
            try AVAudioSession.sharedInstance().setActive(true)
            let audio = try await client.speech(caption, language: "en-IN")
            try audio.write(to: FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("smoke-voice.mp3"))
            await voice.say(caption, language: "en-IN", client: client)
            status = "Live Worker + ElevenLabs passed · synthetic screen"
            try? AVAudioSession.sharedInstance().setActive(false)
        } catch { self.error = error.localizedDescription; status = "Live smoke test failed" }
    }
    #endif
}
