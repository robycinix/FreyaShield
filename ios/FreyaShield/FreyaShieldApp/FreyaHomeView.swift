import SwiftUI
import UserNotifications

struct FreyaHomeView: View {
    @StateObject private var coordinator = FreyaCallDirectoryCoordinator()
    @State private var policy = FreyaLocalStore.shared.policy
    @State private var events = FreyaLocalStore.shared.recentEvents()
    @State private var newNumber = ""
    @State private var showIosLimitHelp = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    FreyaHeaderView(
                        title: "FreyaShield",
                        onHelp: { showIosLimitHelp = true }
                    )
                        .listRowInsets(EdgeInsets())
                }

                Section("Protezione") {
                    Text(coordinator.statusText)
                    Button("Aggiorna liste iOS") {
                        coordinator.reloadExtension()
                    }
                    Button("Consenti notifiche Freya") {
                        requestNotifications()
                    }
                }

                Section("Scelte Freya") {
                    TextField("Numero completo", text: $newNumber)
                        .keyboardType(.phonePad)
                    Button("Fidati") {
                        try? FreyaCallDirectoryStore.shared.addTrustedNumber(newNumber)
                        FreyaLocalStore.shared.record(
                            maskedNumber: FreyaPhoneNumber.masked(newNumber),
                            action: 0,
                            reason: "TRUSTED_NUMBER",
                            score: 0
                        )
                        reloadLocalState()
                        coordinator.reloadExtension()
                    }
                    Button("Blocca numero") {
                        try? FreyaCallDirectoryStore.shared.blockNumber(newNumber)
                        FreyaLocalStore.shared.record(
                            maskedNumber: FreyaPhoneNumber.masked(newNumber),
                            action: 3,
                            reason: "USER_BLOCKED",
                            score: 1
                        )
                        reloadLocalState()
                        coordinator.reloadExtension()
                    }
                }

                Section("Consenso") {
                    Picker("Livello", selection: $policy.consentLevel) {
                        Text("Solo registro").tag(0)
                        Text("Avviso").tag(1)
                        Text("Identifica").tag(2)
                        Text("Blocca lista").tag(3)
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: policy.consentLevel) { _, _ in
                        FreyaLocalStore.shared.policy = policy
                    }
                }

                Section("Log") {
                    if events.isEmpty {
                        Text("Nessuna chiamata registrata.")
                    } else {
                        ForEach(events) { event in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(event.maskedNumber)
                                    .font(.headline)
                                Text("\(event.reason) - \(event.score, specifier: "%.2f")")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                Section("Limite iOS") {
                Text("Su iPhone Freya usa Call Directory: iOS permette di identificare o bloccare numeri già presenti nelle liste, non di mostrare una scelta sopra ogni chiamata in tempo reale.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .alert("Freya su iPhone", isPresented: $showIosLimitHelp) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("iOS usa Call Directory: Freya può identificare o bloccare numeri già caricati nelle liste, ma non può mostrare una scelta sopra ogni chiamata in tempo reale.")
            }
            .onAppear {
                coordinator.refreshStatus()
                reloadLocalState()
            }
        }
    }

    private func reloadLocalState() {
        policy = FreyaLocalStore.shared.policy
        events = FreyaLocalStore.shared.recentEvents()
        newNumber = ""
    }

    private func requestNotifications() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }
}

private struct FreyaHeaderView: View {
    let title: String
    let onHelp: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Color(red: 0.05, green: 0.04, blue: 0.06)
                Image("freyashield_brand")
                    .resizable()
                    .scaledToFit()
                    .padding(.horizontal, 8)
            }
            .frame(height: 112)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

            HStack {
                Text(title)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(Color(red: 1.0, green: 0.91, blue: 0.71))
                    .lineLimit(1)

                Spacer()

                Button(action: onHelp) {
                    Image(systemName: "questionmark.circle.fill")
                        .font(.title3)
                        .foregroundStyle(Color(red: 1.0, green: 0.91, blue: 0.71))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Aiuto")
            }
            .frame(height: 46)
            .padding(.leading, 20)
            .padding(.trailing, 12)
            .background(Color(red: 0.06, green: 0.04, blue: 0.06))
        }
        .background(Color(red: 0.06, green: 0.04, blue: 0.06))
    }
}
