# SensorFusionJADE

## 1. Descrierea Problemei

Acest proiect demonstrează o arhitectură de fuziune a datelor de la senzori, construită pe o platformă multi-agent (JADE) și augmentată cu capabilități de inteligență artificială. Sistemul simulează o rețea de senzori IoT (temperatură, presiune, umiditate etc.), fiecare reprezentat de un agent software autonom.

**Componentele principale sunt:**

- **Agenți Senzori (`SensorAgent`):** Fiecare agent simulează un senzor hardware. Dispune de o interfață grafică pentru a genera manual date și a simula diverse stări: `NORMAL`, `WARNING` (valori în afara pragului de siguranță) și `SENSOR_ERROR` (valori în afara limitelor hardware).
- **Agentul Controler (`ControllerAgent`):** Acționează ca un centru nervos. Primește date de la toți senzorii, monitorizează starea acestora (inclusiv detectarea senzorilor inactivi) și afișează o imagine de ansamblu în timp real printr-o interfață grafică dedicată.
- **Agentul de Bază de Date (`DBAgent`):** Arhivează toate datele primite de la senzori într-o bază de date cloud (Firebase Realtime Database), adăugând un timestamp fiecărei înregistrări.
- **Agentul de Statistică (`StatisticsAgent`):** Permite vizualizarea datelor istorice. Utilizatorul poate selecta anumiți senzori și o perioadă de timp pentru a genera grafice și tabele cu datele arhivate în Firebase.
- **Serviciul AI Python (`PythonService`):** Un microserviciu construit cu FastAPI care expune un model de limbaj (LLM), precum Gemini de la Google sau un model local prin Ollama.
- **Agentul AI (`PydanticAiAgent`):** Interoghează `DBAgent` pentru date istorice, le trimite serviciului Python împreună cu o întrebare în limbaj natural și afișează răspunsul analitic generat de AI.

Arhitectura modulară, bazată pe agenți, permite o scalabilitate și o flexibilitate ridicată, fiecare componentă fiind decuplată și având responsabilități clare.

## 2. Instalare și Configurare

### 2.1. Prerechizite

- **Java Development Kit (JDK):** Versiunea 11 sau mai recentă.
- **Python:** Versiunea 3.9 sau mai recentă.
- **Cont Firebase:** Un cont Google pentru a crea o bază de date Realtime Database.
- **(Opțional) Cheie API Gemini:** Pentru a folosi modelul AI de la Google, este necesară o cheie API de la [Google AI Studio](https://aistudio.google.com/app/apikey).
- **(Opțional) Ollama:** Pentru a rula un model AI local, este necesară instalarea [Ollama](https://ollama.ai/).

### 2.2. Configurare Java (Eclipse)

1.  **Import Proiect:** Clonați repository-ul și importați-l ca "Existing Projects into Workspace" în Eclipse.
2.  **Biblioteci:** Asigurați-vă că fișierele JAR din folderul `lib/` sunt adăugate în Java Build Path:
    - `jade.jar`
    - `flatlaf-3.5.jar`
    - `json-20231013.jar`
    Click dreapta pe proiect -> Properties -> Java Build Path -> Libraries -> Add JARs...

3.  **Configurare Firebase:**
    - Deschideți fișierul `src/com/sensorfusion/jade/agents/DBAgent.java`.
    - Înlocuiți valoarea constantei `FIREBASE_URL` cu URL-ul bazei voastre de date Firebase.
      ```java
      private static final String FIREBASE_URL = "https://your-project-id.firebaseio.com/";
      ```
    - **Notă pentru verificare:** Pentru scopurile de verificare ale proiectului, URL-ul Firebase existent în cod poate fi păstrat, deoarece este deja configurat și funcțional pentru demonstrație.


### 2.3. Configurare Serviciu Python

1.  **Navigați în folderul serviciului:**
    ```bash
    cd PythonService
    ```
2.  **Creați un mediu virtual:**
    ```bash
    python -m venv venv
    ```
3.  **Activați mediul virtual:**
    - Windows: `venv\Scripts\activate`
    - macOS/Linux: `source venv/bin/activate`

4.  **Instalați dependențele:**
    ```bash
    pip install -r requirements.txt
    ```
5.  **Configurați variabilele de mediu:**
    - Creați o copie a fișierului `.env.example` și redenumiți-o `.env`.
    - Editați fișierul `.env`:
      ```ini
      # Lăsați valoarea goală dacă nu aveți cheie sau folosiți Ollama
      GEMINI_API_KEY="AIzaSy...your...key"

      # Setați la "1" pentru a folosi un model local Ollama
      USE_OLLAMA="0" 
      # Numele modelului Ollama (ex: "llama2", "mistral")
      OLLAMA_MODEL="qwen2.5:1.5b-instruct"
      # Adresa serverului Ollama
      OLLAMA_BASE="http://localhost:11434"
      ```
    - **Notă:** Dacă `GEMINI_API_KEY` este prezent, acesta va fi folosit prioritar. Dacă este gol și `USE_OLLAMA` este "1", se va folosi Ollama. Dacă ambele sunt neconfigurate, serviciul va rula într-un mod de test (mock).

## 3. Lansarea Aplicației

Lansarea se face în doi pași: întâi se pornește serviciul Python, apoi platforma JADE.

### 3.1. Pornirea Serviciului Python

1.  Asigurați-vă că mediul virtual este activat (vezi pasul 2.3.3).
2.  Din folderul `PythonService`, rulați comanda:
    ```bash
    uvicorn src.app:app --reload
    ```
    Serverul va porni și va asculta pe `http://localhost:8000`.

### 3.2. Pornirea Platformei JADE

Aplicația Java poate fi pornită în două moduri din Eclipse.

#### Mod Standard

Acest mod pornește doar containerul principal JADE și agenții de sistem (`Controller`, `DBAgent`). Este util pentru a avea o bază pe care se pot lansa manual alți agenți.

1.  În Eclipse, găsiți clasa `src/com/sensorfusion/jade/core/SystemStarter.java`.
2.  Click dreapta pe fișier -> Run As -> Java Application.
3.  Se va deschide interfața JADE, unde puteți vedea agenții activi.

#### Mod Autostart

Acest mod pornește platforma JADE și lansează automat toate interfețele grafice ale sistemului: `LauncherGui` (pentru a crea senzori), `ControllerGui` (pentru monitorizare), `StatisticsGui` și `PydanticAiGui`.

1.  În Eclipse, deschideți configurația de rulare: Run -> Run Configurations...
2.  Selectați `SystemStarter` din lista de aplicații Java.
3.  Accesați tab-ul **Arguments**.
4.  În câmpul **Program arguments**, introduceți textul `autostart`.
5.  Click **Apply**, apoi **Run**.

![Autostart Configuration](https://i.imgur.com/your-image-here.png) <!-- Placeholder for autostart image -->

După pornire, vor apărea progresiv ferestrele pentru:
- **Lansator de Senzori:** De aici puteți crea noi agenți-senzori.
- **Controler Central:** Aici vedeți starea tuturor senzorilor activi.
- **Statistică:** Aici puteți vizualiza date istorice.
- **Interfață AI:** Aici puteți interoga modelul AI despre datele senzorilor.
