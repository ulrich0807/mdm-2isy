# Agent Android MDM 2ISY

Ce module est le premier socle natif Kotlin de l’agent MDM. Il cible les terminaux professionnels Android 13+ et communique avec le contrat déjà exposé par `mdm-2isy-api`.

## Fonctionnalités de cette tranche

- écran d’enrôlement manuel avec URL API et jeton à usage unique ;
- UUID stable par installation et jeton appareil chiffré par Android Keystore/AES-GCM ;
- inventaire fabricant, modèle, version/build Android, batterie, stockage, version agent, numéro de série et IMEI lorsque le rôle Device Owner l’autorise ;
- heartbeat toutes les 60 secondes et polling des commandes toutes les 30 secondes ;
- service foreground persistant `systemExempted` sur un terminal Device Owner ;
- reprise après redémarrage pour un terminal enrôlé et provisionné ;
- journal local idempotent avant ACK et avant publication du résultat final ;
- exécution de `locate`, `lock` et `wipe` avec preuves compatibles avec Laravel ;
- retry exponentiel réseau, prise en compte de `Retry-After`, révocation sur HTTP 401 et quarantaine des transitions incompatibles ;
- 15 tests JVM sur les URL, modèles, délais, préconditions Device Owner et preuves de commandes.

## Versions du projet

| Élément | Version |
|---|---:|
| Android Gradle Plugin | `9.3.1` |
| Gradle | `9.5.0` |
| Java | `17` |
| `minSdk` | `33` |
| `compileSdk` | `37` |
| `targetSdk` | `36` |

Kotlin est intégré à AGP 9 : ne pas ajouter le plugin `org.jetbrains.kotlin.android`.

## Prérequis manquants sur ce poste

Au moment de cette tranche, la machine ne contient ni Android Studio/JDK, ni SDK Android, ni Gradle/ADB. Le dépôt contient `gradle-wrapper.properties`, avec le SHA-256 officiel de la distribution, mais pas encore `gradle-wrapper.jar`, `gradlew` ou `gradlew.bat`.

Installer Android Studio avec JDK 17 et Android SDK Platform 37, puis générer une fois le wrapper avec Gradle 9.5 :

```powershell
cd mdm-2isy-android
gradle wrapper --gradle-version 9.5.0
```

Versionner ensuite les trois fichiers générés. Le téléchargement automatique du JAR depuis cette session n’a pas abouti ; aucun binaire non vérifié n’a été ajouté.

Commandes de validation à lancer après installation :

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

## URL de l’API appareil

La base exacte est :

```text
https://serveur/api/v1/device
```

Routes utilisées :

```text
POST  {base}/enroll
POST  {base}/heartbeat
GET   {base}/commands?limit=10
POST  {base}/commands/{public_id}/ack
POST  {base}/commands/{public_id}/result
```

Le build debug préremplit :

```text
http://10.0.2.2:8000/api/v1/device
```

`10.0.2.2` désigne le PC hôte depuis l’émulateur Android. Le manifeste debug autorise ce HTTP local. Une release refuse toute URL autre que HTTPS et ne contient volontairement aucune adresse de production par défaut.

Sur un téléphone physique, utiliser une URL HTTPS joignable depuis le terminal. Une adresse `localhost` pointerait vers le téléphone lui-même.

## Enrôlement API et Device Owner

Il s’agit de deux opérations différentes :

1. l’enrôlement API échange l’invitation à usage unique contre `device_id` et `device_token` ;
2. le provisionnement Android Device Owner accorde les privilèges système du DPC.

Une installation APK ou un enrôlement API ne peut pas promouvoir l’application elle-même Device Owner. En production, le provisionnement doit se faire pendant l’assistant initial d’un appareil réinitialisé, par QR Android Enterprise ou zero-touch.

Pour le laboratoire uniquement, installer l’APK sur un émulateur/appareil vierge, sans compte, puis utiliser :

```powershell
# Build debug : applicationId suffixé par .debug
adb shell dpm set-device-owner "com.mdm2isy.agent.debug/com.mdm2isy.agent.admin.MdmDeviceAdminReceiver"

# Build release
adb shell dpm set-device-owner "com.mdm2isy.agent/com.mdm2isy.agent.admin.MdmDeviceAdminReceiver"

adb shell dpm list owners
```

Après provisionnement, ouvrir l’application et saisir l’URL ainsi que le jeton générés dans la console MDM. L’agent accorde alors à son propre package les permissions d’inventaire et de localisation autorisées au Device Owner.

## Traitement sécurisé des commandes

Séquence locale :

```text
RECEIVED -> ACKNOWLEDGED -> EXECUTING -> RESULT_PENDING -> FINAL
```

Chaque commande est persistée avant l’ACK. Le JSON final exact est persisté avant l’appel `/result` et rejoué après une coupure réseau. Les conflits expirés deviennent `EXPIRED`; une commande inconnue ou incompatible devient `QUARANTINED`.

Preuves de succès requises :

```json
{"status":"succeeded","result":{"lat":5.3599,"lng":-4.0083,"accuracy_m":20}}
{"status":"succeeded","result":{"locked":true}}
{"status":"succeeded","result":{"wipe_started":true}}
```

Le verrouillage ne produit `locked=true` qu’après le retour de `DevicePolicyManager.lockNow()`. L’effacement exige un Device Owner actif :

- Android 14+ : `DevicePolicyManager.wipeDevice(0)` ;
- Android 13 : `DevicePolicyManager.wipeData(0)`.

`wipe_started=true` n’est jamais fabriqué avant l’appel Android. Le terminal peut néanmoins redémarrer avant de publier le résultat final ; le serveur possède déjà la trace de livraison et l’ACK.

> `wipe` réinitialise réellement le terminal. Ne jamais le tester sur un téléphone personnel ou contenant des données utiles. Utiliser uniquement un émulateur jetable ou un appareil de laboratoire explicitement autorisé.

## Scénario de validation

1. Démarrer Laravel sur une adresse accessible, par exemple `0.0.0.0:8000` en laboratoire.
2. Créer un émulateur Android 13+ vierge et installer `app-debug.apk`.
3. Définir le package debug comme Device Owner et vérifier `dpm list owners`.
4. Générer une invitation dans MDM 2ISY puis enrôler l’agent.
5. Vérifier un heartbeat et l’inventaire dans la console web.
6. Tester `locate`, puis `lock`.
7. Couper le réseau entre ACK et résultat, le rétablir et vérifier le replay sans seconde exécution.
8. Tester le redémarrage de l’émulateur.
9. Tester `wipe` en dernier, uniquement sur cet environnement jetable.

## Limites et prochaine tranche

Ce socle utilise un foreground service et du polling. La prochaine évolution devra ajouter FCM comme déclencheur quasi temps réel et WorkManager comme filet de sécurité. Restent également hors de cette tranche :

- activités de provisionnement QR/zero-touch de production ;
- gestion des applications (installation, mise à jour, suppression, listes autorisées/interdites) ;
- politiques de mot de passe et restrictions USB/Bluetooth/caméra ;
- mode kiosque mono/multi-application ;
- rotation de jeton, attestation matérielle et pinning de certificat ;
- tests instrumentés Android 13, 14, 16 et 17 sur matériel réel.

Références : [AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes), [Kotlin intégré](https://developer.android.com/build/migrate-to-built-in-kotlin), [Device Owner](https://developer.android.com/work/dpc/dedicated-devices), [types de foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types), [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager).
