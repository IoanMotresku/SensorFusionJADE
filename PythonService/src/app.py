import os
import sys
import logging
from typing import List, Literal, Optional
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from dotenv import load_dotenv

# Importuri PydanticAI
from pydantic_ai import Agent, RunContext
# Folosim GoogleModel (standardul nou)
from pydantic_ai.models.google import GoogleModel
from pydantic_ai.models.test import TestModel

# 1. Încărcăm variabilele de mediu
load_dotenv()

# Configurare Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("SensorAgent")

# 2. Configurare Model
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
USE_OLLAMA = os.getenv("USE_OLLAMA") == "1"

if GEMINI_API_KEY:
    logger.info("🚀 Mod activ: Google Gemini (via GoogleModel)")
    # Folosim gemini-1.5-flash (rapid și ieftin)
    MODEL = GoogleModel(
        'gemini-2.5-flash', 
        # api_key=GEMINI_API_KEY
    )
elif USE_OLLAMA:
    logger.info("🦙 Mod activ: Ollama Local")
    from pydantic_ai.models.openai import OpenAIChatModel
    from pydantic_ai.providers.ollama import OllamaProvider
    
    MODEL = OpenAIChatModel(
        model_name=os.getenv("OLLAMA_MODEL", "qwen2.5:1.5b-instruct"),
        provider=OllamaProvider(base_url=f"{os.getenv('OLLAMA_BASE', 'http://localhost:11434')}/v1"),
    )
else:
    logger.warning("🧪 Mod activ: TestModel (Mock) - Nu va genera răspunsuri reale!")
    MODEL = TestModel()

# 3. Modele de Date (Pydantic)

class Plan(BaseModel):
    """Structura răspunsului AI."""
    steps: List[str] = Field(description="Pașii logici de analiză.")
    answer: str = Field(description="Concluzia finală pentru utilizator.")

class SensorData(BaseModel):
    id: str = Field(description="ID unic al senzorului")
    status: Literal["OK", "WARNING", "ERROR"] = Field(description="Statusul curent")
    timestamp: str = Field(description="Data și ora citirii")
    type: str = Field(description="Tipul senzorului (ex: Umiditate, Temperatura)")
    unit: str = Field(description="Unitatea de măsură (ex: %, C)")
    val: float = Field(description="Valoarea numerică citită")

class AnalyzeRequest(BaseModel):
    question: str = Field(..., description="Întrebarea utilizatorului")
    sensors: List[SensorData] = Field(..., description="Lista curentă de date de la senzori")

# 4. Definirea Agentului
# Observație: Nu mai punem system_prompt aici, îl punem mai jos prin decorator
agent = Agent(
    MODEL,
    deps_type=List[SensorData],
    output_type=Plan,
    retries=2
)

# 5. System Prompt Dinamic (Aici injectăm datele senzorilor)
@agent.system_prompt
def add_sensors_to_prompt(ctx: RunContext[List[SensorData]]) -> str:
    # 1. Luăm lista de senzori din context
    sensors_list = ctx.deps
    
    # 2. O transformăm într-un text lizibil
    # Folosim model_dump_json() pentru a converti obiectele Pydantic în text JSON
    sensors_text = "\n".join([f"- {s.model_dump_json()}" for s in sensors_list])
    
    if not sensors_text:
        sensors_text = "Niciun senzor conectat/disponibil în acest moment."

    # 3. Construim instrucțiunile finale
    return (
        f"Ești un expert IoT. Analizează următoarele date LIVE de la senzori:\n"
        f"====================\n"
        f"{sensors_text}\n"
        f"====================\n"
        f"Sarcina ta: Verifică statusurile și valorile. "
        f"Dacă statusul e WARNING sau ERROR, explică de ce (bazat pe tipul senzorului). "
        f"Dacă totul e OK, confirmă scurt. "
        f"Returnează un plan structurat (steps + answer)."
    )

# 6. Tools (Unelte opționale)
@agent.tool
def get_ideal_thresholds(ctx: RunContext[List[SensorData]], sensor_type: str) -> str:
    """Returnează limitele normale pentru senzori."""
    st = sensor_type.lower()
    if "umiditate" in st:
        return "Umiditate ideală: 40% - 60%. Peste 65% risc mucegai."
    if "temp" in st:
        return "Temperatura ideală: 20°C - 24°C."
    return "Nu am date standard pentru acest senzor."

# 7. API FastAPI
app = FastAPI(title="Sistem Inteligent Senzori")

@app.post("/agent/analyze", response_model=Plan)
async def analyze_sensors(req: AnalyzeRequest) -> Plan:
    try:
        # Rulăm agentul. 'deps' sunt datele care ajung în add_sensors_to_prompt
        result = await agent.run(req.question, deps=req.sensors)
        
        # Gestionăm diferențele de versiune PydanticAI
        if hasattr(result, 'output'):
            return result.output  # Versiuni noi
        elif hasattr(result, 'data'):
            return result.data    # Versiuni vechi
        else:
            raise ValueError("Structura rezultatului agentului este necunoscută.")
            
    except Exception as e:
        logger.error(f"Eroare: {e}")
        # Returnăm eroarea completă pentru debugging
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/health")
async def health():
    return {
        "status": "online", 
        "model": "GoogleModel" if GEMINI_API_KEY else "Other"
    }

# Opțional: bloc pentru rulare directă cu `python app.py`
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)