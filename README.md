# FreyaShield

FreyaShield e un progetto mobile anti-spoofing/anti-spam per Android e iOS, costruito a partire da una specifica privacy-first.

La prima parte implementata e `core_engine`: una libreria C++20 condivisibile con Android tramite JNI e con iOS tramite Objective-C++.

## Stato attuale

- Documento di progetto: `CALLGUARDIAN_PROGETTO.md`
- Core C++20: `core_engine/`
- Euristiche iniziali:
  - verifica operatore fallita;
  - verifica operatore passata;
  - neighbor spoofing;
  - alta frequenza;
  - primo contatto internazionale;
  - pattern numerici artificiali;
  - whitelist esatta o con wildcard finale.
- Android MVP: `android/`
  - progetto Gradle con Kotlin e Compose;
  - `GuardianCallScreeningService` registrato nel manifest;
  - bridge JNI verso il core C++20;
  - dashboard Compose con stato ruolo Call Screening e smoke test del core;
  - schermate per whitelist e soglie policy;
  - persistenza locale cifrata di soglie, toggle e whitelist tramite Android Keystore;
  - applicazione delle policy al core tramite JNI;
  - statistiche aggregate locali degli ultimi 7 giorni senza numeri in chiaro.

## Build del core

Con MSYS2 UCRT64 installato:

```powershell
C:\msys64\usr\bin\bash.exe -lc 'export PATH=/ucrt64/bin:$PATH; cd /c/Users/rober/Documents/New\ project && cmake -S core_engine -B core_engine/build -G Ninja && cmake --build core_engine/build && ./core_engine/build/callguardian_core_tests.exe'
```

Con CMake installato:

```powershell
cmake -S core_engine -B core_engine/build
cmake --build core_engine/build --config Release
.\core_engine\build\Release\callguardian_core_tests.exe
```

Su Windows servono:

- Visual Studio Build Tools con workload "Desktop development with C++";
- Windows SDK;
- CMake nel PATH, oppure CMake installato con Visual Studio.

Se `cl` fallisce con `crtdbg.h: No such file or directory`, manca il componente Windows SDK/C++ runtime headers nei Build Tools.

## Build Android

Con il Gradle incluso in `.tools`:

```powershell
.\.tools\gradle-8.9\bin\gradle.bat -p android assembleDebug
```

L'APK debug viene generato in:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Prossimo passo

Il passo successivo e completare la robustezza privacy e i test Android:

- aggiungere test Android per servizio e bridge con input vuoti/null;
- aggiungere una UI di registro mascherato opzionale;
- iniziare lo scheletro iOS con SwiftUI e Call Directory Extension.
