import Foundation

struct FreyaPolicy: Codable, Equatable {
    var warnThreshold: Float = 0.35
    var silenceThreshold: Float = 0.50
    var blockThreshold: Float = 0.65
    var consentLevel: Int = 1
    var manualFeedbackActions: Bool = true

    var normalized: FreyaPolicy {
        var policy = self
        policy.warnThreshold = min(max(policy.warnThreshold, 0.10), 0.95)
        policy.silenceThreshold = min(max(policy.silenceThreshold, policy.warnThreshold), 0.95)
        policy.blockThreshold = min(max(policy.blockThreshold, policy.silenceThreshold), 0.95)
        policy.consentLevel = min(max(policy.consentLevel, 0), 3)
        return policy
    }
}

struct FreyaCallEvent: Codable, Identifiable, Equatable {
    var id = UUID()
    let maskedNumber: String
    let action: Int
    let reason: String
    let score: Float
    let timestamp: Date
}

final class FreyaLocalStore {
    static let shared = FreyaLocalStore()

    private let policyKey = "freya_policy"
    private let eventsKey = "freya_recent_events"
    private let maxEvents = 5
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private init() {
        encoder.dateEncodingStrategy = .iso8601
        decoder.dateDecodingStrategy = .iso8601
    }

    var policy: FreyaPolicy {
        get {
            guard let data = defaults.data(forKey: policyKey),
                  let policy = try? decoder.decode(FreyaPolicy.self, from: data) else {
                return FreyaPolicy()
            }
            return policy.normalized
        }
        set {
            if let data = try? encoder.encode(newValue.normalized) {
                defaults.set(data, forKey: policyKey)
            }
        }
    }

    func recentEvents() -> [FreyaCallEvent] {
        guard let data = defaults.data(forKey: eventsKey),
              let events = try? decoder.decode([FreyaCallEvent].self, from: data) else {
            return []
        }
        return Array(events.prefix(maxEvents))
    }

    func record(maskedNumber: String, action: Int, reason: String, score: Float) {
        let event = FreyaCallEvent(
            maskedNumber: maskedNumber,
            action: action,
            reason: reason,
            score: min(max(score, 0.0), 1.0),
            timestamp: Date()
        )
        let events = Array(([event] + recentEvents()).prefix(maxEvents))
        if let data = try? encoder.encode(events) {
            defaults.set(data, forKey: eventsKey)
        }
    }

    private var defaults: UserDefaults {
        UserDefaults(suiteName: FreyaSharedConfig.appGroupIdentifier) ?? .standard
    }
}
