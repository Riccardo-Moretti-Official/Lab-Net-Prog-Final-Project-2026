# Challenge #1 - Procedura di test del mirroring Modbus FC4

Questa guida descrive i passaggi precisi per verificare la Challenge #1: il traffico Modbus TCP con Function Code 4 deve essere mirrorato verso un terzo host, chiamato `observer`, e il mirroring deve poter essere abilitato e disabilitato tramite AAS.

La topologia attesa e':

```text
modbusclient -- s1 -- s2 -- modbusserver
                  |
               observer
```

Il traffico Modbus normale va da `modbusclient` a `modbusserver`. Quando il mirroring e' abilitato su `s1`, i pacchetti Modbus FC4 vengono copiati anche verso `observer`.

## 1. Prerequisiti

Aprire PowerShell nella root del progetto:

```powershell
cd C:\Lab-Net-Prog-Final-Project-2026
```

Verificare che Docker e Kathara siano disponibili:

```powershell
docker --version
kathara --version
```

Docker Desktop deve essere avviato prima di continuare.

## 2. Avvio pulito del laboratorio

Pulire eventuali istanze precedenti:

```powershell
kathara lclean
kathara wipe -f
```

Avviare il laboratorio senza aprire terminali separati:

```powershell
kathara lstart --noterminals
```

Controllare che i container siano attivi:

```powershell
kathara linfo
```

Controllare il file di startup:

```powershell
Get-Content .\shared\startup.temp
```

Risultato atteso: nel file devono comparire almeno questi nodi:

```text
modbusclient modbusserver s1 s2
```

Se il file non esiste ancora o mancano dei nodi, attendere qualche secondo e ripetere il comando.

## 3. Verifica dell'observer

Controllare che il nodo `observer` esista e abbia `tcpdump` installato:

```powershell
kathara exec observer "tcpdump --version"
```

Risultato atteso: viene stampata la versione di `tcpdump`, ad esempio:

```text
tcpdump version 4.99.3
```

Questo conferma che l'observer usa l'immagine corretta e puo' catturare traffico.

## 4. Verifica base del traffico Modbus

Prima di testare il mirroring, verificare che il client riesca a raggiungere il server Modbus:

```powershell
kathara exec modbusclient "python modbus_ops.py test --host 200.1.1.7 --port 502"
```

Poi generare una richiesta Modbus Function Code 4:

```powershell
kathara exec modbusclient "python modbus_ops.py fc4 --address 0 --count 1 --host 200.1.1.7 --port 502"
```

Risultato atteso: il comando termina senza errori e restituisce una lettura Modbus valida.

Se questo test fallisce, il problema e' nel percorso client-server, nel server Modbus o negli switch P4. In quel caso non ha senso proseguire con il test del mirroring.

## 5. Verifica dell'AAS

Controllare che l'AAS Network Infrastructure sia raggiungibile sulla porta `6001`:

```powershell
Test-NetConnection -ComputerName localhost -Port 6001
```

Risultato atteso:

```text
TcpTestSucceeded : True
```

Opzionalmente aprire la Web UI BaSyx:

```text
http://localhost:3001
```

Nella Web UI dovrebbero essere visibili le shell AAS del progetto, tra cui `Network Infrastructure`, `Network Control Plane`, `ModbusClient` e `ModbusServer`.

## 6. Abilitazione del mirroring tramite AAS

Abilitare il mirroring sullo switch `s1`:

```powershell
Invoke-RestMethod -Uri "http://localhost:6001/aas/submodels/NetworkTopology/submodel/submodelElements/SetMirroring/invoke" -Method Post -ContentType "application/json" -Body '[1, true]'
```

Significato del body:

```text
[1, true]
```

- `1` indica lo switch `s1`.
- `true` abilita il mirroring.

Risultato atteso: la risposta deve indicare che e' stata aggiunta una regola alla tabella `mirror_table`, ad esempio:

```text
table_add mirror_table mirror_to_observer 4 =>
```

## 7. Cattura del traffico mirrorato

Aprire due terminali PowerShell nella root del progetto:

```powershell
cd C:\Lab-Net-Prog-Final-Project-2026
```

Nel primo terminale, avviare la cattura sull'observer:

```powershell
kathara exec observer "tcpdump -n -i eth0 tcp port 502 -c 1 -w /shared/observer.pcap"
```

Questo comando resta in attesa finche' non cattura un pacchetto TCP sulla porta Modbus `502`.

Nel secondo terminale, generare traffico Modbus FC4 dal client:

```powershell
kathara exec modbusclient "python modbus_ops.py fc4 --address 0 --count 1 --host 200.1.1.7 --port 502"
```

Risultato atteso:

- il comando del client termina senza errori;
- il comando `tcpdump` nel primo terminale termina dopo aver catturato `1` pacchetto;
- viene creato il file `/shared/observer.pcap`.

## 8. Ispezione del pacchetto catturato

Leggere il file `.pcap` dall'observer:

```powershell
kathara exec observer "tcpdump -nn -r /shared/observer.pcap"
```

Risultato atteso: deve comparire un pacchetto Modbus dal client al server, simile a:

```text
195.11.14.5.xxxxx > 200.1.1.7.502
```

Gli indirizzi importanti sono:

```text
195.11.14.5  = modbusclient
200.1.1.7    = modbusserver
502          = porta Modbus TCP
```

Se il pacchetto e' presente, il mirroring FC4 verso l'observer funziona.

## 9. Disabilitazione del mirroring tramite AAS

Disabilitare il mirroring su `s1`:

```powershell
Invoke-RestMethod -Uri "http://localhost:6001/aas/submodels/NetworkTopology/submodel/submodelElements/SetMirroring/invoke" -Method Post -ContentType "application/json" -Body '[1, false]'
```

Significato del body:

```text
[1, false]
```

- `1` indica lo switch `s1`.
- `false` disabilita il mirroring.

Risultato atteso: la risposta deve confermare la pulizia della tabella di mirroring, ad esempio:

```text
table_clear mirror_table
```

## 10. Test negativo dopo la disabilitazione

Questo passaggio serve a dimostrare che il mirroring non e' sempre attivo, ma viene controllato dall'AAS.

Nel primo terminale, avviare una nuova cattura:

```powershell
kathara exec observer "tcpdump -n -i eth0 tcp port 502 -c 1 -w /shared/observer_disabled.pcap"
```

Nel secondo terminale, generare di nuovo traffico Modbus FC4:

```powershell
kathara exec modbusclient "python modbus_ops.py fc4 --address 0 --count 1 --host 200.1.1.7 --port 502"
```

Risultato atteso: il client deve funzionare normalmente, ma `tcpdump` sull'observer non dovrebbe terminare perche' non riceve piu' il pacchetto mirrorato.

Per interrompere `tcpdump`, premere:

```text
CTRL+C
```

Se dopo la disabilitazione l'observer continua a catturare pacchetti FC4, il mirroring non e' stato disattivato correttamente.

## 11. Verifica opzionale della tabella P4

Per controllare direttamente le tabelle dello switch `s1`, usare:

```powershell
kathara exec s1 "echo 'table_dump mirror_table' | simple_switch_CLI"
```

Con mirroring abilitato, la tabella deve contenere una regola per il valore `4`.

Con mirroring disabilitato, la tabella deve essere vuota.

## 12. Spegnimento del laboratorio

Quando i test sono conclusi:

```powershell
kathara lclean
```

## Checklist finale

La challenge e' verificata se:

- `observer` esiste e puo' eseguire `tcpdump`;
- il client Modbus riesce a comunicare con il server;
- l'AAS sulla porta `6001` risponde;
- `SetMirroring` con body `[1, true]` abilita il mirroring;
- una richiesta Modbus FC4 viene catturata sull'observer;
- `SetMirroring` con body `[1, false]` disabilita il mirroring;
- dopo la disabilitazione l'observer non riceve piu' il traffico mirrorato.
