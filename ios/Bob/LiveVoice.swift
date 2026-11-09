import AVFoundation
import Speech

@MainActor
final class LiveVoice: NSObject, AVAudioPlayerDelegate, AVSpeechSynthesizerDelegate {
    private let engine = AVAudioEngine()
    private var recognizer: SFSpeechRecognizer?
    private var recognition: SFSpeechRecognitionTask?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var player: AVAudioPlayer?
    private let fallback = AVSpeechSynthesizer()
    private var playbackDone: CheckedContinuation<Void, Never>?
    private var listening = false
    private var speaking = false
    private var restart: Task<Void, Never>?
    private var utteranceDebounce: Task<Void, Never>?
    private var lastPartial = ""
    private var recentRecognitionErrors: [Date] = []
    private var voiceGeneration = 0
    private var recognitionGeneration = 0
    private var onQuestion: ((String) -> Void)?
    var onFailure: ((String) -> Void)?

    override init() { super.init(); fallback.delegate = self }

    func start(language: String, onQuestion: @escaping (String) -> Void) async throws {
        let speech = await withCheckedContinuation { c in SFSpeechRecognizer.requestAuthorization { c.resume(returning: $0) } }
        guard speech == .authorized else { throw failure("Allow Speech Recognition for Bob in Settings to use live help.") }
        let mic = await withCheckedContinuation { c in AVAudioApplication.requestRecordPermission { c.resume(returning: $0) } }
        guard mic else { throw failure("Allow Microphone access for Bob in Settings to use live help.") }
        try Task.checkCancellation()
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: language)), recognizer.isAvailable else {
            throw failure("Speech recognition is unavailable for this language right now.")
        }
        self.recognizer = recognizer
        self.onQuestion = onQuestion
        let audio = AVAudioSession.sharedInstance()
        try audio.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetoothHFP])
        try audio.setActive(true)
        // The actual listening session provides background audio activity. No silent keepalive.
        try engine.inputNode.setVoiceProcessingEnabled(true)
        listening = true
        beginRecognition()
        engine.prepare()
        try engine.start()
        TrialDiagnostics.record("listening-started", ["volume": String(audio.outputVolume), "output": audio.currentRoute.outputs.map { $0.portType.rawValue }.joined(separator: ",")])
    }

    private func beginRecognition() {
        guard listening, !speaking, let recognizer else { return }
        restart?.cancel(); restart = nil
        recognitionGeneration += 1
        let generation = recognitionGeneration
        recognition?.cancel()
        self.request?.endAudio()
        utteranceDebounce?.cancel()
        lastPartial = ""
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.request = request
        engine.inputNode.removeTap(onBus: 0)
        let format = engine.inputNode.outputFormat(forBus: 0)
        engine.inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in request.append(buffer) }
        recognition = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self, self.listening, generation == self.recognitionGeneration else { return }
                if let result, !self.speaking {
                    let text = result.bestTranscription.formattedString.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !text.isEmpty, text != self.lastPartial {
                        self.lastPartial = text
                        self.utteranceDebounce?.cancel()
                        self.utteranceDebounce = Task { @MainActor in
                            if !result.isFinal { try? await Task.sleep(for: .milliseconds(1200)) }
                            guard !Task.isCancelled, self.listening, !self.speaking,
                                  generation == self.recognitionGeneration else { return }
                            self.onQuestion?(text)
                            // End this segment so a later final transcript cannot resubmit it.
                            if self.listening { self.beginRecognition() }
                        }
                    }
                }
                if error != nil && result?.isFinal != true {
                    let failure = error! as NSError
                    TrialDiagnostics.record("recognition-error", ["domain": failure.domain, "code": String(failure.code)])
                    self.recentRecognitionErrors = self.recentRecognitionErrors.filter { Date().timeIntervalSince($0) < 30 }
                    self.recentRecognitionErrors.append(Date())
                    if self.recentRecognitionErrors.count >= 3 {
                        self.onFailure?("Listening stopped. Return to Bob to start a new session.")
                        return
                    }
                    self.restart?.cancel()
                    self.restart = Task { @MainActor in
                        try? await Task.sleep(for: .seconds(1))
                        if !Task.isCancelled { self.beginRecognition() }
                    }
                }
            }
        }
    }

    func say(_ text: String, language: String, client: WorkerClient) async {
        let generation = voiceGeneration
        speaking = true
        restart?.cancel(); restart = nil
        TrialDiagnostics.record("speech-request")
        // Discard recognition during Bob's output, including delayed final transcripts.
        recognitionGeneration += 1
        utteranceDebounce?.cancel(); utteranceDebounce = nil
        recognition?.cancel(); recognition = nil
        request?.endAudio(); request = nil
        engine.inputNode.removeTap(onBus: 0)
        defer {
            speaking = false
            if listening, generation == voiceGeneration { beginRecognition() }
        }
        do {
            let audio = try await client.speech(text, language: language)
            TrialDiagnostics.record("speech-received", ["bytes": String(audio.count)])
            try Task.checkCancellation()
            guard generation == voiceGeneration else { return }
            let player = try AVAudioPlayer(data: audio)
            player.delegate = self
            self.player = player
            await withCheckedContinuation { continuation in
                playbackDone = continuation
                let playing = player.play()
                TrialDiagnostics.record("playback", ["playing": String(playing), "volume": String(AVAudioSession.sharedInstance().outputVolume)])
                if !playing { finishPlayback() }
            }
        } catch {
            guard !Task.isCancelled, generation == voiceGeneration else { return }
            TrialDiagnostics.record("speech-fallback", ["domain": (error as NSError).domain, "code": String((error as NSError).code)])
            let utterance = AVSpeechUtterance(string: text)
            utterance.voice = AVSpeechSynthesisVoice(language: language)
            utterance.rate = AVSpeechUtteranceDefaultSpeechRate * 0.9
            await withCheckedContinuation { continuation in
                playbackDone = continuation
                fallback.speak(utterance)
            }
        }
    }
    func stop() {
        voiceGeneration += 1
        recognitionGeneration += 1
        listening = false
        restart?.cancel(); restart = nil
        utteranceDebounce?.cancel(); utteranceDebounce = nil
        recognition?.cancel(); recognition = nil
        request?.endAudio(); request = nil
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        player?.stop(); player = nil
        fallback.stopSpeaking(at: .immediate)
        finishPlayback()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
    private func finishPlayback() { let done = playbackDone; playbackDone = nil; done?.resume() }
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in TrialDiagnostics.record("playback-finished", ["ok": String(flag)]); self.finishPlayback() }
    }
    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        Task { @MainActor in self.finishPlayback() }
    }
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in self.finishPlayback() }
    }
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in self.finishPlayback() }
    }
    private func failure(_ text: String) -> NSError { NSError(domain: "BobVoice", code: 1, userInfo: [NSLocalizedDescriptionKey: text]) }
}
