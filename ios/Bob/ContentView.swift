import SwiftUI
import ReplayKit

struct ContentView: View {
    @ObservedObject var session: LiveSession
    private let languages = [("en-IN", "English"), ("hi-IN", "हिन्दी"), ("ta-IN", "தமிழ்"), ("te-IN", "తెలుగు"), ("kn-IN", "ಕನ್ನಡ"), ("ml-IN", "മലയാളം")]
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        Image("BobMark").resizable().scaledToFit().frame(width: 44, height: 44).clipShape(Circle()).accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("One step at a time").font(.headline)
                            Text("Live help for your iPhone").foregroundStyle(Color(uiColor: .label))
                        }
                    }.padding(.vertical, 8)
                }
                Section {
                    TextField("Your task", text: $session.goal, prompt: Text("For example, make the text bigger").foregroundColor(Color(uiColor: .label)), axis: .vertical)
                        .lineLimit(2...4).disabled(session.active || session.preparing)
                        .accessibilityLabel("What would you like help with?")
                    Picker("Language", selection: $session.language) {
                        ForEach(languages, id: \.0) { Text($0.1).tag($0.0) }
                    }.disabled(session.active || session.preparing)
                } header: { Text("What would you like to do?").foregroundStyle(Color(uiColor: .label)) }
                if !session.caption.isEmpty {
                    Section {
                        Text(session.caption).font(.title3).fontWeight(.semibold).fixedSize(horizontal: false, vertical: true)
                        Label(session.status, systemImage: session.preview ? "info.circle" : "waveform").font(.subheadline).foregroundStyle(Color(uiColor: .label))
                    } header: { Text("Bob’s next step").foregroundStyle(Color(uiColor: .label)) }
                } else if session.status != "Ready when you are" {
                    Section { Label(session.status, systemImage: "info.circle").foregroundStyle(Color(uiColor: .label)) }
                }
                Section {
                    if session.active {
                        Label(session.status, systemImage: "mic.fill").foregroundStyle(Color(uiColor: .label))
                        HStack {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Share your screen").font(.headline)
                                Text("Tap the recording button, choose Bob Screen Help, then Start Broadcast. Open the app you need help with.").foregroundStyle(Color(uiColor: .label))
                            }
                            BroadcastPicker().frame(width: 56, height: 56)
                        }.padding(.vertical, 8)
                        Button("Stop help", role: .destructive) { session.stop() }.frame(minHeight: 44)
                    } else {
                        #if DEBUG && targetEnvironment(simulator)
                        Button {
                            Task { await session.smokeTest() }
                        } label: {
                            Label { Text("Try sample guidance").foregroundStyle(.white) } icon: { Image(systemName: "play.fill").foregroundStyle(.white) }.frame(maxWidth: .infinity, minHeight: 44)
                        }.buttonStyle(.borderedProminent).disabled(session.preparing)
                        #else
                        Button { session.begin() } label: {
                            HStack {
                                if session.preparing { ProgressView().tint(.white) } else { Image(systemName: "mic").foregroundStyle(.white) }
                                Text(session.preparing ? "Getting ready…" : "Start live help").fontWeight(.semibold).foregroundStyle(.white)
                            }.frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(session.preparing)
                        if session.preparing { Button("Cancel", role: .cancel) { session.stop() }.frame(minHeight: 44) }
                        #endif
                    }
                } footer: {
                    Text("During help, Bob listens for your questions. Screen text is read on your iPhone and sent to Bob’s server to choose the next step. Screen images stay on your phone. Stop help or stop the broadcast at any time.").font(.callout).foregroundStyle(Color(uiColor: .label))
                }
                if let error = session.error {
                    Section { Label { Text(error).foregroundStyle(Color(uiColor: .label)) } icon: { Image(systemName: "exclamationmark.triangle").foregroundStyle(.red) }.fixedSize(horizontal: false, vertical: true) }
                }
                Section {
                    Label("Bob speaks. You make every tap.", systemImage: "hand.tap")
                    Label("Known payment, password and code screens pause help. Detection may miss a private screen; stop sharing before entering secrets.", systemImage: "lock.shield")
                    Label("iOS trial: voice guidance, with no floating pointer. Ten minutes or twenty steps per session.", systemImage: "iphone")
                    #if targetEnvironment(simulator)
                    Label("Live screen broadcasting needs a real iPhone. This Simulator can preview Bob and test its voice connection.", systemImage: "desktopcomputer")
                    #endif
                } header: { Text("Before you start").foregroundStyle(Color(uiColor: .label)) }
            }
            .navigationTitle("Bob")
            .scrollDismissesKeyboard(.interactively)
        }
    }
}

private struct BroadcastPicker: UIViewRepresentable {
    func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let picker = RPSystemBroadcastPickerView(frame: CGRect(x: 0, y: 0, width: 56, height: 56))
        picker.preferredExtension = "app.saathi.bob.ios.broadcast"
        // Bob's main app handles the microphone. Broadcast audio buffers are ignored.
        picker.showsMicrophoneButton = false
        picker.accessibilityLabel = "Start sharing your screen with Bob"
        return picker
    }
    func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}
