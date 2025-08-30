# 🚀 Configurazione Avvio Automatico LiveTV

## Panoramica

LiveTV è configurato per avviarsi automaticamente all'accensione di Android TV e al risveglio dallo standby. Questa funzionalità garantisce che l'app sia sempre disponibile e pronta per l'uso.

## ✨ Funzionalità

### 🔌 Avvio Automatico all'Accensione
- L'app si avvia automaticamente quando la TV viene accesa
- Configurabile tramite le impostazioni dell'app
- Utilizza il servizio `AutoStartService` per gestire l'avvio

### 📺 Risveglio dallo Standby
- L'app si riavvia automaticamente quando la TV esce dallo standby
- Gestisce i cambiamenti di stato della TV (schermo acceso/spento)
- Mantiene i servizi attivi in background

### 🔄 Aggiornamenti App
- Riavvio automatico dei servizi dopo l'aggiornamento dell'app
- Mantenimento delle impostazioni e dello stato

## ⚙️ Configurazione

### 1. Impostazioni nell'App

Apri le **Impostazioni** di LiveTV e vai alla sezione **🚀 Avvio Automatico**:

- **Avvia automaticamente all'accensione della TV**: Abilita l'avvio automatico
- **Avvia automaticamente al risveglio dallo standby**: Abilita il risveglio automatico
- **Mantieni l'app attiva in background**: Mantiene i servizi attivi
- **Imposta come app predefinita per la TV**: Configura l'app come predefinita

### 2. Impostazioni di Sistema

#### Ottimizzazioni Batteria
1. Vai su **Impostazioni** → **App** → **LiveTV**
2. Tocca **Batteria**
3. Seleziona **Nessuna limitazione** o **Ottimizzazione manuale**
4. Disabilita **Ottimizzazione batteria in background**

#### Permessi di Avvio
1. Vai su **Impostazioni** → **App** → **LiveTV**
2. Tocca **Permessi**
3. Abilita **Avvio automatico** e **Esecuzione in background**

### 3. Impostazioni TV (Specifiche per Brand)

#### Xiaomi/Mi TV
1. **Impostazioni** → **App** → **Gestione app**
2. **LiveTV** → **Permessi** → **Avvio automatico** ✅
3. **Impostazioni** → **Batteria e prestazioni** → **Gestione batteria**
4. **LiveTV** → **Nessuna limitazione**

#### Samsung TV
1. **Impostazioni** → **App** → **Gestione app**
2. **LiveTV** → **Permessi** → **Avvio automatico** ✅
3. **Impostazioni** → **Generali** → **Modalità sviluppatore**
4. **Modalità sviluppatore** → **ON**

#### LG TV
1. **Impostazioni** → **Generali** → **App**
2. **LiveTV** → **Permessi** → **Avvio automatico** ✅
3. **Impostazioni** → **Generali** → **Modalità sviluppatore**
4. **Modalità sviluppatore** → **ON**

## 🔧 Componenti Tecnici

### BootReceiver
- Gestisce gli eventi di sistema (`BOOT_COMPLETED`, `SCREEN_ON`, etc.)
- Avvia i servizi appropriati in base agli eventi
- Priorità alta per garantire l'esecuzione

### AutoStartService
- Servizio foreground per l'avvio automatico
- Gestisce l'avvio di MainActivity
- Mantiene l'app attiva in background

### BackgroundService
- Mantiene i servizi attivi
- Gestisce i cambiamenti di stato della TV
- Utilizza wake lock per mantenere l'attività

## 📱 Test e Verifica

### Test Avvio Automatico
1. Vai su **Impostazioni** → **🚀 Avvio Automatico**
2. Tocca **🧪 Test Avvio**
3. Verifica che l'app si avvii correttamente

### Test Risveglio
1. Metti la TV in standby
2. Risveglia la TV
3. Verifica che LiveTV si avvii automaticamente

### Verifica Servizi
1. Vai su **Impostazioni** → **App** → **LiveTV**
2. Tocca **Memoria e archiviazione**
3. Verifica che i servizi siano attivi

## 🚨 Risoluzione Problemi

### L'app non si avvia automaticamente

1. **Controlla le impostazioni**:
   - Verifica che l'avvio automatico sia abilitato
   - Controlla le ottimizzazioni della batteria

2. **Verifica i permessi**:
   - Controlla i permessi di avvio automatico
   - Verifica i permessi di esecuzione in background

3. **Controlla le impostazioni TV**:
   - Verifica le impostazioni specifiche del brand
   - Controlla la modalità sviluppatore

### L'app si chiude in background

1. **Disabilita le ottimizzazioni**:
   - Vai su **Impostazioni** → **Batteria**
   - Seleziona **Nessuna limitazione** per LiveTV

2. **Verifica i servizi**:
   - Controlla che i servizi siano attivi
   - Verifica le notifiche persistenti

### Problemi di performance

1. **Ottimizza le impostazioni**:
   - Disabilita funzionalità non necessarie
   - Riduci la frequenza di aggiornamento EPG

2. **Controlla la memoria**:
   - Verifica l'utilizzo della memoria
   - Riavvia l'app se necessario

## 📋 Checklist Configurazione

- [ ] Avvio automatico abilitato nelle impostazioni app
- [ ] Risveglio automatico abilitato
- [ ] Servizio di background attivo
- [ ] Ottimizzazioni batteria disabilitate
- [ ] Permessi di avvio automatico concessi
- [ ] Modalità sviluppatore attivata (se richiesto)
- [ ] Test avvio automatico completato
- [ ] Test risveglio completato

## 🔗 Link Utili

- [Documentazione Android TV](https://developer.android.com/docs/quality-guidelines/tv-app-quality)
- [Guida Permessi Android](https://developer.android.com/guide/topics/permissions/overview)
- [Gestione Servizi Android](https://developer.android.com/guide/components/services)

## 📞 Supporto

Se riscontri problemi con l'avvio automatico:

1. Controlla i log dell'app
2. Verifica le impostazioni di sistema
3. Consulta la documentazione del brand TV
4. Contatta il supporto tecnico

---

**Nota**: Le impostazioni specifiche possono variare in base al modello e alla versione di Android TV. Consulta sempre la documentazione del produttore per configurazioni specifiche.
