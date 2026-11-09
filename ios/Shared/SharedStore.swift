import Foundation

struct SharedStore {
    let directory: URL
    init() throws {
        let group = Bundle.main.object(forInfoDictionaryKey: "BobAppGroup") as? String ?? "group.app.saathi.bob.ios"
        guard let shared = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else {
            throw NSError(domain: "Bob", code: 1, userInfo: [NSLocalizedDescriptionKey: "Screen sharing is not set up for this installation."])
        }
        directory = shared.appendingPathComponent("LiveTrial", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }
    // For isolated tests; never silently falls back to a non-shared folder on a phone.
    init(directory: URL) throws {
        self.directory = directory
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }
    func write<T: Encodable>(_ value: T, named name: String) throws {
        let data = try JSONEncoder().encode(value)
        try data.write(to: directory.appendingPathComponent(name), options: [.atomic, .completeFileProtection])
    }
    func read<T: Decodable>(_ type: T.Type, named name: String) -> T? {
        guard let data = try? Data(contentsOf: directory.appendingPathComponent(name)) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
    func clearFrame() {
        for name in ["frame.json", "capture.json"] {
            try? FileManager.default.removeItem(at: directory.appendingPathComponent(name))
        }
    }
    func clear() {
        for name in ["frame.json", "capture.json", "status.json", "session.json"] {
            try? FileManager.default.removeItem(at: directory.appendingPathComponent(name))
        }
    }
}
