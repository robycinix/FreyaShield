import CallKit
import Foundation

enum FreyaPhoneNumber {
    static func callDirectoryNumber(from rawValue: String, defaultCountryCode: String = "39") -> CXCallDirectoryPhoneNumber? {
        var cleaned = rawValue
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: "(", with: "")
            .replacingOccurrences(of: ")", with: "")

        if cleaned.hasPrefix("00") {
            cleaned.removeFirst(2)
        } else if cleaned.hasPrefix("+") {
            cleaned.removeFirst()
        } else if !cleaned.isEmpty {
            cleaned = defaultCountryCode + cleaned
        }

        guard cleaned.allSatisfy(\.isNumber), let value = CXCallDirectoryPhoneNumber(cleaned) else {
            return nil
        }
        return value
    }

    static func masked(_ rawValue: String) -> String {
        let cleaned = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleaned.count > 6 else {
            return "******"
        }
        return String(cleaned.prefix(6)) + "******"
    }
}
