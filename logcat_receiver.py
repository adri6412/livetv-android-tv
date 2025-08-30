#!/usr/bin/env python3
"""
Logcat Receiver Service
Si connette ad Android e riceve i log via TCP
"""

import socket
import threading
import time
import json
import argparse
from datetime import datetime
import logging

class LogcatReceiver:
    def __init__(self, host='0.0.0.0', port=8080):
        self.host = host
        self.port = port
        self.server_socket = None
        self.clients = []
        self.running = False
        
        # Configura logging
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler('logcat_receiver.log'),
                logging.StreamHandler()
            ]
        )
        self.logger = logging.getLogger(__name__)
        
    def start_server(self):
        """Avvia il server per ricevere connessioni da Android"""
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(5)
            self.running = True
            
            self.logger.info(f"[START] Server avviato su {self.host}:{self.port}")
            self.logger.info("[WAIT] In attesa di connessioni da Android...")
            self.logger.info("[INFO] Avvia il LogcatService su Android per inviare i log")
            
            # Thread per accettare connessioni
            accept_thread = threading.Thread(target=self.accept_connections)
            accept_thread.daemon = True
            accept_thread.start()
            
            # Thread per gestire i client
            client_thread = threading.Thread(target=self.manage_clients)
            client_thread.daemon = True
            client_thread.start()
            
            # Loop principale
            try:
                while self.running:
                    time.sleep(1)
            except KeyboardInterrupt:
                self.logger.info("[STOP] Interruzione richiesta dall'utente")
                
        except Exception as e:
            self.logger.error(f"[ERROR] Errore nell'avvio del server: {e}")
        finally:
            self.cleanup()
    
    def accept_connections(self):
        """Accetta nuove connessioni da Android"""
        while self.running:
            try:
                client_socket, address = self.server_socket.accept()
                self.logger.info(f"[CONN] Nuova connessione da {address}")
                
                # Aggiungi client alla lista
                self.clients.append({
                    'socket': client_socket,
                    'address': address,
                    'connected_at': datetime.now(),
                    'last_activity': datetime.now()
                })
                
                # Thread per gestire questo client
                client_thread = threading.Thread(
                    target=self.handle_client,
                    args=(client_socket, address)
                )
                client_thread.daemon = True
                client_thread.start()
                
            except Exception as e:
                if self.running:
                    self.logger.error(f"[ERROR] Errore nell'accettare connessione: {e}")
    
    def handle_client(self, client_socket, address):
        """Gestisce un client Android"""
        try:
            self.logger.info(f"[HANDLE] Gestione client {address}")
            
            while self.running:
                try:
                    # Ricevi dati dal client
                    data = client_socket.recv(4096)
                    if not data:
                        break
                    
                    # Processa i log ricevuti
                    self.process_log_data(data, address)
                    
                    # Aggiorna ultima attività
                    self.update_client_activity(address)
                    
                except Exception as e:
                    self.logger.error(f"[ERROR] Errore nella ricezione da {address}: {e}")
                    break
                    
        except Exception as e:
            self.logger.error(f"[ERROR] Errore nella gestione client {address}: {e}")
        finally:
            self.remove_client(address)
            try:
                client_socket.close()
            except:
                pass
    
    def process_log_data(self, data, address):
        """Processa i dati di log ricevuti"""
        try:
            # Decodifica i dati ricevuti
            log_text = data.decode('utf-8', errors='ignore')
            
            # Gestisci batch di log separati da newline
            log_lines = log_text.strip().split('\n')
            
            # Filtra righe vuote e conta i log validi
            valid_logs = [line for line in log_lines if line.strip()]
            
            if valid_logs:
                self.logger.info(f"[BATCH] Ricevuti {len(valid_logs)} log da {address}")
                
                for line in valid_logs:
                    try:
                        # Prova a decodificare come JSON
                        log_data = json.loads(line.strip())
                        
                        # Gestisci heartbeat separatamente
                        if log_data.get('tag') == 'heartbeat':
                            self.logger.debug(f"[HEARTBEAT] Heartbeat da {address}")
                        else:
                            self.logger.info(f"[LOG] Log da {address}: {log_data}")
                            
                    except json.JSONDecodeError:
                        # Fallback: tratta come testo semplice
                        self.logger.info(f"[LOG] Log da {address}: {line.strip()}")
                
        except Exception as e:
            self.logger.error(f"[ERROR] Errore nel processare log da {address}: {e}")
    
    def update_client_activity(self, address):
        """Aggiorna l'ultima attività di un client"""
        for client in self.clients:
            if client['address'] == address:
                client['last_activity'] = datetime.now()
                break
    
    def remove_client(self, address):
        """Rimuove un client dalla lista"""
        self.clients = [c for c in self.clients if c['address'] != address]
        self.logger.info(f"[DISCONN] Client {address} disconnesso")
    
    def manage_clients(self):
        """Gestisce la lista dei client e rimuove quelli inattivi"""
        while self.running:
            try:
                current_time = datetime.now()
                inactive_clients = []
                
                for client in self.clients:
                    # Rimuovi client inattivi per più di 30 secondi
                    if (current_time - client['last_activity']).total_seconds() > 30:
                        inactive_clients.append(client['address'])
                
                for address in inactive_clients:
                    self.logger.warning(f"[TIMEOUT] Client {address} inattivo, rimozione...")
                    self.remove_client(address)
                
                time.sleep(10)  # Controlla ogni 10 secondi
                
            except Exception as e:
                self.logger.error(f"[ERROR] Errore nella gestione client: {e}")
                time.sleep(10)
    
    def cleanup(self):
        """Pulisce le risorse"""
        self.running = False
        
        # Chiudi tutti i client
        for client in self.clients:
            try:
                client['socket'].close()
            except:
                pass
        self.clients.clear()
        
        # Chiudi il server
        if self.server_socket:
            try:
                self.server_socket.close()
            except:
                pass
        
        self.logger.info("[CLEANUP] Pulizia completata")

def main():
    parser = argparse.ArgumentParser(description='Logcat Receiver Service')
    parser.add_argument('--host', default='0.0.0.0', help='Host per il binding (default: 0.0.0.0)')
    parser.add_argument('--port', type=int, default=8080, help='Porta per il binding (default: 8080)')
    
    args = parser.parse_args()
    
    receiver = LogcatReceiver(args.host, args.port)
    
    try:
        receiver.start_server()
    except KeyboardInterrupt:
        print("\n[STOP] Servizio interrotto")
    except Exception as e:
        print(f"[ERROR] Errore: {e}")

if __name__ == "__main__":
    main()
