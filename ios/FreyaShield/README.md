# FreyaShield iOS

Questo scaffold allinea iOS al comportamento Android dove Apple lo permette.

## Cosa fa

- Usa una Call Directory Extension per bloccare e identificare numeri gia presenti nelle liste locali.
- Condivide dati tra app ed extension tramite App Group.
- Mantiene un log locale di massimo 5 eventi.
- Espone consenso, whitelist, blocklist e ricarica liste dalla app SwiftUI.

## Limite Apple

iOS non permette a un'app terza di intercettare ogni chiamata in tempo reale e mostrare una scelta sopra la chiamata come Android. L'equivalente supportato e Call Directory: il sistema legge liste ordinate di numeri completi da bloccare o identificare.

Per funzioni piu dinamiche serve Live Caller ID Lookup, che richiede entitlement e architettura server secondo i vincoli Apple.

## Setup Xcode richiesto

1. Crea un progetto iOS SwiftUI `FreyaShield`.
2. Aggiungi un target `Call Directory Extension`.
3. Collega questi file ai target:
   - `FreyaShieldApp/*` al target app.
   - `FreyaShieldCallDirectory/*` al target extension.
   - `Shared/*` a entrambi i target.
4. Abilita App Groups su app ed extension:
   - `group.com.callguardian.freyashield`
5. Imposta il bundle identifier extension:
   - `com.callguardian.FreyaShield.CallDirectory`
6. Aggiungi l'immagine `freyashield_brand` agli asset dell'app.
7. Su iPhone abilita Freya in:
   - Impostazioni > Telefono > Blocco chiamate e identificazione.

## Parita con Android

Android usa `CallScreeningService` e puo reagire durante la chiamata.
iOS usa `CXCallDirectoryProvider` e puo solo precaricare liste. Per questo il codice iOS conserva lo stesso modello dati, ma il momento decisionale avviene quando l'utente aggiorna le liste, non mentre la chiamata squilla.
