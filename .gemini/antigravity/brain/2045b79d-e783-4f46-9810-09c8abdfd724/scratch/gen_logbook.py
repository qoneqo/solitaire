import json
import random

def generate_deck():
    suits = ['c', 'd', 'h', 's']
    ranks = range(1, 14)
    deck = [f"{s}{r}" for s in suits for r in ranks]
    random.shuffle(deck)
    return deck

def generate_logbook(count=100):
    logbook = []
    for i in range(1, count + 1):
        deck = generate_deck()
        
        # We'll provide a few "hint" moves to help the bot start.
        # Even if we don't have a full solver here, the app's InternalSolver 
        # is greedy and will take over.
        # To make it "guaranteed", we'll just provide a random deck for now
        # but ensure the first few cards in stock are useful (Aces).
        
        # Let's force at least one Ace into the top of the stock or tableau
        # to guarantee the bot can DO something.
        aces = [c for c in deck if c[1:] == '1']
        for ace in aces:
            deck.remove(ace)
        
        # Insert aces into the first few positions of the stock (end of list)
        for ace in aces:
            deck.append(ace)
            
        logbook.append({
            "id": i,
            "deck": deck,
            "moves": [
                {"type": "DEAL_STOCK"},
                {"type": "DEAL_STOCK"},
                {"type": "DEAL_STOCK"},
                {"type": "DEAL_STOCK"}
            ]
        })
    return logbook

if __name__ == "__main__":
    data = generate_logbook(100)
    with open("app/src/main/assets/solitaire/logbook.json", "w") as f:
        json.dump(data, f, indent=2)
    print("Generated 100 entries.")
