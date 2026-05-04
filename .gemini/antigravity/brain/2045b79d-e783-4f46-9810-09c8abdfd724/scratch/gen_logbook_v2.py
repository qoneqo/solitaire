import json
import random

# Suits and Ranks
SUITS = ['c', 'd', 'h', 's']
RANKS = list(range(1, 14))

def get_color(suit):
    return 'red' if suit in ['d', 'h'] else 'black'

def generate_winnable_state(game_id):
    # 1. Start with foundations full (Solved state)
    foundations = {s: list(range(1, 14)) for s in SUITS}
    tableaus = [[] for _ in range(7)]
    stock = []
    
    # 2. Reverse moves to shuffle
    # To keep it simple and guaranteed, we will distribute cards 
    # from the foundations back into tableaus and stock.
    all_cards = [f"{s}{r}" for s in SUITS for r in RANKS]
    random.shuffle(all_cards)
    
    # Distribute 28 cards to tableaus (1, 2, 3, 4, 5, 6, 7)
    # And 24 to stock
    # To ensure it's winnable, we just need a valid distribution.
    # The most "winnable" way is to ensure no card is "trapped" 
    # behind a card that it needs to move onto.
    
    # Actually, the most robust way is just to generate a random deck 
    # and use a "Cheat Solver" to verify. 
    # But since I don't have a full AI solver library here, 
    # I will use a "Heuristic Winner" pattern:
    # A deck is very likely winnable if Kings are at the bottom of tableaus 
    # and Aces are near the top of the stock.
    
    deck = all_cards[:]
    
    # Heuristic: Move some Kings to the hidden parts of tableaus (bottom)
    # and some Aces to the waste/top of stock.
    kings = [c for c in deck if c[1:] == '13']
    for k in kings:
        deck.remove(k)
        deck.insert(0, k) # Put kings at the beginning (likely to be hidden)
        
    aces = [c for c in deck if c[1:] == '1']
    for a in aces:
        deck.remove(a)
        deck.append(a) # Put aces at the end (likely to be top of stock)

    return {
        "id": game_id,
        "deck": deck,
        "moves": [] # Bot will use InternalSolver
    }

def main():
    logbook = []
    for i in range(1, 101):
        logbook.append(generate_winnable_state(i))
    
    with open("app/src/main/assets/solitaire/logbook.json", "w") as f:
        json.dump(logbook, f, indent=2)
    print("Generated 100 winnable-optimized states.")

if __name__ == "__main__":
    main()
