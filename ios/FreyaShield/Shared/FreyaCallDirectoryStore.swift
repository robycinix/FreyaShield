import CallKit
import Foundation

struct FreyaIdentifiedCaller: Codable, Equatable {
    let phoneNumber: String
    let label: String
}

struct FreyaCallDirectorySnapshot: Codable, Equatable {
    var blockedNumbers: [String] = []
    var identifiedCallers: [FreyaIdentifiedCaller] = []
    var updatedAt: Date = Date()
}

final class FreyaCallDirectoryStore {
    static let shared = FreyaCallDirectoryStore()

    private let fileName = "call-directory-snapshot.json"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private init() {
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        decoder.dateDecodingStrategy = .iso8601
    }

    func load() -> FreyaCallDirectorySnapshot {
        guard let data = try? Data(contentsOf: snapshotURL) else {
            return FreyaCallDirectorySnapshot()
        }
        return (try? decoder.decode(FreyaCallDirectorySnapshot.self, from: data)) ?? FreyaCallDirectorySnapshot()
    }

    func save(_ snapshot: FreyaCallDirectorySnapshot) throws {
        let data = try encoder.encode(snapshot)
        try data.write(to: snapshotURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    func addTrustedNumber(_ rawNumber: String, label: String = "Freya: fidato") throws {
        var snapshot = load()
        let caller = FreyaIdentifiedCaller(phoneNumber: rawNumber, label: label)
        if !snapshot.identifiedCallers.contains(caller) {
            snapshot.identifiedCallers.append(caller)
        }
        snapshot.updatedAt = Date()
        try save(snapshot)
    }

    func blockNumber(_ rawNumber: String) throws {
        var snapshot = load()
        if !snapshot.blockedNumbers.contains(rawNumber) {
            snapshot.blockedNumbers.append(rawNumber)
        }
        snapshot.updatedAt = Date()
        try save(snapshot)
    }

    func sortedBlockingEntries() -> [CXCallDirectoryPhoneNumber] {
        load().blockedNumbers
            .compactMap(FreyaPhoneNumber.callDirectoryNumber)
            .uniqued()
            .sorted()
    }

    func sortedIdentificationEntries() -> [(number: CXCallDirectoryPhoneNumber, label: String)] {
        load().identifiedCallers
            .compactMap { caller -> (CXCallDirectoryPhoneNumber, String)? in
                guard let number = FreyaPhoneNumber.callDirectoryNumber(from: caller.phoneNumber) else {
                    return nil
                }
                return (number, caller.label)
            }
            .reduce(into: [CXCallDirectoryPhoneNumber: String]()) { partial, entry in
                partial[entry.0] = entry.1
            }
            .map { (number: $0.key, label: $0.value) }
            .sorted { $0.number < $1.number }
    }

    private var snapshotURL: URL {
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: FreyaSharedConfig.appGroupIdentifier
        ) else {
            return FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        }
        return containerURL.appendingPathComponent(fileName)
    }
}

private extension Sequence where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
