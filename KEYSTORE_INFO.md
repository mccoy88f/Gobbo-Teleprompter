# Informazioni Keystore

## File Keystore
- **File**: `gobbo-release-key.jks`
- **Alias**: `gobbo`
- **Password store**: `gobbo123`
- **Password key**: `gobbo123`
- **Validità**: 10000 giorni (~27 anni)

## ⚠️ IMPORTANTE

**CONSERVA QUESTA KEYSTORE IN SICUREZZA!**

- La keystore è necessaria per firmare tutte le versioni future dell'app
- Se perdi la keystore, non potrai più aggiornare l'app pubblicata
- La keystore è già aggiunta al `.gitignore` per sicurezza
- **NON condividere mai la keystore pubblicamente**

## Cambiare la Password

Se vuoi cambiare la password della keystore:

```bash
keytool -storepasswd -keystore gobbo-release-key.jks
keytool -keypasswd -keystore gobbo-release-key.jks -alias gobbo
```

Poi aggiorna le password in `app/build.gradle.kts` nella sezione `signingConfigs`.

## Backup

Fai un backup sicuro della keystore:
- Salvala in un luogo sicuro (password manager, cloud criptato, ecc.)
- Non committarla mai nel repository Git
