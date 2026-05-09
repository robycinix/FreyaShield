import CallKit
import Foundation

final class CallDirectoryHandler: CXCallDirectoryProvider {
    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        let store = FreyaCallDirectoryStore.shared

        if context.isIncremental {
            context.removeAllBlockingEntries()
            context.removeAllIdentificationEntries()
        }

        store.sortedBlockingEntries().forEach { phoneNumber in
            context.addBlockingEntry(withNextSequentialPhoneNumber: phoneNumber)
        }

        store.sortedIdentificationEntries().forEach { entry in
            context.addIdentificationEntry(withNextSequentialPhoneNumber: entry.number, label: entry.label)
        }

        context.completeRequest()
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        NSLog("FreyaShield Call Directory failed: \(error.localizedDescription)")
    }
}
