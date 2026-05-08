# PROGETTO CALLGUARDIAN - Specifica Tecnica Completa

Versione: 1.0  
Data: 2026-05-07  
Piattaforme target: Android 10+ (API 29+), iOS 15+  
Linguaggi principali: Kotlin, Swift, C++20  
Obiettivo: creare un'app anti-spoofing e anti-spam telefonico, ispirata a una soluzione "SpoofShield-like", ma progettata per essere piu completa, privata, modulare e portabile tra Android e iOS.

Questo file e pensato per essere usato come documento di progetto completo e come prompt operativo per un agente AI locale sul PC dello sviluppatore. L'agente deve trattare questo documento come specifica esecutiva.

---

## 1. Visione del prodotto

CallGuardian e un'app mobile per riconoscere, segnalare, silenziare o bloccare chiamate potenzialmente false, spoofate, automatizzate o fraudolente.

Il principio centrale e: proteggere l'utente senza costruire un sistema invasivo. L'app non deve leggere la rubrica, non deve caricare numeri su server esterni e deve funzionare il piu possibile offline.

### Obiettivi principali

- Rilevare chiamate sospette tramite euristiche locali.
- Supportare Android e iOS rispettando i limiti reali delle due piattaforme.
- Usare un core engine condiviso in C++20 per mantenere coerenti le decisioni.
- Consentire all'utente di controllare soglie, whitelist, log e comportamento di blocco.
- Evitare dipendenze cloud obbligatorie.
- Conservare solo dati minimi, preferibilmente aggregati e cifrati.

### Non-obiettivi iniziali

- Non creare un database pubblico globale di numeri spam.
- Non usare la rubrica come fonte primaria.
- Non inviare numeri a un servizio remoto.
- Non promettere blocco in tempo reale su iOS oltre cio che CallKit permette.
- Non implementare machine learning pesante nella prima versione.

---

## 2. Architettura generale

L'applicazione segue una struttura a tre livelli:

```text
┌─────────────────────────────────────────────────────────────┐
│ UI Layer                                                     │
│ Android: Jetpack Compose                                     │
│ iOS: SwiftUI                                                 │
├─────────────────────────────────────────────────────────────┤
│ Platform Bridge                                              │
│ Android: Kotlin + JNI + CMake                                │
│ iOS: Swift + Objective-C++ bridge                            │
├─────────────────────────────────────────────────────────────┤
│ Core Engine C++20                                            │
│ - Call Analyzer                                              │
│ - Spoof Detector                                             │
│ - Decision Engine                                            │
│ - Policy Manager                                             │
│ - Whitelist Matcher                                          │
│ - Behavioral Rules                                           │
└─────────────────────────────────────────────────────────────┘
```

Il core engine non deve conoscere dettagli specifici di Android o iOS. Riceve dati normalizzati, restituisce un risultato strutturato e lascia al livello piattaforma la responsabilita di bloccare, silenziare o mostrare informazioni.

---

## 3. Struttura proposta del repository

```text
CallGuardian/
├── README.md
├── docs/
│   ├── CALLGUARDIAN_PROGETTO.md
│   ├── PRIVACY_MODEL.md
│   ├── THREAT_MODEL.md
│   └── PLATFORM_LIMITATIONS.md
│
├── core_engine/
│   ├── CMakeLists.txt
│   ├── include/
│   │   ├── call_info.h
│   │   ├── risk_assessment.h
│   │   ├── call_analyzer.h
│   │   ├── spoof_detector.h
│   │   ├── decision_engine.h
│   │   ├── policy_config.h
│   │   ├── whitelist_matcher.h
│   │   └── phone_number_normalizer.h
│   ├── src/
│   │   ├── call_analyzer.cpp
│   │   ├── spoof_detector.cpp
│   │   ├── decision_engine.cpp
│   │   ├── policy_config.cpp
│   │   ├── whitelist_matcher.cpp
│   │   └── phone_number_normalizer.cpp
│   └── tests/
│       ├── test_call_analyzer.cpp
│       ├── test_spoof_detector.cpp
│       └── test_phone_number_normalizer.cpp
│
├── android/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/callguardian/
│           │   ├── MainActivity.kt
│           │   ├── engine/CoreEngineBridge.kt
│           │   ├── engine/models.kt
│           │   ├── screening/GuardianCallScreeningService.kt
│           │   ├── data/AppDatabase.kt
│           │   ├── data/BlockedCallEntity.kt
│           │   ├── data/TrustedPatternEntity.kt
│           │   └── ui/
│           │       ├── DashboardScreen.kt
│           │       ├── SettingsScreen.kt
│           │       └── WhitelistScreen.kt
│           └── cpp/
│               └── core_bridge.cpp
│
└── ios/
    ├── CallGuardian.xcodeproj
    ├── CallGuardian/
    │   ├── CallGuardianApp.swift
    │   ├── ContentView.swift
    │   ├── CoreEngineBridge.mm
    │   ├── CoreEngineBridge.h
    │   ├── DashboardView.swift
    │   ├── SettingsView.swift
    │   └── WhitelistView.swift
    └── CallGuardianExtension/
        ├── CallDirectoryHandler.swift
        └── Info.plist
```

---

## 4. Core engine C++20

Il core engine deve essere compilabile separatamente. Deve contenere logica pura, testabile con unit test senza telefono reale.

### 4.1 Modello dati CallInfo

```cpp
#pragma once

#include <cstdint>
#include <string>

enum class CallDirection {
    Incoming = 0,
    Outgoing = 1,
    Unknown = 2
};

enum class VerificationStatus {
    Unknown = 0,
    Passed = 1,
    Failed = 2,
    NotValidated = 3
};

struct CallInfo {
    std::string rawPhoneNumber;
    std::string normalizedPhoneNumber;
    std::string userCountryCode;
    std::string deviceNumberHint;
    std::int64_t timestampMillis = 0;
    CallDirection direction = CallDirection::Unknown;
    VerificationStatus verificationStatus = VerificationStatus::Unknown;
    bool isRoaming = false;
    bool seenBefore = false;
    int recentCallsFromSameNumber = 0;
    int recentCallsFromSamePrefix = 0;
    int userRejectedCallsFromSamePrefix = 0;
};
```

### 4.2 Modello dati RiskAssessment

```cpp
#pragma once

#include <string>
#include <vector>

enum class RecommendedAction {
    Allow = 0,
    Warn = 1,
    Silence = 2,
    Block = 3
};

struct RiskSignal {
    std::string code;
    float weight = 0.0f;
    std::string explanation;
};

struct RiskAssessment {
    float score = 0.0f;
    RecommendedAction action = RecommendedAction::Allow;
    std::string primaryReason;
    std::vector<RiskSignal> signals;
};
```

### 4.3 PolicyConfig

```cpp
#pragma once

struct PolicyConfig {
    float warnThreshold = 0.35f;
    float silenceThreshold = 0.50f;
    float blockThreshold = 0.65f;

    bool blockFailedVerification = true;
    bool warnNeighborSpoof = true;
    bool blockHighFrequencyRobocall = true;
    bool allowVerifiedCalls = true;
    bool allowWhitelistedPatterns = true;
};
```

### 4.4 Phone number normalization

Il modulo `phone_number_normalizer` deve:

- Rimuovere spazi, trattini, parentesi e caratteri decorativi.
- Conservare il prefisso internazionale se presente.
- Convertire numeri in formato locale in formato E.164 quando possibile.
- Non dipendere da servizi remoti.
- Accettare un country code predefinito dell'utente, per esempio `IT`.

Per la prima versione si puo implementare una normalizzazione conservativa:

```text
"02 1234 5678" con paese IT -> "+390212345678"
"347 123 4567" con paese IT -> "+393471234567"
"+39 347 1234567" -> "+393471234567"
"0039 347 1234567" -> "+393471234567"
```

### 4.5 SpoofDetector

Responsabilita:

- rilevare neighbor spoofing;
- rilevare chiamate con firma fallita;
- riconoscere frequenza sospetta;
- riconoscere pattern internazionali ad alto rischio;
- riconoscere sequenze numeriche artificiali;
- produrre segnali leggibili e pesati.

Interfaccia:

```cpp
#pragma once

#include "call_info.h"
#include "risk_assessment.h"
#include <vector>

class SpoofDetector {
public:
    std::vector<RiskSignal> detect(const CallInfo& call) const;

private:
    RiskSignal detectVerificationFailure(const CallInfo& call) const;
    RiskSignal detectNeighborSpoof(const CallInfo& call) const;
    RiskSignal detectHighFrequency(const CallInfo& call) const;
    RiskSignal detectInternationalAnomaly(const CallInfo& call) const;
    RiskSignal detectArtificialPattern(const CallInfo& call) const;
};
```

### 4.6 Euristica: STIR/SHAKEN o verifica operatore

Quando la piattaforma fornisce un valore di verifica:

- `Passed`: riduce fortemente il rischio, ma non azzera se ci sono segnali gravi.
- `Failed`: aggiunge rischio alto.
- `NotValidated` o `Unknown`: non prova frode, ma rende piu importanti le altre euristiche.

Regole:

```text
Verification Passed:
  -0.25 rischio, minimo 0.0

Verification Failed:
  +0.55 rischio
  primaryReason candidato: VERIFICATION_FAILED

Unknown/NotValidated:
  +0.05 rischio solo se esistono altri segnali sospetti
```

### 4.7 Euristica: neighbor spoofing

Il neighbor spoofing avviene quando il chiamante imita le prime cifre del numero dell'utente o del suo prefisso locale per sembrare familiare.

Esempio:

```text
Numero utente:    +393471234567
Numero chiamante: +393479998888
Prefisso comune:  +39347
```

Regola iniziale:

```text
Se caller e deviceNumberHint condividono almeno 5-7 cifre iniziali
e la chiamata non e verificata
e il numero non e in whitelist
allora aggiungi rischio tra 0.35 e 0.60.
```

Peso consigliato:

- 5 cifre comuni: +0.25
- 6 cifre comuni: +0.40
- 7+ cifre comuni: +0.55

### 4.8 Euristica: alta frequenza

Regole:

- stesso numero: piu di 3 chiamate in 10 minuti -> +0.45;
- stesso prefisso: piu di 8 chiamate in 60 minuti -> +0.35;
- prefisso con alto tasso di rifiuto manuale -> +0.25.

Questa euristica richiede che Android/iOS passino al core dati aggregati, non l'intero registro chiamate.

### 4.9 Euristica: pattern artificiale

Numeri con molte cifre ripetute o sequenze evidenti possono essere piu sospetti.

Esempi:

- `+390211111111`
- `+390212345678`
- `+393330000000`

Il peso deve essere moderato, per evitare falsi positivi:

```text
pattern artificiale semplice: +0.10
pattern artificiale + non visto prima + non verificato: +0.20
```

### 4.10 DecisionEngine

Il `DecisionEngine` converte il punteggio in azione:

```text
score < warnThreshold       -> Allow
warnThreshold <= score      -> Warn
silenceThreshold <= score   -> Silence
blockThreshold <= score     -> Block
```

Se il numero e in whitelist:

```text
score = 0.0
action = Allow
primaryReason = WHITELIST
```

Se verifica fallita e `blockFailedVerification = true`:

```text
score = max(score, blockThreshold)
action = Block
```

---

## 5. Android

### 5.1 Componenti Android

- `GuardianCallScreeningService`: intercetta chiamate usando `CallScreeningService`.
- `CoreEngineBridge`: ponte Kotlin/JNI verso C++.
- `Room Database`: conserva impostazioni, whitelist e log aggregati.
- `DataStore`: conserva preferenze utente semplici.
- `WorkManager`: pulizia periodica log e calcolo statistiche.
- `Jetpack Compose`: interfaccia utente.

### 5.2 Permessi e manifest

L'app deve dichiarare il servizio di screening chiamate.

```xml
<service
    android:name=".screening.GuardianCallScreeningService"
    android:permission="android.permission.BIND_SCREENING_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
</service>
```

L'app deve chiedere all'utente di diventare app di screening chiamate tramite `RoleManager`:

```kotlin
val roleManager = getSystemService(RoleManager::class.java)
if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
    !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
) {
    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    startActivityForResult(intent, REQUEST_CALL_SCREENING_ROLE)
}
```

### 5.3 GuardianCallScreeningService

```kotlin
package com.callguardian.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.callguardian.engine.CoreEngineBridge
import com.callguardian.engine.PlatformCallInfo

class GuardianCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart.orEmpty()

        val verification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            details.callerNumberVerificationStatus
        } else {
            0
        }

        val callInfo = PlatformCallInfo(
            rawPhoneNumber = number,
            timestampMillis = System.currentTimeMillis(),
            verificationStatus = verification,
            direction = 0,
            userCountryCode = "IT",
            deviceNumberHint = "",
            seenBefore = false,
            recentCallsFromSameNumber = 0,
            recentCallsFromSamePrefix = 0,
            userRejectedCallsFromSamePrefix = 0
        )

        val assessment = CoreEngineBridge.analyzeCall(callInfo)

        val response = when (assessment.action) {
            3 -> CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()

            2 -> CallResponse.Builder()
                .setSilenceCall(true)
                .setDisallowCall(false)
                .build()

            else -> CallResponse.Builder()
                .setDisallowCall(false)
                .build()
        }

        respondToCall(details, response)
    }
}
```

### 5.4 CoreEngineBridge Kotlin

```kotlin
package com.callguardian.engine

data class PlatformCallInfo(
    val rawPhoneNumber: String,
    val timestampMillis: Long,
    val verificationStatus: Int,
    val direction: Int,
    val userCountryCode: String,
    val deviceNumberHint: String,
    val seenBefore: Boolean,
    val recentCallsFromSameNumber: Int,
    val recentCallsFromSamePrefix: Int,
    val userRejectedCallsFromSamePrefix: Int
)

data class PlatformRiskAssessment(
    val score: Float,
    val action: Int,
    val primaryReason: String,
    val explanation: String
)

object CoreEngineBridge {
    init {
        System.loadLibrary("callguardian_core")
    }

    external fun analyzeCall(callInfo: PlatformCallInfo): PlatformRiskAssessment
    external fun updatePolicy(jsonPolicy: String)
    external fun setWhitelistPatterns(patternsJson: String)
}
```

### 5.5 JNI bridge

```cpp
#include <jni.h>
#include <string>
#include "call_analyzer.h"
#include "call_info.h"

extern "C" JNIEXPORT jobject JNICALL
Java_com_callguardian_engine_CoreEngineBridge_analyzeCall(
    JNIEnv* env,
    jobject,
    jobject callInfoObj
) {
    // Implementare conversione sicura da PlatformCallInfo a CallInfo.
    // Chiamare CallAnalyzer::evaluate.
    // Convertire RiskAssessment in PlatformRiskAssessment.
    return nullptr;
}
```

L'agente locale deve completare questa funzione leggendo i campi via `GetFieldID` e `GetObjectField`, gestendo sempre stringhe null e conversioni UTF.

### 5.6 Database Android

Entita minime:

```kotlin
@Entity(tableName = "trusted_patterns")
data class TrustedPatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encryptedPattern: ByteArray,
    val type: String,
    val createdAtMillis: Long
)

@Entity(tableName = "blocked_call_stats")
data class BlockedCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prefixHash: String,
    val reason: String,
    val score: Float,
    val timestampMillis: Long
)
```

I numeri completi non devono essere salvati in chiaro. Se serve correlazione, usare hash con salt locale.

---

## 6. iOS

### 6.1 Limiti reali di iOS

iOS non consente a un'app terza di intercettare e decidere liberamente ogni chiamata in tempo reale come Android.

Le API principali sono:

- Call Directory Extension: permette di registrare numeri da bloccare o identificare.
- CallKit: gestisce integrazioni telefoniche, ma con limiti forti.
- Live Caller ID Lookup: possibilita piu avanzata, soggetta a entitlement e vincoli Apple.

La prima versione iOS deve quindi adottare un modello a lista precalcolata:

- l'app principale calcola numeri o pattern sospetti;
- l'estensione carica liste ordinate di numeri completi;
- l'utente abilita l'estensione da Impostazioni iOS;
- per numeri mai visti prima, l'app puo identificare meno rispetto ad Android.

### 6.2 CallDirectoryHandler

```swift
import CallKit

final class CallDirectoryHandler: CXCallDirectoryProvider {
    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        do {
            let entries = try BlockListStore.loadBlockingEntries()
            for number in entries.sorted() {
                context.addBlockingEntry(withNextSequentialPhoneNumber: number)
            }
            context.completeRequest()
        } catch {
            context.cancelRequest(withError: error)
        }
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        // Log minimale per debug; non salvare numeri in chiaro.
    }
}
```

### 6.3 CoreEngineBridge iOS

Header:

```objc
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface CGRiskAssessment : NSObject
@property(nonatomic, assign) float score;
@property(nonatomic, assign) NSInteger action;
@property(nonatomic, copy) NSString *primaryReason;
@property(nonatomic, copy) NSString *explanation;
@end

@interface CoreEngineBridge : NSObject
- (CGRiskAssessment *)analyzePhoneNumber:(NSString *)phoneNumber
                              countryCode:(NSString *)countryCode
                        deviceNumberHint:(NSString *)deviceNumberHint;
@end

NS_ASSUME_NONNULL_END
```

Objective-C++ implementation:

```objc
#import "CoreEngineBridge.h"
#include "call_analyzer.h"

@implementation CGRiskAssessment
@end

@implementation CoreEngineBridge

- (CGRiskAssessment *)analyzePhoneNumber:(NSString *)phoneNumber
                              countryCode:(NSString *)countryCode
                        deviceNumberHint:(NSString *)deviceNumberHint {
    CallInfo info;
    info.rawPhoneNumber = [phoneNumber UTF8String] ?: "";
    info.userCountryCode = [countryCode UTF8String] ?: "";
    info.deviceNumberHint = [deviceNumberHint UTF8String] ?: "";

    CallAnalyzer analyzer;
    auto result = analyzer.evaluate(info);

    CGRiskAssessment *assessment = [CGRiskAssessment new];
    assessment.score = result.score;
    assessment.action = static_cast<NSInteger>(result.action);
    assessment.primaryReason = [NSString stringWithUTF8String:result.primaryReason.c_str()];
    assessment.explanation = @"";
    return assessment;
}

@end
```

### 6.4 Dati condivisi tra app ed extension

Usare App Groups:

```text
group.com.callguardian.shared
```

Salvare la lista blocchi in un file condiviso:

```text
Library/Group Containers/group.com.callguardian.shared/blocklist.dat
```

La lista deve essere:

- ordinata numericamente, come richiesto da Call Directory;
- priva di duplicati;
- priva di numeri in chiaro se non strettamente necessari alla Call Directory;
- rigenerabile dall'app principale.

---

## 7. Sicurezza e privacy

### 7.1 Regole obbligatorie

- Non chiedere accesso alla rubrica.
- Non inviare numeri a server esterni.
- Non salvare numeri completi in chiaro nei log.
- Non usare identificatori pubblicitari.
- Non usare analytics invasivi.
- Non aggiungere permesso `INTERNET` su Android nella prima versione, salvo necessita esplicita futura.

### 7.2 Crittografia

Android:

- generare chiave AES tramite Android Keystore;
- cifrare whitelist e impostazioni sensibili;
- salvare solo hash salted per statistiche.

iOS:

- usare Keychain per segreti;
- usare CryptoKit per cifratura simmetrica;
- usare App Group solo per dati necessari all'estensione.

### 7.3 Retention

- Conservare log aggregati massimo 30 giorni.
- Offrire pulsante "Cancella tutti i dati locali".
- Consentire esportazione diagnostica solo senza numeri completi.

---

## 8. Interfaccia utente

### 8.1 Schermate principali

Dashboard:

- stato protezione;
- chiamate bloccate negli ultimi 7 giorni;
- motivi principali di blocco;
- pulsante per abilitare screening chiamate;
- accesso rapido a whitelist e impostazioni.

Whitelist:

- aggiunta numero fidato;
- aggiunta prefisso fidato;
- rimozione pattern;
- import/export locale cifrato opzionale.

Impostazioni:

- soglia avviso;
- soglia silenziamento;
- soglia blocco;
- blocca verifica fallita;
- avvisa neighbor spoofing;
- cancella log locali;
- modalita severita: Bassa, Standard, Alta.

Registro:

- mostra evento senza numero completo, per esempio `+39347******`;
- mostra punteggio e ragione;
- consente "fidati di questo numero" solo quando il numero completo e ancora disponibile in memoria o nel registro di sistema, senza salvarlo inutilmente.

---

## 9. Piano di sviluppo per agente AI locale

L'agente locale deve procedere in questo ordine:

1. Creare `core_engine/` con CMake e classi C++.
2. Implementare normalizzazione base dei numeri.
3. Implementare `SpoofDetector`.
4. Implementare `DecisionEngine`.
5. Scrivere test unitari C++.
6. Creare progetto Android minimo con `CallScreeningService`.
7. Collegare JNI.
8. Creare UI Android essenziale.
9. Creare progetto iOS con SwiftUI e Call Directory Extension.
10. Collegare bridge Objective-C++.
11. Implementare storage sicuro.
12. Aggiungere documentazione privacy e limiti piattaforma.

### Prima richiesta operativa che l'agente locale deve fare

Quando l'agente locale ha caricato questo documento, deve chiedere:

```text
Vuoi che generi il modulo core_engine C++ ora?
```

---

## 10. Test minimi richiesti

### Test core

- numero verificato passato -> azione Allow;
- verifica fallita -> azione Block se policy attiva;
- neighbor spoof non verificato -> almeno Warn o Silence;
- numero whitelist -> sempre Allow;
- frequenza alta -> Block se policy attiva;
- numero internazionale non visto prima -> Warn, non Block automatico;
- pattern artificiale isolato -> massimo Warn.

### Test Android

- il servizio risponde senza crash con numero nullo;
- il bridge JNI gestisce stringhe vuote;
- la UI mostra stato ruolo Call Screening;
- WorkManager cancella log vecchi.

### Test iOS

- Call Directory carica numeri ordinati;
- App Group accessibile da app ed extension;
- bridge Objective-C++ restituisce assessment;
- nessun numero completo scritto nei log diagnostici.

---

## 11. CMake iniziale per core_engine

```cmake
cmake_minimum_required(VERSION 3.22)
project(callguardian_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(callguardian_core
    src/call_analyzer.cpp
    src/spoof_detector.cpp
    src/decision_engine.cpp
    src/policy_config.cpp
    src/whitelist_matcher.cpp
    src/phone_number_normalizer.cpp
)

target_include_directories(callguardian_core PUBLIC include)

add_executable(callguardian_core_tests
    tests/test_call_analyzer.cpp
    tests/test_spoof_detector.cpp
    tests/test_phone_number_normalizer.cpp
)

target_link_libraries(callguardian_core_tests PRIVATE callguardian_core)
```

---

## 12. Criteri di accettazione MVP

L'MVP e accettabile quando:

- il core C++ compila e passa i test;
- Android puo analizzare una chiamata entrante tramite `CallScreeningService`;
- Android puo bloccare o silenziare in base al risultato;
- iOS puo caricare una Call Directory Extension con lista blocchi;
- le impostazioni utente modificano le policy;
- la whitelist locale bypassa le euristiche;
- nessun numero viene mandato in rete;
- i log non contengono numeri completi in chiaro;
- il comportamento e documentato in modo trasparente.

---

## 13. Nota finale per lo sviluppatore

CallGuardian deve essere costruita con realismo tecnico. Android consente screening piu diretto; iOS richiede compromessi tramite Call Directory. Il prodotto deve comunicare chiaramente questi limiti all'utente senza promettere capacita impossibili.

La priorita della prima versione non e bloccare tutto, ma ridurre il rischio con un sistema locale, spiegabile e rispettoso della privacy.

