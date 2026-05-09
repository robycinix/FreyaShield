import CallKit
import Foundation

@MainActor
final class FreyaCallDirectoryCoordinator: ObservableObject {
    @Published private(set) var statusText = "Call Directory non verificata"

    func reloadExtension() {
        CXCallDirectoryManager.sharedInstance.reloadExtension(
            withIdentifier: FreyaSharedConfig.callDirectoryExtensionIdentifier
        ) { [weak self] error in
            Task { @MainActor in
                if let error {
                    self?.statusText = "Ricarica non riuscita: \(error.localizedDescription)"
                } else {
                    self?.statusText = "Liste Freya aggiornate su iPhone"
                }
            }
        }
    }

    func refreshStatus() {
        CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(
            withIdentifier: FreyaSharedConfig.callDirectoryExtensionIdentifier
        ) { [weak self] status, error in
            Task { @MainActor in
                if let error {
                    self?.statusText = "Stato non disponibile: \(error.localizedDescription)"
                    return
                }

                switch status {
                case .enabled:
                    self?.statusText = "Call Directory attiva"
                case .disabled:
                    self?.statusText = "Abilita Freya in Impostazioni > Telefono > Blocco chiamate e identificazione"
                case .unknown:
                    self?.statusText = "Stato Call Directory sconosciuto"
                @unknown default:
                    self?.statusText = "Stato Call Directory non riconosciuto"
                }
            }
        }
    }
}
