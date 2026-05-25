kathara lstart --noterminals


# Verifica dell'observer
Controllare che il nodo `observer` esista e abbia `tcpdump` installato:

kathara exec observer "tcpdump --version"


# Verifica base del traffico Modbus

kathara exec modbusclient "python modbus_ops.py test --host 200.1.1.7 --port 502"

Poi generare una richiesta Modbus Function Code 4:

kathara exec modbusclient "python modbus_ops.py fc4 --address 0 --count 1 --host 200.1.1.7 --port 502"

# Verifica dell'AAS

Test-NetConnection -ComputerName localhost -Port 6001


# Abilitazione del mirroring tramite AAS

```powershell
Invoke-RestMethod -Uri "http://localhost:6001/aas/submodels/NetworkTopology/submodel/submodelElements/SetMirroring/invoke" -Method Post -ContentType "application/json" -Body '[1, true]'
```

# Cattura del traffico mirrorato

Nel primo terminale, avviare la cattura sull'observer:

```powershell
kathara exec observer "tcpdump -n -i eth0 tcp port 502 -c 1 -w /shared/observer.pcap"

Nel secondo terminale, generare traffico Modbus FC4 dal client:

```powershell
kathara exec modbusclient "python modbus_ops.py fc4 --address 0 --count 1 --host 200.1.1.7 --port 502"
```
# Ispezione del pacchetto catturato

kathara exec observer "tcpdump -nn -r /shared/observer.pcap"


Risultato atteso: deve comparire un pacchetto Modbus dal client al server, simile a:


195.11.14.5.xxxxx > 200.1.1.7.502


# Disabilitazione del mirroring tramite AAS

Disabilitare il mirroring su `s1`:

```powershell
Invoke-RestMethod -Uri "http://localhost:6001/aas/submodels/NetworkTopology/submodel/submodelElements/SetMirroring/invoke" -Method Post -ContentType "application/json" -Body '[1, false]'
```
# Test negativo dopo la disabilitazione

Questo passaggio serve a dimostrare che il mirroring non e' sempre attivo, ma viene controllato dall'AAS.

Nel primo terminale, avviare una nuova cattura:

```powershell
kathara exec observer "tcpdump -n -i eth0 tcp port 502 -c 1 -w /shared/observer_disabled.pcap"
```

Nel secondo terminale, generare di nuovo traffico Modbus FC4:

```powershell
kathara exec modbusclient "python modbus_ops.py fc4 --address 0 --count 1 --host 200.1.1.7 --port 502"
```

# Verifica della tabella P4

Per controllare direttamente le tabelle dello switch `s1`, usare:

```powershell
kathara exec s1 "echo 'table_dump mirror_table' | simple_switch_CLI"
```

Con mirroring abilitato, la tabella deve contenere una regola per il valore `4`.

Con mirroring disabilitato, la tabella deve essere vuota.