import SwiftUI

@main
struct BobApp: App {
    @StateObject private var session = LiveSession()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            ContentView(session: session)
                .tint(Color(red: 31/255, green: 95/255, blue: 224/255))
                .onChange(of: phase) { _, phase in session.sceneChanged(phase) }
                .task {
                    #if DEBUG
                    if ProcessInfo.processInfo.arguments.contains("--preview") { session.showPreview() }
                    if ProcessInfo.processInfo.arguments.contains("--stopped-preview") {
                        session.showPreview()
                        session.caption = ""
                        session.status = "Example: audio was interrupted. Start a new session when you are ready."
                    }
                    if ProcessInfo.processInfo.arguments.contains("--smoke") { await session.smokeTest() }
                    #endif
                }
        }
    }
}
