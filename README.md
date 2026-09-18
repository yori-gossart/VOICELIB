# VOX Android — V0.1

Prototype de lecteur vocal d'écran Android.

## Ce que fait cette V0.1

- Service d'accessibilité Android opt-in.
- Petite bulle flottante `VOX ▶` au-dessus de l'application active.
- Un appui capture le texte actuellement exposé à l'accessibilité et le lit avec le moteur TTS du téléphone.
- Notification avec **Précédent / Pause-Reprendre / Suivant / Arrêter**.
- Réglage local de la vitesse et de la hauteur de voix.
- Presets simples **Naturel / Deep / Clair**.
- Aucun serveur, aucun compte, aucune API payante.

## Pas encore dans cette version

- Auto-scroll et lecture continue des nouveaux paragraphes.
- « Lire à partir d'ici ».
- Commandes vocales mains libres.
- Effets audio Robot/Radio/ASMR.
- OCR des écrans qui n'exposent pas leur texte à Android.

## Compiler sur GitHub

Le workflow `.github/workflows/android-build.yml` compile automatiquement l'APK de debug
à chaque push sur `main` (et à la demande via **Actions → Build APK → Run workflow**).

Dans GitHub : **Actions → Build APK → dernier run → Artifacts → VOICELIB-v0.1-debug-apk**.

Chaîne de build : JDK 17, Gradle 9.7.1 (wrapper versionné dans le dépôt),
Android Gradle Plugin 9.4.0, `compileSdk`/`targetSdk` 36, `minSdk` 26.

## Compiler en local

```
./gradlew :app:assembleDebug
```

L'APK est produit dans `app/build/outputs/apk/debug/app-debug.apk`.
Un SDK Android avec `platforms;android-36` et `build-tools;36.0.0` est requis.

## Installer sur Android

1. Télécharger l'APK de debug sur le téléphone.
2. Autoriser l'installation depuis la source utilisée pour le téléchargement si Android le demande.
3. Ouvrir VOX.
4. Autoriser les notifications pour obtenir les contrôles de lecture.
5. Appuyer sur **Activer VOX dans Accessibilité** et activer le service **VOX — lecteur d'écran**.
6. Revenir dans ChatGPT ou Chrome.
7. Appuyer sur la bulle **VOX ▶**.
8. Pour arrêter rapidement : appui long sur la bulle ou bouton **Arrêter** dans la notification.

## Confidentialité de ce prototype

La lecture est déclenchée explicitement par l'utilisateur. La V0.1 n'envoie aucun texte vers un serveur : le texte capturé reste dans le processus de l'application et est transmis au moteur TTS Android configuré sur l'appareil. Le comportement réseau éventuel du moteur TTS lui-même dépend du moteur choisi dans les réglages Android.
