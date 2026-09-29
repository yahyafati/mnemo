#!/usr/bin/env python3
"""Fills a Mnemo database (schema v4) with a realistic demo collection for store screenshots.

Usage: seed_demo.py mnemo.db   (a database the app has already created; existing decks, notes,
cards and review logs are replaced). See README.md in the parent folder.
"""
import json, math, random, sqlite3, sys, time, uuid
from datetime import datetime, timedelta

random.seed(7)
NOW = int(time.time() * 1000)
DAY = 86_400_000
HISTORY_DAYS = 80

BASIC, REVERSED, CLOZE, TYPEIN, MC = (f"00000000-0000-4000-8000-00000000000{i}" for i in range(1, 6))
uid = lambda: str(uuid.uuid4())

# deck path -> (category, notes). A note is (kind, fields..., optional dict(hint=, tags=, source=)).
DECKS = {
    "Spanish A2": ("Languages", [
        (REVERSED, "el ayuntamiento", "town hall"), (REVERSED, "cansado / cansada", "tired"),
        (REVERSED, "la cuenta", "the bill (at a restaurant)"), (REVERSED, "el barrio", "neighbourhood"),
        (REVERSED, "la receta", "recipe; prescription"), (REVERSED, "el alquiler", "rent"),
        (REVERSED, "mudarse", "to move (house)"), (REVERSED, "quedar con alguien", "to meet up with someone"),
        (REVERSED, "recoger", "to pick up; to collect"), (REVERSED, "la sobremesa", "time spent talking at the table after a meal"),
        (REVERSED, "aprovechar", "to make the most of"), (REVERSED, "el tráfico", "traffic"),
        (BASIC, "How do you say “I used to live in Madrid”?", "Vivía en Madrid."),
        (BASIC, "Preterite or imperfect: “Cuando era niño, ___ (jugar) al fútbol cada día.”", "jugaba (imperfect: a habit in the past)"),
        (CLOZE, "Ayer {{c1::fui}} al mercado y {{c2::compré}} unas naranjas.", "ir → fui, comprar → compré (pretérito indefinido)"),
        (CLOZE, "Si {{c1::tuviera}} más tiempo, {{c2::viajaría}} más.", "Second conditional: imperfect subjunctive + conditional."),
        (TYPEIN, "the fridge (la ___)", "nevera", dict(hint="Starts with n")),
        (TYPEIN, "the key (la ___)", "llave"),
        (MC, "Which verb means “to get up” (from bed)?", "levantarse", "acostarse\nsentarse\nquedarse"),
        (MC, "“Hace dos semanas que vivo aquí” means…", "I have lived here for two weeks.", "I lived here two weeks ago.\nI will live here for two weeks.\nI lived here for two weeks and left."),
        (BASIC, "por vs. para: “Este regalo es ___ ti.”", "para (recipient)"),
        (BASIC, "por vs. para: “Gracias ___ tu ayuda.”", "por (reason, thanks)"),
        (REVERSED, "el enchufe", "power socket; plug"),
        (REVERSED, "madrugar", "to get up early"), (REVERSED, "estrenar", "to use or wear for the first time"),
        (REVERSED, "el aeropuerto", "airport"),
        (REVERSED, "la llegada", "arrival"),
        (REVERSED, "el equipaje", "luggage"),
        (REVERSED, "el billete", "ticket"),
        (REVERSED, "retrasado", "delayed"),
        (REVERSED, "el andén", "platform"),
        (REVERSED, "la estación", "station"),
        (REVERSED, "el conductor", "driver"),
        (REVERSED, "alquilar", "to rent"),
        (REVERSED, "el seguro", "insurance"),
        (REVERSED, "la reserva", "booking"),
        (REVERSED, "la habitación", "room"),
        (REVERSED, "el ascensor", "lift"),
        (REVERSED, "la planta baja", "ground floor"),
        (REVERSED, "el desayuno", "breakfast"),
        (REVERSED, "la cena", "dinner"),
        (REVERSED, "el postre", "dessert"),
        (REVERSED, "la propina", "tip"),
        (REVERSED, "el camarero", "waiter"),
        (REVERSED, "el menú del día", "set menu"),
        (REVERSED, "picante", "spicy"),
        (REVERSED, "dulce", "sweet"),
        (REVERSED, "salado", "salty"),
        (REVERSED, "la nata", "cream"),
        (REVERSED, "el ajo", "garlic"),
        (REVERSED, "la cebolla", "onion"),
        (REVERSED, "el pimiento", "pepper"),
        (REVERSED, "el pollo", "chicken"),
        (REVERSED, "la ternera", "beef"),
        (REVERSED, "el cordero", "lamb"),
        (REVERSED, "los mariscos", "seafood"),
        (REVERSED, "la merluza", "hake"),
        (REVERSED, "las judías", "beans"),
        (REVERSED, "el aceite", "oil"),
        (REVERSED, "la harina", "flour"),
        (REVERSED, "el azúcar", "sugar"),
        (REVERSED, "la mantequilla", "butter"),
        (REVERSED, "el queso", "cheese"),
        (REVERSED, "el horno", "oven"),
        (REVERSED, "el cuchillo", "knife"),
        (REVERSED, "el tenedor", "fork"),
        (REVERSED, "la cuchara", "spoon"),
        (REVERSED, "el vaso", "glass"),
        (REVERSED, "la taza", "cup"),
        (REVERSED, "la servilleta", "napkin"),
        (REVERSED, "el mantel", "tablecloth"),
        (REVERSED, "el frigorífico", "fridge"),
        (REVERSED, "la lavadora", "washing machine"),
        (REVERSED, "la escoba", "broom"),
        (REVERSED, "el cubo", "bucket"),
        (REVERSED, "el grifo", "tap"),
        (REVERSED, "la ducha", "shower"),
        (REVERSED, "la toalla", "towel"),
        (REVERSED, "el jabón", "soap"),
        (REVERSED, "el peine", "comb"),
        (REVERSED, "el espejo", "mirror"),
        (REVERSED, "la almohada", "pillow"),
        (REVERSED, "la manta", "blanket"),
        (REVERSED, "el armario", "wardrobe"),
        (REVERSED, "la estantería", "shelf"),
        (REVERSED, "la cortina", "curtain"),
        (REVERSED, "la alfombra", "rug"),
        (REVERSED, "el sótano", "basement"),
        (REVERSED, "el tejado", "roof"),
        (REVERSED, "el jardín", "garden"),
        (REVERSED, "la valla", "fence"),
        (REVERSED, "el vecino", "neighbour"),
        (REVERSED, "la calle", "street"),
        (REVERSED, "la esquina", "corner"),
        (REVERSED, "el semáforo", "traffic light"),
        (REVERSED, "el puente", "bridge"),
        (REVERSED, "la acera", "pavement"),
        (REVERSED, "el cruce", "junction"),
        (REVERSED, "la obra", "roadworks"),
        (REVERSED, "la farmacia", "pharmacy"),
        (REVERSED, "la panadería", "bakery"),
        (REVERSED, "la carnicería", "butcher"),
        (REVERSED, "el estanco", "tobacconist"),
        (REVERSED, "la biblioteca", "library"),
        (REVERSED, "el juzgado", "court"),
        (REVERSED, "la comisaría", "police station"),
        (REVERSED, "el buzón", "letterbox"),
        (REVERSED, "el sello", "stamp"),
        (REVERSED, "el sobre", "envelope"),
        (REVERSED, "la huelga", "strike"),
        (REVERSED, "el sueldo", "salary"),
        (REVERSED, "el jefe", "boss"),
        (REVERSED, "el ascenso", "promotion"),
        (REVERSED, "la entrevista", "interview"),
        (REVERSED, "el currículum", "CV"),
        (REVERSED, "el plazo", "deadline"),
        (REVERSED, "la reunión", "meeting"),
        (REVERSED, "el borrador", "draft"),
        (REVERSED, "la factura", "invoice"),
    ]),
    "Biology::Cell Biology": ("Science", [
        (CLOZE, "The {{c1::mitochondria}} produce most of the cell’s {{c2::ATP}} through oxidative phosphorylation.", ""),
        (CLOZE, "The {{c1::Golgi apparatus}} modifies, sorts and packages proteins for secretion.", ""),
        (BASIC, "What is the function of the rough endoplasmic reticulum?", "Synthesises proteins destined for membranes or secretion; ribosomes on its surface make them.", dict(tags=["organelles"])),
        (BASIC, "Which organelle contains hydrolytic enzymes for digestion?", "The lysosome (acidic pH ≈ 4.5–5).", dict(tags=["organelles"])),
        (MC, "Which molecule carries amino acids to the ribosome?", "tRNA", "mRNA\nrRNA\nDNA polymerase"),
        (MC, "Which phase of mitosis aligns chromosomes at the cell’s equator?", "Metaphase", "Prophase\nAnaphase\nTelophase"),
        (BASIC, "Osmosis is…", "the diffusion of water across a semi-permeable membrane, from low to high solute concentration."),
        (CLOZE, "The {{c1::sodium–potassium pump}} moves {{c2::3 Na⁺}} out and {{c3::2 K⁺}} in per ATP.", "Maintains the resting membrane potential."),
        (BASIC, "What does the Krebs cycle produce per turn (per acetyl-CoA)?", "3 NADH, 1 FADH₂, 1 GTP/ATP and 2 CO₂."),
        (TYPEIN, "The double membrane pouch stacks in chloroplasts are called…", "thylakoids", dict(hint="Grana are stacks of them")),
        (BASIC, "Prokaryotes vs eukaryotes: where is the DNA?", "Prokaryotes: nucleoid region, no membrane. Eukaryotes: inside a nucleus."),
        (REVERSED, "Cytokinesis", "Division of the cytoplasm after mitosis, producing two daughter cells."),
        (CLOZE, "During {{c1::S phase}} of interphase the cell {{c2::replicates its DNA}}.", ""),
        (BASIC, "What makes the cell membrane “fluid”?", "Phospholipids and proteins move laterally; unsaturated tails and cholesterol tune fluidity (fluid mosaic model)."),
    ]),
    "Biology::Genetics": ("Science", [
        (BASIC, "State Mendel’s law of independent assortment.", "Alleles of different genes assort independently of one another during gamete formation (for unlinked genes)."),
        (MC, "A test cross is between an unknown genotype and…", "a homozygous recessive", "a heterozygote\na homozygous dominant\na carrier"),
        (CLOZE, "In a monohybrid cross of two heterozygotes (Aa × Aa) the phenotype ratio is {{c1::3:1}} and the genotype ratio is {{c2::1:2:1}}.", ""),
        (BASIC, "What is codominance?", "Both alleles are fully expressed in the heterozygote, e.g. AB blood type."),
        (TYPEIN, "The enzyme that unwinds the DNA double helix at the replication fork:", "helicase"),
        (BASIC, "Why is DNA replication called semi-conservative?", "Each new double helix has one original (parental) strand and one newly made strand."),
        (CLOZE, "Transcription happens in the {{c1::nucleus}}; translation happens at the {{c2::ribosome}}.", "Prokaryotes do both in the cytoplasm."),
        (MC, "A point mutation that changes a codon to a stop codon is…", "nonsense", "silent\nmissense\nframeshift"),
        (BASIC, "What is a Barr body?", "The condensed, inactive X chromosome in female mammalian cells."),
        (BASIC, "Hardy–Weinberg: write the genotype-frequency equation.", "\\(p^2 + 2pq + q^2 = 1\\)"),
    ]),
    "Organic Chemistry": ("Science", [
        (BASIC, "SN1 vs SN2: which one gives racemisation?", "SN1 (planar carbocation intermediate)."),
        (BASIC, "What does Markovnikov’s rule predict?", "In HX addition to an alkene, H adds to the carbon with more H’s; X ends up on the more substituted carbon."),
        (MC, "Which is the best leaving group?", "I⁻", "F⁻\nOH⁻\nNH₂⁻"),
        (BASIC, "Rate law for a bimolecular SN2 reaction?", "\\(v = k[\\text{RX}][\\text{Nu}^-]\\)"),
        (CLOZE, "An {{c1::aldehyde}} has a carbonyl at the end of the chain; a {{c2::ketone}} has it inside the chain.", ""),
        (BASIC, "Why is benzene unusually stable?", "Aromatic stabilisation: a cyclic, planar, fully conjugated ring with 4n+2 π electrons (6)."),
        (TYPEIN, "Reagent that oxidises a primary alcohol only to an aldehyde (abbr.):", "PCC", dict(hint="Pyridinium chlorochromate")),
        (BASIC, "Name the functional group R–CO–O–R′.", "Ester"),
        (MC, "Which conformation of cyclohexane is the most stable?", "Chair", "Boat\nHalf-chair\nTwist-boat"),
        (BASIC, "What is a chiral centre?", "A carbon bonded to four different groups."),
        (CLOZE, "Grignard reagents are made from an {{c1::alkyl halide}} and {{c2::magnesium}} in dry ether.", "They add to carbonyls to form alcohols."),
        (BASIC, "E2 needs which geometry?", "Anti-periplanar H and leaving group (strong base, one concerted step)."),
    ]),
    "World History": ("Humanities", [
        (BASIC, "When did the Western Roman Empire fall, and by which event is it conventionally dated?", "476 CE: Odoacer deposed Romulus Augustulus."),
        (CLOZE, "The {{c1::Treaty of Westphalia}} (1648) ended the {{c2::Thirty Years’ War}} and shaped the idea of state sovereignty.", ""),
        (MC, "Which empire was ruled by Suleiman the Magnificent?", "Ottoman Empire", "Safavid Empire\nMughal Empire\nByzantine Empire"),
        (BASIC, "What was the Columbian Exchange?", "The transfer of plants, animals, people and diseases between the Americas and the Old World after 1492."),
        (TYPEIN, "The 1215 charter that limited the power of the English king:", "Magna Carta"),
        (BASIC, "Name two causes of the French Revolution.", "Fiscal crisis of the state; social inequality of the Estates; Enlightenment ideas; food shortages."),
        (CLOZE, "The {{c1::Meiji Restoration}} began in {{c2::1868}} and modernised Japan.", ""),
        (BASIC, "What triggered the start of World War I in July 1914?", "The assassination of Archduke Franz Ferdinand in Sarajevo (28 June) and the alliance system’s chain reaction."),
        (MC, "Who led the Haitian Revolution?", "Toussaint Louverture", "Simón Bolívar\nJosé de San Martín\nJean-Jacques Dessalines only"),
        (BASIC, "What was the Silk Road?", "A network of trade routes linking China with the Mediterranean; carried goods, religions and ideas."),
        (BASIC, "Which agreement divided the New World between Spain and Portugal (1494)?", "The Treaty of Tordesillas."),
        (REVERSED, "Renaissance", "“Rebirth”: 14th–17th c. revival of classical learning and art, beginning in Italy."),
    ]),
    "Cloud Networking": ("Tech", [
        (BASIC, "What is a VPC?", "A logically isolated virtual network in a cloud provider where you define subnets, routing and security.", dict(source="Ai")),
        (BASIC, "Public vs private subnet?", "A public subnet has a route to an internet gateway; a private one doesn’t and reaches out through NAT.", dict(source="Ai")),
        (CLOZE, "A {{c1::NAT gateway}} lets instances in a private subnet start outbound connections but blocks unsolicited inbound traffic.", "", dict(source="Ai")),
        (MC, "Which layer of the OSI model does a load balancer with path-based routing operate on?", "Layer 7 (application)", "Layer 2\nLayer 3\nLayer 4", dict(source="Ai")),
        (BASIC, "Security group vs network ACL?", "Security groups are stateful and attach to instances; network ACLs are stateless and attach to subnets.", dict(source="Ai")),
        (TYPEIN, "CIDR block for 256 addresses (write the prefix, e.g. /16):", "/24", dict(source="Ai")),
        (BASIC, "What does DNS TTL control?", "How long resolvers may cache a record before asking again.", dict(source="Ai")),
        (MC, "Which protocol does TLS run on top of?", "TCP", "UDP\nICMP\nARP", dict(source="Ai")),
        (BASIC, "What is a CDN edge cache hit ratio?", "The share of requests served from the edge without contacting the origin.", dict(source="Ai")),
        (CLOZE, "A {{c1::peering connection}} routes traffic between two VPCs using private IPs, but it is {{c2::not transitive}}.", "", dict(source="Ai")),
    ]),
}

# Cards that stay new (never studied), by deck: they show up in the New counts.
NEW_SHARE = {"Spanish A2": 0.30, "Cloud Networking": 0.6, "World History": 0.25}


def ms(dt): return int(dt.timestamp() * 1000)
def local_day_start(ts): return datetime.fromtimestamp(ts / 1000).replace(hour=0, minute=0, second=0, microsecond=0)


def retrievability(t, s): return (1 + 19 / 81 * t / s) ** -0.5


def main(path):
    db = sqlite3.connect(path)
    for t in ("review_logs", "cards", "notes", "decks"):
        db.execute(f"DELETE FROM {t}")
    now_dt = datetime.fromtimestamp(NOW / 1000)
    start = local_day_start(NOW) - timedelta(days=HISTORY_DAYS)
    deck_ids, rows_cards, rows_notes, rows_logs = {}, [], [], []

    def deck_id(full):
        parts = full.split("::")
        parent = None
        for i in range(len(parts)):
            key = "::".join(parts[: i + 1])
            if key not in deck_ids:
                deck_ids[key] = uid()
                cat = DECKS[full][0]
                db.execute("INSERT INTO decks VALUES (?,?,?,?,?,?,?,?,?,?)", (
                    deck_ids[key], parent, parts[i], "", cat, 1 if key in ("Organic Chemistry",) else 0,
                    ms(start), NOW, None, (int(NOW // DAY) + 31) if key == "Organic Chemistry" else None))
            parent = deck_ids[key]
        return deck_ids[full]

    # Notes and cards, in a shuffled introduction order.
    pending = []  # (card row dict, deck path)
    for path_, (cat, notes) in DECKS.items():
        did = deck_id(path_)
        for n in notes:
            kind, *rest = n
            extra = rest.pop() if isinstance(rest[-1], dict) else {}
            fields = rest
            if kind == MC: fields = [rest[0], rest[1], rest[2]]
            if kind == CLOZE and len(fields) == 1: fields.append("")
            nid = uid()
            created = start + timedelta(days=random.randint(0, 3), hours=random.randint(8, 21))
            rows_notes.append((nid, did, kind, json.dumps(fields, ensure_ascii=False), json.dumps(extra.get("tags", [])),
                               extra.get("source", "Manual"), ms(created), NOW, None, None, extra.get("hint")))
            if kind == REVERSED: ords = [0, 1]
            elif kind == CLOZE: ords = sorted({int(x) for x in __import__("re").findall(r"\{\{c(\d+)::", fields[0])})
            else: ords = [0]
            for o in ords:
                pending.append(dict(id=uid(), note=nid, deck=did, path=path_, ord=o, created=ms(created), source=extra.get("source")))
    for n in rows_notes: db.execute("INSERT INTO notes VALUES (?,?,?,?,?,?,?,?,?,?,?)", n)

    random.shuffle(pending)
    studied = [c for c in pending if random.random() > NEW_SHARE.get(c["path"], 0.08)]
    fresh = [c for c in pending if c not in studied]
    # Introduction days: a few per day over the first 70 days; AI deck came in later (last 3 weeks).
    for i, c in enumerate(studied):
        lo = 55 if c["source"] == "Ai" else 0
        c["intro"] = start + timedelta(days=lo + int((i / len(studied)) * (HISTORY_DAYS - lo - 8)) + random.randint(0, 3), hours=random.randint(7, 21), minutes=random.randint(0, 59))

    log = lambda *a: rows_logs.append(a)
    # Days the learner skipped entirely (life happens): their cards wait a day.
    skip = {d for d in range(2, HISTORY_DAYS) if random.random() < 0.09}
    days_ago = lambda dt: (local_day_start(NOW) - local_day_start(ms(dt))).days
    quota_today_done = 0.3  # share of today's due cards already done at 15:00 (the rest stay due)
    for c in studied:
        t = c["intro"]
        S = D = None
        state, reps, lapses, last = 0, 0, 0, None
        while t <= now_dt:
            # Review at t.
            if state == 0:
                r = random.choices([1, 2, 3, 4], [0.05, 0.15, 0.6, 0.2])[0]
                S = {1: 0.4, 2: 1.2, 3: 3.1, 4: 15.5}[r]; D = {1: 8.0, 2: 6.5, 3: 5.0, 4: 3.5}[r] + random.uniform(-.8, .8)
                elapsed, before = 0, 0
            else:
                elapsed = (t - last).total_seconds() / 86400
                R = retrievability(max(elapsed, 0.01), S)
                r = 1 if random.random() > R * 0.985 else random.choices([2, 3, 4], [0.12, 0.72, 0.16])[0]
                before = 2
                if r == 1:
                    lapses += 1; S = max(0.35, S * random.uniform(0.28, 0.42)); D = min(10, D + 0.9)
                else:
                    mult = {2: 0.65, 3: 1.0, 4: 1.75}[r]
                    S = S * (1 + math.exp(1.0) * (11 - D) * S ** -0.2 * (math.exp(0.9 * (1 - R)) - 1) * mult)
                    D = max(1.2, min(10, D + (0.4 if r == 2 else -0.15 if r == 3 else -0.5)))
            reps += 1
            # A learner who asked for ~95% retention: intervals about half of the stability.
            interval = 1 if r == 1 else max(1, round(S * 0.5 * random.uniform(0.9, 1.1)))
            log(uid(), c["id"], r, before, ms(t), int(elapsed), interval, random.randint(2500, 14000), round(S, 3), round(D, 3), ms(t), ms(t), None)
            state, last = 2, t
            due = t + timedelta(days=interval)
            # The next review: on the due day, sometimes a day or two late, at a random hour.
            slack = random.choices([0, 1, 2, 5], [0.72, 0.2, 0.06, 0.02])[0]
            nxt = local_day_start(ms(due)) + timedelta(days=slack, hours=random.choice([7, 8, 9, 12, 13, 18, 19, 20, 21, 22]), minutes=random.randint(0, 59))
            if nxt >= now_dt.replace(hour=15, minute=0) and local_day_start(ms(nxt)) <= local_day_start(NOW):
                # today's slot: some are already done, the rest stay due
                if random.random() < quota_today_done: t = now_dt - timedelta(minutes=random.randint(5, 400)); c["_done_today"] = True; continue
                break
            if nxt > now_dt: break
            if random.random() < 0.02: nxt += timedelta(days=random.randint(2, 4))  # a missed stretch
            while days_ago(nxt) in skip: nxt += timedelta(days=1)
            t = nxt
        if state == 0:
            c.update(state=0, due=ms(now_dt), stab=None, diff=None, reps=0, lapses=0, last=None)
        else:
            due_ms = ms(last + timedelta(days=max(1, [l for l in rows_logs if l[1] == c["id"]][-1][6])))
            c.update(state=2, due=due_ms, stab=round(S, 3), diff=round(D, 3), reps=reps, lapses=lapses, last=ms(last))
    for c in fresh:
        c.update(state=0, due=ms(now_dt), stab=None, diff=None, reps=0, lapses=0, last=None)
    # A few learning cards due in the next hour.
    for c in random.sample([c for c in studied if c["state"] == 2 and c["reps"] > 1], 3):
        c.update(state=1, due=NOW + random.randint(2, 40) * 60_000)

    for c in pending:
        db.execute("INSERT INTO cards VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (
            c["id"], c["note"], c["deck"], c["ord"], c["state"], c["due"], c["stab"], c["diff"], None, c["last"],
            c["reps"], c["lapses"], 0, 1 if random.random() < 0.04 else 0, 0, None, c["created"], NOW, None))
    # A local "Ollama" provider that talks to mock_ai_server.py (adb reverse tcp:11435 tcp:11435).
    for tbl in ("ai_providers", "ai_models", "ai_task_routes", "ai_usage"):
        db.execute(f"DELETE FROM {tbl}")
    pid = uid()
    db.execute("INSERT INTO ai_providers VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (
        pid, "Ollama", "http://localhost:11435/v1", "ollama", "{}", "llama3.1:8b", 1, 0, 120, 1, NOW, 1, NOW, NOW, NOW, None))
    db.execute("INSERT INTO ai_models VALUES (?,?,?,?,?,?,?,?,?,?)", (pid, "llama3.1:8b", 0, 0, 1, 1, 1, NOW, NOW, None))
    db.executemany("INSERT INTO ai_usage VALUES (?,?,?,?,?,?,?,?,?,?)", [
        (uid(), pid, "Extract", "llama3.1:8b", 14, 41200, 9800, NOW, NOW, None),
        (uid(), pid, "CoAuthor", "llama3.1:8b", 6, 12300, 3100, NOW, NOW, None)])
    db.executemany("INSERT INTO review_logs VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", rows_logs)
    db.commit()
    for tbl in ("decks", "notes", "cards", "review_logs"):
        print(tbl, db.execute(f"SELECT COUNT(*) FROM {tbl}").fetchone()[0])
    print("due today:", db.execute("SELECT COUNT(*) FROM cards WHERE state=2 AND due < ?", (ms(local_day_start(NOW) + timedelta(days=1, hours=4)),)).fetchone()[0])
    db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    db.close()


if __name__ == "__main__":
    main(sys.argv[1])
