#!/usr/bin/env python3
"""A tiny OpenAI-compatible server with canned replies, so the AI screens can be shot for the store
listing without a real provider. Listens on 127.0.0.1:11435 (one above Ollama's port, so a real Ollama can keep running); forward it to the
emulator with `adb reverse tcp:11435 tcp:11435` and add an "Ollama" provider (see README.md).
"""
import json, re, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

EXTRACT = {"cards": [
    {"type": "basic", "front": "What is the main job of the Calvin cycle?", "back": "It uses ATP and NADPH from the light reactions to fix CO₂ into sugar (G3P).", "options": [], "tags": ["photosynthesis"]},
    {"type": "cloze", "front": "The light-dependent reactions take place in the {{c1::thylakoid membranes}} of the chloroplast.", "back": "", "options": [], "tags": ["photosynthesis"]},
    {"type": "basic", "front": "Which enzyme fixes CO₂ in the Calvin cycle?", "back": "RuBisCO (ribulose-1,5-bisphosphate carboxylase/oxygenase).", "options": [], "tags": ["photosynthesis", "enzymes"]},
    {"type": "choice", "front": "Where does the Calvin cycle occur?", "back": "The stroma", "options": ["The thylakoid lumen", "The cristae", "The cytosol"], "tags": ["photosynthesis"]},
    {"type": "cloze", "front": "Water is split during the light reactions, releasing {{c1::oxygen}} as a by-product.", "back": "Photolysis of water at photosystem II.", "options": [], "tags": ["photosynthesis"]},
    {"type": "basic", "front": "Why do plants look green?", "back": "Chlorophyll absorbs mostly red and blue light and reflects green.", "options": [], "tags": ["pigments"]},
    {"type": "basic", "front": "What is photorespiration?", "back": "RuBisCO binds O₂ instead of CO₂, wasting energy and releasing CO₂; it is worse in hot, dry conditions.", "options": [], "tags": ["photosynthesis"]},
    {"type": "choice", "front": "Which plants use the C4 pathway to reduce photorespiration?", "back": "Maize and sugarcane", "options": ["Wheat and rice", "Potato and tomato", "Pine and spruce"], "tags": ["photosynthesis"]},
]}
SUGGEST = {"cards": [
    {"type": "basic", "front": "What is the role of the nuclear envelope?", "back": "A double membrane that separates the nucleus from the cytoplasm; nuclear pores control what passes.", "options": [], "tags": ["organelles"]},
    {"type": "cloze", "front": "{{c1::Peroxisomes}} break down fatty acids and detoxify hydrogen peroxide with {{c2::catalase}}.", "back": "", "options": [], "tags": ["organelles"]},
    {"type": "basic", "front": "What are microtubules made of, and what do they do?", "back": "Tubulin dimers; they form the spindle, tracks for transport and cilia/flagella.", "options": [], "tags": ["cytoskeleton"]},
    {"type": "choice", "front": "Which structure gives a plant cell its rigid shape?", "back": "The cell wall (cellulose)", "options": ["The plasma membrane", "The central vacuole alone", "The cytoskeleton alone"], "tags": ["plant cells"]},
    {"type": "basic", "front": "Endocytosis vs exocytosis?", "back": "Endocytosis brings material into the cell in vesicles; exocytosis releases it by fusing vesicles with the membrane.", "options": [], "tags": ["transport"]},
    {"type": "basic", "front": "What is the function of the nucleolus?", "back": "It assembles ribosomal RNA and ribosome subunits.", "options": [], "tags": ["organelles"]},
]}
CHAT = ("Your **Cell Biology** deck covers organelles and transport well. Gaps I can see:\n\n"
        "- **Cytoskeleton**: no cards on microtubules, actin or intermediate filaments.\n"
        "- **Membrane transport**: osmosis is covered, but not endocytosis/exocytosis or facilitated diffusion.\n"
        "- **Cell signalling**: nothing yet.\n\n"
        "**Q:** What do actin filaments do? — **A:** Support cell shape, drive muscle contraction and cytokinesis.\n\n"
        "Try *Suggest missing cards* to draft a batch you can review.")
EXPLAIN = ("**Why it's right:** a load balancer that routes by URL path has to read the HTTP request, and HTTP lives at "
           "**layer 7**, the application layer. Layer 4 balancers only see IPs and ports.\n\n"
           "**Memory hook:** *7 = the level where paths and headers live.* If it looks at `/api/…`, it's layer 7.")


EXPLAIN_PUMP = ("**Why it's 2 K⁺:** the pump is *electrogenic*: each ATP moves **3 Na⁺ out** but only **2 K⁺ in**, "
                "so the cell loses one net positive charge per cycle. That keeps the inside negative, "
                "the resting membrane potential.\n\n"
                "**Memory hook:** *3 out, 2 in: the pump leaves one behind.* Sodium is the bigger number, and it leaves.")


EXPLAIN_SPHASE = ("**Why S phase:** the **S** stands for *synthesis*. Before a cell can divide, every chromosome must be "
                   "copied so each daughter cell gets a full set, and that copying happens in S phase, between G1 and G2.\n\n"
                   "**Memory hook:** *S = Synthesis = Same DNA, twice.* G1 grows, **S** copies, G2 checks, M divides.")


def reply_for(messages):
    system = next((m["content"] for m in messages if m["role"] == "system"), "")
    user = messages[-1]["content"]
    if "fill gaps in an existing deck" in system: return json.dumps(SUGGEST)
    if "You write flashcards" in system: return json.dumps(EXTRACT)
    if "You are Co-Author" in system: return CHAT
    if "improve flashcards" in system: return json.dumps({"front": "Which OSI layer routes by URL path?", "back": "Layer 7 (application)"})
    if "sodium" in user.lower(): return EXPLAIN_PUMP
    if "s phase" in user.lower(): return EXPLAIN_SPHASE
    return EXPLAIN


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a): pass

    def _json(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def do_GET(self):
        self._json({"object": "list", "data": [{"id": "llama3.1:8b", "object": "model"}]})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        text = reply_for(body["messages"])
        if not body.get("stream"):
            return self._json({"choices": [{"message": {"role": "assistant", "content": text}}], "usage": {"prompt_tokens": 900, "completion_tokens": len(text) // 4}})
        self.send_response(200); self.send_header("Content-Type", "text/event-stream"); self.end_headers()
        step = 24
        for i in range(0, len(text), step):
            chunk = {"choices": [{"delta": {"content": text[i:i + step]}}]}
            self.wfile.write(f"data: {json.dumps(chunk)}\n\n".encode()); self.wfile.flush(); time.sleep(0.05)
        done = {"choices": [{"delta": {}, "finish_reason": "stop"}], "usage": {"prompt_tokens": 900, "completion_tokens": len(text) // 4}}
        self.wfile.write(f"data: {json.dumps(done)}\n\ndata: [DONE]\n\n".encode()); self.wfile.flush()


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 11435), Handler).serve_forever()
